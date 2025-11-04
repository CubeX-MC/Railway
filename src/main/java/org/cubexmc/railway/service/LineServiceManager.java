package org.cubexmc.railway.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.LineManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.service.strategy.GlobalDispatchStrategy;
import org.cubexmc.railway.service.strategy.LocalDispatchStrategy;
import org.cubexmc.railway.train.TrainInstance;
import org.cubexmc.railway.util.SchedulerUtil;

public class LineServiceManager {

    private final Railway plugin;
    private final Map<String, LineService> lineIdToService = new HashMap<>();
    private final Map<UUID, TrainInstance> trainsById = new HashMap<>();
    private final Map<UUID, TrainInstance> trainsByMinecart = new HashMap<>();
    private final NamespacedKey trainKey;
    private final BlockSectionManager blockSectionManager = new BlockSectionManager();
    private Object heartbeat;
    private final OperationMode operationMode;

    public LineServiceManager(Railway plugin) {
        this.plugin = plugin;
        this.operationMode = OperationMode.from(plugin.getConfig().getString("service.mode", "local"), OperationMode.LOCAL);
        this.trainKey = new NamespacedKey(plugin, "train-id");

        LineManager lineManager = plugin.getLineManager();
        for (Line line : lineManager.getAllLines()) {
            if (line.isServiceEnabled()) {
                createAndRegisterService(line.getId(), line.getHeadwaySeconds(), line.getDwellTicks(), line.getTrainCars());
            }
        }

        startHeartbeat();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        this.heartbeat = SchedulerUtil.globalRun(plugin, () -> {
            for (LineService service : lineIdToService.values()) {
                service.tick();
            }
        }, 1L, 1L);
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            SchedulerUtil.cancelTask(heartbeat);
            heartbeat = null;
        }
    }

    public void shutdown() {
        stopHeartbeat();
        for (LineService service : lineIdToService.values()) {
            service.shutdown();
        }
        lineIdToService.clear();
        trainsById.clear();
        trainsByMinecart.clear();
    }

    public LineService getService(String lineId) {
        return lineIdToService.get(lineId);
    }

    public void registerService(String lineId, LineService service) {
        lineIdToService.put(lineId, service);
    }

    public LineService createAndRegisterService(String lineId, int headwaySeconds, int dwellTicks, int trainCars) {
        DispatchStrategy strategy = createStrategy();
        LineService service = new LineService(plugin, this, lineId, headwaySeconds, dwellTicks, trainCars, strategy);
        registerService(lineId, service);
        return service;
    }

    private DispatchStrategy createStrategy() {
        switch (operationMode) {
            case GLOBAL:
                return new GlobalDispatchStrategy();
            case LOCAL:
            default:
                return new LocalDispatchStrategy();
        }
    }

    public int estimateNextEtaSeconds(String lineId, String stopId) {
        LineService service = lineIdToService.get(lineId);
        if (service == null) return plugin.getConfig().getInt("service.default_headway_seconds", 120);
        long currentTick = Bukkit.getCurrentTick();
        return service.estimateNextEtaSeconds(currentTick);
    }

    public void startService(Line line) {
        if (line == null) return;
        if (lineIdToService.containsKey(line.getId())) return;
        createAndRegisterService(line.getId(), line.getHeadwaySeconds(), line.getDwellTicks(), line.getTrainCars());
    }

    public void stopService(String lineId) {
        LineService service = lineIdToService.remove(lineId);
        if (service != null) {
            service.shutdown();
        }
    }

    public void rebuildFromLines() {
        stopHeartbeat();
        for (LineService service : lineIdToService.values()) {
            service.shutdown();
        }
        lineIdToService.clear();
        trainsById.clear();
        trainsByMinecart.clear();

        LineManager lineManager = plugin.getLineManager();
        for (Line line : lineManager.getAllLines()) {
            if (line.isServiceEnabled()) {
                createAndRegisterService(line.getId(), line.getHeadwaySeconds(), line.getDwellTicks(), line.getTrainCars());
            }
        }
        startHeartbeat();
    }

    public BlockSectionManager getBlockSectionManager() {
        return blockSectionManager;
    }

    public NamespacedKey getTrainKey() {
        return trainKey;
    }

    public void registerTrain(TrainInstance train) {
        trainsById.put(train.getId(), train);
        train.getConsist().getCars().forEach(car -> trainsByMinecart.put(car.getUniqueId(), train));
    }

    public void unregisterTrain(TrainInstance train) {
        trainsById.remove(train.getId());
        train.getConsist().getCars().forEach(car -> trainsByMinecart.remove(car.getUniqueId()));
    }

    public TrainInstance getTrainByMinecart(UUID minecartId) {
        return trainsByMinecart.get(minecartId);
    }
}

