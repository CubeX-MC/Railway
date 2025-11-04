package org.cubexmc.railway.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.model.Stop;

public class StopManager {

    private final Railway plugin;
    private final File configFile;
    private FileConfiguration config;
    private final Map<String, Stop> stops = new HashMap<>();

    public StopManager(Railway plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "stops.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) plugin.saveResource("stops.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
        stops.clear();
        ConfigurationSection root = config.getConfigurationSection("");
        if (root != null) {
            Set<String> ids = root.getKeys(false);
            for (String id : ids) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                Stop stop = new Stop(id, sec);
                stops.put(id, stop);
            }
        }
        plugin.getLogger().info("Loaded " + stops.size() + " stops");
    }

    public void saveConfig() {
        for (Map.Entry<String, Stop> e : stops.entrySet()) {
            String stopId = e.getKey();
            Stop stop = e.getValue();
            config.set(stopId, null);
            ConfigurationSection section = config.createSection(stopId);
            stop.saveToConfig(section);
        }
        try { config.save(configFile); } catch (IOException ex) { plugin.getLogger().severe("Could not save stops.yml: " + ex.getMessage()); }
    }

    public Stop getStop(String stopId) { return stops.get(stopId); }

    public Stop getStopContainingLocation(Location location) {
        for (Stop stop : stops.values()) {
            if (stop.isInStop(location)) return stop;
        }
        return null;
    }

    public Set<String> getAllStopIds() { return stops.keySet(); }
    public void reload() { loadConfig(); }

    // convenience ops for commands
    public boolean createStop(String stopId, String displayName, Location corner1, Location corner2, java.util.UUID ownerId) {
        if (stops.containsKey(stopId)) return false;
        Stop stop = new Stop(stopId, displayName == null || displayName.isEmpty() ? stopId : displayName);
        stop.setOwner(ownerId);
        if (corner1 != null && corner2 != null) {
            stop.setCorner1(corner1.clone());
            stop.setCorner2(corner2.clone());
        }
        stops.put(stopId, stop);
        saveConfig();
        return true;
    }

    public boolean setStopCorners(String stopId, Location c1, Location c2) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        stop.setCorner1(c1 != null ? c1.clone() : null);
        stop.setCorner2(c2 != null ? c2.clone() : null);
        saveConfig();
        return true;
    }

    public boolean setStopPoint(String stopId, Location loc, float yaw) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        stop.setStopPointLocation(loc != null ? loc.clone() : null);
        stop.setLaunchYaw(yaw);
        saveConfig();
        return true;
    }

    public boolean setStopName(String stopId, String name) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        stop.setName(name);
        saveConfig();
        return true;
    }

    public boolean deleteStop(String stopId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        stops.remove(stopId);
        config.set(stopId, null);
        saveConfig();
        // also remove from lines
        plugin.getLineManager().delStopFromAllLines(stopId);
        return true;
    }

    public boolean setStopOwner(String stopId, java.util.UUID ownerId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        stop.setOwner(ownerId);
        saveConfig();
        return true;
    }

    public boolean addStopAdmin(String stopId, java.util.UUID adminId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.addAdmin(adminId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean removeStopAdmin(String stopId, java.util.UUID adminId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.removeAdmin(adminId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean allowLineLink(String stopId, String lineId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.allowLine(lineId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean denyLineLink(String stopId, String lineId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.denyLine(lineId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean addTransferLine(String stopId, String lineId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.addTransferableLine(lineId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean removeTransferLine(String stopId, String lineId) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.removeTransferableLine(lineId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean setStopTitle(String stopId, String titleType, java.util.Map<String,String> fields) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        java.util.Map<String, String> merged = new java.util.HashMap<>();
        java.util.Map<String, String> existing = stop.getCustomTitle(titleType);
        if (existing != null) {
            merged.putAll(existing);
        }
        merged.putAll(fields);
        stop.setCustomTitle(titleType, merged);
        saveConfig();
        return true;
    }

    public boolean removeStopTitle(String stopId, String titleType) {
        Stop stop = stops.get(stopId);
        if (stop == null) return false;
        boolean changed = stop.removeCustomTitle(titleType);
        if (changed) saveConfig();
        return changed;
    }
}


