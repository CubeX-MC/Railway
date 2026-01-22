package org.cubexmc.railway.physics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.train.TrainInstance;
import org.cubexmc.railway.util.LocationUtil;
import org.cubexmc.railway.util.MinecartNmsUtil;
import org.cubexmc.railway.util.MinecartPhysicsUtil;

/**
 * Kinematic rail physics engine - COMPLETE override of vanilla minecart
 * physics.
 * 
 * Philosophy:
 * - Vanilla physics is COMPLETELY ignored
 * - Position and velocity are set EVERY tick, overriding any vanilla changes
 * - NO lerping or smoothing that could allow vanilla physics to interfere
 * - Passengers have ZERO effect on movement
 */
public class KinematicRailPhysics implements TrainPhysicsEngine {
    private static final double CART_DISTANCE_FORCER = 0.1;

    private static final class TrailPoint {
        final double x, y, z;
        final double vx, vy, vz;
        final double cumulativeDistance;

        TrailPoint(double x, double y, double z, double vx, double vy, double vz, double cumDist) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.cumulativeDistance = cumDist;
        }
    }

    private static final class TrailSample {
        final Location location;
        final Vector tangent;
        final double speed;

        TrailSample(Location location, Vector tangent, double speed) {
            this.location = location;
            this.tangent = tangent;
            this.speed = speed;
        }
    }

    // Trail buffer for followers
    private final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();

    // Kinematic state (our source of truth)
    private double leadX, leadY, leadZ;
    private double leadVx, leadVy, leadVz;
    private boolean initialized;
    private Vector lastLeadDirection = new Vector(1, 0, 0);

    @Override
    public void init(TrainInstance train) {
        trail.clear();
        Minecart lead = train.getConsist().getLeadCar();
        if (lead != null) {
            Location loc = lead.getLocation();
            // Try to snap to rail for initial position
            Location snapped = LocationUtil.snapToRail(loc, loc.getWorld());
            if (snapped != null) {
                loc = snapped;
            }
            leadX = loc.getX();
            leadY = loc.getY();
            leadZ = loc.getZ();
            Vector initialVelocity = lead.getVelocity();
            if (initialVelocity != null) {
                leadVx = initialVelocity.getX();
                leadVy = initialVelocity.getY();
                leadVz = initialVelocity.getZ();
            } else {
                leadVx = leadVy = leadVz = 0.0;
            }
            Vector launchDir = train.getTravelDirection();
            if (launchDir != null && launchDir.lengthSquared() > 1.0e-6) {
                lastLeadDirection = launchDir.clone().normalize();
            } else {
                lastLeadDirection = new Vector(1, 0, 0);
            }
        }
        seedTrailFromConsist(train);
        initialized = true;
    }

    @Override
    public void onDeparture(TrainInstance train, Stop fromStop) {
        init(train);
    }

    @Override
    public void tick(TrainInstance train, double timeFraction, long currentTick) {
        if (!initialized)
            init(train);

        Railway plugin = train.getService().getPlugin();
        Minecart lead = train.getConsist().getLeadCar();
        if (lead == null || lead.isDead())
            return;

        double baseSpeed = train.getService().getCartSpeed();
        boolean safe = plugin.isSafeSpeedMode();
        Vector travelDir = train.getTravelDirection();
        List<Minecart> cars = train.getConsist().getCars();
        double spacing = Math.max(0.1, train.getService().getTrainSpacing());
        Vector[] spacingCorrections = (cars != null && cars.size() > 1)
                ? calculateSpeedFactor(cars, spacing)
                : null;

        // === LEAD CAR: KINEMATIC CONTROL ===
        if (plugin.isPhysicsLeadKinematic()) {
            Vector leadCorrection = spacingCorrections != null ? spacingCorrections[0] : null;
            updateLeadKinematic(lead, baseSpeed, safe, travelDir, timeFraction, plugin, leadCorrection);
        } else {
            Vector leadCorrection = spacingCorrections != null ? spacingCorrections[0] : null;
            updateLeadVanilla(lead, baseSpeed, safe, travelDir, plugin, leadCorrection);
        }

        maintainTrail(train);

        // === FOLLOWERS: TRAINCARTS-STYLE VELOCITY CORRECTION ===
        updateFollowers(train, plugin, baseSpeed, safe, spacingCorrections, spacing);
    }

    /**
     * Update lead car with full kinematic control (TrainCarts-inspired approach)
     * Strategy:
     * 1. Calculate ideal position based on our physics
     * 2. Force minecart to that position (defeating passenger effects)
     * 3. Use aggressive enforcement to maintain control
     */
    private void updateLeadKinematic(Minecart lead, double baseSpeed, boolean safe,
            Vector travelDir, double timeFraction, Railway plugin, Vector spacingCorrection) {
        // Calculate position based on our kinematic simulation
        Location currentLoc = new Location(lead.getWorld(), leadX, leadY, leadZ);
        currentLoc = RailPathUtil.project(currentLoc);

        double configuredMax = Math.max(0.05, baseSpeed);
        lead.setMaxSpeed(configuredMax);

        // Use velocity from our simulation, not from the minecart
        Vector fallbackDir = new Vector(leadVx, leadVy, leadVz);
        if (fallbackDir.lengthSquared() < 1.0e-6) {
            fallbackDir = travelDir != null ? travelDir : new Vector(1, 0, 0);
        }

        // Get rail direction with smooth transition
        Vector railDir = RailPathUtil.computeDirection(currentLoc, fallbackDir);
        if (railDir == null || railDir.lengthSquared() < 1.0e-6) {
            railDir = fallbackDir.clone().normalize();
        } else {
            railDir = railDir.normalize();

            // Blend with previous direction to avoid sudden snaps
            // FIX: Use aggressive smoothing for slopes (0.85) to fix "Right Tilt" snap,
            // but keep it fast enough to avoid "Left Tilt" lag.
            LocationUtil.RailType railType = LocationUtil.getRailType(currentLoc);
            boolean isSlope = railType == LocationUtil.RailType.ASCENDING
                    || railType == LocationUtil.RailType.DESCENDING;
            double threshold = isSlope ? 0.99 : 0.707; // Always smooth slopes (cos(0) = 1)
            double blendFactor = isSlope ? 0.85 : 0.4;

            Vector prevDir = lastLeadDirection.clone();
            if (prevDir.lengthSquared() > 1.0e-6) {
                prevDir.normalize();
                double dot = railDir.dot(prevDir);

                // For slopes, we want to smooth the 0->45 degree transition
                if (dot < threshold) {
                    railDir = prevDir.multiply(1.0 - blendFactor).add(railDir.multiply(blendFactor)).normalize();
                }
            }
        }
        Vector facingDir = railDir.clone();

        // Calculate target speed with lookahead
        LocationUtil.RailType railType = LocationUtil.getRailType(currentLoc);
        double targetSpeed = LocationUtil.getSafeSpeedForRail(railType, baseSpeed, safe);

        int lookahead = plugin.getPhysicsLookaheadBlocks();
        double minSpeedAhead = targetSpeed;
        if (safe && lookahead > 0) {
            for (int i = 1; i <= lookahead; i++) {
                Location ahead = currentLoc.clone().add(railDir.clone().multiply(i));
                LocationUtil.RailType aheadType = LocationUtil.getRailType(ahead);
                double aheadSafe = LocationUtil.getSafeSpeedForRail(aheadType, baseSpeed, safe);
                minSpeedAhead = Math.min(minSpeedAhead, aheadSafe);
                if (aheadSafe < targetSpeed - 1.0e-4) {
                    double blendFactor = (double) i / (lookahead + 1);
                    double blended = targetSpeed * blendFactor + aheadSafe * (1.0 - blendFactor);
                    targetSpeed = Math.min(targetSpeed, blended);
                }
            }
        }

        targetSpeed = applyTerrainBoost(currentLoc, railType, targetSpeed, baseSpeed);
        if (railType == LocationUtil.RailType.CURVE && minSpeedAhead < targetSpeed) {
            targetSpeed = minSpeedAhead;
        }
        targetSpeed = Math.max(0.05, targetSpeed);

        Vector targetVelocity = railDir.clone().multiply(targetSpeed);
        Vector correctedVelocity = onSpacingUpdate(targetVelocity, spacingCorrection, lead, baseSpeed);
        correctedVelocity = alignVelocityToRail(currentLoc, correctedVelocity, railDir);
        correctedVelocity = clampVelocity(correctedVelocity, 999.0, targetSpeed);

        // Calculate movement step based on corrected velocity
        Vector step = correctedVelocity.clone().multiply(timeFraction);

        // Update kinematic state
        leadX += step.getX();
        leadY += step.getY();
        leadZ += step.getZ();
        leadVx = correctedVelocity.getX();
        leadVy = correctedVelocity.getY();
        leadVz = correctedVelocity.getZ();

        // Validate and snap to rails
        Location newLoc = new Location(lead.getWorld(), leadX, leadY, leadZ);
        if (!LocationUtil.isRail(newLoc)) {
            Location snapped = LocationUtil.snapToRail(newLoc, lead.getWorld());
            if (snapped != null && snapped.distanceSquared(newLoc) < 4.0) {
                leadX = snapped.getX();
                leadY = snapped.getY();
                leadZ = snapped.getZ();
                newLoc = snapped;
            }
        }

        newLoc = RailPathUtil.project(newLoc);

        // CRITICAL: Snap to position, overriding vanilla physics completely
        // This is the key to defeating passenger mass effects
        Vector leadVelocity = new Vector(leadVx, leadVy, leadVz);
        snapToPosition(lead, newLoc, leadVelocity, facingDir, plugin, baseSpeed);
        lastLeadDirection = facingDir.clone();

        // Record trail for followers
        addTrailPoint(leadX, leadY, leadZ, leadVx, leadVy, leadVz);
    }

    /**
     * Update lead car with vanilla physics (just clamp speed)
     */
    private void updateLeadVanilla(Minecart lead, double baseSpeed, boolean safe,
            Vector travelDir, Railway plugin, Vector spacingCorrection) {
        Location loc = lead.getLocation();
        Vector vel = lead.getVelocity();

        // Update our kinematic state from actual position
        leadX = loc.getX();
        leadY = loc.getY();
        leadZ = loc.getZ();

        double configuredMax = Math.max(0.05, baseSpeed);
        lead.setMaxSpeed(configuredMax);

        // Clamp speed if needed
        double currentSpeed = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
        LocationUtil.RailType railType = LocationUtil.getRailType(loc);
        double maxSpeed = LocationUtil.getSafeSpeedForRail(railType, baseSpeed, safe);

        if (currentSpeed > maxSpeed) {
            double scale = maxSpeed / currentSpeed;
            vel.multiply(scale);
            MinecartPhysicsUtil.forceVelocity(lead, vel, plugin);
        }

        Vector corrected = onSpacingUpdate(vel, spacingCorrection, lead, baseSpeed);
        corrected = alignVelocityToRail(loc, corrected, corrected);
        corrected = clampVelocity(corrected, 999.0, maxSpeed);
        MinecartPhysicsUtil.forceVelocity(lead, corrected, plugin);

        leadVx = corrected.getX();
        leadVy = corrected.getY();
        leadVz = corrected.getZ();

        Vector fallback = (travelDir != null && travelDir.lengthSquared() > 1.0e-6)
                ? travelDir.clone()
                : lastLeadDirection.clone();
        Vector facingDir = normalizeOr(corrected, fallback);
        lastLeadDirection = facingDir.clone();

        addTrailPoint(leadX, leadY, leadZ, leadVx, leadVy, leadVz);
    }

    /**
     * Update all follower cars using TrainCarts' exact approach:
     * 1. Calculate spacing correction (spacing factor)
     * 2. Apply correction to velocity (onSpacingUpdate)
     * 3. Snap to trail position (snapToPosition)
     * 
     * TrainCarts snapToPosition: directly sets position and preserves speed
     * magnitude
     */
    private void updateFollowers(TrainInstance train, Railway plugin, double baseSpeed, boolean safe,
            Vector[] spacingCorrections, double spacing) {
        List<Minecart> cars = train.getConsist().getCars();
        int n = cars.size();
        if (n <= 1) {
            return;
        }

        // Process each follower cart
        for (int i = 1; i < n; i++) {
            Minecart car = cars.get(i);
            if (car == null || car.isDead()) {
                continue;
            }

            car.setGravity(false);
            car.setSlowWhenEmpty(false);
            car.setFlyingVelocityMod(new Vector(0, 0, 0));
            car.setMaxSpeed(Math.max(0.05, baseSpeed));

            // Step 1: Determine spacing correction vector (TrainCarts onSpacingUpdate
            // factor)
            Vector correction = (spacingCorrections != null && i < spacingCorrections.length)
                    ? spacingCorrections[i]
                    : null;

            // Step 2: Find target position on trail (spacing * i behind lead)
            double distanceBehind = spacing * i;
            TrailSample sample = sampleTrailState(distanceBehind, car.getWorld());

            if (sample == null) {
                // No trail available - just apply velocity correction
                Location currentLoc = RailPathUtil.project(car.getLocation());
                Vector existing = car.getVelocity().clone();
                Vector tangent = normalizeOr(existing, lastLeadDirection);
                Vector corrected = onSpacingUpdate(existing, correction, car, baseSpeed);
                Vector aligned = alignVelocityToRail(currentLoc, corrected, tangent);
                aligned = clampVelocity(aligned, 999.0, baseSpeed);
                snapToPosition(car, currentLoc, aligned, tangent, plugin, baseSpeed);
                continue;
            }

            // Step 3: Snap to position (TrainCarts snapToPosition style)
            // - Set position to trail sample location
            // - Preserve velocity magnitude
            // - Use trail tangent for direction
            Location targetPos = RailPathUtil.project(sample.location);
            Vector motionDir = normalizeOr(sample.tangent, lastLeadDirection);

            // Base speed follows the lead's kinematic trail so passengers cannot stall the
            // consist
            double configuredMax = Math.max(0.05, baseSpeed);
            double baseSampleSpeed = Math.min(sample.speed, configuredMax);
            if (baseSampleSpeed < 1.0e-6) {
                baseSampleSpeed = Math.min(car.getVelocity().length(), configuredMax);
            }

            Vector baseVelocity = motionDir.clone().multiply(baseSampleSpeed);
            Vector corrected = onSpacingUpdate(baseVelocity, correction, car, baseSpeed);
            Vector aligned = alignVelocityToRail(targetPos, corrected, motionDir);
            aligned = clampVelocity(aligned, 999.0, baseSpeed);

            Vector facing = normalizeOr(aligned, motionDir);

            // Apply: direct snap to trail position (TrainCarts snapToPosition)
            snapToPosition(car, targetPos, aligned, facing, plugin, baseSpeed);
        }
    }

    /**
     * Apply minimum speed boost for challenging terrain
     */
    private double applyTerrainBoost(Location loc, LocationUtil.RailType railType,
            double currentSpeed, double baseSpeed) {
        // Powered ascending rail: ensure minimum speed
        if (LocationUtil.isPoweredAscendingRailPowered(loc)) {
            double minSpeed = Math.max(0.4, baseSpeed * 0.8);
            return Math.max(currentSpeed, minSpeed);
        }

        // Curves: maintain momentum but don't boost above current speed
        // This prevents speed oscillation in S-curves
        if (railType == LocationUtil.RailType.CURVE) {
            // Only ensure we don't go below a critical minimum
            double criticalMin = Math.max(0.15, baseSpeed * 0.5);
            return Math.max(currentSpeed, criticalMin);
        }

        return currentSpeed;
    }

    private TrailSample sampleTrailState(double distanceBehind, World world) {
        TrailPoint point = sampleTrailAt(distanceBehind);
        if (point == null || world == null) {
            return null;
        }
        Location location = new Location(world, point.x, point.y, point.z);
        Vector tangent = computeTangent(distanceBehind);
        double speed = Math.sqrt(point.vx * point.vx + point.vy * point.vy + point.vz * point.vz);

        if ((tangent == null || tangent.lengthSquared() < 1.0e-8) && speed > 1.0e-6) {
            tangent = new Vector(point.vx, point.vy, point.vz);
        }
        if (tangent == null || tangent.lengthSquared() < 1.0e-8) {
            tangent = lastLeadDirection.clone();
        }
        if (tangent.lengthSquared() < 1.0e-8) {
            tangent = new Vector(1, 0, 0);
        }
        return new TrailSample(location, tangent, speed);
    }

    private Vector computeTangent(double distanceBehind) {
        double delta = Math.max(0.05, Math.min(0.75, (distanceBehind * 0.5) + 0.1));
        double aheadDist = Math.max(0.0, distanceBehind - delta);
        double behindDist = distanceBehind + delta;
        TrailPoint ahead = sampleTrailAt(aheadDist);
        TrailPoint behind = sampleTrailAt(behindDist);
        if (ahead == null || behind == null) {
            TrailPoint center = sampleTrailAt(distanceBehind);
            return center != null ? new Vector(center.vx, center.vy, center.vz) : null;
        }
        Vector tangent = new Vector(ahead.x - behind.x, ahead.y - behind.y, ahead.z - behind.z);
        if (tangent.lengthSquared() < 1.0e-8) {
            TrailPoint center = sampleTrailAt(distanceBehind);
            if (center != null) {
                tangent = new Vector(center.vx, center.vy, center.vz);
            }
        }
        return tangent;
    }

    private Vector normalizeOr(Vector primary, Vector fallback) {
        Vector candidate = primary != null ? primary.clone() : null;
        if (candidate != null && candidate.lengthSquared() > 1.0e-8) {
            return candidate.normalize();
        }
        Vector alt = fallback != null ? fallback.clone() : null;
        if (alt != null && alt.lengthSquared() > 1.0e-8) {
            return alt.normalize();
        }
        return new Vector(1, 0, 0);
    }

    /**
     * Calculate spacing factors for all carts (TrainCarts calculateSpeedFactor
     * equivalent)
     * Returns correction vectors to be applied to each cart's velocity
     */
    private Vector[] calculateSpeedFactor(List<Minecart> cars, double spacing) {
        int n = cars.size();
        Vector[] factors = new Vector[n];
        for (int i = 0; i < n; i++) {
            factors[i] = new Vector(0, 0, 0);
        }
        if (n <= 1 || trail.isEmpty()) {
            return factors;
        }

        // Calculate pairwise corrections between adjacent carts (TrainCarts style)
        for (int i = 0; i < n - 1; i++) {
            Minecart back = cars.get(i);
            Minecart front = cars.get(i + 1);
            if (back == null || back.isDead() || front == null || front.isDead()) {
                continue;
            }

            // Get actual positions
            Location backLoc = RailPathUtil.project(back.getLocation());
            Location frontLoc = RailPathUtil.project(front.getLocation());

            // Calculate direction vector from back to front
            Vector direction = frontLoc.toVector().subtract(backLoc.toVector());
            double actualGap = direction.length();

            if (actualGap < 1.0e-6) {
                // Carts are on same position, use rail direction
                Vector railDir = RailPathUtil.computeDirection(backLoc, lastLeadDirection);
                direction = railDir != null && railDir.lengthSquared() > 1.0e-6
                        ? railDir.normalize()
                        : lastLeadDirection.clone().normalize();
                actualGap = spacing;
            } else {
                direction.normalize();
            }

            // Calculate gap error (positive = too far apart, negative = too close)
            double gapError = actualGap - spacing;

            // TrainCarts approach: correction vector along the line between carts
            Vector correction = direction.multiply(gapError);

            // Limit correction magnitude to prevent excessive forces
            double maxCorrection = spacing * 0.5;
            double corrLen = correction.length();
            if (corrLen > maxCorrection) {
                correction.multiply(maxCorrection / corrLen);
            }

            // Apply equal and opposite corrections
            // Back cart gets pushed forward if gap too large, pulled back if too close
            factors[i].add(correction);
            // Front cart gets pulled back if gap too large, pushed forward if too close
            factors[i + 1].subtract(correction.clone());
        }

        // Average corrections for middle carts (they have two neighbors)
        for (int i = 1; i < n - 1; i++) {
            factors[i].multiply(0.5);
        }

        return factors;
    }

    /**
     * Apply spacing correction to velocity (TrainCarts onSpacingUpdate - EXACT
     * COPY)
     * TrainCarts formula:
     * double f = motLen / member.getEntity().getMaxSpeed();
     * velocity.setX(velocity.getX() + f * factor.getX() *
     * TCConfig.cartDistanceForcer);
     * 
     * FIX: Use fixed maxSpeed to ensure loaded/empty carts behave identically
     */
    private Vector onSpacingUpdate(Vector baseVelocity, Vector correction, Minecart cart, double baseSpeed) {
        Vector result = baseVelocity != null ? baseVelocity.clone() : new Vector();
        if (correction == null || correction.lengthSquared() < 1.0e-8 || cart == null) {
            return result;
        }

        double motLen = result.length();
        if (motLen > 0.01) {
            double maxSpeed = Math.max(0.05, cart.getMaxSpeed());
            double f = motLen / maxSpeed;
            result.setX(result.getX() + f * correction.getX() * CART_DISTANCE_FORCER);
            result.setZ(result.getZ() + f * correction.getZ() * CART_DISTANCE_FORCER);
        }

        return result;
    }

    private Vector alignVelocityToRail(Location loc, Vector velocity, Vector fallback) {
        if (velocity == null) {
            return new Vector();
        }
        Vector base = velocity.clone();
        Vector preferred = fallback != null ? fallback.clone() : base.clone();
        if (preferred.lengthSquared() < 1.0e-8) {
            preferred = base.lengthSquared() > 1.0e-8 ? base.clone() : lastLeadDirection.clone();
        }
        Location snapped = RailPathUtil.project(loc);
        Vector railDir = RailPathUtil.computeDirection(snapped, preferred);
        if (railDir.lengthSquared() > 1.0e-8) {
            double speed = base.length();
            return railDir.normalize().multiply(speed);
        }
        return base;
    }

    private Vector clampVelocity(Vector velocity, double maxSpeed, double fallbackMax) {
        Vector result = velocity.clone();
        // FIXED: Don't use cart.getMaxSpeed() parameter, use fallback only
        // This ensures all carts are clamped to the same limit
        double limit = fallbackMax;
        limit = Math.max(0.05, limit);
        double len = result.length();
        if (len > limit) {
            result.multiply(limit / len);
        }
        return result;
    }

    private void maintainTrail(TrainInstance train) {
        if (trail.isEmpty() || train == null) {
            return;
        }
        TrailPoint head = trail.peekFirst();
        if (head == null) {
            return;
        }
        int carCount = Math.max(1, train.getConsist().getCars().size());
        double spacing = Math.max(0.1, train.getService().getTrainSpacing());
        double maxDistance = (carCount + 3) * spacing + 12.0;
        while (trail.size() > 2) {
            TrailPoint tail = trail.peekLast();
            if (tail == null) {
                break;
            }
            double storedDistance = head.cumulativeDistance - tail.cumulativeDistance;
            if (storedDistance <= maxDistance) {
                break;
            }
            trail.removeLast();
        }
        while (trail.size() > 800) {
            trail.removeLast();
        }
    }

    /**
     * Snap minecart to position (TrainCarts snapToPosition - EXACT COPY)
     * TrainCarts implementation:
     * - entity.setPosition(position.posX, position.posY, position.posZ)
     * - entity.vel.set(position.motX * velocity, position.motY * velocity,
     * position.motZ * velocity)
     */
    private void snapToPosition(Minecart cart, Location loc, Vector vel, Vector facing, Railway plugin,
            double speedLimit) {
        // Disable vanilla physics, match TrainCarts order of operations
        cart.setGravity(false);
        cart.setSlowWhenEmpty(false);
        cart.setFlyingVelocityMod(new Vector(0, 0, 0));
        double configuredMax = Math.max(0.05,
                speedLimit > 0.0 ? speedLimit : (plugin != null ? plugin.getCartSpeed() : 0.4));
        cart.setMaxSpeed(configuredMax);

        // Determine velocity magnitude and direction
        double velocityMag = vel != null ? vel.length() : 0.0;
        Vector motionDir;
        if (vel != null && vel.lengthSquared() > 1.0e-6) {
            motionDir = vel.clone().normalize();
        } else if (facing != null && facing.lengthSquared() > 1.0e-6) {
            motionDir = facing.clone().normalize();
        } else {
            motionDir = lastLeadDirection.clone().normalize();
        }

        // Project onto rail path and align orientation with actual rail direction
        Location targetLoc = RailPathUtil.project(loc.clone());
        Vector railOrientation = RailPathUtil.computeDirection(targetLoc, motionDir);
        if (railOrientation.lengthSquared() > 1.0e-6) {
            motionDir = railOrientation.normalize();
        }

        // TrainCarts snapToPosition uses current velocity magnitude along motion
        // direction
        Vector targetVel = motionDir.clone().multiply(velocityMag);

        // Compute yaw/pitch and wrap exactly like MinecartMember#setRotationWrap
        double dx = motionDir.getX();
        double dy = motionDir.getY();
        double dz = motionDir.getZ();
        double horizLen = Math.sqrt(dx * dx + dz * dz);
        float newYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float newPitch = (float) Math.toDegrees(Math.atan2(-dy, horizLen));

        float oldYaw = cart.getLocation().getYaw();
        while ((newYaw - oldYaw) >= 90.0f) {
            newYaw -= 180.0f;
            newPitch = -newPitch;
        }
        while ((newYaw - oldYaw) < -90.0f) {
            newYaw += 180.0f;
            newPitch = -newPitch;
        }
        while ((newYaw - oldYaw) <= -180.0f) {
            newYaw += 360.0f;
        }
        while ((newYaw - oldYaw) > 180.0f) {
            newYaw -= 360.0f;
        }

        targetLoc.setYaw(newYaw);
        targetLoc.setPitch(newPitch);

        boolean snappedDirect = MinecartNmsUtil.snap(cart, targetLoc, targetVel, newYaw, newPitch);
        if (!snappedDirect) {
            cart.teleport(targetLoc);
            cart.setRotation(newYaw, newPitch);
        }

        if (plugin != null) {
            MinecartPhysicsUtil.forceVelocity(cart, targetVel, plugin);
        } else {
            cart.setVelocity(targetVel);
        }
    }

    private void seedTrailFromConsist(TrainInstance train) {
        List<Minecart> cars = train != null ? train.getConsist().getCars() : null;
        if (cars == null || cars.isEmpty()) {
            return;
        }

        List<TrailPoint> points = new ArrayList<>();

        Minecart lead = cars.get(0);
        if (lead == null || lead.isDead()) {
            return;
        }

        Location leadProjected = RailPathUtil.project(lead.getLocation());
        if (leadProjected == null) {
            leadProjected = lead.getLocation();
        }
        Vector leadVelocity = lead.getVelocity() != null ? lead.getVelocity().clone() : new Vector(0, 0, 0);

        double cumulative = 0.0;
        points.add(new TrailPoint(leadProjected.getX(), leadProjected.getY(), leadProjected.getZ(),
                leadVelocity.getX(), leadVelocity.getY(), leadVelocity.getZ(), cumulative));

        Location prevLoc = leadProjected.clone();
        Vector fallbackDir = leadVelocity.clone();

        for (int i = 1; i < cars.size(); i++) {
            Minecart car = cars.get(i);
            if (car == null || car.isDead()) {
                continue;
            }
            Location projected = RailPathUtil.project(car.getLocation());
            if (projected == null) {
                projected = car.getLocation();
            }

            double gap = projected.distance(prevLoc);
            if (gap > 1.0e-4) {
                cumulative += gap;
            }

            Vector velocity = car.getVelocity() != null ? car.getVelocity().clone() : fallbackDir.clone();
            points.add(new TrailPoint(projected.getX(), projected.getY(), projected.getZ(),
                    velocity.getX(), velocity.getY(), velocity.getZ(), cumulative));

            prevLoc = projected;
            fallbackDir = velocity;
        }

        trail.clear();
        for (int idx = points.size() - 1; idx >= 0; idx--) {
            trail.addFirst(points.get(idx));
        }
    }

    /**
     * Add a trail point for followers to track
     */
    private void addTrailPoint(double x, double y, double z, double vx, double vy, double vz) {
        double cumDist = 0.0;

        if (!trail.isEmpty()) {
            TrailPoint last = trail.peekFirst();
            double dx = x - last.x;
            double dy = y - last.y;
            double dz = z - last.z;
            cumDist = last.cumulativeDistance + Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        trail.addFirst(new TrailPoint(x, y, z, vx, vy, vz, cumDist));

        // Keep trail limited
        while (trail.size() > 900) {
            trail.removeLast();
        }
    }

    /**
     * Sample the trail at a specific distance behind the lead
     */
    private TrailPoint sampleTrailAt(double distanceBehind) {
        if (trail.isEmpty())
            return null;

        TrailPoint head = trail.peekFirst();
        double targetDist = head.cumulativeDistance - distanceBehind;

        if (targetDist < 0) {
            // Requesting position beyond our trail - return furthest point
            return trail.peekLast();
        }

        TrailPoint prev = head;
        for (TrailPoint point : trail) {
            if (point.cumulativeDistance <= targetDist) {
                // Interpolate between prev and point
                double segmentDist = prev.cumulativeDistance - point.cumulativeDistance;

                if (segmentDist < 1.0e-6) {
                    return prev;
                }

                double t = (targetDist - point.cumulativeDistance) / segmentDist;
                t = Math.max(0.0, Math.min(1.0, t));

                // Linear interpolation
                double x = point.x + t * (prev.x - point.x);
                double y = point.y + t * (prev.y - point.y);
                double z = point.z + t * (prev.z - point.z);
                double vx = point.vx + t * (prev.vx - point.vx);
                double vy = point.vy + t * (prev.vy - point.vy);
                double vz = point.vz + t * (prev.vz - point.vz);

                return new TrailPoint(x, y, z, vx, vy, vz, targetDist);
            }
            prev = point;
        }

        return trail.peekLast();
    }

    @Override
    public void onArrival(TrainInstance train, Stop atStop, long currentTick) {
        // Nothing needed
    }

    @Override
    public void cleanup(TrainInstance train) {
        trail.clear();
        initialized = false;
    }
}
