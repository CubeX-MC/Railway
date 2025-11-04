package org.cubexmc.railway.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.util.SchedulerUtil;
import org.cubexmc.railway.util.TextUtil;
import org.cubexmc.railway.util.AdventureUtil;

public class PlayerMoveListener implements Listener {

    private final Railway plugin;
    private final Map<UUID, Object> actionBarTasks = new HashMap<>();
    private final Map<UUID, Object> titleTasks = new HashMap<>();
    private final Map<UUID, String> playerStopMap = new HashMap<>();

    public PlayerMoveListener(Railway plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isSameBlock(event.getFrom(), event.getTo())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("railway.use")) return;
        Location target = event.getTo() != null ? event.getTo() : player.getLocation();
        processPlayerLocation(player, target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getExited();
        if (!player.hasPermission("railway.use")) {
            return;
        }
        SchedulerUtil.entityRun(plugin, player, () -> processPlayerLocation(player, player.getLocation()), 1L, -1L);
    }

    private void showEnterStopUI(Player player, Line line, Stop stop, StopDisplayContext context) {
        if (player.isInsideVehicle()) {
            return;
        }
        int eta = plugin.getLineServiceManager().estimateNextEtaSeconds(line.getId(), stop.getId());
        sendStopTitle(player, line, stop, context, eta);
    }

    private void startActionBarTask(Player player, Line line, Stop stop, StopDisplayContext context) {
        UUID pid = player.getUniqueId();
        if (player.isInsideVehicle()) {
            return;
        }
        Object task = SchedulerUtil.entityRun(plugin, player, () -> {
            if (!player.isOnline()) { cancelTasks(pid); return; }
            if (player.isInsideVehicle()) { cancelTasks(pid); return; }
            Location loc = player.getLocation();
            if (!stop.isInStop(loc)) { cancelTasks(pid); return; }
            int eta = plugin.getLineServiceManager().estimateNextEtaSeconds(line.getId(), stop.getId());
            String actionbar = applyPlaceholders(context.actionbarTemplate, line, stop, context.lastStop, context.nextStop, context.terminalStop, eta);
            AdventureUtil.sendActionBar(player, actionbar);
        }, 0L, 20L);
        actionBarTasks.put(pid, task);
    }

    private Line findFirstLineContaining(Stop stop) {
        if (stop == null) return null;
        for (Line line : plugin.getLineManager().getAllLines()) {
            if (line.containsStop(stop.getId())) {
                return line;
            }
        }
        return null;
    }

    private void startTitleTask(Player player, Line line, Stop stop, StopDisplayContext context) {
        UUID pid = player.getUniqueId();
        cancelTitleTask(pid);
        if (player.isInsideVehicle()) {
            return;
        }
        if (!context.alwaysShow) {
            return;
        }

        Object task = SchedulerUtil.entityRun(plugin, player, () -> {
            if (!player.isOnline()) { cancelTasks(pid); return; }
            if (player.isInsideVehicle()) { cancelTasks(pid); return; }
            Location loc = player.getLocation();
            if (!stop.isInStop(loc)) { cancelTasks(pid); return; }
            int eta = plugin.getLineServiceManager().estimateNextEtaSeconds(line.getId(), stop.getId());
            sendStopTitle(player, line, stop, context, eta);
        }, Math.max(1L, context.intervalTicks), Math.max(1L, context.intervalTicks));
        titleTasks.put(pid, task);
    }

    private void processPlayerLocation(Player player, Location location) {
        StopManager stopManager = plugin.getStopManager();
        Stop stop = stopManager.getStopContainingLocation(location);
        UUID pid = player.getUniqueId();
        String current = playerStopMap.get(pid);

        if (stop != null) {
            if (!stop.getId().equals(current)) {
                playerStopMap.put(pid, stop.getId());
                cancelTasks(pid);
                Line line = findFirstLineContaining(stop);
                if (line != null && !player.isInsideVehicle()) {
                    StopDisplayContext context = buildDisplayContext(line, stop);
                    showEnterStopUI(player, line, stop, context);
                    startActionBarTask(player, line, stop, context);
                    startTitleTask(player, line, stop, context);
                }
            } else if (!player.isInsideVehicle()) {
                boolean hasActionTask = actionBarTasks.containsKey(pid);
                boolean hasTitleTask = titleTasks.containsKey(pid);
                boolean needsTitleTask = plugin.isStopContinuousAlways() && !hasTitleTask;
                if (!hasActionTask || needsTitleTask) {
                    Line line = findFirstLineContaining(stop);
                    if (line != null) {
                        StopDisplayContext context = buildDisplayContext(line, stop);
                        showEnterStopUI(player, line, stop, context);
                        if (!hasActionTask) {
                            startActionBarTask(player, line, stop, context);
                        }
                        if (needsTitleTask) {
                            startTitleTask(player, line, stop, context);
                        }
                    }
                }
            } else {
                cancelTasks(pid);
                clearTitles(player);
            }
        } else if (current != null) {
            playerStopMap.remove(pid);
            cancelTasks(pid);
            clearTitles(player);
        }
    }

    private void clearTitles(Player player) {
        AdventureUtil.clearTitle(player);
        AdventureUtil.sendActionBar(player, "");
    }

    private StopDisplayContext buildDisplayContext(Line line, Stop stop) {
        StopManager stopManager = plugin.getStopManager();
        String stopId = stop.getId();
        String prevId = line.getPreviousStopId(stopId);
        String nextId = line.getNextStopId(stopId);

        Stop prev = prevId != null ? stopManager.getStop(prevId) : null;
        Stop next = nextId != null ? stopManager.getStop(nextId) : null;

        Stop terminal = null;
        List<String> stopIds = line.getOrderedStopIds();
        if (!stopIds.isEmpty()) {
            String terminalId = stopIds.get(stopIds.size() - 1);
            terminal = stopManager.getStop(terminalId);
        }

        String title;
        String subtitle;
        String actionbar;

        if (prev == null) {
            title = plugin.getStartStopTitle();
            subtitle = plugin.getStartStopSubtitle();
            actionbar = plugin.getStartStopActionbar();
        } else if (next == null) {
            title = plugin.getEndStopTitle();
            subtitle = plugin.getEndStopSubtitle();
            actionbar = plugin.getStopContinuousActionbar();
        } else {
            title = plugin.getStopContinuousTitle();
            subtitle = plugin.getStopContinuousSubtitle();
            actionbar = plugin.getStopContinuousActionbar();
        }

        Map<String, String> custom = stop.getCustomTitle("stop_continuous");
        if (custom != null) {
            if (custom.containsKey("title")) title = custom.get("title");
            if (custom.containsKey("subtitle")) subtitle = custom.get("subtitle");
            if (custom.containsKey("actionbar")) actionbar = custom.get("actionbar");
        }

        StopDisplayContext context = new StopDisplayContext();
        context.lastStop = prev;
        context.nextStop = next;
        context.terminalStop = terminal;
        context.titleTemplate = title;
        context.subtitleTemplate = subtitle;
        context.actionbarTemplate = actionbar;
        context.alwaysShow = plugin.isStopContinuousAlways();
        context.intervalTicks = Math.max(1, plugin.getStopContinuousInterval());
        if (context.alwaysShow) {
            context.fadeIn = 0;
            context.fadeOut = 0;
            int stay = plugin.getStopContinuousStay();
            context.stay = Math.max(stay, context.intervalTicks + 1);
        } else {
            context.fadeIn = plugin.getStopContinuousFadeIn();
            context.stay = plugin.getStopContinuousStay();
            context.fadeOut = plugin.getStopContinuousFadeOut();
        }
        return context;
    }

    private String applyPlaceholders(String template, Line line, Stop current, Stop last, Stop next, Stop terminal, int etaSeconds) {
        String result = TextUtil.replacePlaceholders(template, line, current, last, next, terminal, plugin.getLineManager());
        String etaString = etaSeconds >= 0 ? String.valueOf(etaSeconds) : "--";
        String etaFormatted = etaSeconds >= 0 ? formatEta(etaSeconds) : "--:--";
        result = result.replace("{eta_seconds}", etaString);
        result = result.replace("{eta_formatted}", etaFormatted);
        return result;
    }

    private void sendStopTitle(Player player, Line line, Stop stop, StopDisplayContext context, int etaSeconds) {
        String title = applyPlaceholders(context.titleTemplate, line, stop, context.lastStop, context.nextStop, context.terminalStop, etaSeconds);
        String subtitle = applyPlaceholders(context.subtitleTemplate, line, stop, context.lastStop, context.nextStop, context.terminalStop, etaSeconds);
        AdventureUtil.sendTitle(player, title, subtitle, context.fadeIn, context.stay, context.fadeOut);
    }

    private String formatEta(int etaSeconds) {
        int minutes = Math.max(0, etaSeconds) / 60;
        int seconds = Math.max(0, etaSeconds) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void cancelTasks(UUID pid) {
        cancelActionBarTask(pid);
        cancelTitleTask(pid);
    }

    private void cancelActionBarTask(UUID pid) {
        Object task = actionBarTasks.remove(pid);
        if (task != null) {
            SchedulerUtil.cancelTask(task);
        }
    }

    private void cancelTitleTask(UUID pid) {
        Object task = titleTasks.remove(pid);
        if (task != null) {
            SchedulerUtil.cancelTask(task);
        }
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return true;
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private static class StopDisplayContext {
        Stop lastStop;
        Stop nextStop;
        Stop terminalStop;
        String titleTemplate;
        String subtitleTemplate;
        String actionbarTemplate;
        boolean alwaysShow;
        int fadeIn;
        int stay;
        int fadeOut;
        int intervalTicks;
    }
}


