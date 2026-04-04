package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
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
import javax.swing.UIManager

/**
 * End-to-end test verifying the text editor's background and foreground
 * colours match the active FlatLaf theme.
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally without a virtual display.
 */
class EditorThemeUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var editorPanel: EditorPanel

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

    private fun buildFrame(themeId: String): JFrame {
        return GuiActionRunner.execute<JFrame> {
            ThemeRegistry.apply(themeId)
            val store = JsonConfigStore(tempDir.resolve("config.json"))
            val ctx = AppContext(configStore = store)
            ctx.updateConfig(AppConfig(theme = themeId))
            editorPanel = EditorPanel(ctx)
            editorPanel.applyTheme(ThemeRegistry.isDark(themeId))
            JFrame("Editor Theme Test").apply {
                contentPane.add(editorPanel)
                setSize(800, 600)
            }
        }
    }

    @Test
    fun `editor background matches UIManager TextArea background in dark theme`() {
        val frame = buildFrame("dark")
        fixture = FrameFixture(robot, frame)
        fixture.show()

        val (expectedBg, editorBg) = GuiActionRunner.execute<Pair<Color, Color>> {
            val expected = UIManager.getColor("TextArea.background")
            val field = EditorPanel::class.java.getDeclaredField("editor")
            field.isAccessible = true
            val editor = field.get(editorPanel) as org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
            Pair(expected, editor.background)
        }

        assertEquals(expectedBg, editorBg,
            "Editor background must match UIManager TextArea.background in dark theme")
    }

    @Test
    fun `editor background matches UIManager TextArea background in light theme`() {
        val frame = buildFrame("light")
        fixture = FrameFixture(robot, frame)
        fixture.show()

        val (expectedBg, editorBg) = GuiActionRunner.execute<Pair<Color, Color>> {
            val expected = UIManager.getColor("TextArea.background")
            val field = EditorPanel::class.java.getDeclaredField("editor")
            field.isAccessible = true
            val editor = field.get(editorPanel) as org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
            Pair(expected, editor.background)
        }

        assertEquals(expectedBg, editorBg,
            "Editor background must match UIManager TextArea.background in light theme")
    }

    @Test
    fun `editor colours change when theme switches from dark to light`() {
        val frame = buildFrame("dark")
        fixture = FrameFixture(robot, frame)
        fixture.show()

        val darkBg = GuiActionRunner.execute<Color> {
            val field = EditorPanel::class.java.getDeclaredField("editor")
            field.isAccessible = true
            (field.get(editorPanel) as org.fife.ui.rsyntaxtextarea.RSyntaxTextArea).background
        }

        GuiActionRunner.execute {
            ThemeRegistry.apply("light")
            javax.swing.SwingUtilities.updateComponentTreeUI(fixture.target())
            editorPanel.applyTheme(false)
        }

        val lightBg = GuiActionRunner.execute<Color> {
            val field = EditorPanel::class.java.getDeclaredField("editor")
            field.isAccessible = true
            (field.get(editorPanel) as org.fife.ui.rsyntaxtextarea.RSyntaxTextArea).background
        }

        assertNotEquals(darkBg, lightBg,
            "Editor background must change when switching from dark to light theme")
    }
}
