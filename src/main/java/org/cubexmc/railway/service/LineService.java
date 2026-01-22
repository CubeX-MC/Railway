package org.cubexmc.railway.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.train.TrainConsist;
import org.cubexmc.railway.train.TrainInstance;
import org.cubexmc.railway.util.LocationUtil;
import org.cubexmc.railway.service.strategy.GlobalDispatchStrategy;

public class LineService {

    private final Railway plugin;
    private final LineServiceManager manager;
    private final String lineId;
    private final DispatchStrategy dispatchStrategy;
    private final List<TrainInstance> activeTrains = new ArrayList<>();

    private int headwaySeconds;
    private int dwellTicks;
    private int trainCars;
    private long lastDepartureTick = -1L;

    public LineService(Railway plugin, LineServiceManager manager, String lineId, int headwaySeconds, int dwellTicks,
            int trainCars, DispatchStrategy dispatchStrategy) {
        this.plugin = plugin;
        this.manager = manager;
        this.lineId = lineId;
        this.headwaySeconds = headwaySeconds;
        this.dwellTicks = dwellTicks;
        this.trainCars = Math.max(1, trainCars);
        this.dispatchStrategy = dispatchStrategy;
    }

    public void tick() {
        long currentTick = Bukkit.getCurrentTick();
        if (dispatchStrategy != null) {
            dispatchStrategy.tick(this, currentTick);
        }
        updateActiveTrains(currentTick);
    }

    public void shutdown() {
        for (TrainInstance train : activeTrains) {
            train.finishImmediately();
            manager.unregisterTrain(train);
            train.cleanup();
        }
        activeTrains.clear();
    }

    public String getLineId() {
        return lineId;
    }

    public int getHeadwaySeconds() {
        return headwaySeconds;
    }

    public int getHeadwayTicks() {
        return Math.max(20, headwaySeconds * 20);
    }

    public int getDwellTicks() {
        return dwellTicks;
    }

    public int getTrainCars() {
        return trainCars;
    }

    public Railway getPlugin() {
        return plugin;
    }

    public NamespacedKey getTrainKey() {
        return manager.getTrainKey();
    }

    public BlockSectionManager getBlockSectionManager() {
        return manager.getBlockSectionManager();
    }

    public StopManager getStopManager() {
        return plugin.getStopManager();
    }

    public Line getLine() {
        return plugin.getLineManager().getLine(lineId);
    }

    public List<TrainInstance> getActiveTrains() {
        return new ArrayList<>(activeTrains);
    }

    public double getCartSpeed() {
        Line line = getLine();
        if (line != null) {
            double lineSpeed = line.getMaxSpeed();
            if (lineSpeed > 0) {
                return lineSpeed;
            }
        }
        return plugin.getCartSpeed();
    }

    public double getTrainSpacing() {
        return plugin.getTrainSpacing();
    }

    public String buildSectionKey(String fromStopId, String toStopId) {
        return lineId + ":" + fromStopId + "->" + toStopId;
    }

    public int estimateNextEtaSeconds(long currentTick) {
        int headwayTicks = getHeadwayTicks();
        if (lastDepartureTick < 0) {
            long mod = currentTick % headwayTicks;
            long etaTicks = (mod == 0) ? headwayTicks : (headwayTicks - mod);
            return (int) Math.ceil(etaTicks / 20.0);
        }
        long elapsed = currentTick - lastDepartureTick;
        if (elapsed < 0)
            elapsed = 0;
        long remaining = headwayTicks - (elapsed % headwayTicks);
        if (remaining == headwayTicks)
            remaining = 0;
        return (int) Math.ceil(remaining / 20.0);
    }

    public boolean isDepartureWindow(long currentTick) {
        if (lastDepartureTick < 0) {
            return true;
        }
        return currentTick - lastDepartureTick >= getHeadwayTicks();
    }

    public void markDeparture(long currentTick) {
        lastDepartureTick = currentTick;
    }

    public boolean tryMarkDeparture(long currentTick) {
        if (isDepartureWindow(currentTick)) {
            markDeparture(currentTick);
            return true;
        }
        return false;
    }

    public void markVirtualizedTrain(long currentTick) {
        if (isGlobalMode()) {
            return;
        }
        int headwayTicks = getHeadwayTicks();
        lastDepartureTick = Math.max(0L, currentTick - headwayTicks);
    }

    public void spawnTrain(long currentTick) {
        Line line = getLine();
        if (line == null) {
            plugin.getLogger().warning("Line " + lineId + " not found when spawning train.");
            return;
        }

        List<String> stops = line.getOrderedStopIds();
        if (stops.size() < 2) {
            plugin.getLogger().warning("Line " + lineId + " needs at least two stops to spawn trains.");
            return;
        }

        Stop startStop = plugin.getStopManager().getStop(stops.get(0));
        if (startStop == null || startStop.getStopPointLocation() == null) {
            plugin.getLogger().warning("Start stop for line " + lineId + " is not configured with a stop point.");
            return;
        }

        TrainConsist consist = spawnConsist(line, startStop);
        if (consist.getCars().isEmpty()) {
            plugin.getLogger().warning("Failed to spawn minecart consist for line " + lineId + ".");
            return;
        }

        TrainInstance train = new TrainInstance(this, line, consist, stops, currentTick, dwellTicks);
        activeTrains.add(train);
        manager.registerTrain(train);
    }

    private TrainConsist spawnConsist(Line line, Stop startStop) {
        TrainConsist consist = new TrainConsist();
        Location basePoint = LocationUtil.center(startStop.getStopPointLocation().clone());
        float yaw = startStop.getLaunchYaw();
        Vector direction = LocationUtil.vectorFromYaw(yaw);
        if (direction == null || direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        double spacing = getTrainSpacing();
        double maxSpeed = getCartSpeed();

        for (int i = 0; i < trainCars; i++) {
            Location spawnLoc = basePoint.clone().subtract(direction.clone().multiply(i * spacing));
            spawnLoc.setYaw(yaw);
            if (!LocationUtil.isRail(spawnLoc)) {
                plugin.getLogger().warning("Spawn location for train car is not on rail at " + spawnLoc);
                continue;
            }

            Minecart cart = spawnLoc.getWorld().spawn(spawnLoc, Minecart.class, minecart -> {
                minecart.setCustomName("RailwayTrain");
                minecart.setCustomNameVisible(false);
                minecart.setSlowWhenEmpty(false);
                minecart.setMaxSpeed(maxSpeed);
                minecart.setGravity(false);
            });
            consist.addCar(cart);
        }

        return consist;
    }

    private void updateActiveTrains(long currentTick) {
        Iterator<TrainInstance> iterator = activeTrains.iterator();
        while (iterator.hasNext()) {
            TrainInstance train = iterator.next();
            train.update(currentTick);
            if (train.isFinished()) {
                manager.unregisterTrain(train);
                train.cleanup();
                iterator.remove();
            }
        }
    }

    public void setHeadwaySeconds(int headwaySeconds) {
        this.headwaySeconds = headwaySeconds;
    }

    public void setDwellTicks(int dwellTicks) {
        this.dwellTicks = dwellTicks;
    }

    public void setTrainCars(int trainCars) {
        this.trainCars = Math.max(1, trainCars);
    }

    public Vector computeTravelDirection(Stop fromStop, Stop toStop) {
        if (fromStop == null || toStop == null || fromStop.getStopPointLocation() == null
                || toStop.getStopPointLocation() == null) {
            return fromStop != null ? LocationUtil.vectorFromYaw(fromStop.getLaunchYaw()) : new Vector(0, 0, 0);
        }

        Location fromLocation = LocationUtil.center(fromStop.getStopPointLocation().clone());
        Location toLocation = LocationUtil.center(toStop.getStopPointLocation().clone());
        if (fromLocation == null || toLocation == null || fromLocation.getWorld() == null
                || toLocation.getWorld() == null || !fromLocation.getWorld().equals(toLocation.getWorld())) {
            return LocationUtil.vectorFromYaw(fromStop.getLaunchYaw());
        }

        Vector direction = toLocation.toVector().subtract(fromLocation.toVector());
        direction.setY(0);
        if (direction.lengthSquared() == 0) {
            return LocationUtil.vectorFromYaw(fromStop.getLaunchYaw());
        }
        return direction.normalize();
    }

    public void handleTrainDerail(TrainInstance train) {
        train.finishImmediately();
        manager.unregisterTrain(train);
        train.cleanup();
        activeTrains.remove(train);
    }

    public boolean isGlobalMode() {
        return dispatchStrategy instanceof GlobalDispatchStrategy;
    }

    public boolean isLoopLine() {
        Line line = getLine();
        if (line == null)
            return false;
        List<String> stops = line.getOrderedStopIds();
        if (stops == null || stops.size() < 2)
            return false;
        String first = stops.get(0);
        String last = stops.get(stops.size() - 1);
        return first != null && first.equals(last);
    }

    // ===== Virtual Railway System: Multi-mode Spawn Methods =====

    /**
     * Spawn a train for a virtual train, using the configured spawn mode.
     * 
     * @param currentTick    Current server tick
     * @param fromStopIndex  The stop the train is coming from
     * @param toStopIndex    The stop the train is heading to
     * @param progress       Progress through the segment (0-1)
     * @param virtualTrainId The virtual train ID to associate
     * @param targetStopId   The stop where player demand exists
     * @return The spawned TrainInstance, or null if spawn failed
     */
    public TrainInstance spawnTrainForVirtual(long currentTick, int fromStopIndex, int toStopIndex,
            double progress, java.util.UUID virtualTrainId, String targetStopId) {
        Line line = getLine();
        if (line == null)
            return null;
        List<String> stops = line.getOrderedStopIds();
        if (stops.size() < 2)
            return null;

        org.cubexmc.railway.service.virtual.SpawnMode mode = plugin.getLocalSpawnMode();
        Location spawnLoc = null;
        float yaw = 0f;
        int effectiveFromIndex = fromStopIndex;

        switch (mode) {
            case MID_SEGMENT:
                spawnLoc = findMidSegmentSpawnLocation(stops, fromStopIndex, toStopIndex, progress);
                if (spawnLoc != null) {
                    yaw = computeYawBetweenStops(stops, fromStopIndex, toStopIndex);
                    break;
                }
                // Fall through to PREVIOUS_STOP if mid-segment fails

            case PREVIOUS_STOP:
                Stop fromStop = plugin.getStopManager().getStop(stops.get(fromStopIndex));
                if (fromStop != null && fromStop.getStopPointLocation() != null) {
                    spawnLoc = LocationUtil.center(fromStop.getStopPointLocation().clone());
                    yaw = fromStop.getLaunchYaw();
                    effectiveFromIndex = fromStopIndex;
                }
                break;

            case PLATFORM_BOUNDARY:
                spawnLoc = findPlatformBoundarySpawnLocation(stops, targetStopId, fromStopIndex);
                if (spawnLoc != null) {
                    yaw = computeYawTowardsStop(spawnLoc, targetStopId);
                } else {
                    // Fallback to previous stop
                    Stop fallbackStop = plugin.getStopManager().getStop(stops.get(fromStopIndex));
                    if (fallbackStop != null && fallbackStop.getStopPointLocation() != null) {
                        spawnLoc = LocationUtil.center(fallbackStop.getStopPointLocation().clone());
                        yaw = fallbackStop.getLaunchYaw();
                    }
                }
                break;

            case CURRENT_STOP:
                Stop targetStop = plugin.getStopManager().getStop(targetStopId);
                if (targetStop != null && targetStop.getStopPointLocation() != null) {
                    spawnLoc = LocationUtil.center(targetStop.getStopPointLocation().clone());
                    yaw = targetStop.getLaunchYaw();
                    effectiveFromIndex = stops.indexOf(targetStopId);
                    if (effectiveFromIndex < 0)
                        effectiveFromIndex = 0;
                }
                break;
        }

        if (spawnLoc == null) {
            plugin.getLogger().warning("Failed to find spawn location for line " + lineId + " in mode " + mode);
            return null;
        }

        // Ensure spawn location is on rail
        Location railLoc = findNearestRail(spawnLoc, plugin.getLocalRailSearchRadius());
        if (railLoc == null) {
            plugin.getLogger().warning("No rail found near spawn location for line " + lineId + " at " + spawnLoc);
            // Last resort: use first stop
            Stop firstStop = plugin.getStopManager().getStop(stops.get(0));
            if (firstStop != null && firstStop.getStopPointLocation() != null) {
                railLoc = LocationUtil.center(firstStop.getStopPointLocation().clone());
                yaw = firstStop.getLaunchYaw();
                effectiveFromIndex = 0;
            } else {
                return null;
            }
        } else {
            railLoc = LocationUtil.center(railLoc);
        }

        // Spawn the consist
        TrainConsist consist = spawnConsistAt(railLoc, yaw);
        if (consist.getCars().isEmpty()) {
            plugin.getLogger().warning("Failed to spawn minecart consist for line " + lineId);
            return null;
        }

        // Create train instance with adjusted state
        TrainInstance train = new TrainInstance(this, line, consist, stops, currentTick, dwellTicks);
        train.setVirtualTrainId(virtualTrainId);

        // Adjust train state based on spawn mode
        if (mode == org.cubexmc.railway.service.virtual.SpawnMode.CURRENT_STOP) {
            // Train spawned at destination, start in waiting state
            train.forceWaitingState(effectiveFromIndex, currentTick);
        } else if (mode == org.cubexmc.railway.service.virtual.SpawnMode.PLATFORM_BOUNDARY
                && fromStopIndex == toStopIndex) {
            // Simulating arrival at current stop (from previous)
            int prevIndex = effectiveFromIndex - 1;
            if (prevIndex < 0 && isLoopLine()) {
                prevIndex = stops.size() - 2;
            }

            if (prevIndex >= 0) {
                // Set as if departing from previous stop -> will target current stop
                // (effectiveFromIndex)
                train.adjustStartIndex(prevIndex, currentTick);
            } else {
                // Fallback for start of line (no previous stop)
                // Spawn at boundary of first stop means we are arriving at first stop
                train.forceArrivingState(effectiveFromIndex, currentTick);
            }
        } else {
            // Train spawned mid-route, adjust current index
            train.adjustStartIndex(effectiveFromIndex, currentTick);
        }

        activeTrains.add(train);
        manager.registerTrain(train);
        return train;
    }

    private Location findMidSegmentSpawnLocation(List<String> stops, int fromIndex, int toIndex, double progress) {
        if (fromIndex < 0 || fromIndex >= stops.size() || toIndex < 0 || toIndex >= stops.size()) {
            return null;
        }
        Stop fromStop = plugin.getStopManager().getStop(stops.get(fromIndex));
        Stop toStop = plugin.getStopManager().getStop(stops.get(toIndex));
        if (fromStop == null || toStop == null)
            return null;

        Location from = fromStop.getStopPointLocation();
        Location to = toStop.getStopPointLocation();
        if (from == null || to == null || from.getWorld() == null)
            return null;
        if (!from.getWorld().equals(to.getWorld()))
            return null;

        // Interpolate position
        double t = Math.max(0, Math.min(1, progress));
        Location interpolated = new Location(
                from.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t);

        // Find nearest rail
        return findNearestRail(interpolated, plugin.getLocalRailSearchRadius());
    }

    private Location findPlatformBoundarySpawnLocation(List<String> stops, String targetStopId, int fromIndex) {
        Stop targetStop = plugin.getStopManager().getStop(targetStopId);
        if (targetStop == null)
            return null;

        Location stopPoint = targetStop.getStopPointLocation();
        if (stopPoint == null)
            return null;

        // Calculate direction from previous stop
        // Use LaunchYaw to determine direction of travel through the station.
        // This is more reliable than calculating vector from previous stop, which
        // can be misleading if tracks curve between stations.
        Vector direction = LocationUtil.vectorFromYaw(targetStop.getLaunchYaw());

        if (direction == null) {
            direction = new Vector(1, 0, 0);
        } else {
            // Normalize just in case
            if (direction.lengthSquared() > 0) {
                direction.normalize();
            }
        }

        // Check if stop has defined boundaries
        Location corner1 = targetStop.getCorner1();
        Location corner2 = targetStop.getCorner2();

        if (corner1 != null && corner2 != null && corner1.getWorld() != null
                && corner1.getWorld().equals(corner2.getWorld())) {
            // Calculate boundary based on incoming direction
            double minX = Math.min(corner1.getX(), corner2.getX());
            double maxX = Math.max(corner1.getX(), corner2.getX());
            double minZ = Math.min(corner1.getZ(), corner2.getZ());
            double maxZ = Math.max(corner1.getZ(), corner2.getZ());

            // Find entry point on boundary
            double boundaryOffset = 3.0; // Spawn 3 blocks outside boundary
            Location entryPoint = stopPoint.clone();

            if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
                // Entering from X direction
                if (direction.getX() > 0) {
                    entryPoint.setX(minX - boundaryOffset);
                } else {
                    entryPoint.setX(maxX + boundaryOffset);
                }
            } else {
                // Entering from Z direction
                if (direction.getZ() > 0) {
                    entryPoint.setZ(minZ - boundaryOffset);
                } else {
                    entryPoint.setZ(maxZ + boundaryOffset);
                }
            }

            return findNearestRail(entryPoint, plugin.getLocalRailSearchRadius() + 3);
        }

        // No boundaries defined, spawn some distance back
        double distanceBack = 20.0;
        Location approxEntry = stopPoint.clone().subtract(direction.multiply(distanceBack));
        return findNearestRail(approxEntry, plugin.getLocalRailSearchRadius());
    }

    private float computeYawBetweenStops(List<String> stops, int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= stops.size() || toIndex < 0 || toIndex >= stops.size()) {
            return 0f;
        }
        Stop fromStop = plugin.getStopManager().getStop(stops.get(fromIndex));
        Stop toStop = plugin.getStopManager().getStop(stops.get(toIndex));
        if (fromStop == null || toStop == null)
            return fromStop != null ? fromStop.getLaunchYaw() : 0f;

        Location from = fromStop.getStopPointLocation();
        Location to = toStop.getStopPointLocation();
        if (from == null || to == null)
            return fromStop.getLaunchYaw();

        Vector dir = to.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1e-6)
            return fromStop.getLaunchYaw();

        return (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
    }

    private float computeYawTowardsStop(Location from, String toStopId) {
        Stop toStop = plugin.getStopManager().getStop(toStopId);
        if (toStop == null || toStop.getStopPointLocation() == null || from == null)
            return 0f;

        Vector dir = toStop.getStopPointLocation().toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1e-6)
            return toStop.getLaunchYaw();

        return (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
    }

    private Location findNearestRail(Location center, int radius) {
        if (center == null || center.getWorld() == null)
            return null;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location test = new Location(center.getWorld(), cx + dx, cy + dy, cz + dz);
                    if (LocationUtil.isRail(test)) {
                        double distSq = test.distanceSquared(center);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = test.clone();
                        }
                    }
                }
            }
        }
        return best;
    }

    private TrainConsist spawnConsistAt(Location basePoint, float yaw) {
        TrainConsist consist = new TrainConsist();
        Vector direction = LocationUtil.vectorFromYaw(yaw);
        if (direction == null || direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        double spacing = getTrainSpacing();
        double maxSpeed = getCartSpeed();

        for (int i = 0; i < trainCars; i++) {
            Location spawnLoc = basePoint.clone().subtract(direction.clone().multiply(i * spacing));
            spawnLoc.setYaw(yaw);

            // Try to find rail for this car
            if (!LocationUtil.isRail(spawnLoc)) {
                Location nearRail = findNearestRail(spawnLoc, 2);
                if (nearRail != null) {
                    spawnLoc = LocationUtil.center(nearRail);
                    spawnLoc.setYaw(yaw);
                } else {
                    plugin.getLogger().warning("Spawn location for train car is not on rail at " + spawnLoc);
                    continue;
                }
            }

            Minecart cart = spawnLoc.getWorld().spawn(spawnLoc, Minecart.class, minecart -> {
                minecart.setCustomName("RailwayTrain");
                minecart.setCustomNameVisible(false);
                minecart.setSlowWhenEmpty(false);
                minecart.setMaxSpeed(maxSpeed);
                minecart.setGravity(false);
            });
            consist.addCar(cart);
        }

        return consist;
    }

    /**
     * Virtualize a train back to the virtual pool.
     */
    public void virtualizeBackToPool(TrainInstance train, long currentTick) {
        if (dispatchStrategy instanceof org.cubexmc.railway.service.strategy.LocalDispatchStrategy) {
            ((org.cubexmc.railway.service.strategy.LocalDispatchStrategy) dispatchStrategy)
                    .returnTrainToPool(train, currentTick);
        }
    }
}
