package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.AppConfig

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

    const val CURRENT_VERSION = 4

    fun migrate(config: AppConfig): AppConfig {
        if (config.configVersion >= CURRENT_VERSION) return config
        return runMigrations(config).copy(configVersion = CURRENT_VERSION)
    }

    private fun runMigrations(config: AppConfig): AppConfig {
        // Prompt and command libraries are no longer persisted (v3+).
        // They always come from code via defaultPromptLibrary() / defaultCommandLibrary().
        // No per-version migration steps needed at this time.
        return config
    }
}
