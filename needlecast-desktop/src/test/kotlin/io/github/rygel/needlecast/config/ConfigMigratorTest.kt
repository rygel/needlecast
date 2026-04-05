package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.AppConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigMigratorTest {

    @Test
    fun `config at current version is returned unchanged`() {
        val config = AppConfig(configVersion = ConfigMigrator.CURRENT_VERSION)
        val result = ConfigMigrator.migrate(config)
        assertSame(config, result)
    }

    @Test
    fun `config below current version is bumped to current`() {
        val old = AppConfig(configVersion = 0)
        val result = ConfigMigrator.migrate(old)
        assertEquals(ConfigMigrator.CURRENT_VERSION, result.configVersion)
    }

    @Test
    fun `migration preserves existing data`() {
        val old = AppConfig(configVersion = 0, theme = "light", windowWidth = 1920)
        val result = ConfigMigrator.migrate(old)
        assertEquals("light", result.theme)
        assertEquals(1920, result.windowWidth)
    }

    @Test
    fun `v2 migration seeds prompt library when empty`() {
        val old = AppConfig(configVersion = 1, promptLibrary = emptyList())
        val result = ConfigMigrator.migrate(old)
        assertTrue(result.promptLibrary.isNotEmpty())
    }

    @Test
    fun `v2 migration keeps existing prompt library`() {
        val custom = AppConfig().promptLibrary.first()
        val old = AppConfig(configVersion = 1, promptLibrary = listOf(custom))
        val result = ConfigMigrator.migrate(old)
        // v3 merges new defaults but preserves the existing prompt
        assertTrue(result.promptLibrary.any { it.name == custom.name })
    }

    @Test
    fun `v3 migration merges new prompts into existing library`() {
        val defaults = AppConfig().promptLibrary
        // Simulate a user who had only one custom prompt
        val custom = defaults.first()
        val old = AppConfig(configVersion = 2, promptLibrary = listOf(custom), commandLibrary = emptyList())
        val result = ConfigMigrator.migrate(old)
        // Should have the custom prompt plus all defaults that weren't already present
        assertTrue(result.promptLibrary.size > 1)
        assertTrue(result.promptLibrary.any { it.name == custom.name })
        // Command library should also be populated
        assertTrue(result.commandLibrary.isNotEmpty())
    }

    @Test
    fun `v3 migration does not duplicate existing prompts`() {
        val defaults = AppConfig().promptLibrary
        val old = AppConfig(configVersion = 2, promptLibrary = defaults)
        val result = ConfigMigrator.migrate(old)
        assertEquals(defaults.size, result.promptLibrary.size)
    }

    @Test
    fun `migrate is idempotent`() {
        val config = AppConfig(configVersion = 0)
        val once  = ConfigMigrator.migrate(config)
        val twice = ConfigMigrator.migrate(once)
        assertEquals(once.configVersion, twice.configVersion)
    }
}
