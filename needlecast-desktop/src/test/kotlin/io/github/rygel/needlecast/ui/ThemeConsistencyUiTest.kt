package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.ui.explorer.EditorPanel
import io.github.rygel.needlecast.ui.terminal.QuickLaunchTerminalSettings
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * End-to-end tests verifying that the terminal, editor, and surrounding app all
 * use colours from the active FlatLaf theme.
 *
 * Covers:
 * - Terminal default style bg/fg derived from UIManager (not hardcoded)
 * - Editor bg/fg derived from UIManager (not hardcoded)
 * - Both components update when the theme switches
 * - Terminal and editor backgrounds match each other (same UIManager key)
 * - Selection colours derived from UIManager
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally without a virtual display.
 */
class ThemeConsistencyUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanUp()
        robot.cleanUp()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun applyThemeAndGetColors(themeId: String): ThemeSnapshot {
        return GuiActionRunner.execute<ThemeSnapshot> {
            val dark = ThemeRegistry.apply(themeId)

            // Terminal settings
            val settings = QuickLaunchTerminalSettings(dark = dark)
            settings.applyDark(dark)
            val themeBg = UIManager.getColor("TextArea.background")
                ?: UIManager.getColor("Panel.background")
            val themeFg = UIManager.getColor("TextArea.foreground")
                ?: UIManager.getColor("Panel.foreground")
            settings.applyThemeColors(themeFg, themeBg)
            val termStyle = settings.getDefaultStyle()
            val termBg = termStyle.background?.toAwtColor() ?: Color.BLACK
            val termFg = termStyle.foreground?.toAwtColor() ?: Color.WHITE
            val selStyle = settings.getSelectionColor()
            val selBg = selStyle.background?.toAwtColor() ?: Color.BLUE

            // Editor
            val store = JsonConfigStore(tempDir.resolve("cfg-$themeId.json"))
            val ctx = AppContext(configStore = store)
            ctx.updateConfig(AppConfig(theme = themeId))
            val editorPanel = EditorPanel(ctx)
            editorPanel.applyTheme(dark)
            val editorField = EditorPanel::class.java.getDeclaredField("editor")
            editorField.isAccessible = true
            val editor = editorField.get(editorPanel) as org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
            val editorBg = editor.background
            val editorFg = editor.foreground

            // UIManager reference
            val uiBg = UIManager.getColor("TextArea.background")
            val uiFg = UIManager.getColor("TextArea.foreground")
            val uiSelBg = UIManager.getColor("TextArea.selectionBackground")

            ThemeSnapshot(
                themeId = themeId, dark = dark,
                uiBg = uiBg, uiFg = uiFg, uiSelBg = uiSelBg,
                termBg = termBg, termFg = termFg, termSelBg = selBg,
                editorBg = editorBg, editorFg = editorFg,
            )
        }
    }

    private data class ThemeSnapshot(
        val themeId: String, val dark: Boolean,
        val uiBg: Color, val uiFg: Color, val uiSelBg: Color?,
        val termBg: Color, val termFg: Color, val termSelBg: Color,
        val editorBg: Color, val editorFg: Color,
    )

    private fun com.jediterm.terminal.TerminalColor.toAwtColor(): Color {
        // TerminalColor.toAwtColor() is package-private; use reflection
        try {
            val m = this.javaClass.getDeclaredMethod("toAwtColor")
            m.isAccessible = true
            return m.invoke(this) as Color
        } catch (_: Exception) {
            // Fallback: create from RGB via toString parsing
            return Color.MAGENTA // obvious failure colour
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `terminal background matches UIManager in dark theme`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val s = applyThemeAndGetColors("dark")
        assertEquals(s.uiBg, s.termBg,
            "Terminal bg must match UIManager TextArea.background in dark theme")
    }

    @Test
    fun `terminal background matches UIManager in light theme`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val s = applyThemeAndGetColors("light")
        assertEquals(s.uiBg, s.termBg,
            "Terminal bg must match UIManager TextArea.background in light theme")
    }

    @Test
    fun `editor background matches UIManager in dark theme`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val s = applyThemeAndGetColors("dark")
        assertEquals(s.uiBg, s.editorBg,
            "Editor bg must match UIManager TextArea.background in dark theme")
    }

    @Test
    fun `editor background matches UIManager in light theme`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val s = applyThemeAndGetColors("light")
        assertEquals(s.uiBg, s.editorBg,
            "Editor bg must match UIManager TextArea.background in light theme")
    }

    @Test
    fun `terminal and editor backgrounds are identical`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val s = applyThemeAndGetColors("dark")
        assertEquals(s.termBg, s.editorBg,
            "Terminal and editor must share the same background colour")
    }

    @Test
    fun `terminal selection colour matches UIManager`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val s = applyThemeAndGetColors("dark")
        if (s.uiSelBg != null) {
            assertEquals(s.uiSelBg, s.termSelBg,
                "Terminal selection bg must match UIManager TextArea.selectionBackground")
        }
    }

    @Test
    fun `colours change when switching from dark to light`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        val dark = applyThemeAndGetColors("dark")
        val light = applyThemeAndGetColors("light")
        assertNotEquals(dark.termBg, light.termBg,
            "Terminal bg must change when switching dark → light")
        assertNotEquals(dark.editorBg, light.editorBg,
            "Editor bg must change when switching dark → light")
    }

    @Test
    fun `themed variant colours propagate to terminal and editor`() {
        val frame = GuiActionRunner.execute<JFrame> { JFrame().apply { setSize(100, 100) } }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        // Use a themed dark variant — its UIManager colors differ from plain "dark"
        val plain = applyThemeAndGetColors("dark")
        val themed = applyThemeAndGetColors("catppuccin-mocha")
        // Both are dark themes but with different palettes
        assertNotEquals(plain.termBg, themed.termBg,
            "Themed variant must produce different terminal bg than plain dark")
        assertNotEquals(plain.editorBg, themed.editorBg,
            "Themed variant must produce different editor bg than plain dark")
    }
}
