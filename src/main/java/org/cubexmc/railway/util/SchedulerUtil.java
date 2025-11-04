package org.cubexmc.railway.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Scheduler utility for Bukkit/Folia compatibility
 */
public class SchedulerUtil {
    
    /**
     * Check if server is running on Folia
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Schedule global task
     */
    public static Object globalRun(Plugin plugin, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Server server = Bukkit.getServer();
            GlobalRegionScheduler globalScheduler = server.getGlobalRegionScheduler();
            Consumer<ScheduledTask> foliaTask = scheduledTask -> task.run();
            if (period <= 0) {
                if (delay == 0)
                    return globalScheduler.run(plugin, foliaTask);
                else
                    return globalScheduler.runDelayed(plugin, foliaTask, delay);
            } else {
                return globalScheduler.runAtFixedRate(plugin, foliaTask, Math.max(1, delay), period);
            }
        } else {
            if (period < 0) {
                if (delay == 0)
                    return Bukkit.getScheduler().runTask(plugin, task);
                else
                    return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            } else {
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        }
    }
    
    /**
     * Cancel scheduled task
     */
    public static void cancelTask(Object task) {
        if (task == null) return;
        try {
            if (isFolia()) {
                if (task instanceof ScheduledTask)
                    ((ScheduledTask) task).cancel();
            } else {
                if (task instanceof BukkitTask)
                    ((BukkitTask) task).cancel();
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Schedule entity-bound task
     */
    @SuppressWarnings("unchecked")
    public static Object entityRun(Plugin plugin, Entity entity, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            EntityScheduler entityScheduler = entity.getScheduler();
            Consumer<ScheduledTask> foliaTask;
            if (task instanceof Runnable)
                foliaTask = scheduledTask -> ((Runnable) task).run();
            else if (task instanceof Consumer)
                foliaTask = (Consumer<ScheduledTask>) task;
            else
                throw new IllegalArgumentException("Task must be either Runnable or Consumer<ScheduledTask>");
            
            Runnable retiredCallback = () -> {
                plugin.getLogger().fine("Entity scheduler task cancelled: entity no longer exists");
            };
            
            if (period <= 0) {
                if (delay == 0)
                    return entityScheduler.run(plugin, foliaTask, retiredCallback);
                else
                    return entityScheduler.runDelayed(plugin, foliaTask, retiredCallback, delay);
            } else {
                return entityScheduler.runAtFixedRate(plugin, foliaTask, retiredCallback, Math.max(1, delay), period);
            }
        } else {
            if (period <= 0) {
                if (delay == 0)
                    return Bukkit.getScheduler().runTask(plugin, (Runnable) task);
                else
                    return Bukkit.getScheduler().runTaskLater(plugin, (Runnable) task, delay);
            } else {
                return Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) task, delay, period);
            }
        }
    }

    /**
     * Teleport entity safely (Folia-compatible)
     */
    public static CompletableFuture<Boolean> teleportEntity(Entity entity, Location location) {
        if (location == null || entity == null) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        if (isFolia()) {
            return entity.teleportAsync(location);
        }
        boolean success = entity.teleport(location);
        return CompletableFuture.completedFuture(success);
    }
    
    /**
     * Schedule region-bound task
     */
    public static Object regionRun(Plugin plugin, Location location, Runnable task, long delay, long period) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Server server = Bukkit.getServer();
            RegionScheduler regionScheduler = server.getRegionScheduler();
            Consumer<ScheduledTask> foliaTask = scheduledTask -> task.run();

            if (period <= 0) {
                if (delay == 0)
                    return regionScheduler.run(plugin, location, foliaTask);
                else
                    return regionScheduler.runDelayed(plugin, location, foliaTask, delay);
            } else {
                return regionScheduler.runAtFixedRate(plugin, location, foliaTask, Math.max(1, delay), period);
            }
        } else {
            if (period <= 0) {
                if (delay == 0)
                    return Bukkit.getScheduler().runTask(plugin, task);
                else
                    return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            } else {
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
        }
    }
    
    /**
     * Schedule async task
     */
    public static void asyncRun(Plugin plugin, Runnable task, long delay) {
        delay = Math.max(0, delay);
        if (isFolia()) {
            Server server = Bukkit.getServer();
            AsyncScheduler asyncScheduler = server.getAsyncScheduler();
            Consumer<ScheduledTask> foliaTask = scheduledTask -> task.run();
            if (delay <= 0) {
                asyncScheduler.runNow(plugin, foliaTask);
            } else {
                asyncScheduler.runDelayed(plugin, foliaTask, delay * 50, TimeUnit.MILLISECONDS);
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }
}

