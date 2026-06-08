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
    fun `migrate is idempotent`() {
        val config = AppConfig(configVersion = 0)
        val once = ConfigMigrator.migrate(config)
        val twice = ConfigMigrator.migrate(once)
        assertEquals(once.configVersion, twice.configVersion)
    }

    @Test
    fun `version 5 config migrates to version 6 with new defaults`() {
        val old = AppConfig(configVersion = 5)
        val result = ConfigMigrator.migrate(old)
        assertEquals(6, result.configVersion)
        assertNull(result.editorBackground)
        assertNull(result.editorForeground)
        assertTrue(result.gitAutoFetch)
        assertEquals(5, result.gitAutoFetchIntervalMinutes)
        assertTrue(result.showContextualHints)
        assertTrue(result.showHelpPopups)
        assertTrue(result.dismissedHints.isEmpty())
        assertTrue(result.shownHints.isEmpty())
        assertFalse(result.diffLegendDismissed)
        assertFalse(result.tourCompleted)
    }
}
