package io.github.rygel.needlecast.ui.terminal

import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

/**
 * End-to-end test verifying Ctrl+V paste works in the embedded terminal.
 *
 * The bug: [ShrinkableJediTermWidget] overrides [processKeyEvent] to intercept
 * Ctrl+V, but keyboard focus sits on JediTerm's *inner* TerminalPanel, so the
 * event never reaches the widget's override.
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally without a virtual display.
 */
class TerminalPasteUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var terminal: TerminalPanel

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        val frame = GuiActionRunner.execute<JFrame> {
            val f = JFrame("Terminal Paste Test")
            terminal = TerminalPanel()
            f.contentPane.add(terminal)
            f.setSize(800, 600)
            f
        }
        fixture = FrameFixture(robot, frame)
        fixture.show()
        // Allow shell process to start and the terminal to settle
        Thread.sleep(3000)
    }

    @AfterEach
    fun tearDown() {
        GuiActionRunner.execute { terminal.dispose() }
        fixture.cleanUp()
        robot.cleanUp()
    }

    @Test
    fun `Ctrl+V pastes clipboard content into terminal`() {
        val pasteTriggered = CountDownLatch(1)

        // Spy on the paste callback via reflection so we can detect when it fires.
        GuiActionRunner.execute {
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal)

            val pasteField = widget.javaClass.getDeclaredField("onPasteRequested")
            pasteField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val original = pasteField.get(widget) as? Function0<Unit>
            pasteField.set(widget, {
                original?.invoke()
                pasteTriggered.countDown()
            } as () -> Unit)
        }

        // Put a known marker on the system clipboard
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection("PASTE_MARKER_TEST"), null)

        // Click the terminal area to give focus to the inner terminal component
        robot.click(terminal)
        Thread.sleep(500)

        // Press Ctrl+V — this must reach our paste handler
        robot.pressKey(KeyEvent.VK_CONTROL)
        robot.pressKey(KeyEvent.VK_V)
        robot.releaseKey(KeyEvent.VK_V)
        robot.releaseKey(KeyEvent.VK_CONTROL)

        assertTrue(
            pasteTriggered.await(5, TimeUnit.SECONDS),
            "Ctrl+V must trigger the paste callback — the key event did not reach the paste handler"
        )
    }
}
