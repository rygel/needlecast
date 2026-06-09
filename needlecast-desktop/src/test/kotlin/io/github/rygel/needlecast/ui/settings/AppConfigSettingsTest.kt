package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppConfigSettingsTest {
    // ── ProjectDirectory.label ───────────────────────────────────────────────

    @Test
    fun `label returns displayName when set`() {
        val dir = ProjectDirectory(path = "/home/user/proj", displayName = "My Project")
        assertThat(dir.label()).isEqualTo("My Project")
    }

    @Test
    fun `label returns last path segment when displayName is null`() {
        val dir = ProjectDirectory(path = "/home/user/projects/needlecast", displayName = null)
        assertThat(dir.label()).isEqualTo("needlecast")
    }

    @Test
    fun `label handles Windows-style backslashes`() {
        val dir = ProjectDirectory(path = "C:\\Users\\me\\Projects\\needlecast")
        assertThat(dir.label()).isEqualTo("needlecast")
    }

    @Test
    fun `label falls back to full path when both path and displayName are blank`() {
        val dir = ProjectDirectory(path = "")
        // Empty path after substringAfterLast → blank → returns the original path (empty string)
        // The behavior is "ifBlank { path }" so for empty path it stays empty.
        // We assert the actual behavior so this test documents it.
        assertThat(dir.label()).isEqualTo("")
    }

    @Test
    fun `label uses path when path is just a single segment`() {
        val dir = ProjectDirectory(path = "needlecast")
        assertThat(dir.label()).isEqualTo("needlecast")
    }

    // ── ProjectDirectory.label with privacy mode ─────────────────────────────

    @Test
    fun `label returns dots when private and privacy mode enabled`() {
        val dir = ProjectDirectory(path = "/secret/path", isPrivate = true)
        assertThat(dir.label(privacyModeEnabled = true)).isEqualTo("\u2022\u2022\u2022\u2022\u2022\u2022")
    }

    @Test
    fun `label returns normal label when private but privacy mode disabled`() {
        val dir = ProjectDirectory(path = "/secret/path", isPrivate = true)
        assertThat(dir.label(privacyModeEnabled = false)).isEqualTo("path")
    }

    @Test
    fun `label returns normal label when not private regardless of privacy mode`() {
        val dir = ProjectDirectory(path = "/normal/path", isPrivate = false)
        assertThat(dir.label(privacyModeEnabled = true)).isEqualTo("path")
        assertThat(dir.label(privacyModeEnabled = false)).isEqualTo("path")
    }

    // ── ProjectDirectory.redactedPath ────────────────────────────────────────

    @Test
    fun `redactedPath returns dots when private and privacy mode enabled`() {
        val dir = ProjectDirectory(path = "/secret/path", isPrivate = true)
        assertThat(dir.redactedPath(privacyModeEnabled = true)).isEqualTo("\u2022\u2022\u2022\u2022\u2022\u2022")
    }

    @Test
    fun `redactedPath returns full path when privacy mode disabled`() {
        val dir = ProjectDirectory(path = "/secret/path", isPrivate = true)
        assertThat(dir.redactedPath(privacyModeEnabled = false)).isEqualTo("/secret/path")
    }

    @Test
    fun `redactedPath returns path when not private regardless of privacy mode`() {
        val dir = ProjectDirectory(path = "/normal/path", isPrivate = false)
        assertThat(dir.redactedPath(privacyModeEnabled = true)).isEqualTo("/normal/path")
    }

    // ── AppConfig defaults ───────────────────────────────────────────────────

    @Test
    fun `AppConfig default values match contract`() {
        val cfg = AppConfig()
        assertThat(cfg.configVersion).isEqualTo(6)
        assertThat(cfg.windowWidth).isEqualTo(1200)
        assertThat(cfg.windowHeight).isEqualTo(800)
        assertThat(cfg.theme).isEqualTo("dark-purple")
        assertThat(cfg.language).isEqualTo("en")
        assertThat(cfg.showConsole).isTrue()
        assertThat(cfg.showExplorer).isTrue()
        assertThat(cfg.tabsOnTop).isTrue()
        assertThat(cfg.editorFontSize).isEqualTo(12)
        assertThat(cfg.terminalFontSize).isEqualTo(13)
        assertThat(cfg.gitAutoFetch).isTrue()
        assertThat(cfg.gitAutoFetchIntervalMinutes).isEqualTo(5)
        assertThat(cfg.claudeHooksEnabled).isFalse()
        assertThat(cfg.claudeQuotaEnabled).isTrue()
        assertThat(cfg.privacyModeEnabled).isFalse()
        assertThat(cfg.mediaAutoplay).isTrue()
        assertThat(cfg.showContextualHints).isTrue()
        assertThat(cfg.showHelpPopups).isTrue()
    }

    @Test
    fun `AppConfig default external editors include VS Code Zed and IntelliJ`() {
        val cfg = AppConfig()
        val names = cfg.externalEditors.map { it.name }
        assertThat(names).contains("VS Code", "Zed", "IntelliJ IDEA")
    }

    @Test
    fun `AppConfig default collections are empty`() {
        val cfg = AppConfig()
        assertThat(cfg.groups).isEmpty()
        assertThat(cfg.projectTree).isEmpty()
        assertThat(cfg.commandHistory).isEmpty()
        assertThat(cfg.shortcuts).isEmpty()
        assertThat(cfg.aiCliEnabled).isEmpty()
        assertThat(cfg.customAiClis).isEmpty()
        assertThat(cfg.commandOverrides).isEmpty()
        assertThat(cfg.dismissedHints).isEmpty()
        assertThat(cfg.shownHints).isEmpty()
    }

    @Test
    fun `AppConfig is data class with copy semantics`() {
        val a = AppConfig()
        val b = a.copy(theme = "light")
        assertThat(b.theme).isEqualTo("light")
        assertThat(a.theme).isEqualTo("dark-purple")
        assertThat(b.windowWidth).isEqualTo(a.windowWidth)
    }
}
