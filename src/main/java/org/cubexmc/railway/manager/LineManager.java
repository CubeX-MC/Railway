package org.cubexmc.railway.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.railway.Railway;
import org.cubexmc.railway.model.Line;

public class LineManager {

    private final Railway plugin;
    private final File configFile;
    private FileConfiguration config;
    private final Map<String, Line> lines = new HashMap<>();

    public LineManager(Railway plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lines.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("lines.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadLines();
    }

    private void loadLines() {
        lines.clear();
        ConfigurationSection root = config.getConfigurationSection("");
        if (root == null) return;
        for (String lineId : root.getKeys(false)) {
            String name = config.getString(lineId + ".name", lineId);
            Line line = new Line(lineId, name);
            List<String> stopIds = config.getStringList(lineId + ".ordered_stop_ids");
            for (String stopId : stopIds) line.addStop(stopId, -1);
            String color = config.getString(lineId + ".color");
            if (color != null) line.setColor(color);
            String terminusName = config.getString(lineId + ".terminus_name");
            if (terminusName != null) line.setTerminusName(terminusName);
            Double maxSpeed = config.getDouble(lineId + ".max_speed", -1);
            if (maxSpeed >= 0) line.setMaxSpeed(maxSpeed);

            String ownerString = config.getString(lineId + ".owner");
            if (ownerString != null && !ownerString.isEmpty()) {
                try { line.setOwner(UUID.fromString(ownerString)); } catch (IllegalArgumentException ignored) {}
            }
            List<String> adminStrings = config.getStringList(lineId + ".admins");
            if (adminStrings != null && !adminStrings.isEmpty()) {
                Set<UUID> adminIds = new HashSet<>();
                for (String s : adminStrings) { try { adminIds.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {} }
                line.setAdmins(adminIds);
            }

            // service.*
            boolean enabled = config.getBoolean(lineId + ".service.enabled", false);
            int headway = config.getInt(lineId + ".service.headway_seconds", plugin.getConfig().getInt("service.default_headway_seconds", 120));
            int dwell = config.getInt(lineId + ".service.dwell_ticks", plugin.getConfig().getInt("service.default_dwell_ticks", 100));
            int cars = config.getInt(lineId + ".service.train_cars", plugin.getConfig().getInt("service.default_train_cars", 3));
            String dirMode = config.getString(lineId + ".service.direction_mode", "bi_directional");
            long firstTick = config.getLong(lineId + ".service.first_departure_tick", 0L);
            String controlMode = config.getString(lineId + ".service.control_mode", null);

            line.setServiceEnabled(enabled);
            line.setHeadwaySeconds(headway);
            line.setDwellTicks(dwell);
            line.setTrainCars(cars);
            line.setDirectionMode(dirMode);
            line.setFirstDepartureTick(firstTick);
            if (controlMode != null && !controlMode.isEmpty()) {
                line.setControlMode(controlMode);
            }

            lines.put(lineId, line);
        }
    }

    public void saveConfig() {
        try {
            for (Line line : lines.values()) {
                String id = line.getId();
                config.set(id + ".name", line.getName());
                config.set(id + ".ordered_stop_ids", line.getOrderedStopIds());
                config.set(id + ".color", line.getColor());
                config.set(id + ".terminus_name", line.getTerminusName());
                config.set(id + ".max_speed", line.getMaxSpeed() != null ? line.getMaxSpeed() : null);
                config.set(id + ".owner", line.getOwner() != null ? line.getOwner().toString() : null);
                java.util.List<String> adminStrings = new java.util.ArrayList<>();
                for (UUID adminId : line.getAdmins()) {
                    if (line.getOwner() != null && line.getOwner().equals(adminId)) continue;
                    adminStrings.add(adminId.toString());
                }
                config.set(id + ".admins", adminStrings.isEmpty() ? null : adminStrings);
                config.set(id + ".service.enabled", line.isServiceEnabled());
                config.set(id + ".service.headway_seconds", line.getHeadwaySeconds());
                config.set(id + ".service.dwell_ticks", line.getDwellTicks());
                config.set(id + ".service.train_cars", line.getTrainCars());
                config.set(id + ".service.direction_mode", line.getDirectionMode());
                config.set(id + ".service.first_departure_tick", line.getFirstDepartureTick());
                config.set(id + ".service.control_mode", line.getControlMode());
            }
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save lines.yml", e);
        }
    }

    public Line getLine(String lineId) { return lines.get(lineId); }
    public List<Line> getAllLines() { return new ArrayList<>(lines.values()); }
    public List<String> getAllLineIds() { return new ArrayList<>(lines.keySet()); }
    public void reload() { loadConfig(); }

    public boolean setLineOwner(String lineId, UUID ownerId) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setOwner(ownerId);
        saveConfig();
        return true;
    }

    public boolean addLineAdmin(String lineId, UUID adminId) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        boolean changed = line.addAdmin(adminId);
        if (changed) saveConfig();
        return changed;
    }

    public boolean removeLineAdmin(String lineId, UUID adminId) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        boolean changed = line.removeAdmin(adminId);
        if (changed) saveConfig();
        return changed;
    }

    // convenience ops for commands
    public boolean createLine(String lineId, String name) {
        if (lines.containsKey(lineId)) return false;
        Line line = new Line(lineId, name == null || name.isEmpty() ? lineId : name);
        lines.put(lineId, line);
        saveConfig();
        return true;
    }

    public boolean deleteLine(String lineId) {
        if (!lines.containsKey(lineId)) return false;
        lines.remove(lineId);
        config.set(lineId, null);
        saveConfig();
        return true;
    }

    public boolean addStopToLine(String lineId, String stopId, int index) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.addStop(stopId, index);
        saveConfig();
        return true;
    }

    public boolean delStopFromLine(String lineId, String stopId) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.delStop(stopId);
        saveConfig();
        return true;
    }

    public void delStopFromAllLines(String stopId) {
        for (Line line : lines.values()) {
            if (line.containsStop(stopId)) line.delStop(stopId);
        }
        saveConfig();
    }

    public boolean setLineName(String lineId, String name) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setName(name);
        saveConfig();
        return true;
    }

    public boolean setLineColor(String lineId, String color) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setColor(color);
        saveConfig();
        return true;
    }

    public boolean setLineTerminusName(String lineId, String terminusName) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setTerminusName(terminusName);
        saveConfig();
        return true;
    }

    public boolean setLineMaxSpeed(String lineId, Double maxSpeed) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setMaxSpeed(maxSpeed);
        saveConfig();
        return true;
    }

    public boolean setServiceEnabled(String lineId, boolean enabled) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setServiceEnabled(enabled);
        saveConfig();
        return true;
    }

    public boolean setHeadway(String lineId, int seconds) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setHeadwaySeconds(seconds);
        saveConfig();
        return true;
    }

    public boolean setDwell(String lineId, int ticks) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setDwellTicks(ticks);
        saveConfig();
        return true;
    }

    public boolean setTrainCars(String lineId, int cars) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setTrainCars(cars);
        saveConfig();
        return true;
    }

    public boolean setLineControlMode(String lineId, String mode) {
        Line line = lines.get(lineId);
        if (line == null) return false;
        line.setControlMode(mode);
        saveConfig();
        return true;
    }
}


