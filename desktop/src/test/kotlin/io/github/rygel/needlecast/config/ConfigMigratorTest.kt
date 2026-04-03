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
        val old = AppConfig(configVersion = 1, promptLibrary = listOf())
            .copy(promptLibrary = listOf(AppConfig().promptLibrary.first()))
        val result = ConfigMigrator.migrate(old)
        assertEquals(old.promptLibrary, result.promptLibrary)
    }

    @Test
    fun `migrate is idempotent`() {
        val config = AppConfig(configVersion = 0)
        val once  = ConfigMigrator.migrate(config)
        val twice = ConfigMigrator.migrate(once)
        assertEquals(once.configVersion, twice.configVersion)
    }
}
