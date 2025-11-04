package org.cubexmc.railway.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.LanguageManager;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.service.LineServiceManager;
import org.cubexmc.railway.train.ScoreboardManager;
import org.cubexmc.railway.train.TrainInstance;
import org.cubexmc.railway.util.LocationUtil;

public class VehicleListener implements Listener {

    private final Railway plugin;
    private final LanguageManager language;

    public VehicleListener(Railway plugin) {
        this.plugin = plugin;
        this.language = plugin.getLanguageManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        Minecart minecart = (Minecart) event.getVehicle();
        LineServiceManager manager = plugin.getLineServiceManager();
        TrainInstance train = manager.getTrainByMinecart(minecart.getUniqueId());
        if (train == null || !train.isLead(minecart)) {
            return;
        }

        if (!LocationUtil.isOnRail(event.getTo())) {
            train.getService().handleTrainDerail(train);
            return;
        }

        String targetStopId = train.getTargetStopId();
        if (targetStopId == null) {
            return;
        }

        Stop targetStop = plugin.getStopManager().getStop(targetStopId);
        boolean arrived = false;
        if (targetStop != null) {
            Location to = event.getTo();
            Location stopPoint = targetStop.getStopPointLocation();
            if (stopPoint != null && stopPoint.getWorld() != null && to.getWorld() != null
                    && stopPoint.getWorld().equals(to.getWorld())) {
                if (stopPoint.distanceSquared(to) <= 4.0) {
                    arrived = true;
                }
            } else if (targetStop.isInStop(to)) {
                arrived = true;
            }
        }

        if (arrived) {
            train.handleArrival(targetStop, Bukkit.getCurrentTick());
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }
        
        Entity entered = event.getEntered();
        if (!(entered instanceof Player)) {
            return;
        }
        
        Minecart minecart = (Minecart) event.getVehicle();
        Player player = (Player) entered;
        LineServiceManager manager = plugin.getLineServiceManager();
        TrainInstance train = manager.getTrainByMinecart(minecart.getUniqueId());
        
        if (train == null) {
            return;
        }
        
        // Only allow boarding during WAITING state
        if (!train.isMoving()) {
            train.addPassenger(player, minecart);
            
            // Show welcome message
            String colorized = ChatColor.translateAlternateColorCodes('&', train.getLine().getColor());
            player.sendMessage(language.getMessage("passenger.boarded",
                    LanguageManager.put(LanguageManager.put(LanguageManager.args(),
                            "color", colorized),
                            "line_name", train.getLine().getName())));
            
            // Initialize scoreboard
            ScoreboardManager.updateTravelingScoreboard(player, train.getLine(), train.getTargetStopId());
        } else {
            // Train is moving, don't allow boarding
            event.setCancelled(true);
            player.sendMessage(language.getMessage("passenger.cannot_board_moving"));
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }
        
        Entity exited = event.getExited();
        if (!(exited instanceof Player)) {
            return;
        }
        
        Minecart minecart = (Minecart) event.getVehicle();
        Player player = (Player) exited;
        LineServiceManager manager = plugin.getLineServiceManager();
        TrainInstance train = manager.getTrainByMinecart(minecart.getUniqueId());
        
        if (train != null) {
            train.removePassenger(player);
            ScoreboardManager.clearPlayerDisplay(player);
        } else if (ScoreboardManager.hasRailwayScoreboard(player)) {
            // In case the train mapping was already cleared (e.g., train finished & cleaned),
            // but the player still has our scoreboard, clear it proactively.
            ScoreboardManager.clearPlayerDisplay(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Minecart minecart = (Minecart) event.getVehicle();
        LineServiceManager manager = plugin.getLineServiceManager();
        TrainInstance train = manager.getTrainByMinecart(minecart.getUniqueId());
        if (train == null) {
            return;
        }

        train.getService().handleTrainDerail(train);
    }
}


