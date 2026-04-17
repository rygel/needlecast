package io.github.rygel.needlecast

import io.github.rygel.needlecast.config.ConfigStore
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.config.PromptLibraryStore
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.git.ProcessGitService
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.defaultCommandLibrary
import io.github.rygel.needlecast.model.defaultPromptLibrary
import io.github.rygel.needlecast.process.CommandRunner
import io.github.rygel.needlecast.process.ProcessCommandRunner
import io.github.rygel.needlecast.scanner.CompositeProjectScanner
import io.github.rygel.needlecast.scanner.ProjectScanner
import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.theme.ThemeService
import java.nio.file.Path
import java.util.Locale

/** Implemented by objects that hold background resources needing cleanup on shutdown. */
interface Disposable {
    fun dispose()
}

class AppContext(
    val configStore: ConfigStore = JsonConfigStore(),
    val scanner: ProjectScanner = CompositeProjectScanner(),
    val commandRunner: CommandRunner = ProcessCommandRunner(),
    val gitService: GitService = ProcessGitService(),
    val promptLibraryStore: PromptLibraryStore = PromptLibraryStore(
        Path.of(System.getProperty("user.home"), ".needlecast", "prompts"),
        Path.of(System.getProperty("user.home"), ".needlecast", "commands"),
    ),
) {
    var config: AppConfig = configStore.load()
        private set

    init {
        promptLibraryStore.seedDefaults(defaultPromptLibrary(), defaultCommandLibrary())
    }

    // ── i18n ──────────────────────────────────────────────────────────────

    /** Application-wide translation service. Locale is loaded from [config.language]. */
    val i18n: I18nService = I18nService.create(
        "i18n/messages",
        Locale.forLanguageTag(config.language),
        AppContext::class.java.classLoader,
    )

    /** Switch the active locale and persist the choice to config. Fires all [I18nService] listeners. */
    fun switchLocale(locale: Locale) {
        i18n.setLocale(locale)
        updateConfig(config.copy(language = locale.toLanguageTag()))
    }

    // ── Theme ─────────────────────────────────────────────────────────────

    /** Color palette for the current base theme (dark / light). */
    var themeService: ThemeService = loadThemeService()
        private set

    /** Reloads [themeService] to match the current [config.theme]. Call after a theme switch. */
    fun reloadTheme() {
        themeService = loadThemeService()
    }

    private fun loadThemeService(): ThemeService {
        val name = if (ThemeRegistry.isDark(config.theme)) "dark" else "light"
        return try {
            ThemeService.builder()
                .fromClasspath("themes/$name.json")
                .build()
        } catch (_: Exception) {
            ThemeService.create()
        }
    }

    // ── Config listeners ──────────────────────────────────────────────────

    private val configListeners = mutableListOf<(AppConfig) -> Unit>()

    /** Register a callback invoked on every [updateConfig] call. */
    fun addConfigListener(listener: (AppConfig) -> Unit) {
        configListeners.add(listener)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    private val disposables = mutableListOf<Disposable>()

    /** Register a [Disposable] to be cleaned up on [disposeAll]. */
    fun register(d: Disposable) {
        disposables.add(d)
    }

    /** Dispose all registered [Disposable] instances. Call from `windowClosing`. */
    fun disposeAll() {
        disposables.forEach { it.dispose() }
        disposables.clear()
    }

    // ── Config mutations ──────────────────────────────────────────────────

    fun saveConfig() = configStore.save(config)

    fun updateConfig(updated: AppConfig) {
        config = updated
        saveConfig()
        configListeners.forEach { it(updated) }
    }
}
