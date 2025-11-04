package org.cubexmc.railway.service.strategy;

import java.util.List;

import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.service.DispatchStrategy;
import org.cubexmc.railway.service.LineService;
import org.cubexmc.railway.train.TrainInstance;

public class GlobalDispatchStrategy implements DispatchStrategy {
    @Override
    public void tick(LineService service, long currentTick) {
        if (!service.isDepartureWindow(currentTick)) {
            return;
        }

        if (service.isLoopLine() && isLoopTrainArrivingSoon(service, currentTick)) {
            return;
        }

        service.markDeparture(currentTick);
        service.spawnTrain(currentTick);
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


