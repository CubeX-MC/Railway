package org.cubexmc.railway.train;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.manager.LineManager;
import org.cubexmc.railway.manager.StopManager;
import org.cubexmc.railway.model.Line;
import org.cubexmc.railway.model.Stop;
import org.cubexmc.railway.util.SchedulerUtil;
import org.cubexmc.railway.util.AdventureUtil;

/**
 * Scoreboard manager for displaying line information when players ride trains
 */
public class ScoreboardManager {
    
    private static final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private static Railway plugin;
    
    /**
     * Initialize scoreboard manager
     * 
     * @param railwayPlugin Railway plugin instance
     */
    public static void initialize(Railway railwayPlugin) {
        plugin = railwayPlugin;
    }
    
    /**
     * Create new scoreboard
     * 
     * @param player Player
     * @param title Scoreboard title
     */
    public static void createScoreboard(Player player, String title) {
        if (player == null || !player.isOnline() || plugin == null) {
            return;
        }
        
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("railway", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    /**
     * Set scoreboard line
     * 
     * @param player Player
     * @param score Line score (for sorting, higher is shown higher)
     * @param text Line text
     */
    public static void setLine(Player player, int score, String text) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            return;
        }
        
        Objective objective = scoreboard.getObjective("railway");
        if (objective == null) {
            return;
        }
        
        Score scoreObj = objective.getScore(text);
        scoreObj.setScore(score);
    }
    
    /**
     * Update scoreboard for passenger entering stop area
     * 
     * @param player Player
     * @param line Current line being traveled
     * @param currentStopId Current stop ID
     */
    public static void updateEnteringStopScoreboard(Player player, Line line, String currentStopId) {
        if (player == null || !player.isOnline() || line == null || plugin == null) {
            return;
        }
        
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        
        List<String> stopIds = line.getOrderedStopIds();
        int currentIndex = stopIds.indexOf(currentStopId);
        String nextStopId = (currentIndex >= 0 && currentIndex < stopIds.size() - 1) 
            ? stopIds.get(currentIndex + 1) : null;
        
        updateScoreboardInternal(player, line, currentStopId, nextStopId);
    }
    
    /**
     * Update scoreboard for traveling passenger
     * 
     * @param player Player
     * @param line Current line being traveled
     * @param targetStopId Target stop ID
     */
    public static void updateTravelingScoreboard(Player player, Line line, String targetStopId) {
        if (player == null || !player.isOnline() || line == null || plugin == null) {
            return;
        }
        
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        
        updateScoreboardInternal(player, line, null, targetStopId);
    }
    
    /**
     * Update scoreboard for passenger at terminal
     * 
     * @param player Player
     * @param line Current line being traveled
     * @param currentStopId Current terminal stop ID
     */
    public static void updateTerminalScoreboard(Player player, Line line, String currentStopId) {
        if (player == null || !player.isOnline() || line == null || plugin == null) {
            return;
        }
        
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        
        updateScoreboardInternal(player, line, currentStopId, null);
    }
    
    /**
     * Internal method: core scoreboard update logic
     * 
     * @param player Player
     * @param line Current line being traveled
     * @param currentStopId Current stop ID (can be null)
     * @param nextStopId Next stop ID (can be null)
     */
    private static void updateScoreboardInternal(Player player, Line line, String currentStopId, String nextStopId) {
        if (SchedulerUtil.isFolia()) {
            return;
        }
        
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = playerScoreboards.getOrDefault(player.getUniqueId(), manager.getNewScoreboard());
        if (scoreboard == null) {
            return;
        }

        // Clear old scoreboard content
        if (scoreboard.getObjective("railway") != null) {
            scoreboard.getObjective("railway").unregister();
        }

        // Create new scoreboard
        Objective objective = scoreboard.registerNewObjective("railway", "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + line.getName());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Get all stops on the line
        List<String> stopIds = line.getOrderedStopIds();
        StopManager stopManager = plugin.getStopManager();
        LineManager lineManager = plugin.getLineManager();

        // Get style settings from config
        String currentStopStyle = plugin.getConfig().getString("scoreboard.styles.current_stop", "&f");
        String nextStopStyle = plugin.getConfig().getString("scoreboard.styles.next_stop", "&a");
        String otherStopsStyle = plugin.getConfig().getString("scoreboard.styles.other_stops", "&7");

        currentStopStyle = ChatColor.translateAlternateColorCodes('&', currentStopStyle);
        nextStopStyle = ChatColor.translateAlternateColorCodes('&', nextStopStyle);
        otherStopsStyle = ChatColor.translateAlternateColorCodes('&', otherStopsStyle);

        String lineSymbol = plugin.getConfig().getString("scoreboard.line_symbol", "●");

        int scoreValue = stopIds.size();
        Map<String, String> displayedStops = new HashMap<>();

        int currentStopIndex = currentStopId != null ? stopIds.indexOf(currentStopId) : -1;
        int nextStopIndex = nextStopId != null ? stopIds.indexOf(nextStopId) : -1;

        for (int i = 0; i < stopIds.size(); i++) {
            String stopId = stopIds.get(i);
            Stop stop = stopManager.getStop(stopId);
            if (stop != null) {
                String displayName = stop.getName();

                if (displayedStops.containsKey(displayName)) {
                    continue;
                }

                displayedStops.put(displayName, displayName);

                List<String> transferableLines = stop.getTransferableLines();
                StringBuilder transferInfo = new StringBuilder();

                if (transferableLines != null && !transferableLines.isEmpty()) {
                    List<String> filteredLines = new ArrayList<>(transferableLines);
                    filteredLines.remove(line.getId());

                    if (!filteredLines.isEmpty()) {
                        for (String transferLineId : filteredLines) {
                            Line transferLine = lineManager.getLine(transferLineId);
                            if (transferLine != null) {
                                String coloredSymbol = ChatColor.translateAlternateColorCodes('&', 
                                    transferLine.getColor()) + lineSymbol + " ";
                                transferInfo.append(coloredSymbol);
                            }
                        }
                    }
                }

                String formattedName;
                if (i == currentStopIndex) {
                    formattedName = currentStopStyle + displayName;
                } else if (i == nextStopIndex) {
                    formattedName = nextStopStyle + displayName;
                } else {
                    formattedName = otherStopsStyle + displayName;
                }

                if (transferInfo.length() > 0) {
                    formattedName += " " + transferInfo;
                }

                Score score = objective.getScore(formattedName);
                score.setScore(scoreValue--);
            }
        }

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getUniqueId(), scoreboard);
    }
    
    /**
     * Clear player's railway scoreboard
     * 
     * @param player Player to clear scoreboard for
     */
    public static void clearScoreboard(Player player) {
        if (SchedulerUtil.isFolia()) {
            return;
        }
        
        if (player == null || !player.isOnline()) {
            return;
        }

        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getNewScoreboard());
            playerScoreboards.remove(player.getUniqueId());
        }
    }
    
    /**
     * Clear player's display content (including scoreboard and title)
     * 
     * @param player Player to clear display for
     */
    public static void clearPlayerDisplay(Player player) {
        clearScoreboard(player);
        
        if (player != null && player.isOnline()) {
            AdventureUtil.clearTitle(player);
        }
    }

    /**
     * Checks whether the player currently has a Railway-managed scoreboard.
     */
    public static boolean hasRailwayScoreboard(Player player) {
        if (player == null) return false;
        return playerScoreboards.containsKey(player.getUniqueId());
    }
}

