package org.cubexmc.railway.physics;

import org.bukkit.entity.Minecart;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.train.TrainInstance;

/**
 * Leashed mode: reactive movement + visual leash couplers.
 */
public class LeashedRailPhysics extends ReactiveRailPhysics {

    private LeashCoupler coupler;

    @Override
    public void init(TrainInstance train) {
        super.init(train);
        coupler = new LeashCoupler(train.getService().getPlugin(), train);
        coupler.start();
    }

    @Override
    public void onDeparture(TrainInstance train, Stop fromStop) {
        super.onDeparture(train, fromStop);
        if (coupler == null) {
            coupler = new LeashCoupler(train.getService().getPlugin(), train);
            coupler.start();
        }
    }

    @Override
    public void tick(TrainInstance train, double timeFraction, long currentTick) {
        for (Minecart car : train.getConsist().getCars()) {
            if (car != null && !car.isDead()) {
                car.setSlowWhenEmpty(false);
                car.setGravity(false);
                car.setFlyingVelocityMod(new org.bukkit.util.Vector(0, 0, 0));
            }
        }
        super.tick(train, timeFraction, currentTick);
        if (coupler != null) coupler.update();
    }

    @Override
    public void cleanup(TrainInstance train) {
        if (coupler != null) {
            coupler.cleanup();
            coupler = null;
        }
        super.cleanup(train);
    }
}


