package org.cubexmc.railway.train;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.service.LineService;
import org.cubexmc.railway.estimation.TravelTimeEstimator;
import org.cubexmc.railway.util.LocationUtil;
import org.cubexmc.railway.util.MinecartPhysicsUtil;
import org.cubexmc.railway.physics.TrainPhysicsEngine;
import org.cubexmc.railway.physics.KinematicRailPhysics;
import org.cubexmc.railway.physics.ReactiveRailPhysics;
import org.cubexmc.railway.physics.LeashedRailPhysics;
import org.cubexmc.railway.control.TrainControlMode;
import org.cubexmc.railway.util.SchedulerUtil;
import org.cubexmc.railway.Railway;
import org.bukkit.entity.ArmorStand;

public class TrainInstance {

    private enum TrainState {
        WAITING, MOVING, TERMINATING, FINISHED
    }

    private final UUID id = UUID.randomUUID();
    private final LineService service;
    private final Line line;
    private final TrainConsist consist;
    private final List<String> stopIds;
    private final NamespacedKey key;

    private int currentIndex;
    private int targetIndex = -1;
    private TrainState state;
    private long stateSinceTick;
    private String sectionKey;
    private Vector travelDirection;

    private boolean cleaned;
    private final int dwellTicks;
    private boolean readyToDepart;
    private long segmentStartTick = -1L;
    private final Set<UUID> segmentDepartPassengers = new HashSet<>();
    private long idleVirtualTicks = 0L;
    private int stalledTicks = 0;

    // Passenger tracking
    private final Map<UUID, Player> passengers = new HashMap<>();
    private PassengerExperience passengerExperience;
    private TrainPhysicsEngine physicsEngine;

    // Chunk loading state
    private final Set<Long> forcedChunks = new HashSet<>();
    private long lastChunkUpdateTick = 0L;

    // Virtual train system
    private UUID virtualTrainId = null;

    public TrainInstance(LineService service, Line line, TrainConsist consist, List<String> orderedStopIds,
            long spawnTick, int dwellTicks) {
        this.service = service;
        this.line = line;
        this.consist = consist;
        this.stopIds = new ArrayList<>(orderedStopIds);
        this.key = service.getTrainKey();
        this.tagMinecarts();
        this.currentIndex = 0;
        this.state = TrainState.WAITING;
        this.stateSinceTick = spawnTick;
        this.dwellTicks = Math.max(20, dwellTicks);
        this.passengerExperience = new PassengerExperience(service.getPlugin(), this);
        this.physicsEngine = selectEngine();
        this.physicsEngine.init(this);
    }

    private void tagMinecarts() {
        for (Minecart cart : consist.getCars()) {
            cart.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toString());
        }
    }

    public UUID getId() {
        return id;
    }

    public TrainConsist getConsist() {
        return consist;
    }

    public LineService getService() {
        return service;
    }

    public Line getLine() {
        return line;
    }

    public boolean isFinished() {
        return state == TrainState.FINISHED;
    }

    public boolean isMoving() {
        return state == TrainState.MOVING;
    }

    public void update(long currentTick) {
        if (state != TrainState.MOVING) {
            idleVirtualTicks = 0L;
        }
        switch (state) {
            case WAITING:
                consist.zeroVelocity();
                if (!readyToDepart && currentTick - stateSinceTick >= dwellTicks) {
                    readyToDepart = true;
                }
                if (readyToDepart) {
                    attemptDeparture(currentTick);
                } else {
                    // Show waiting UI updates periodically
                    int waitingInterval = service.getPlugin().getWaitingInterval();
                    if (waitingInterval > 0 && (currentTick - stateSinceTick) % waitingInterval == 0) {
                        updateWaitingUI(currentTick);
                    }
                }
                break;
            case MOVING:
                checkArrival(currentTick);
                if (state != TrainState.MOVING)
                    break; // State might have changed to WAITING

                double leadSpeed = getLeadSpeed();
                int subSteps = computeSubSteps(leadSpeed);
                double stepFraction = subSteps <= 1 ? 1.0 : 1.0 / subSteps;
                for (int i = 0; i < subSteps; i++) {
                    maintainVelocity(stepFraction, leadSpeed);
                }
                // If head appears stuck for a short while, nudge it once
                if (leadSpeed < 1.0e-3) {
                    stalledTicks++;
                    if (stalledTicks >= 4) {
                        Minecart lead = consist.getLeadCar();
                        if (lead != null && !lead.isDead()) {
                            Vector dir = (travelDirection != null && travelDirection.lengthSquared() > 0)
                                    ? travelDirection.clone().normalize()
                                    : new Vector(1, 0, 0);
                            Vector v = dir.multiply(Math.max(0.1, service.getCartSpeed() * 0.5));
                            if (SchedulerUtil.isFolia()) {
                                SchedulerUtil.entityRun(service.getPlugin(), lead, (Runnable) () -> {
                                    lead.setVelocity(v);
                                }, 0L, -1L);
                            } else {
                                lead.setVelocity(v);
                            }
                        }
                        stalledTicks = 0;
                    }
                } else {
                    stalledTicks = 0;
                }
                maybeVirtualize(currentTick);
                // Show journey UI updates periodically
                int departureInterval = service.getPlugin().getDepartureInterval();
                if (departureInterval > 0 && (currentTick - stateSinceTick) % departureInterval == 0) {
                    updateJourneyUI();
                }
                break;
            case TERMINATING:
                consist.zeroVelocity();
                if (currentTick - stateSinceTick >= dwellTicks) {
                    finish();
                }
                break;
            case FINISHED:
            default:
                break;
        }

        // Update chunk loading (Global mode OR occupied Local mode)
        if (service.getPlugin().isChunkLoadingEnabled()) {
            updateChunkLoading(currentTick);
        }
    }

    private void updateWaitingUI(long currentTick) {
        if (!hasPassengers()) {
            return;
        }

        long elapsed = currentTick - stateSinceTick;
        long remaining = dwellTicks - elapsed;
        int secondsRemaining = Math.max(0, (int) (remaining / 20));

        Stop currentStop = service.getStopManager().getStop(stopIds.get(currentIndex));
        Stop nextStop = null;
        if (currentIndex < stopIds.size() - 1) {
            nextStop = service.getStopManager().getStop(stopIds.get(currentIndex + 1));
        }
        Stop terminalStop = service.getStopManager().getStop(stopIds.get(stopIds.size() - 1));

        passengerExperience.onWaiting(currentStop, nextStop, terminalStop, secondsRemaining);
    }

    private void updateJourneyUI() {
        if (!hasPassengers()) {
            return;
        }

        Stop nextStop = null;
        if (targetIndex >= 0 && targetIndex < stopIds.size()) {
            nextStop = service.getStopManager().getStop(stopIds.get(targetIndex));
        }
        Stop terminalStop = service.getStopManager().getStop(stopIds.get(stopIds.size() - 1));

        passengerExperience.updateJourneyDisplay(nextStop, terminalStop);
    }

    private void attemptDeparture(long currentTick) {
        if (stopIds.size() < 2) {
            beginTermination(currentTick);
            return;
        }

        if (targetIndex == -1) {
            targetIndex = currentIndex + 1;
        }

        if (targetIndex >= stopIds.size()) {
            beginTermination(currentTick);
            return;
        }

        String fromId = stopIds.get(currentIndex);
        String toId = stopIds.get(targetIndex);

        Stop fromStop = service.getStopManager().getStop(fromId);
        Stop toStop = service.getStopManager().getStop(toId);
        if (fromStop == null || toStop == null || toStop.getStopPointLocation() == null) {
            beginTermination(currentTick);
            return;
        }

        String section = service.buildSectionKey(fromId, toId);
        if (!service.getBlockSectionManager().tryEnter(section)) {
            return;
        }
        this.sectionKey = section;

        // Determine initial boost direction
        // Always use the stop's launchYaw, as tracks can curve or loop.
        // Direct station-to-station vector is unreliable for complex geometry.
        Vector boostDir = LocationUtil.vectorFromYaw(fromStop.getLaunchYaw());
        if (boostDir == null || boostDir.lengthSquared() < 1e-6) {
            // Fallback if yaw is somehow invalid (should not happen if set correctly)
            boostDir = new Vector(0, 0, 1);
        }

        // Update travelDirection for physics usage
        travelDirection = boostDir.normalize();

        applyInitialBoost(travelDirection);
        physicsEngine.onDeparture(this, fromStop);
        state = TrainState.MOVING;
        stateSinceTick = currentTick;
        readyToDepart = false;
        segmentStartTick = currentTick;
        segmentDepartPassengers.clear();
        segmentDepartPassengers.addAll(passengers.keySet());

        // Trigger passenger departure experience
        Stop terminalStop = service.getStopManager().getStop(stopIds.get(stopIds.size() - 1));
        passengerExperience.onDeparture(fromStop, toStop, terminalStop);
    }

    private void checkArrival(long currentTick) {
        if (targetIndex < 0 || targetIndex >= stopIds.size()) {
            // Invalid target, maybe finish?
            return;
        }

        Stop targetStop = service.getStopManager().getStop(stopIds.get(targetIndex));
        if (targetStop == null || targetStop.getStopPointLocation() == null) {
            return;
        }

        Minecart lead = consist.getLeadCar();
        if (lead == null || lead.isDead()) {
            return;
        }

        double distSq = lead.getLocation().distanceSquared(targetStop.getStopPointLocation());
        double threshold = 2.0; // 2 blocks radius

        // If speed is high, we might need larger threshold, but physics aims for stop.
        // With kinematic physics, we should land pretty close.

        if (distSq < threshold * threshold) {
            // Arrived!
            arriveAtStop(targetStop, currentTick);
        }
    }

    private void arriveAtStop(Stop stop, long currentTick) {
        state = TrainState.WAITING;
        stateSinceTick = currentTick;
        currentIndex = targetIndex;
        targetIndex = -1;
        readyToDepart = false;

        consist.zeroVelocity();
        travelDirection = null;

        // Snap to exact stop location to fix alignment
        if (stop.getStopPointLocation() != null) {
            Minecart lead = consist.getLeadCar();
            if (lead != null && !lead.isDead()) {
                // Determine orientation based on rail or last movement
                float yaw = lead.getLocation().getYaw();
                if (stop.getLaunchYaw() != 0) {
                    // Could snap to launch yaw? Or keep current?
                    // Keep current is safer to avoid flipping around.
                }

                Location snapLoc = stop.getStopPointLocation().clone();
                snapLoc.setYaw(yaw);
                snapLoc.setPitch(lead.getLocation().getPitch());
                lead.teleport(snapLoc);
                MinecartPhysicsUtil.forceVelocity(lead, new Vector(0, 0, 0), service.getPlugin());
            }
        }

        physicsEngine.onArrival(this, stop, currentTick);

        // Trigger passenger arrival experience
        Stop nextStop = null;
        boolean isTerminal = currentIndex >= stopIds.size() - 1;

        if (isTerminal && service.isLoopLine()) {
            if (!stopIds.isEmpty() && stopIds.get(0).equals(stop.getId())) {
                currentIndex = 0;
            }
            isTerminal = false;
        } else if (isTerminal) {
            state = TrainState.TERMINATING;
        }

        if (!isTerminal && currentIndex < stopIds.size() - 1) {
            nextStop = service.getStopManager().getStop(stopIds.get(currentIndex + 1));
        }
        Stop terminalStop = service.getStopManager().getStop(stopIds.get(stopIds.size() - 1));
        passengerExperience.onArrival(stop, nextStop, terminalStop, isTerminal);

        // Record travel time sample for estimator
        tryRecordTravelTimeSample(currentTick);
    }

    private double getLeadSpeed() {
        Minecart lead = consist.getLeadCar();
        if (lead == null || lead.isDead()) {
            return 0.0;
        }
        return lead.getVelocity().length();
    }

    private int computeSubSteps(double leadSpeed) {
        int maxSubSteps = 12;
        if (leadSpeed <= 0.35)
            return 1;
        int steps = (int) Math.ceil(leadSpeed / 0.35);
        return Math.max(1, Math.min(steps, maxSubSteps));
    }

    private void maintainVelocity(double timeFraction, double leadSpeed) {
        Minecart lead = consist.getLeadCar();
        if (lead == null || lead.isDead()) {
            return;
        }
        physicsEngine.tick(this, timeFraction, -1L);
    }

    private boolean hasAnyPassengers() {
        for (Minecart cart : consist.getCars()) {
            if (!cart.getPassengers().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void maybeVirtualize(long currentTick) {
        if (service.isGlobalMode()) {
            idleVirtualTicks = 0L;
            return;
        }

        Railway plugin = service.getPlugin();
        if (!plugin.isLocalVirtualizationEnabled()) {
            idleVirtualTicks = 0L;
            return;
        }

        // Prevent virtualization if ANY entity is on board (not just players)
        if (hasAnyPassengers()) {
            idleVirtualTicks = 0L;
            return;
        }

        if (targetIndex < 0 || targetIndex >= stopIds.size()) {
            idleVirtualTicks = 0L;
            return;
        }

        double radius = plugin.getLocalActivationRadius();
        double radiusSq = radius * radius;
        int lookahead = plugin.getLocalVirtualLookaheadStops();

        StopManager stopManager = service.getStopManager();
        boolean demandAhead = false;
        for (int i = 0; i < lookahead && (targetIndex + i) < stopIds.size(); i++) {
            Stop candidate = stopManager.getStop(stopIds.get(targetIndex + i));
            if (candidate != null && plugin.isPlayerWithinStopRadius(candidate, radiusSq)) {
                demandAhead = true;
                break;
            }
        }

        if (demandAhead) {
            idleVirtualTicks = 0L;
            return;
        }

        idleVirtualTicks++;
        if (idleVirtualTicks >= plugin.getLocalVirtualIdleTicks()) {
            service.markVirtualizedTrain(currentTick);
            finishImmediately();
        }
    }

    // legacy trail helpers removed; physics engine owns path following

    private void applyInitialBoost(Vector direction) {
        Vector launchDir = direction != null ? direction.clone() : new Vector(0, 0, 1);
        if (launchDir.lengthSquared() == 0) {
            launchDir = new Vector(0, 0, 1);
        }
        launchDir.normalize();

        double initialSpeed = service.getCartSpeed();

        List<Minecart> cars = consist.getCars();
        for (Minecart cart : cars) {
            if (cart == null || cart.isDead()) {
                continue;
            }
            Vector v = launchDir.clone().multiply(initialSpeed);
            if (SchedulerUtil.isFolia()) {
                SchedulerUtil.entityRun(service.getPlugin(), cart, (Runnable) () -> {
                    cart.setVelocity(v);
                }, 0L, -1L);
            } else {
                cart.setVelocity(v);
            }
        }
    }

    public void handleArrival(Stop stop, long currentTick) {
        if (state != TrainState.MOVING) {
            return;
        }

        String targetId = getTargetStopId();
        if (targetId == null || !targetId.equals(stop.getId())) {
            return;
        }

        consist.zeroVelocity();
        if (sectionKey != null) {
            service.getBlockSectionManager().leave(sectionKey);
            sectionKey = null;
        }
        travelDirection = null;
        state = TrainState.WAITING;
        stateSinceTick = currentTick;
        readyToDepart = false;
        currentIndex = targetIndex;
        targetIndex = -1;

        boolean isTerminal = currentIndex >= stopIds.size() - 1;
        if (isTerminal && service.isLoopLine()) {
            // Treat loop lines as continuous service: wrap to first stop and continue
            // waiting
            if (!stopIds.isEmpty() && stopIds.get(0).equals(stop.getId())) {
                currentIndex = 0;
            }
            isTerminal = false;
        } else if (isTerminal) {
            state = TrainState.TERMINATING;
            stateSinceTick = currentTick;
        }

        physicsEngine.onArrival(this, stop, currentTick);
        // Trigger passenger arrival experience
        Stop nextStop = null;
        Stop terminalStop = service.getStopManager().getStop(stopIds.get(stopIds.size() - 1));
        if (!isTerminal && currentIndex < stopIds.size() - 1) {
            nextStop = service.getStopManager().getStop(stopIds.get(currentIndex + 1));
        }
        passengerExperience.onArrival(stop, nextStop, terminalStop, isTerminal);

        // Record travel time sample for estimator
        tryRecordTravelTimeSample(currentTick);
    }

    private void beginTermination(long currentTick) {
        if (sectionKey != null) {
            service.getBlockSectionManager().leave(sectionKey);
            sectionKey = null;
        }
        travelDirection = null;
        state = TrainState.TERMINATING;
        stateSinceTick = currentTick;
    }

    private void finish() {
        consist.zeroVelocity();
        state = TrainState.FINISHED;
    }

    public void finishImmediately() {
        if (sectionKey != null) {
            service.getBlockSectionManager().leave(sectionKey);
            sectionKey = null;
        }
        travelDirection = null;
        state = TrainState.FINISHED;
    }

    public void cleanup() {
        if (cleaned) {
            return;
        }
        cleaned = true;
        physicsEngine.cleanup(this);
        // Release forced chunks
        releaseAllForcedChunks();
        if (sectionKey != null) {
            service.getBlockSectionManager().leave(sectionKey);
            sectionKey = null;
        }
        for (Minecart cart : consist.getCars()) {
            cart.getPersistentDataContainer().remove(key);
            if (!cart.isDead()) {
                for (Entity passenger : new ArrayList<>(cart.getPassengers())) {
                    if (passenger instanceof Player) {
                        ScoreboardManager.clearPlayerDisplay((Player) passenger);
                    }
                    passenger.leaveVehicle();
                }
                cart.remove();
            }
        }
        consist.clear();
    }

    public String getTargetStopId() {
        if (targetIndex >= 0 && targetIndex < stopIds.size()) {
            return stopIds.get(targetIndex);
        }
        return null;
    }

    // Helpers for local strategy ETA
    public String getCurrentFromStopId() {
        if (targetIndex >= 0 && currentIndex >= 0 && currentIndex < stopIds.size()) {
            return stopIds.get(currentIndex);
        }
        return null;
    }

    public String getCurrentToStopId() {
        if (targetIndex >= 0 && targetIndex < stopIds.size()) {
            return stopIds.get(targetIndex);
        }
        return null;
    }

    public double getSegmentElapsedSeconds(long currentTick) {
        if (state == TrainState.MOVING) {
            return Math.max(0.0, (currentTick - stateSinceTick) / 20.0);
        }
        return 0.0;
    }

    public double estimateEtaSecondsToStop(String stopId, long currentTick, TravelTimeEstimator estimator) {
        if (stopId == null || estimator == null)
            return Double.POSITIVE_INFINITY;
        List<String> stops = this.stopIds;
        int pos = stops.indexOf(stopId);
        if (pos < 0)
            return Double.POSITIVE_INFINITY;

        boolean loop = service.isLoopLine();
        int n = stops.size();
        if (n < 2)
            return Double.POSITIVE_INFINITY;

        if (state == TrainState.WAITING) {
            if (currentIndex == pos) {
                return 0.0;
            }
            // sum from next (currentIndex+1) to pos, with wrap if loop
            double sum = 0.0;
            int i = currentIndex;
            while (true) {
                int from = i;
                int to = (i + 1) % n;
                if (!loop && to <= from)
                    return Double.POSITIVE_INFINITY; // cannot wrap on non-loop
                sum += estimator.estimateSeconds(service.getLineId(), stops.get(from), stops.get(to));
                if (to == pos)
                    break;
                i = to;
            }
            return sum;
        }

        if (state == TrainState.MOVING) {
            if (targetIndex < 0)
                return Double.POSITIVE_INFINITY;
            double elapsed = getSegmentElapsedSeconds(currentTick);
            double segTotal = estimator.estimateSeconds(service.getLineId(), stops.get(currentIndex),
                    stops.get(targetIndex));
            double remaining = Math.max(0.0, segTotal - elapsed);
            if (pos == targetIndex) {
                return remaining;
            }
            double sum = remaining;
            int i = targetIndex;
            while (true) {
                int from = i;
                int to = (i + 1) % n;
                if (!loop && to <= from)
                    return Double.POSITIVE_INFINITY;
                sum += estimator.estimateSeconds(service.getLineId(), stops.get(from), stops.get(to));
                if (to == pos)
                    break;
                i = to;
            }
            return sum;
        }

        return Double.POSITIVE_INFINITY;
    }

    public boolean isLead(Minecart cart) {
        Minecart lead = consist.getLeadCar();
        return lead != null && lead.getUniqueId().equals(cart.getUniqueId());
    }

    public Vector getTravelDirection() {
        return travelDirection;
    }

    private TrainPhysicsEngine selectEngine() {
        // Determine effective mode: line override or global default
        TrainControlMode mode;
        String override = line.getControlMode();
        if (override != null && !override.isEmpty()) {
            mode = TrainControlMode.from(override, service.getPlugin().getDefaultControlMode());
        } else {
            mode = service.getPlugin().getDefaultControlMode();
        }
        switch (mode) {
            case REACTIVE:
                return new ReactiveRailPhysics();
            case LEASHED:
                return new LeashedRailPhysics();
            case KINEMATIC:
            default:
                return new KinematicRailPhysics();
        }
    }

    // Passenger management methods

    public void addPassenger(Player player, Minecart cart) {
        if (player != null && cart != null && consist.getCars().contains(cart)) {
            passengers.put(player.getUniqueId(), player);
        }
    }

    public void removePassenger(Player player) {
        if (player != null) {
            passengers.remove(player.getUniqueId());
        }
    }

    public List<Player> getPassengers() {
        List<Player> result = new ArrayList<>();
        for (Player player : passengers.values()) {
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    public boolean hasPassengers() {
        for (Player player : passengers.values()) {
            if (player != null && player.isOnline()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPassenger(Player player) {
        return player != null && passengers.containsKey(player.getUniqueId());
    }

    public Minecart getPassengerCart(Player player) {
        if (player == null || player.getVehicle() == null) {
            return null;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart && consist.getCars().contains((Minecart) vehicle)) {
            return (Minecart) vehicle;
        }

        return null;
    }

    private void tryRecordTravelTimeSample(long currentTick) {
        if (segmentStartTick < 0 || targetIndex < 0 || targetIndex >= stopIds.size()) {
            segmentStartTick = -1L;
            segmentDepartPassengers.clear();
            return;
        }
        String fromId = stopIds.get(Math.max(0, currentIndex - 1));
        String toId = stopIds.get(currentIndex);
        double durationSeconds = Math.max(0.0, (currentTick - segmentStartTick) / 20.0);

        boolean hasCompletePassenger = false;
        for (UUID uid : segmentDepartPassengers) {
            if (passengers.containsKey(uid)) {
                hasCompletePassenger = true;
                break;
            }
        }

        double weight;
        if (hasCompletePassenger) {
            weight = 1.0;
        } else if (service.getPlugin().isUseUnboardedSamples()) {
            weight = service.getPlugin().getUnboardedSampleWeight();
        } else {
            weight = 0.0;
        }

        if (weight > 0.0) {
            service.getPlugin().getTravelTimeEstimator().record(
                    service.getLineId(), fromId, toId, durationSeconds, weight);
        }

        // Reset segment tracking
        segmentStartTick = -1L;
        segmentDepartPassengers.clear();
    }

    // ===== Chunk loading (global mode) =====
    private void updateChunkLoading(long currentTick) {
        // Throttle updates
        Railway plugin = service.getPlugin();
        int interval = plugin.getChunkLoadingUpdateIntervalTicks();
        if (interval > 1 && (currentTick - lastChunkUpdateTick) < interval) {
            return;
        }
        lastChunkUpdateTick = currentTick;

        // Enable chunk loading if Global Mode OR if we have passengers (even in Local
        // Mode)
        // This ensures entities can travel autonomously without unloading
        boolean shouldLoad = service.isGlobalMode() || hasAnyPassengers();

        if (!shouldLoad || !service.getPlugin().isChunkLoadingEnabled()) {
            return;
        }

        if (plugin.isChunkLoadingOnlyWhenMoving() && state != TrainState.MOVING) {
            // Not moving: optionally release all
            releaseAllForcedChunks();
            return;
        }

        List<Minecart> cars = consist.getCars();
        if (cars.isEmpty()) {
            releaseAllForcedChunks();
            return;
        }

        int radius = Math.max(0, plugin.getChunkLoadingRadius());
        int fwd = Math.max(0, plugin.getForwardPreloadRadius());

        Set<Long> desired = new HashSet<>();
        // Moving window around each car
        for (Minecart cart : cars) {
            if (cart == null || cart.isDead())
                continue;
            Location loc = cart.getLocation();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            addChunkSquare(desired, cx, cz, radius);
        }

        // Forward preloading ahead of lead car
        Minecart lead = consist.getLeadCar();
        if (lead != null && !lead.isDead() && fwd > 0) {
            Vector dir = (travelDirection != null && travelDirection.lengthSquared() > 0)
                    ? travelDirection.clone().setY(0)
                    : lead.getVelocity().clone().setY(0);
            if (dir.lengthSquared() > 0.0001) {
                dir.normalize();
                Location base = lead.getLocation();
                for (int step = 1; step <= fwd; step++) {
                    Location ahead = base.clone().add(dir.clone().multiply(16 * step));
                    int acx = ahead.getBlockX() >> 4;
                    int acz = ahead.getBlockZ() >> 4;
                    addChunkSquare(desired, acx, acz, radius);
                }
            }
        }

        // Apply diff: force new, release old
        // Add new
        for (Long key : desired) {
            if (!forcedChunks.contains(key)) {
                int cx = (int) (key >> 32);
                int cz = (int) (key & 0xffffffffL);
                forceChunk(cx, cz, true);
            }
        }
        // Remove old
        for (Long key : new HashSet<>(forcedChunks)) {
            if (!desired.contains(key)) {
                int cx = (int) (key >> 32);
                int cz = (int) (key & 0xffffffffL);
                forceChunk(cx, cz, false);
            }
        }

        // Update current set
        forcedChunks.clear();
        forcedChunks.addAll(desired);
    }

    private void addChunkSquare(Set<Long> out, int cx, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(packChunk(cx + dx, cz + dz));
            }
        }
    }

    private long packChunk(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    private void forceChunk(int cx, int cz, boolean forced) {
        World world = getLeadWorld();
        if (world == null)
            return;
        // Use region scheduler for Folia safety; pick a location in the target chunk
        Location center = new Location(world, (cx << 4) + 8, Math.max(world.getMinHeight() + 1, 64), (cz << 4) + 8);
        SchedulerUtil.regionRun(service.getPlugin(), center, () -> {
            try {
                world.setChunkForceLoaded(cx, cz, forced);
            } catch (Throwable t) {
                service.getPlugin().getLogger()
                        .warning("Failed to set chunk force-loaded at " + cx + "," + cz + ": " + t.getMessage());
            }
        }, 0L, -1L);
    }

    private World getLeadWorld() {
        Minecart lead = consist.getLeadCar();
        return (lead != null) ? lead.getWorld() : null;
    }

    private void releaseAllForcedChunks() {
        if (forcedChunks.isEmpty())
            return;
        World world = getLeadWorld();
        if (world == null) {
            forcedChunks.clear();
            return;
        }
        for (Long key : new HashSet<>(forcedChunks)) {
            int cx = (int) (key >> 32);
            int cz = (int) (key & 0xffffffffL);
            forceChunk(cx, cz, false);
        }
        forcedChunks.clear();
    }

    public void prepareForBoarding(Minecart cart) {
        // No-op, conductor removed
    }

    // ===== Virtual Train System Methods =====

    public void setVirtualTrainId(UUID id) {
        this.virtualTrainId = id;
    }

    public UUID getVirtualTrainId() {
        return virtualTrainId;
    }

    /**
     * Force the train into WAITING state at a specific stop.
     * Used when spawning directly at the target stop.
     */
    public void forceWaitingState(int stopIndex, long currentTick) {
        this.currentIndex = Math.max(0, Math.min(stopIndex, stopIds.size() - 1));
        this.targetIndex = -1;
        this.state = TrainState.WAITING;
        this.stateSinceTick = currentTick;
        this.readyToDepart = false;
        this.travelDirection = null;
        this.segmentProgress = 0.0;
        consist.zeroVelocity();
    }

    /**
     * Force the train into MOVING state treating it as arriving at targetIndex.
     */
    public void forceArrivingState(int targetIndex, long currentTick) {
        forceArrivingState(targetIndex, currentTick, null);
    }

    /**
     * Force the train into MOVING state with explicit direction.
     */
    public void forceArrivingState(int targetIndex, long currentTick, Vector explicitDirection) {
        this.targetIndex = Math.max(0, Math.min(targetIndex, stopIds.size() - 1));
        this.currentIndex = this.targetIndex; // For ETA calculation, assume we are in the segment ending at target
        this.state = TrainState.MOVING;
        this.stateSinceTick = currentTick;
        this.readyToDepart = false;

        // Calculate travel direction
        if (explicitDirection != null && explicitDirection.lengthSquared() > 1e-6) {
            this.travelDirection = explicitDirection.clone().normalize();
        } else {
            // Fallback to launchYaw if no explicit direction provided
            Stop stop = service.getStopManager().getStop(stopIds.get(this.targetIndex));
            if (stop != null) {
                this.travelDirection = LocationUtil.vectorFromYaw(stop.getLaunchYaw());
            }
        }

        if (this.travelDirection == null || this.travelDirection.lengthSquared() < 1e-6) {
            this.travelDirection = new Vector(0, 0, 1);
        }

        // Initial velocity to start moving
        applyInitialBoost(this.travelDirection);
    }

    /**
     * Adjust the starting index for a train spawned mid-route.
     * The train will depart from this index.
     */
    public void adjustStartIndex(int startIndex, long currentTick) {
        this.currentIndex = Math.max(0, Math.min(startIndex, stopIds.size() - 1));
        this.targetIndex = -1;
        this.state = TrainState.WAITING;
        this.stateSinceTick = currentTick;
        // Set ready to depart immediately if spawned mid-route
        this.readyToDepart = true;
    }

    // Field to track segment progress for virtualization
    private double segmentProgress = 0.0;

    /**
     * Get current state for virtualization.
     * Returns [currentIndex, targetIndex, segmentProgress, isWaiting]
     */
    public VirtualizationState getVirtualizationState(long currentTick) {
        boolean isWaiting = (state == TrainState.WAITING || state == TrainState.TERMINATING);
        double progress = 0.0;

        if (state == TrainState.MOVING && targetIndex >= 0) {
            // Calculate progress through current segment
            double elapsed = getSegmentElapsedSeconds(currentTick);
            double total = service.getPlugin().getTravelTimeEstimator()
                    .estimateSeconds(service.getLineId(),
                            stopIds.get(currentIndex),
                            stopIds.get(targetIndex));
            if (total > 0) {
                progress = Math.min(1.0, elapsed / total);
            }
        }

        return new VirtualizationState(currentIndex, targetIndex, progress, isWaiting);
    }

    /**
     * State data for virtualizing a physical train back to virtual.
     */
    public static class VirtualizationState {
        public final int currentIndex;
        public final int targetIndex;
        public final double progress;
        public final boolean isWaiting;

        public VirtualizationState(int currentIndex, int targetIndex, double progress, boolean isWaiting) {
            this.currentIndex = currentIndex;
            this.targetIndex = targetIndex;
            this.progress = progress;
            this.isWaiting = isWaiting;
        }
    }
}
