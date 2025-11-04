package org.cubexmc.railway.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

/**
 * Reflection bridge that mirrors TrainCarts' direct NMS control over minecarts.
 * Falls back to standard Bukkit APIs when the bridge is unavailable.
 */
public final class MinecartNmsUtil {

    private static final Object INIT_LOCK = new Object();

    private static volatile boolean initialized;
    private static volatile boolean available;

    private static Method getHandleMethod;
    private static Method moveMethod;
    private static int moveMethodParamCount;
    private static Method rotationMethod;
    private static Method setYawMethod;
    private static Method setPitchMethod;
    private static Method setDeltaMovementMethod;
    private static Constructor<?> vec3Constructor;
    private static Class<?> vec3Class;
    private static boolean setDeltaMovementUsesVec3;

    private static final AtomicBoolean failureLogged = new AtomicBoolean(false);

    private MinecartNmsUtil() {
    }

    private static void init() {
        if (initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }
            try {
                Class<?> craftMinecart = Class.forName("org.bukkit.craftbukkit.entity.CraftMinecart");
                getHandleMethod = craftMinecart.getMethod("getHandle");
                available = true;
            } catch (Throwable t) {
                available = false;
                logFailure("Failed to initialize minecart NMS bridge", t);
            } finally {
                initialized = true;
            }
        }
    }

    public static boolean snap(Minecart cart, Location location, Vector velocity, float yaw, float pitch) {
        init();
        if (!available || cart == null || location == null) {
            return false;
        }
        try {
            Object handle = getHandleMethod.invoke(cart);
            ensureNmsMethods(handle);

            if (moveMethod == null) {
                return false;
            }

            if (moveMethodParamCount == 5) {
                moveMethod.invoke(handle, location.getX(), location.getY(), location.getZ(), yaw, pitch);
            } else if (moveMethodParamCount == 3) {
                moveMethod.invoke(handle, location.getX(), location.getY(), location.getZ());
                applyRotation(handle, yaw, pitch);
            } else {
                return false;
            }

            if (velocity != null) {
                applyVelocity(handle, velocity);
            }
            return true;
        } catch (Throwable t) {
            available = false;
            logFailure("Failed to apply minecart NMS snap - falling back to Bukkit API", t);
            return false;
        }
    }

    private static void ensureNmsMethods(Object handle) throws Exception {
        if (moveMethod != null && setDeltaMovementMethod != null) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (moveMethod == null) {
                discoverMoveMethods(handle);
            }
            if (setDeltaMovementMethod == null) {
                discoverVelocityMethods(handle);
            }
        }
    }

    private static void discoverMoveMethods(Object handle) throws Exception {
        Class<?> clazz = handle.getClass();

        // Try known method names first (5-parameter variants)
        for (String name : Arrays.asList("absMoveTo", "moveTo", "setLocation")) {
            Method method = findMethod(clazz, name, double.class, double.class, double.class, float.class, float.class);
            if (method != null) {
                moveMethod = method;
                moveMethodParamCount = 5;
                return;
            }
        }

        // Search for any public method with signature (double,double,double,float,float)
        for (Method method : clazz.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 5 && isDouble(params[0]) && isDouble(params[1]) && isDouble(params[2])
                    && isFloat(params[3]) && isFloat(params[4])) {
                moveMethod = method;
                moveMethodParamCount = 5;
                return;
            }
        }

        // Try known three-parameter method names for position only
        for (String name : Arrays.asList("setPos", "setPosition", "b", "a")) {
            Method method = findMethod(clazz, name, double.class, double.class, double.class);
            if (method != null) {
                moveMethod = method;
                moveMethodParamCount = 3;
                discoverRotationMethods(clazz);
                return;
            }
        }

        // Search for any method with (double,double,double)
        for (Method method : clazz.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3 && isDouble(params[0]) && isDouble(params[1]) && isDouble(params[2])) {
                moveMethod = method;
                moveMethodParamCount = 3;
                discoverRotationMethods(clazz);
                return;
            }
        }

        moveMethod = null;
        moveMethodParamCount = 0;
    }

    private static void discoverRotationMethods(Class<?> clazz) {
        for (String name : Arrays.asList("setRotation", "setRot")) {
            Method method = findMethod(clazz, name, float.class, float.class);
            if (method != null) {
                rotationMethod = method;
                return;
            }
        }

        // Fall back to single-axis rotation methods
        setYawMethod = findMethod(clazz, "setYRot", float.class);
        setPitchMethod = findMethod(clazz, "setXRot", float.class);
    }

    private static void discoverVelocityMethods(Object handle) throws Exception {
        Class<?> clazz = handle.getClass();

        for (String name : Arrays.asList("setDeltaMovement", "setMot", "setVelocity")) {
            Method method = findMethod(clazz, name, double.class, double.class, double.class);
            if (method != null) {
                setDeltaMovementMethod = method;
                setDeltaMovementUsesVec3 = false;
                return;
            }
        }

        for (Method method : clazz.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3 && isDouble(params[0]) && isDouble(params[1]) && isDouble(params[2])) {
                setDeltaMovementMethod = method;
                setDeltaMovementUsesVec3 = false;
                return;
            }
        }

        // Try Vec3 signature
        if (vec3Class == null) {
            try {
                vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
                vec3Constructor = vec3Class.getConstructor(double.class, double.class, double.class);
            } catch (Throwable ignored) {
                vec3Class = null;
                vec3Constructor = null;
            }
        }

        if (vec3Class != null) {
            for (String name : Arrays.asList("setDeltaMovement", "setMot")) {
                Method method = findMethod(clazz, name, vec3Class);
                if (method != null) {
                    setDeltaMovementMethod = method;
                    setDeltaMovementUsesVec3 = true;
                    return;
                }
            }

            for (Method method : clazz.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && vec3Class.isAssignableFrom(params[0])) {
                    setDeltaMovementMethod = method;
                    setDeltaMovementUsesVec3 = true;
                    return;
                }
            }
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... signature) {
        try {
            return clazz.getMethod(name, signature);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean isDouble(Class<?> type) {
        return type == double.class || type == Double.class;
    }

    private static boolean isFloat(Class<?> type) {
        return type == float.class || type == Float.class;
    }

    private static void applyRotation(Object handle, float yaw, float pitch) {
        try {
            if (rotationMethod != null) {
                rotationMethod.invoke(handle, yaw, pitch);
                return;
            }
            if (setYawMethod != null) {
                setYawMethod.invoke(handle, yaw);
            }
            if (setPitchMethod != null) {
                setPitchMethod.invoke(handle, pitch);
            }
        } catch (Throwable ignored) {
            // Ignore rotation failures - Bukkit fallback will handle it
        }
    }

    private static void applyVelocity(Object handle, Vector velocity) throws Exception {
        if (setDeltaMovementMethod == null) {
            return;
        }
        if (setDeltaMovementUsesVec3) {
            Object vec = vec3Constructor != null
                    ? vec3Constructor.newInstance(velocity.getX(), velocity.getY(), velocity.getZ())
                    : null;
            if (vec != null) {
                setDeltaMovementMethod.invoke(handle, vec);
            }
        } else {
            setDeltaMovementMethod.invoke(handle, velocity.getX(), velocity.getY(), velocity.getZ());
        }
    }

    private static void logFailure(String message, Throwable throwable) {
        if (failureLogged.compareAndSet(false, true)) {
            Bukkit.getLogger().log(Level.FINE, "[Railway] " + message, throwable);
        }
    }
}
