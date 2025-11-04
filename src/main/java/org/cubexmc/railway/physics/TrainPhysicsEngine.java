package org.cubexmc.railway.physics;

import org.cubexmc.railway.train.TrainInstance;
import org.cubexmc.railway.model.Stop;

public interface TrainPhysicsEngine {

    void init(TrainInstance train);

    void onDeparture(TrainInstance train, Stop fromStop);

    void tick(TrainInstance train, double timeFraction, long currentTick);

    void onArrival(TrainInstance train, Stop atStop, long currentTick);

    void cleanup(TrainInstance train);
}


