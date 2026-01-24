package org.cubexmc.railway.estimation;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
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
            SectionStats s = new SectionStats(
                    cs.getDouble("mu0", plugin.getDefaultSectionSeconds()),
                    cs.getDouble("kappa0", plugin.getPriorStrength()),
                    cs.getDouble("sum", 0.0),
                    cs.getDouble("weight", 0.0),
                    cs.getDouble("mean", 0.0),
                    cs.getDouble("m2", 0.0),
                    cs.getInt("lastDay", LocalDate.now().getDayOfYear()));
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
            cs.set("kappa0", s.kappa0);
            cs.set("sum", s.sum);
            cs.set("weight", s.weight);
            cs.set("mean", s.mean);
            cs.set("m2", s.m2);
            cs.set("lastDay", s.lastDay);
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
        if (s.weight <= 0.0) {
            return s.mu0; // prior only
        }
        // posterior mean = (kappa0*mu0 + sum) / (kappa0 + weight)
        return (s.kappa0 * s.mu0 + s.sum) / (s.kappa0 + s.weight);
    }

    public void record(String lineId, String fromStopId, String toStopId, double durationSeconds, double sampleWeight) {
        if (!plugin.isTravelTimeEnabled())
            return;
        if (durationSeconds <= 0.01)
            return;
        sampleWeight = Math.max(0.0, sampleWeight);
        if (sampleWeight == 0.0)
            return;

        SectionStats s = getOrCreate(lineId, fromStopId, toStopId);
        s.decayIfNeeded(plugin.getDecayPerDay());

        // Outlier rejection using current observed mean/variance (not posterior)
        if (s.weight >= 2.0) {
            double sd = s.stddev();
            if (sd > 0) {
                double z = Math.abs(durationSeconds - s.mean) / sd;
                if (z > plugin.getOutlierSigma()) {
                    return; // reject outlier
                }
            }
        }

        // Online weighted Welford update for observed mean/variance
        // Convert to unit-weight updates by splitting? We'll approximate using simple
        // accumulation for mean/variance.
        // Mean/variance maintenance for diagnostics/outlier only; sum/weight drive the
        // posterior.
        double totalWeight = s.weight + sampleWeight;
        double delta = durationSeconds - s.mean;
        double r = sampleWeight / Math.max(1e-9, totalWeight);
        s.mean += r * delta;
        s.m2 += sampleWeight * delta * (durationSeconds - s.mean);

        // Update posterior sufficient stats (observed part)
        s.weight = totalWeight;
        s.sum += sampleWeight * durationSeconds;
    }

    private SectionStats getOrCreate(String lineId, String fromStopId, String toStopId) {
        String key = key(lineId, fromStopId, toStopId);
        SectionStats s = statsByKey.get(key);
        if (s == null) {
            s = new SectionStats(plugin.getDefaultSectionSeconds(), plugin.getPriorStrength(), 0.0, 0.0, 0.0, 0.0,
                    LocalDate.now().getDayOfYear());
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
        final double kappa0;
        double sum; // weighted sum of observations
        double weight; // total weight of observations
        double mean; // observed mean (for outlier detection)
        double m2; // observed sum of squares of differences from mean
        int lastDay; // day-of-year for decay

        SectionStats(double mu0, double kappa0, double sum, double weight, double mean, double m2, int lastDay) {
            this.mu0 = mu0;
            this.kappa0 = Math.max(0.0, kappa0);
            this.sum = sum;
            this.weight = Math.max(0.0, weight);
            this.mean = mean;
            this.m2 = Math.max(0.0, m2);
            this.lastDay = lastDay;
        }

        double stddev() {
            if (weight <= 1.0)
                return 0.0;
            double variance = m2 / (weight - 1.0);
            return variance > 0 ? Math.sqrt(variance) : 0.0;
        }

        void decayIfNeeded(double decayPerDay) {
            if (decayPerDay >= 0.9999)
                return;
            int today = LocalDate.now().getDayOfYear();
            int days = today - lastDay;
            if (days <= 0)
                return;
            double factor = Math.pow(Math.max(0.0, Math.min(1.0, decayPerDay)), days);
            sum *= factor;
            weight *= factor;
            // For mean/m2, applying same factor approximates decay of effective
            // observations
            m2 *= factor;
            lastDay = today;
        }
    }
}
