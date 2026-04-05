package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.defaultCommandLibrary
import io.github.rygel.needlecast.model.defaultPromptLibrary

/**
 * Applies sequential schema migrations to configs loaded from disk.
 *
 * When the stored [AppConfig.configVersion] is below [CURRENT_VERSION], each
 * intermediate migration is applied in order and the version is bumped.
 * This prevents silent data loss when the schema evolves.
 *
 * **Adding a new migration:**
 * 1. Increment [CURRENT_VERSION].
 * 2. Add an `if (c.configVersion < N) c = vN_1_to_vN(c)` block in [runMigrations].
 * 3. Add a test in `ConfigMigratorTest`.
 */
object ConfigMigrator {

    const val CURRENT_VERSION = 3

    fun migrate(config: AppConfig): AppConfig {
        if (config.configVersion >= CURRENT_VERSION) return config
        return runMigrations(config).copy(configVersion = CURRENT_VERSION)
    }

    private fun runMigrations(config: AppConfig): AppConfig {
        var c = config
        if (c.configVersion < 2) c = v1ToV2(c)
        if (c.configVersion < 3) c = v2ToV3(c)
        return c
    }

    private fun v1ToV2(config: AppConfig): AppConfig {
        return if (config.promptLibrary.isEmpty()) {
            config.copy(promptLibrary = defaultPromptLibrary())
        } else {
            config
        }
    }

    /**
     * v2 → v3: Merge new default prompts and commands into existing libraries.
     *
     * Earlier versions shipped with a small set of basic prompts. v3 has a much
     * richer library. We add any defaults whose name doesn't already exist in
     * the user's library, preserving custom prompts they created.
     */
    private fun v2ToV3(config: AppConfig): AppConfig {
        val existingPromptNames = config.promptLibrary.map { it.name }.toSet()
        val newPrompts = defaultPromptLibrary().filter { it.name !in existingPromptNames }

        val existingCommandNames = config.commandLibrary.map { it.name }.toSet()
        val newCommands = defaultCommandLibrary().filter { it.name !in existingCommandNames }

        return config.copy(
            promptLibrary = config.promptLibrary + newPrompts,
            commandLibrary = config.commandLibrary + newCommands,
        )
    }
}
