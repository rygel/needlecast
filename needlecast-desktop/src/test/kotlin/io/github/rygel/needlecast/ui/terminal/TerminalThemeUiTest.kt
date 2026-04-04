package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.model.StyleState
import io.github.rygel.needlecast.ThemeRegistry
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.UIManager

/**
 * End-to-end tests verifying the terminal background and foreground colours
 * actually reach JediTerm's inner rendering surface.
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally without a virtual display.
 */
class TerminalThemeUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var terminal: TerminalPanel

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
    }

    @AfterEach
    fun tearDown() {
        GuiActionRunner.execute { terminal.dispose() }
        fixture.cleanUp()
        robot.cleanUp()
    }

    private fun createTerminal(themeId: String): JFrame {
        return GuiActionRunner.execute<JFrame> {
            val dark = ThemeRegistry.apply(themeId)
            terminal = TerminalPanel(dark = dark)
            terminal.applyTheme(dark)
            JFrame("Terminal Theme Test").apply {
                contentPane.add(terminal)
                setSize(800, 600)
            }
        }
    }

    @Test
    fun `pushStyleToJediTerm reflection reaches StyleState on inner TerminalPanel`() {
        val frame = createTerminal("dark")
        fixture = FrameFixture(robot, frame)
        fixture.show()
        Thread.sleep(2000)

        val styleState = GuiActionRunner.execute<StyleState?> {
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as com.jediterm.terminal.ui.JediTermWidget
            val innerPanel = widget.terminalPanel
            val field = innerPanel.javaClass.getDeclaredField("myStyleState")
            field.isAccessible = true
            field.get(innerPanel) as? StyleState
        }

        assertNotNull(styleState, "Must be able to access myStyleState on JediTerm's inner TerminalPanel")
    }

    @Test
    fun `terminal inner panel getBackground returns theme colour after applyTheme`() {
        val frame = createTerminal("dark")
        fixture = FrameFixture(robot, frame)
        fixture.show()
        Thread.sleep(2000)

        val (expectedBg, actualBg) = GuiActionRunner.execute<Pair<Color, Color>> {
            val uiBg = UIManager.getColor("TextArea.background")
                ?: UIManager.getColor("Panel.background")

            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as com.jediterm.terminal.ui.JediTermWidget
            val innerBg = widget.terminalPanel.background

            Pair(uiBg, innerBg)
        }

        assertEquals(expectedBg, actualBg,
            "JediTerm inner TerminalPanel.getBackground() must return UIManager colour after applyTheme")
    }

    @Test
    fun `terminal background changes when switching themes`() {
        val frame = createTerminal("dark")
        fixture = FrameFixture(robot, frame)
        fixture.show()
        Thread.sleep(2000)

        val darkBg = GuiActionRunner.execute<Color> {
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as com.jediterm.terminal.ui.JediTermWidget
            widget.terminalPanel.background
        }

        GuiActionRunner.execute {
            val dark = ThemeRegistry.apply("light")
            terminal.applyTheme(dark)
        }
        Thread.sleep(500)

        val lightBg = GuiActionRunner.execute<Color> {
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as com.jediterm.terminal.ui.JediTermWidget
            widget.terminalPanel.background
        }

        assertNotEquals(darkBg, lightBg,
            "Terminal background must change when switching dark → light")
    }

    @Test
    fun `terminal rendered pixels match expected background colour`() {
        val frame = createTerminal("light")
        fixture = FrameFixture(robot, frame)
        fixture.show()
        Thread.sleep(3000) // let shell start and terminal render

        val (expectedBg, sampledColor) = GuiActionRunner.execute<Pair<Color, Color>> {
            val uiBg = UIManager.getColor("TextArea.background")
                ?: Color.WHITE

            // Take a screenshot of the terminal widget
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as com.jediterm.terminal.ui.JediTermWidget
            val innerPanel = widget.terminalPanel
            val w = innerPanel.width
            val h = innerPanel.height
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            innerPanel.paint(img.graphics)

            // Sample a pixel in the middle-right area (likely empty, no text)
            val sampleX = (w * 0.8).toInt().coerceAtLeast(1)
            val sampleY = (h * 0.8).toInt().coerceAtLeast(1)
            val pixel = Color(img.getRGB(sampleX, sampleY))

            // Save screenshot for debugging
            try {
                ImageIO.write(img, "png", java.io.File("target/terminal-theme-test.png"))
            } catch (_: Exception) {}

            Pair(uiBg, pixel)
        }

        assertEquals(expectedBg, sampledColor,
            "Rendered terminal pixel at (80%, 80%) must match UIManager background. " +
            "Screenshot saved to target/terminal-theme-test.png")
    }
}
