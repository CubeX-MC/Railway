package org.cubexmc.railway.update;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility class for merging default configuration values into existing config files.
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static void applyDefaults(JavaPlugin plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("Default resource not found: " + resourcePath);
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            plugin.getConfig().addDefaults(defaults);
            plugin.getConfig().options().copyDefaults(true);
            plugin.saveConfig();
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to apply default config values from " + resourcePath + ": " + ex.getMessage());
        }
    }
}


