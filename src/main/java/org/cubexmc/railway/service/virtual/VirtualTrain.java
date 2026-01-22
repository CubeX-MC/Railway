package org.cubexmc.railway.service.virtual;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.cubexmc.railway.estimation.TravelTimeEstimator;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.model.Stop;

/**
 * Represents a virtual train that exists only in memory, tracking its position
 * along a line without spawning physical minecart entities.
 * 
 * Virtual trains continuously update their position based on travel time
 * estimates,
 * and can be materialized into physical TrainInstance when players are nearby.
 */
public class VirtualTrain {

    public enum State {
        WAITING, // Stopped at a station, waiting for dwell time
        MOVING // Traveling between stations
    }

    private final UUID id;
    private final String lineId;
    private final List<String> stopIds;
    private final int dwellTicks;
    private final boolean isLoop;

    private State state;
    private int currentStopIndex; // Current or last departed stop index
    private int targetStopIndex; // Target stop index when moving (-1 when waiting)
    private double segmentProgress; // 0.0 to 1.0 progress through current segment
    private long stateStartTick; // Tick when current state started

    // Cached segment travel time for efficiency
    private double cachedSegmentSeconds = -1;

    /**
     * Create a new virtual train.
     * 
     * @param lineId           The line this train runs on
     * @param stopIds          Ordered list of stop IDs for the line
     * @param dwellTicks       Time to wait at each stop in ticks
     * @param initialStopIndex Initial position (stop index)
     * @param initialProgress  Initial progress (0-1, 0 = at stop, >0 = moving)
     * @param initialTick      Current tick when created
     */
    public VirtualTrain(String lineId, List<String> stopIds, int dwellTicks,
            int initialStopIndex, double initialProgress, long initialTick) {
        this.id = UUID.randomUUID();
        this.lineId = lineId;
        this.stopIds = stopIds;
        this.dwellTicks = Math.max(20, dwellTicks);
        this.isLoop = stopIds.size() >= 2 && stopIds.get(0).equals(stopIds.get(stopIds.size() - 1));

        this.currentStopIndex = Math.max(0, Math.min(initialStopIndex, stopIds.size() - 1));
        this.stateStartTick = initialTick;

        if (initialProgress > 0 && currentStopIndex < stopIds.size() - 1) {
            this.state = State.MOVING;
            this.targetStopIndex = currentStopIndex + 1;
            this.segmentProgress = Math.min(1.0, initialProgress);
        } else {
            this.state = State.WAITING;
            this.targetStopIndex = -1;
            this.segmentProgress = 0.0;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getLineId() {
        return lineId;
    }

    public State getState() {
        return state;
    }

    public int getCurrentStopIndex() {
        return currentStopIndex;
    }

    public int getTargetStopIndex() {
        return targetStopIndex;
    }

    public double getSegmentProgress() {
        return segmentProgress;
    }

    public String getCurrentStopId() {
        if (currentStopIndex >= 0 && currentStopIndex < stopIds.size()) {
            return stopIds.get(currentStopIndex);
        }
        return null;
    }

    public String getTargetStopId() {
        if (targetStopIndex >= 0 && targetStopIndex < stopIds.size()) {
            return stopIds.get(targetStopIndex);
        }
        return null;
    }

    /**
     * Update the virtual train's position based on elapsed time.
     * 
     * @param currentTick Current server tick
     * @param estimator   Travel time estimator for segment durations
     */
    public void tick(long currentTick, TravelTimeEstimator estimator) {
        switch (state) {
            case WAITING:
                tickWaiting(currentTick);
                break;
            case MOVING:
                tickMoving(currentTick, estimator);
                break;
        }
    }

    private void tickWaiting(long currentTick) {
        long elapsed = currentTick - stateStartTick;
        if (elapsed >= dwellTicks) {
            // Ready to depart
            if (currentStopIndex >= stopIds.size() - 1) {
                // At terminal
                if (isLoop) {
                    // Loop line: wrap to start
                    currentStopIndex = 0;
                    targetStopIndex = -1;
                    state = State.WAITING; // Wait at start
                    stateStartTick = currentTick;
                    segmentProgress = 0.0;
                    cachedSegmentSeconds = -1;
                } else {
                    // Non-loop terminal: "turnaround" - reset to first stop
                    // This simulates the train going back to the depot and
                    // restarting service from the beginning
                    currentStopIndex = 0;
                    targetStopIndex = -1;
                    state = State.WAITING; // Wait at start
                    stateStartTick = currentTick;
                    segmentProgress = 0.0;
                    cachedSegmentSeconds = -1;
                }
            } else {
                // Depart to next stop
                targetStopIndex = currentStopIndex + 1;
                state = State.MOVING;
                stateStartTick = currentTick;
                segmentProgress = 0.0;
                cachedSegmentSeconds = -1;
            }
        }
    }

    private void tickMoving(long currentTick, TravelTimeEstimator estimator) {
        if (targetStopIndex < 0 || targetStopIndex >= stopIds.size()) {
            // Invalid state, reset to waiting
            state = State.WAITING;
            stateStartTick = currentTick;
            return;
        }

        // Get segment travel time
        if (cachedSegmentSeconds < 0) {
            String fromId = stopIds.get(currentStopIndex);
            String toId = stopIds.get(targetStopIndex);
            cachedSegmentSeconds = estimator.estimateSeconds(lineId, fromId, toId);
            if (cachedSegmentSeconds <= 0) {
                cachedSegmentSeconds = 10.0; // Fallback
            }
        }

        // Calculate progress
        long elapsedTicks = currentTick - stateStartTick;
        double elapsedSeconds = elapsedTicks / 20.0;
        segmentProgress = Math.min(1.0, elapsedSeconds / cachedSegmentSeconds);

        // Check arrival
        if (segmentProgress >= 1.0) {
            // Arrived at target
            currentStopIndex = targetStopIndex;
            targetStopIndex = -1;
            state = State.WAITING;
            stateStartTick = currentTick;
            segmentProgress = 0.0;
            cachedSegmentSeconds = -1;
        }
    }

    /**
     * Estimate the ETA (in seconds) from current position to a specific stop.
     * 
     * @param stopIndex Target stop index
     * @param estimator Travel time estimator
     * @return Estimated seconds to reach the stop, or Double.POSITIVE_INFINITY if
     *         unreachable
     */
    public double estimateEtaToStop(int stopIndex, TravelTimeEstimator estimator) {
        if (stopIndex < 0 || stopIndex >= stopIds.size()) {
            return Double.POSITIVE_INFINITY;
        }

        // Already at or past this stop?
        if (state == State.WAITING && currentStopIndex == stopIndex) {
            return 0.0;
        }

        // Behind us (non-loop) or too far ahead?
        if (!isLoop && stopIndex < currentStopIndex) {
            return Double.POSITIVE_INFINITY;
        }

        double eta = 0.0;

        if (state == State.MOVING) {
            // Add remaining time in current segment
            String fromId = stopIds.get(currentStopIndex);
            String toId = stopIds.get(targetStopIndex);
            double segmentTotal = estimator.estimateSeconds(lineId, fromId, toId);
            double remaining = segmentTotal * (1.0 - segmentProgress);
            eta += remaining;

            if (targetStopIndex == stopIndex) {
                return eta;
            }

            // Sum from target to destination
            int from = targetStopIndex;
            int to = stopIndex;
            eta += sumTravelTime(from, to, estimator);
        } else {
            // Waiting state: sum from current to destination
            eta += sumTravelTime(currentStopIndex, stopIndex, estimator);
        }

        return eta;
    }

    private double sumTravelTime(int fromIndex, int toIndex, TravelTimeEstimator estimator) {
        if (fromIndex == toIndex)
            return 0.0;

        double sum = 0.0;
        int i = fromIndex;
        int n = stopIds.size();

        while (i != toIndex) {
            int next = i + 1;
            if (next >= n) {
                if (isLoop) {
                    next = 1; // Skip duplicate first/last
                } else {
                    return Double.POSITIVE_INFINITY; // Can't reach
                }
            }

            String fid = stopIds.get(i);
            String tid = stopIds.get(next);
            sum += estimator.estimateSeconds(lineId, fid, tid);
            sum += dwellTicks / 20.0; // Add dwell time at intermediate stops

            i = next;
            if (i == fromIndex) {
                // Looped around without finding target
                return Double.POSITIVE_INFINITY;
            }
        }

        return sum;
    }

    /**
     * Estimate the current physical location of this virtual train.
     * Uses linear interpolation between stop locations.
     * 
     * @param stopManager Stop manager to look up stop locations
     * @return Estimated location, or null if cannot be determined
     */
    public Location estimateCurrentLocation(StopManager stopManager) {
        if (state == State.WAITING) {
            Stop stop = stopManager.getStop(stopIds.get(currentStopIndex));
            return stop != null ? stop.getStopPointLocation() : null;
        }

        // Moving: interpolate between current and target stops
        Stop fromStop = stopManager.getStop(stopIds.get(currentStopIndex));
        Stop toStop = stopManager.getStop(stopIds.get(targetStopIndex));

        if (fromStop == null || toStop == null)
            return null;
        Location from = fromStop.getStopPointLocation();
        Location to = toStop.getStopPointLocation();
        if (from == null || to == null || from.getWorld() == null)
            return null;
        if (!from.getWorld().equals(to.getWorld()))
            return from;

        double t = segmentProgress;
        return new Location(
                from.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t);
    }

    /**
     * Estimate the travel direction of this virtual train.
     * 
     * @param stopManager Stop manager to look up stop locations
     * @return Normalized direction vector, or null if cannot be determined
     */
    public Vector estimateTravelDirection(StopManager stopManager) {
        int fromIdx = currentStopIndex;
        int toIdx = (state == State.MOVING && targetStopIndex >= 0)
                ? targetStopIndex
                : (currentStopIndex + 1 < stopIds.size() ? currentStopIndex + 1 : currentStopIndex);

        if (fromIdx == toIdx)
            return null;

        Stop fromStop = stopManager.getStop(stopIds.get(fromIdx));
        Stop toStop = stopManager.getStop(stopIds.get(toIdx));

        if (fromStop == null || toStop == null)
            return null;
        Location from = fromStop.getStopPointLocation();
        Location to = toStop.getStopPointLocation();
        if (from == null || to == null)
            return null;

        Vector dir = to.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1e-6) {
            return null;
        }
        return dir.normalize();
    }

    /**
     * Check if this train is at or approaching a specific stop within a time
     * threshold.
     * 
     * @param stopIndex     Target stop index
     * @param maxEtaSeconds Maximum ETA in seconds
     * @param estimator     Travel time estimator
     * @return true if train is within threshold
     */
    public boolean isNearStop(int stopIndex, double maxEtaSeconds, TravelTimeEstimator estimator) {
        double eta = estimateEtaToStop(stopIndex, estimator);
        return Double.isFinite(eta) && eta <= maxEtaSeconds;
    }

    /**
     * Restore state from a materialized train that is being virtualized back.
     * 
     * @param stopIndex   Current stop index
     * @param targetIndex Target stop index (-1 if waiting)
     * @param progress    Segment progress (0-1)
     * @param isWaiting   Whether the train was waiting
     * @param currentTick Current tick
     */
    public void restoreState(int stopIndex, int targetIndex, double progress,
            boolean isWaiting, long currentTick) {
        this.currentStopIndex = Math.max(0, Math.min(stopIndex, stopIds.size() - 1));
        this.stateStartTick = currentTick;
        this.cachedSegmentSeconds = -1;

        if (isWaiting || targetIndex < 0) {
            this.state = State.WAITING;
            this.targetStopIndex = -1;
            this.segmentProgress = 0.0;
        } else {
            this.state = State.MOVING;
            this.targetStopIndex = Math.max(0, Math.min(targetIndex, stopIds.size() - 1));
            this.segmentProgress = Math.max(0, Math.min(1.0, progress));
        }
    }

    /**
     * Check if this virtual train has reached terminal and cannot proceed.
     * Only applies to non-loop lines.
     */
    public boolean isAtTerminal() {
        if (isLoop)
            return false;
        return state == State.WAITING && currentStopIndex >= stopIds.size() - 1;
    }

    @Override
    public String toString() {
        return String.format("VirtualTrain[%s, line=%s, state=%s, stop=%d->%d, progress=%.2f]",
                id.toString().substring(0, 8), lineId, state, currentStopIndex, targetStopIndex, segmentProgress);
    }
}
