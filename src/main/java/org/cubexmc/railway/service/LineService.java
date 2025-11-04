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
        if (elapsed < 0) elapsed = 0;
        long remaining = headwayTicks - (elapsed % headwayTicks);
        if (remaining == headwayTicks) remaining = 0;
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
        if (line == null) return false;
        List<String> stops = line.getOrderedStopIds();
        if (stops == null || stops.size() < 2) return false;
        String first = stops.get(0);
        String last = stops.get(stops.size() - 1);
        return first != null && first.equals(last);
    }
}

