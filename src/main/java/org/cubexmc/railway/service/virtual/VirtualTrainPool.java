package org.cubexmc.railway.service.virtual;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.cubexmc.railway.estimation.TravelTimeEstimator;
import org.cubexmc.railway.model.Line;

/**
 * Manages a pool of virtual trains for a single line.
 * Virtual trains run continuously in the background and can be materialized
 * when players are nearby.
 */
public class VirtualTrainPool {

    private final String lineId;
    private final List<VirtualTrain> virtualTrains = new ArrayList<>();
    private final Set<UUID> materializedIds = new HashSet<>();
    private final int dwellTicks;

    /**
     * Create a new virtual train pool for a line.
     * 
     * @param lineId     The line ID
     * @param dwellTicks Dwell time at each stop in ticks
     */
    public VirtualTrainPool(String lineId, int dwellTicks) {
        this.lineId = lineId;
        this.dwellTicks = Math.max(20, dwellTicks);
    }

    /**
     * Initialize the pool with virtual trains based on headway.
     * Trains are distributed evenly along the line.
     * 
     * @param line           The line configuration
     * @param headwaySeconds Headway between trains in seconds
     * @param estimator      Travel time estimator
     * @param currentTick    Current server tick
     */
    public void initialize(Line line, int headwaySeconds, TravelTimeEstimator estimator, long currentTick) {
        virtualTrains.clear();
        materializedIds.clear();

        List<String> stopIds = line.getOrderedStopIds();
        if (stopIds == null || stopIds.size() < 2) {
            return;
        }

        // Calculate total cycle time (travel + dwell at each stop)
        double totalTravelSeconds = 0;
        for (int i = 0; i < stopIds.size() - 1; i++) {
            totalTravelSeconds += estimator.estimateSeconds(lineId, stopIds.get(i), stopIds.get(i + 1));
        }
        double dwellSecondsPerStop = dwellTicks / 20.0;
        double totalCycleSeconds = totalTravelSeconds + (stopIds.size() * dwellSecondsPerStop);

        if (totalCycleSeconds <= 0) {
            totalCycleSeconds = stopIds.size() * 30.0; // Fallback
        }

        // Determine number of virtual trains
        int trainCount = Math.max(1, (int) Math.ceil(totalCycleSeconds / Math.max(10, headwaySeconds)));

        // Cap at reasonable number
        trainCount = Math.min(trainCount, 10);

        // Calculate segment boundaries for distribution
        List<SegmentBoundary> boundaries = buildSegmentBoundaries(stopIds, estimator);
        double totalTime = boundaries.isEmpty() ? totalCycleSeconds
                : boundaries.get(boundaries.size() - 1).cumulativeTime;

        // Distribute trains evenly
        for (int i = 0; i < trainCount; i++) {
            double timeOffset = (totalTime / trainCount) * i;

            // Find segment and progress for this time offset
            int stopIndex = 0;
            double progress = 0.0;

            for (int j = 0; j < boundaries.size(); j++) {
                SegmentBoundary seg = boundaries.get(j);
                if (timeOffset <= seg.cumulativeTime) {
                    stopIndex = seg.fromStopIndex;
                    double segmentStart = j > 0 ? boundaries.get(j - 1).cumulativeTime : 0;
                    double segmentDuration = seg.cumulativeTime - segmentStart;
                    if (segmentDuration > 0) {
                        double withinSegment = timeOffset - segmentStart;
                        if (withinSegment <= dwellSecondsPerStop) {
                            // Still in dwell time
                            progress = 0.0;
                        } else {
                            // In transit
                            double travelTime = segmentDuration - dwellSecondsPerStop;
                            if (travelTime > 0) {
                                progress = Math.min(1.0, (withinSegment - dwellSecondsPerStop) / travelTime);
                            }
                        }
                    }
                    break;
                }
                stopIndex = seg.toStopIndex;
            }

            VirtualTrain vt = new VirtualTrain(lineId, stopIds, dwellTicks, stopIndex, progress, currentTick);
            virtualTrains.add(vt);
        }
    }

    private List<SegmentBoundary> buildSegmentBoundaries(List<String> stopIds, TravelTimeEstimator estimator) {
        List<SegmentBoundary> boundaries = new ArrayList<>();
        double cumulative = 0;
        double dwellSeconds = dwellTicks / 20.0;

        for (int i = 0; i < stopIds.size() - 1; i++) {
            cumulative += dwellSeconds; // Dwell at departure stop
            cumulative += estimator.estimateSeconds(lineId, stopIds.get(i), stopIds.get(i + 1));
            boundaries.add(new SegmentBoundary(i, i + 1, cumulative));
        }
        return boundaries;
    }

    private static class SegmentBoundary {
        final int fromStopIndex;
        final int toStopIndex;
        final double cumulativeTime;

        SegmentBoundary(int from, int to, double time) {
            this.fromStopIndex = from;
            this.toStopIndex = to;
            this.cumulativeTime = time;
        }
    }

    /**
     * Update all virtual trains.
     * 
     * @param currentTick Current server tick
     * @param estimator   Travel time estimator
     */
    public void tick(long currentTick, TravelTimeEstimator estimator) {
        for (VirtualTrain vt : virtualTrains) {
            // Skip materialized trains (they're controlled by physical instance)
            if (materializedIds.contains(vt.getId())) {
                continue;
            }
            vt.tick(currentTick, estimator);
        }
    }

    /**
     * Find the best virtual train candidate to materialize for a given stop.
     * Returns the train with the smallest ETA that is not already materialized.
     * 
     * @param stopId        Target stop ID
     * @param stopIds       Ordered stop IDs for the line
     * @param maxEtaSeconds Maximum ETA in seconds
     * @param estimator     Travel time estimator
     * @return Best candidate, or null if none found
     */
    public VirtualTrain findBestCandidateForStop(String stopId, List<String> stopIds,
            double maxEtaSeconds, TravelTimeEstimator estimator) {
        int stopIndex = stopIds.indexOf(stopId);
        if (stopIndex < 0)
            return null;

        VirtualTrain best = null;
        double bestEta = Double.POSITIVE_INFINITY;

        for (VirtualTrain vt : virtualTrains) {
            if (materializedIds.contains(vt.getId())) {
                continue;
            }
            if (vt.isAtTerminal()) {
                continue;
            }

            double eta = vt.estimateEtaToStop(stopIndex, estimator);
            if (Double.isFinite(eta) && eta <= maxEtaSeconds && eta < bestEta) {
                bestEta = eta;
                best = vt;
            }
        }

        return best;
    }

    /**
     * Mark a virtual train as materialized (physical instance created).
     */
    public void markMaterialized(UUID virtualTrainId) {
        materializedIds.add(virtualTrainId);
    }

    /**
     * Check if a virtual train is currently materialized.
     */
    public boolean isMaterialized(UUID virtualTrainId) {
        return materializedIds.contains(virtualTrainId);
    }

    /**
     * Clear materialized flag without restoring state.
     * Used when the physical train was removed but the virtual train
     * should continue from its current simulated position.
     */
    public void clearMaterialized(UUID virtualTrainId) {
        materializedIds.remove(virtualTrainId);
    }

    /**
     * Return a physical train back to virtual state.
     * 
     * @param virtualTrainId The virtual train ID
     * @param stopIndex      Current stop index
     * @param targetIndex    Target stop index (-1 if waiting)
     * @param progress       Segment progress (0-1)
     * @param isWaiting      Whether the train was waiting
     * @param currentTick    Current tick
     */
    public void returnToVirtual(UUID virtualTrainId, int stopIndex, int targetIndex,
            double progress, boolean isWaiting, long currentTick) {
        materializedIds.remove(virtualTrainId);

        for (VirtualTrain vt : virtualTrains) {
            if (vt.getId().equals(virtualTrainId)) {
                vt.restoreState(stopIndex, targetIndex, progress, isWaiting, currentTick);
                break;
            }
        }
    }

    /**
     * Completely remove a virtual train (e.g., when it completes a non-loop line).
     * A new one will be spawned at the start on the next initialize call.
     */
    public void removeVirtualTrain(UUID virtualTrainId) {
        materializedIds.remove(virtualTrainId);
        virtualTrains.removeIf(vt -> vt.getId().equals(virtualTrainId));
    }

    /**
     * Get all virtual trains (for debugging/monitoring).
     */
    public List<VirtualTrain> getVirtualTrains() {
        return new ArrayList<>(virtualTrains);
    }

    /**
     * Get count of active (non-terminal) virtual trains.
     */
    public int getActiveCount() {
        int count = 0;
        for (VirtualTrain vt : virtualTrains) {
            if (!vt.isAtTerminal()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if there are any available (non-materialized, non-terminal) virtual
     * trains.
     */
    public boolean hasAvailableTrains() {
        for (VirtualTrain vt : virtualTrains) {
            if (!materializedIds.contains(vt.getId()) && !vt.isAtTerminal()) {
                return true;
            }
        }
        return false;
    }

    public String getLineId() {
        return lineId;
    }

    @Override
    public String toString() {
        return String.format("VirtualTrainPool[line=%s, trains=%d, materialized=%d]",
                lineId, virtualTrains.size(), materializedIds.size());
    }
}
