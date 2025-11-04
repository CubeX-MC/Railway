package org.cubexmc.railway.update;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility helpers for merging bundled language defaults into user-facing language files.
 */
public final class LanguageUpdater {

    private LanguageUpdater() {
    }

    public static void merge(JavaPlugin plugin, File targetFile, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                plugin.getLogger().warning("Language resource not found: " + resourcePath);
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(targetFile);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!existing.contains(key)) {
                    existing.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                existing.save(targetFile);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to merge language defaults for " + targetFile.getName() + ": " + ex.getMessage());
        }
    }
}


