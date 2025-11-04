package org.cubexmc.railway.train;

import org.bukkit.scheduler.BukkitRunnable;
import org.cubexmc.railway.Railway;

/**
 * Periodically enforces spacing and velocity alignment for a consist.
 */
public class ConsistController extends BukkitRunnable {

    private final Railway plugin;
    private final TrainConsist consist;

    public ConsistController(Railway plugin, TrainConsist consist) {
        this.plugin = plugin;
        this.consist = consist;
    }

    @Override
    public void run() {
        // Placeholder: keep-alive tick; spacing logic to be added later
    }
}


