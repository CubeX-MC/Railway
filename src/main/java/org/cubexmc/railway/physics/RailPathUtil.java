package org.cubexmc.railway.physics;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

/**
 * Simple rail geometry helper that mirrors TrainCarts' rail paths for vanilla rail shapes.
 * Provides directional vectors and projection helpers that stay consistent with how
 * TrainCarts walks rail segments.
 */
final class RailPathUtil {
    private static final double BASE_Y = 0.0625;
    private static final Map<Rail.Shape, double[][]> SHAPE_POINTS = new EnumMap<>(Rail.Shape.class);

    static {
        // Straight rails
        register(Rail.Shape.NORTH_SOUTH, new double[][] {
                {0.5, BASE_Y, 0.0},
                {0.5, BASE_Y, 1.0}
        });
        register(Rail.Shape.EAST_WEST, new double[][] {
                {0.0, BASE_Y, 0.5},
                {1.0, BASE_Y, 0.5}
        });

        // Curved rails (north/east etc.)
        register(Rail.Shape.NORTH_EAST, new double[][] {
                {0.5, BASE_Y, 0.0},
                {0.75, BASE_Y, 0.25},
                {1.0, BASE_Y, 0.5}
        });
        register(Rail.Shape.NORTH_WEST, new double[][] {
                {0.5, BASE_Y, 0.0},
                {0.25, BASE_Y, 0.25},
                {0.0, BASE_Y, 0.5}
        });
        register(Rail.Shape.SOUTH_EAST, new double[][] {
                {0.5, BASE_Y, 1.0},
                {0.75, BASE_Y, 0.75},
                {1.0, BASE_Y, 0.5}
        });
        register(Rail.Shape.SOUTH_WEST, new double[][] {
                {0.5, BASE_Y, 1.0},
                {0.25, BASE_Y, 0.75},
                {0.0, BASE_Y, 0.5}
        });

        // Ascending rails (slope of 1 block)
        register(Rail.Shape.ASCENDING_EAST, new double[][] {
                {0.0, BASE_Y, 0.5},
                {1.0, BASE_Y + 1.0, 0.5}
        });
        register(Rail.Shape.ASCENDING_WEST, new double[][] {
                {1.0, BASE_Y, 0.5},
                {0.0, BASE_Y + 1.0, 0.5}
        });
        register(Rail.Shape.ASCENDING_NORTH, new double[][] {
                {0.5, BASE_Y, 1.0},
                {0.5, BASE_Y + 1.0, 0.0}
        });
        register(Rail.Shape.ASCENDING_SOUTH, new double[][] {
                {0.5, BASE_Y, 0.0},
                {0.5, BASE_Y + 1.0, 1.0}
        });
    }

    private RailPathUtil() {
        // Utility
    }

    private static void register(Rail.Shape shape, double[][] points) {
        SHAPE_POINTS.put(shape, points);
    }

    /**
     * Computes a motion vector along the rail at the supplied location. If no rail geometry
     * is available, the supplied fallback vector is returned.
     */
    static Vector computeDirection(Location location, Vector fallback) {
        PathSpec spec = resolve(location);
        if (spec == null) {
            return fallback != null ? fallback.clone() : new Vector();
        }
        Vector direction = spec.bestSegmentDirection(location);
        if (direction == null || direction.lengthSquared() < 1.0e-8) {
            return fallback != null ? fallback.clone() : new Vector();
        }
        if (fallback != null && fallback.lengthSquared() > 1.0e-8 && direction.dot(fallback) < 0.0) {
            direction.multiply(-1.0);
        }
        return direction.normalize();
    }

    /**
     * Projects the supplied location onto the rail path. When no rail data is available,
     * the original location is returned.
     */
    static Location project(Location location) {
        PathSpec spec = resolve(location);
        if (spec == null) {
            return location;
        }
        Vector projected = spec.project(location);
        return new Location(location.getWorld(), projected.getX(), projected.getY(), projected.getZ());
    }

    private static PathSpec resolve(Location location) {
        if (location == null) {
            return null;
        }
        Block block = location.getBlock();
        Rail rail = railData(block);
        if (rail == null) {
            block = block.getRelative(0, -1, 0);
            rail = railData(block);
        }
        if (rail == null) {
            return null;
        }
        double[][] points = SHAPE_POINTS.get(rail.getShape());
        if (points == null || points.length < 2) {
            return null;
        }
        return new PathSpec(block, points);
    }

    private static Rail railData(Block block) {
        if (block == null) {
            return null;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Rail) {
            return (Rail) data;
        }
        return null;
    }

    private static final class PathSpec {
        private final Vector[] absolutePoints;

        PathSpec(Block block, double[][] relativePoints) {
            double baseX = block.getX();
            double baseY = block.getY();
            double baseZ = block.getZ();
            this.absolutePoints = new Vector[relativePoints.length];
            for (int i = 0; i < relativePoints.length; i++) {
                double[] p = relativePoints[i];
                this.absolutePoints[i] = new Vector(baseX + p[0], baseY + p[1], baseZ + p[2]);
            }
        }

        Vector bestSegmentDirection(Location location) {
            Vector current = location.toVector();
            double bestDistSq = Double.MAX_VALUE;
            Vector bestDirection = null;
            for (int i = 0; i < absolutePoints.length - 1; i++) {
                Vector start = absolutePoints[i];
                Vector end = absolutePoints[i + 1];
                Vector seg = end.clone().subtract(start);
                double lenSq = seg.lengthSquared();
                if (lenSq < 1.0e-8) {
                    continue;
                }
                double t = current.clone().subtract(start).dot(seg) / lenSq;
                t = Math.max(0.0, Math.min(1.0, t));
                Vector projection = start.clone().add(seg.clone().multiply(t));
                double distSq = projection.distanceSquared(current);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestDirection = seg.clone();
                }
            }
            return bestDirection != null ? bestDirection : null;
        }

        Vector project(Location location) {
            Vector current = location.toVector();
            double bestDistSq = Double.MAX_VALUE;
            Vector bestPoint = null;
            for (int i = 0; i < absolutePoints.length - 1; i++) {
                Vector start = absolutePoints[i];
                Vector end = absolutePoints[i + 1];
                Vector seg = end.clone().subtract(start);
                double lenSq = seg.lengthSquared();
                if (lenSq < 1.0e-8) {
                    continue;
                }
                double t = current.clone().subtract(start).dot(seg) / lenSq;
                t = Math.max(0.0, Math.min(1.0, t));
                Vector projection = start.clone().add(seg.clone().multiply(t));
                double distSq = projection.distanceSquared(current);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPoint = projection;
                }
            }
            if (bestPoint == null) {
                bestPoint = absolutePoints[absolutePoints.length - 1].clone();
            }
            return bestPoint;
        }
    }
}
