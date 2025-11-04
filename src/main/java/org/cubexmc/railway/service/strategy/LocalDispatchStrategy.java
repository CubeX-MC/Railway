package org.cubexmc.railway.service.strategy;

import java.util.List;

import org.cubexmc.railway.Railway;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.service.DispatchStrategy;
import org.cubexmc.railway.service.LineService;
import org.cubexmc.railway.train.TrainInstance;

/**
 * Local mode dispatch: defer physical spawning until needed. Use ETA estimates to schedule a
 * spawn so that the train virtually 'arrives' at a player-occupied station after a short lead time.
 * If a passenger-carrying train is already approaching that station soon, suppress spawning.
 */
public class LocalDispatchStrategy implements DispatchStrategy {

    private long scheduledSpawnTick = -1L;
    private String scheduledForStopId = null;

    @Override
    public void tick(LineService service, long currentTick) {
        // Execute a scheduled spawn when time comes (re-check suppression before spawning)
        if (scheduledSpawnTick > 0 && currentTick >= scheduledSpawnTick) {
            if (!isLoopTrainArrivingSoon(service, currentTick)
                    && !isSuppressedByIncomingPassengerTrain(service, currentTick, scheduledForStopId)) {
                // Keep headway clock consistent when executing a scheduled spawn
                service.markDeparture(currentTick);
                service.spawnTrain(currentTick);
            }
            scheduledSpawnTick = -1L;
            scheduledForStopId = null;
        }

        // Headway gate: on new departure slot, compute next scheduled spawn if demand exists
        if (service.isDepartureWindow(currentTick)) {
            Stop demandStop = findPlayerOccupiedStop(service);
            if (demandStop == null) {
                // no demand visible -> do not spawn now
                return;
            }

            // If incoming passenger train will arrive soon, do not schedule a spawn
            if (isSuppressedByIncomingPassengerTrain(service, currentTick, demandStop.getId())) {
                return;
            }

            if (service.isLoopLine() && isLoopTrainArrivingSoon(service, currentTick)) {
                return;
            }

            long spawnAt = computeSpawnTickForArrivalLead(service, currentTick, demandStop);
            if (spawnAt <= currentTick) {
                // spawn immediately
                service.markDeparture(currentTick);
                service.spawnTrain(currentTick);
            } else {
                service.markDeparture(currentTick);
                scheduledSpawnTick = spawnAt;
                scheduledForStopId = demandStop.getId();
            }
        }
    }

    private Stop findPlayerOccupiedStop(LineService service) {
        Line line = service.getLine();
        if (line == null) return null;
        List<String> stopIds = line.getOrderedStopIds();
        Railway plugin = service.getPlugin();
        double radius = plugin.getLocalActivationRadius();
        double radiusSq = radius * radius;
        for (String sid : stopIds) {
            Stop s = plugin.getStopManager().getStop(sid);
            if (s == null || s.getStopPointLocation() == null) continue;
            if (plugin.isPlayerWithinStopRadius(s, radiusSq)) {
                return s;
            }
        }
        return null;
    }

    private boolean isSuppressedByIncomingPassengerTrain(LineService service, long currentTick, String stopId) {
        if (stopId == null) return false;
        Railway plugin = service.getPlugin();
        double threshold = plugin.getLocalSuppressThresholdSeconds();
        for (TrainInstance t : service.getActiveTrains()) {
            if (!t.isMoving() || !t.hasPassengers()) continue;
            String target = t.getTargetStopId();
            if (target == null || !target.equals(stopId)) continue;
            String fromId = t.getCurrentFromStopId();
            String toId = t.getCurrentToStopId();
            if (fromId == null || toId == null) continue;
            double total = plugin.getTravelTimeEstimator().estimateSeconds(service.getLineId(), fromId, toId);
            double elapsed = t.getSegmentElapsedSeconds(currentTick);
            double remaining = Math.max(0.0, total - elapsed);
            if (remaining <= threshold) {
                return true;
            }
        }
        return false;
    }

    private long computeSpawnTickForArrivalLead(LineService service, long currentTick, Stop demandStop) {
        Railway plugin = service.getPlugin();
        Line line = service.getLine();
        List<String> stops = line.getOrderedStopIds();
        if (stops.isEmpty()) return currentTick;
        String first = stops.get(0);
        String target = demandStop.getId();
        double travelSeconds = 0.0;
        boolean started = false;
        for (int i = 0; i < stops.size() - 1; i++) {
            String from = stops.get(i);
            String to = stops.get(i + 1);
            if (!started && from.equals(first)) started = true;
            if (started) {
                travelSeconds += plugin.getTravelTimeEstimator().estimateSeconds(service.getLineId(), from, to);
                if (to.equals(target)) break;
            }
        }
        long lead = plugin.getLocalSpawnLeadTicks();
        long spawnTick = currentTick + lead - (long) Math.round(travelSeconds * 20.0);
        return Math.max(currentTick, spawnTick);
    }

    private boolean isLoopTrainArrivingSoon(LineService service, long currentTick) {
        if (!service.isLoopLine()) {
            return false;
        }
        Line line = service.getLine();
        if (line == null) return false;
        List<String> ordered = line.getOrderedStopIds();
        if (ordered.isEmpty()) return false;
        String startStopId = ordered.get(0);
        double threshold = Math.max(2.0, service.getHeadwaySeconds() * 0.5);
        for (TrainInstance train : service.getActiveTrains()) {
            double eta = train.estimateEtaSecondsToStop(startStopId, currentTick,
                    service.getPlugin().getTravelTimeEstimator());
            if (Double.isFinite(eta) && eta <= threshold) {
                return true;
            }
        }
        return false;
    }
}


