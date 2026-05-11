package org.cubexmc.metro.train;

final class ArrivalHeuristics {

    private static final double BASE_THRESHOLD_BLOCKS = 2.0;
    private static final double SPEED_FACTOR = 4.0;
    private static final double MAX_THRESHOLD_BLOCKS = 4.0;

    private ArrivalHeuristics() {
    }

    static boolean shouldArrive(double distanceSquared, double speedBlocksPerTick) {
        double threshold = thresholdBlocks(speedBlocksPerTick);
        return distanceSquared <= threshold * threshold;
    }

    static double thresholdBlocks(double speedBlocksPerTick) {
        double clampedSpeed = Math.max(0.0, speedBlocksPerTick);
        return Math.min(MAX_THRESHOLD_BLOCKS, BASE_THRESHOLD_BLOCKS + (clampedSpeed * SPEED_FACTOR));
    }
}