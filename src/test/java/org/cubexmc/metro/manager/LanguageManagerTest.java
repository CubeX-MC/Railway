package org.cubexmc.metro.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.metro.Metro;
import org.cubexmc.metro.update.LanguageUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class LanguageManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void diskTranslationWinsOverBundledCopy() throws IOException {
        LanguageManager manager = createManager("zh_CN");

        assertEquals("disk hello", manager.getMessage("greeting"));
    }

    @Test
    void keyMissingFromDiskFallsBackToBundledCopyOfSameLanguage() throws IOException {
        LanguageManager manager = createManager("zh_CN");

        assertEquals("from jar", manager.getMessage("only_in_jar"));
    }

    @Test
    void keyMissingFromConfiguredLanguageFallsBackDownTheLocaleChain() throws IOException {
        LanguageManager manager = createManager("zh_CN");

        assertEquals("english only", manager.getMessage("only_in_english"));
    }

    @Test
    void unknownKeyStillRendersAsMissingMessage() throws IOException {
        LanguageManager manager = createManager("zh_CN");

        assertEquals("Missing message: does.not.exist", manager.getMessage("does.not.exist"));
    }

    @Test
    void reloadPicksUpChangedLanguageSetting() throws IOException {
        Metro plugin = createPluginMock("zh_CN");
        try (MockedStatic<LanguageUpdater> ignored = mockStatic(LanguageUpdater.class)) {
            LanguageManager manager = new LanguageManager(plugin);
            assertEquals("disk hello", manager.getMessage("greeting"));

            YamlConfiguration english = new YamlConfiguration();
            english.set("settings.default_language", "en_US");
            when(plugin.getConfig()).thenReturn(english);
            manager.reload();

            assertEquals("hello", manager.getMessage("greeting"));
        }
    }

    private LanguageManager createManager(String language) throws IOException {
        Metro plugin = createPluginMock(language);
        // Existing disk files are merged through the real migration pipeline, which is out of scope here.
        try (MockedStatic<LanguageUpdater> ignored = mockStatic(LanguageUpdater.class)) {
            return new LanguageManager(plugin);
        }
    }

    private Metro createPluginMock(String language) throws IOException {
        Path langDir = Files.createDirectories(tempDir.resolve("lang"));
        Files.writeString(langDir.resolve("zh_CN.yml"), "greeting: disk hello\n", StandardCharsets.UTF_8);

        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.default_language", language);

        Metro plugin = mock(Metro.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LanguageManagerTest"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getResource(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0, String.class).replace('\\', '/');
            switch (path) {
                case "lang/zh_CN.yml":
                    return stream("greeting: jar hello\nonly_in_jar: from jar\n");
                case "lang/en_US.yml":
                    return stream("greeting: hello\nonly_in_english: english only\n");
                default:
                    return null;
            }
        });
        return plugin;
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
