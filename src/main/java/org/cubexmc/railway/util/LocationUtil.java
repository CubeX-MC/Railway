package org.cubexmc.railway.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * Rail type classification for speed management
     */
    public enum RailType {
        STRAIGHT,       // N-S or E-W
        CURVE,          // NE, NW, SE, SW
        ASCENDING,      // Any ascending slope
        DESCENDING,     // Any descending slope (derived from ascending check)
        UNKNOWN
    }

    public static boolean isRail(Location location) {
        if (location == null) {
            return false;
        }
        Block block = location.getBlock();
        if (isRailBlock(block.getType())) {
            return true;
        }
        Block below = block.getRelative(0, -1, 0);
        return isRailBlock(below.getType());
    }

    public static boolean isPoweredAscendingRailPowered(Location location) {
        if (location == null) return false;
        Rail rail = getRailData(location.getBlock());
        if (rail == null) {
            rail = getRailData(location.getBlock().getRelative(0, -1, 0));
        }
        if (rail == null) return false;
        Rail.Shape shape = rail.getShape();
        boolean ascending = shape == Rail.Shape.ASCENDING_EAST
                || shape == Rail.Shape.ASCENDING_WEST
                || shape == Rail.Shape.ASCENDING_NORTH
                || shape == Rail.Shape.ASCENDING_SOUTH;
        if (!ascending) return false;
        if (rail instanceof Powerable) {
            return ((Powerable) rail).isPowered();
        }
        return false;
    }

    private static Rail getRailData(Block block) {
        if (block == null) return null;
        BlockData data = block.getBlockData();
        if (data instanceof Rail) {
            return (Rail) data;
        }
        return null;
    }

    public static boolean isCurveRail(Location location) {
        Rail rail = getRailData(location != null ? location.getBlock() : null);
        if (rail == null) {
            rail = getRailData(location != null ? location.getBlock().getRelative(0, -1, 0) : null);
        }
        if (rail == null) return false;
        switch (rail.getShape()) {
            case NORTH_EAST:
            case NORTH_WEST:
            case SOUTH_EAST:
            case SOUTH_WEST:
                return true;
            default:
                return false;
        }
    }

    private static Location computeRailCenter(Block block, Rail rail) {
        Location center = new Location(block.getWorld(), block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
        Rail.Shape shape = rail.getShape();
        switch (shape) {
            case ASCENDING_EAST:
            case ASCENDING_WEST:
            case ASCENDING_NORTH:
            case ASCENDING_SOUTH:
                center.add(0.0, 0.5, 0.0);
                break;
            default:
                break;
        }
        return center;
    }

    private static Location centerRail(Location candidate) {
        if (candidate == null) return candidate;
        Block block = candidate.getBlock();
        Rail rail = getRailData(block);
        if (rail != null) {
            return computeRailCenter(block, rail);
        }
        Block below = block.getRelative(0, -1, 0);
        rail = getRailData(below);
        if (rail != null) {
            return computeRailCenter(below, rail);
        }
        return center(candidate);
    }

    public static boolean isOnRail(Location location) {
        return isRail(location);
    }

    private static boolean isRailBlock(Material material) {
        return material != null && material.name().contains("RAIL");
    }

    /**
     * Detect rail type at location for speed adjustment
     */
    public static RailType getRailType(Location location) {
        if (location == null) return RailType.UNKNOWN;
        Block block = location.getBlock();
        BlockData data = block.getBlockData();
        if (!(data instanceof Rail)) {
            Block below = block.getRelative(0, -1, 0);
            if (below.getBlockData() instanceof Rail) {
                data = below.getBlockData();
            } else {
                return RailType.UNKNOWN;
            }
        }
        Rail rail = (Rail) data;
        Rail.Shape shape = rail.getShape();
        
        switch (shape) {
            case NORTH_SOUTH:
            case EAST_WEST:
                return RailType.STRAIGHT;
            case NORTH_EAST:
            case NORTH_WEST:
            case SOUTH_EAST:
            case SOUTH_WEST:
                return RailType.CURVE;
            case ASCENDING_EAST:
            case ASCENDING_WEST:
            case ASCENDING_NORTH:
            case ASCENDING_SOUTH:
                return RailType.ASCENDING;
            default:
                return RailType.UNKNOWN;
        }
    }

    /**
     * Calculate safe speed based on rail type, respecting vanilla physics limits
     * @param type rail type
     * @param baseSpeed the desired base speed
     * @param safeMode if true, enforce vanilla physics limits; if false, return baseSpeed
     * @return adjusted speed (uses min to enforce hard limits based on vanilla Minecraft physics)
     */
    public static double getSafeSpeedForRail(RailType type, double baseSpeed, boolean safeMode) {
        // If safe mode is disabled, let vanilla physics handle everything
        if (!safeMode) {
            return baseSpeed;
        }
        
        // Safe mode: apply speed limits based on rail type
        switch (type) {
            case STRAIGHT:
                return baseSpeed; // full speed on straight sections
            
            case CURVE:
                // Proportional slowdown, but never exceed vanilla's ~0.25 safe limit for 90° turns with passengers
                return Math.min(baseSpeed * 0.65, 0.25);
            
            case ASCENDING:
                // Uphill: proportional slowdown, capped at vanilla's ~0.35 limit
                return Math.min(baseSpeed * 0.87, 0.35);
            
            case DESCENDING:
                // Downhill: least restrictive, capped at ~0.42 (downhill is most stable)
                return Math.min(baseSpeed * 0.95, 0.42);
            
            default:
                // Conservative fallback
                return Math.min(baseSpeed * 0.80, 0.32);
        }
    }

    public static Vector vectorFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        Vector vector = new Vector(x, 0, z);
        if (vector.lengthSquared() == 0) {
            return new Vector(0, 0, 0);
        }
        return vector.normalize();
    }

    public static Location center(Location location) {
        if (location == null) {
            return null;
        }
        double x = location.getX();
        double z = location.getZ();
        if (isBlockAligned(x) && isBlockAligned(z)) {
            return location.clone().add(0.5, 0, 0.5);
        }
        return location.clone();
    }

    private static boolean isBlockAligned(double coord) {
        return Math.abs(coord - Math.floor(coord)) < 1e-6;
    }

    public static Location snapToRail(Location location, org.bukkit.World world) {
        if (location == null || world == null) {
            return location;
        }

        Location best = null;
        double bestDistance = Double.MAX_VALUE;

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location candidate = new Location(world, baseX + x, baseY + y, baseZ + z);
                    if (isRail(candidate)) {
                        Location center = centerRail(candidate);
                        double distance = center.distanceSquared(location);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = center;
                        }
                    }
                }
            }
        }

        return best != null ? best : location;
    }

    /**
     * Find nearest rail within radius for derailment recovery
     */
    public static Location findNearestRail(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        
        Location best = null;
        double bestDistSq = radius * radius;
        
        int r = (int) Math.ceil(radius);
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location candidate = new Location(location.getWorld(), baseX + dx, baseY + dy, baseZ + dz);
                    if (isRail(candidate)) {
                        Location center = centerRail(candidate);
                        double distSq = center.distanceSquared(location);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = center;
                        }
                    }
                }
            }
        }
        
        return best;
    }

    public static Vector railDirection(Location location, Vector fallback) {
        if (location == null) {
            return fallback;
        }
        Block block = location.getBlock();
        BlockData data = block.getBlockData();
        Rail rail = null;
        
        if (data instanceof Rail) {
            rail = (Rail) data;
        } else {
            // Try block below
            Block below = block.getRelative(0, -1, 0);
            if (below.getBlockData() instanceof Rail) {
                rail = (Rail) below.getBlockData();
            }
        }
        
        if (rail != null) {
            Rail.Shape shape = rail.getShape();
            Vector direction = vectorFromRailShape(shape, location, fallback);
            if (direction != null) {
                // For straight and ascending rails, flip if going wrong way
                if (!isCurveShape(shape)) {
                    if (fallback != null && direction.dot(fallback) < 0) {
                        direction.multiply(-1);
                    }
                }
                return direction;
            }
        }
        
        return fallback;
    }
    
    private static boolean isCurveShape(Rail.Shape shape) {
        return shape == Rail.Shape.NORTH_EAST || shape == Rail.Shape.NORTH_WEST
            || shape == Rail.Shape.SOUTH_EAST || shape == Rail.Shape.SOUTH_WEST;
    }

    private static Vector vectorFromRailShape(Rail.Shape shape, Location location, Vector fallback) {
        if (shape == null) {
            return null;
        }
        
        Vector vector;
        switch (shape) {
            case NORTH_SOUTH:
                vector = new Vector(0, 0, 1);
                break;
            case EAST_WEST:
                vector = new Vector(1, 0, 0);
                break;
            case ASCENDING_EAST:
                vector = new Vector(1, 1, 0);
                break;
            case ASCENDING_WEST:
                vector = new Vector(-1, 1, 0);
                break;
            case ASCENDING_NORTH:
                vector = new Vector(0, 1, -1);
                break;
            case ASCENDING_SOUTH:
                vector = new Vector(0, 1, 1);
                break;
            
            // Curve rails: determine direction based on current position and velocity
            case SOUTH_EAST:
                vector = computeCurveDirection(location, fallback, 
                    new Vector(0, 0, 1),  // South
                    new Vector(1, 0, 0)); // East
                break;
            case SOUTH_WEST:
                vector = computeCurveDirection(location, fallback,
                    new Vector(0, 0, 1),   // South
                    new Vector(-1, 0, 0)); // West
                break;
            case NORTH_WEST:
                vector = computeCurveDirection(location, fallback,
                    new Vector(0, 0, -1),  // North
                    new Vector(-1, 0, 0)); // West
                break;
            case NORTH_EAST:
                vector = computeCurveDirection(location, fallback,
                    new Vector(0, 0, -1),  // North
                    new Vector(1, 0, 0));  // East
                break;
            default:
                vector = null;
                break;
        }
        
        if (vector == null) {
            return null;
        }
        if (vector.lengthSquared() == 0) {
            return null;
        }
        return vector.normalize();
    }
    
    /**
     * Compute direction on a curve rail based on current position within the block
     * and incoming velocity. This creates smooth 90-degree turns.
     */
    private static Vector computeCurveDirection(Location location, Vector fallback, 
                                                Vector dir1, Vector dir2) {
        // Position inside the rail block (relative to center)
        double blockX = location.getX() - Math.floor(location.getX());
        double blockZ = location.getZ() - Math.floor(location.getZ());
        double dx = blockX - 0.5;
        double dz = blockZ - 0.5;

        // When exactly at center, approximate a radial using fallback so we still get a tangent
        Vector radial;
        if (Math.abs(dx) < 1.0e-3 && Math.abs(dz) < 1.0e-3) {
            // Use fallback to infer where we came from; choose radial opposite to fallback
            Vector fb = (fallback != null) ? fallback.clone() : new Vector(1, 0, 0);
            if (fb.lengthSquared() < 1.0e-6) fb = new Vector(1, 0, 0);
            fb.setY(0);
            fb.normalize();
            // Opposite ensures tangent points in the same general direction as fallback
            radial = new Vector(-fb.getX(), 0, -fb.getZ());
        } else {
            radial = new Vector(dx, 0, dz);
        }

        // Compute both CW and CCW tangents around Y-axis
        // CW (clockwise):  (x, z) -> (z, -x)
        // CCW (counter-clockwise): (x, z) -> (-z, x)
        Vector tangentCW = new Vector(radial.getZ(), 0, -radial.getX());
        Vector tangentCCW = new Vector(-radial.getZ(), 0, radial.getX());

        // If no fallback, bias tangent towards the mean direction of the two straight legs
        if (fallback == null || fallback.lengthSquared() < 1.0e-6) {
            Vector avg = dir1.clone().add(dir2).normalize();
            // Choose tangent that aligns best with avg
            return (tangentCW.dot(avg) >= tangentCCW.dot(avg) ? tangentCW : tangentCCW).normalize();
        }

        // Choose tangent that best aligns with fallback (ensures correct left/right turn)
        return (tangentCW.dot(fallback) >= tangentCCW.dot(fallback) ? tangentCW : tangentCCW).normalize();
    }
}


