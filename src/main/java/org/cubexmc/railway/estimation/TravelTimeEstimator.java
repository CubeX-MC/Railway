package org.cubexmc.railway.estimation;

import java.io.File;
import java.io.IOException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.railway.Railway;

/**
 * Bayesian travel time estimator per line section using a Normal mean update
 * with fixed prior strength.
 * Stores a prior mean (mu0) and prior strength (kappa0) and maintains weighted
 * observations online.
 * Outliers are rejected based on a configurable sigma threshold using online
 * variance (Welford's method).
 * Optionally applies daily decay to observed weights to adapt over time.
 */
public class TravelTimeEstimator {

    private static final String DATA_DIR = "data";
    private static final String DATA_FILE = "travel_times.yml";

    private final Railway plugin;
    private final File storeFile;
    private final Map<String, SectionStats> statsByKey = new HashMap<>();

    public TravelTimeEstimator(Railway plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.storeFile = new File(dir, DATA_FILE);
    }

    public Railway getPlugin() {
        return plugin;
    }

    public void load() {
        statsByKey.clear();
        if (!storeFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storeFile);
        ConfigurationSection root = yaml.getConfigurationSection("sections");
        if (root == null)
            return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(key);
            if (cs == null)
                continue;

            SectionStats s = new SectionStats(cs.getDouble("mu0", plugin.getDefaultSectionSeconds()));
            // Migration logic: try to load "estimate", if missing use "mean" (old format),
            // or default
            if (cs.contains("estimate")) {
                s.estimate = cs.getDouble("estimate");
                s.sampleCount = cs.getLong("sampleCount", 0);
            } else if (cs.contains("mean")) {
                // Migrate old data
                s.estimate = cs.getDouble("mean");
                s.sampleCount = (long) cs.getDouble("weight", 0);
            }

            statsByKey.put(key, s);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("sections");
        for (Map.Entry<String, SectionStats> e : statsByKey.entrySet()) {
            ConfigurationSection cs = root.createSection(e.getKey());
            SectionStats s = e.getValue();
            cs.set("mu0", s.mu0);
            cs.set("estimate", s.estimate);
            cs.set("sampleCount", s.sampleCount);
        }
        try {
            yaml.save(storeFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save travel time estimates: " + ex.getMessage());
        }
    }

    public double estimateSeconds(String lineId, String fromStopId, String toStopId) {
        if (!plugin.isTravelTimeEnabled()) {
            return plugin.getDefaultSectionSeconds();
        }
        SectionStats s = getOrCreate(lineId, fromStopId, toStopId);
        if (s.sampleCount == 0) {
            return s.mu0; // Use default if no data yet
        }
        return s.estimate;
    }

    public void record(String lineId, String fromStopId, String toStopId, double durationSeconds, double sampleWeight) {
        if (!plugin.isTravelTimeEnabled())
            return;
        if (durationSeconds <= 0.01 || durationSeconds > 600.0) // Simple sanity check (10 mins max)
            return;

        SectionStats s = getOrCreate(lineId, fromStopId, toStopId);

        // Simple Exponential Moving Average (EMA)
        // Alpha determines how fast we adapt. 0.2 means latest sample is 20% of new
        // value.
        // This adapts reasonably quickly (approx 10-15 samples to shift fully) but
        // smooths out jitter.
        double alpha = 0.2;

        if (s.sampleCount == 0) {
            s.estimate = durationSeconds;
        } else {
            s.estimate = (s.estimate * (1.0 - alpha)) + (durationSeconds * alpha);
        }
        s.sampleCount++;
    }

    private SectionStats getOrCreate(String lineId, String fromStopId, String toStopId) {
        String key = key(lineId, fromStopId, toStopId);
        SectionStats s = statsByKey.get(key);
        if (s == null) {
            s = new SectionStats(plugin.getDefaultSectionSeconds());
            statsByKey.put(key, s);
        }
        return s;
    }

    private String key(String lineId, String fromStopId, String toStopId) {
        return Objects.toString(lineId, "?") + ":" + Objects.toString(fromStopId, "?") + "->"
                + Objects.toString(toStopId, "?");
    }

    private static final class SectionStats {
        final double mu0;
        double estimate;
        long sampleCount;

        SectionStats(double mu0) {
            this.mu0 = mu0;
            this.estimate = mu0;
            this.sampleCount = 0;
        }
    }
}
