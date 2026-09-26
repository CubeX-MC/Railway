package org.cubexmc.metro.manager

import org.cubexmc.core.Reloadable
import org.cubexmc.i18n.ColorMode
import org.cubexmc.i18n.I18nOptions
import org.cubexmc.i18n.I18nService
import org.cubexmc.i18n.I18nServices
import org.cubexmc.i18n.MissingKeyMode
import org.cubexmc.i18n.PlaceholderStyle
import org.cubexmc.metro.Metro
import org.cubexmc.metro.update.LanguageUpdater
import org.cubexmc.metro.update.MetroMigrations
import org.cubexmc.metro.util.MetroTextRenderer
import java.io.File

/**
 * Railway's view of the shared [I18nService].
 *
 * Every lookup goes through the service's locale chain (configured language, then `zh_CN`,
 * `en_US`, then the other bundled locales), and each file on disk is backed by the copy bundled in
 * the jar. A key missing from a server's translation therefore falls back instead of rendering as
 * `Missing message: ...`. Rendering stays with [MetroTextRenderer], which also handles the legacy
 * `{name}` placeholders and `&` colour codes the language files still use.
 */
class LanguageManager(
    private val plugin: Metro,
) : Reloadable {
    private var currentLanguage = DEFAULT_LANGUAGE
    private val i18n: I18nService = I18nServices.create(
        plugin,
        I18nOptions.create()
            .languageDirectory("lang")
            .currentLocale { currentLanguage }
            .defaultLocale(DEFAULT_LANGUAGE)
            .fallbackLocales(listOf("en_US", "zh_CN"))
            .bundledLocales(MetroMigrations.BUNDLED_LANGUAGES)
            .prefixToken("<prefix>")
            .missingKeyMode(MissingKeyMode.RETURN_MISSING_MESSAGE_PREFIX)
            .placeholderStyles(
                listOf(PlaceholderStyle.MINIMESSAGE_TAG, PlaceholderStyle.POSITIONAL_PERCENT_INDEX),
            )
            .colorMode(ColorMode.MINIMESSAGE),
    )

    init {
        loadLanguages()
    }

    /** Reload stage: re-reads `settings.default_language` and every language file. */
    override fun reload() = loadLanguages()

    fun loadLanguages() {
        currentLanguage = plugin.config.getString("settings.default_language", DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

        val languageDirectory = File(plugin.dataFolder, "lang")
        if (!languageDirectory.exists()) {
            languageDirectory.mkdirs()
        }
        for (language in MetroMigrations.BUNDLED_LANGUAGES) {
            saveDefaultLanguageFile(language)
        }

        i18n.reload()
        plugin.logger.info("Language: $currentLanguage")
    }

    private fun saveDefaultLanguageFile(languageCode: String) {
        val languageFile = File(plugin.dataFolder, "lang/$languageCode.yml")
        val resourcePath = "lang/$languageCode.yml"
        if (!languageFile.exists()) {
            plugin.saveResource(resourcePath, false)
        } else {
            LanguageUpdater.merge(plugin, languageFile, resourcePath)
        }
    }

    fun getMessage(key: String): String = getMessage(key, currentLanguage)

    fun getMessage(key: String, languageCode: String): String =
        MetroTextRenderer.renderPreservingPlaceholders(rawMessage(key, languageCode))

    fun getMessage(key: String, vararg arguments: Any?): String {
        val positional: MutableMap<String, Any?> = HashMap()
        for (index in arguments.indices) {
            positional["arg${index + 1}"] = arguments[index]
        }
        return MetroTextRenderer.render(rawMessage(key, currentLanguage), positional)
    }

    fun getMessage(key: String, namedArguments: Map<String, Any?>): String =
        MetroTextRenderer.render(rawMessage(key, currentLanguage), namedArguments)

    private fun rawMessage(key: String, languageCode: String): String =
        i18n.rawOrNull(key, languageCode) ?: "Missing message: $key"

    companion object {
        private const val DEFAULT_LANGUAGE = "zh_CN"

        @JvmStatic
        fun args(): MutableMap<String, Any?> = HashMap()

        @JvmStatic
        fun put(
            arguments: MutableMap<String, Any?>,
            key: String,
            value: Any?,
        ): MutableMap<String, Any?> {
            arguments[key] = value
            return arguments
        }
    }
}
