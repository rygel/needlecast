package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.ui.JediTermWidget
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities

class RealPtyMouseReportingTest {

    private lateinit var terminal: TerminalPanel
    private lateinit var frame: JFrame
    private val capturedOutput = StringBuilder()
    private val outputLatch = CountDownLatch(1)

    @BeforeEach
    fun setUp() {
        val dir = System.getProperty("user.home")
        terminal = TerminalPanel(initialDir = dir)
        frame = JFrame("PTY Mouse Test").apply {
            contentPane.add(terminal)
            setSize(800, 600)
        }
        SwingUtilities.invokeAndWait { frame.isVisible = true }
        Thread.sleep(3000)

        interceptHandleOutput()
        println("[TEST] After setup, mouse mode = ${readMouseMode()}")
    }

    private fun interceptHandleOutput() {
        try {
            val handleOutputField = TerminalPanel::class.java.getDeclaredField("onStatusChanged")
        } catch (_: Exception) {}

        val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
        widgetField.isAccessible = true
        val widget = widgetField.get(terminal) as JediTermWidget

        val ttyField = JediTermWidget::class.java.getDeclaredField("myTtyConnector")
        ttyField.isAccessible = true

        val starterField = JediTermWidget::class.java.getDeclaredField("myTerminalStarter")
        starterField.isAccessible = true

        val emuThreadField = JediTermWidget::class.java.getDeclaredField("myEmuThread")
        emuThreadField.isAccessible = true
        val emuThread = emuThreadField.get(widget) as? Thread
        println("[TEST] Emulator thread: ${emuThread?.name} alive=${emuThread?.isAlive}")
    }

    @AfterEach
    fun tearDown() {
        println("[TEST] Captured output (last 500 chars): ${capturedOutput.takeLast(500)}")
        println("[TEST] Tearing down, mouse mode = ${readMouseMode()}")
        SwingUtilities.invokeAndWait { terminal.dispose(); frame.dispose() }
    }

    @Test
    fun `shell-startup output does not enable mouse reporting`() {
        val mode = readMouseMode()
        assertTrue(mode.contains("NONE"),
            "A plain shell should not enable mouse reporting on startup, was: $mode")
    }

    @Test
    fun `python chr 27 DECSET output enables mouse reporting`() {
        val cmd = if (IS_WINDOWS)
            "python -c \"import sys;sys.stdout.write(chr(27)+'[?1002h');sys.stdout.flush()\"\r\n"
        else
            "python3 -c 'import sys;sys.stdout.write(chr(27)+\"[?1002h\");sys.stdout.flush()'\n"
        println("[TEST] Sending: ${cmd.replace("\r", "<CR>").replace("\n", "<LF>")}")
        terminal.sendInput(cmd)

        Thread.sleep(2000)

        val mode = readMouseMode()
        println("[TEST] After python chr(27): mode=$mode")
        println("[TEST] Terminal output buffer text (last 200 chars): ${getTerminalBufferText().takeLast(200)}")
        assertFalse(mode.contains("NONE"),
            "After python outputs ESC[?1002h via PTY, mouse mode must be active but was: $mode")
    }

    @Test
    fun `mouse wheel is remote action after python chr 27 DECSET via PTY`() {
        val cmd = if (IS_WINDOWS)
            "python -c \"import sys;sys.stdout.write(chr(27)+'[?1002h');sys.stdout.flush()\"\r\n"
        else
            "python3 -c 'import sys;sys.stdout.write(chr(27)+\"[?1002h\");sys.stdout.flush()'\n"
        terminal.sendInput(cmd)

        val mode = pollMouseMode(timeoutMs = 5000)
        assertFalse(mode.contains("NONE"), "Sanity: mouse mode must be active but was: $mode")

        val innerPanel = edtGet {
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as JediTermWidget
            widget.terminalPanel
        }

        val isRemote = edtGet {
            val event = MouseWheelEvent(
                innerPanel, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                100, 100, 100, 100, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.0,
            )
            innerPanel.isRemoteMouseAction(event)
        }

        assertTrue(isRemote,
            "Wheel must be remote action when mouse reporting active via real PTY")
    }

    @Test
    fun `opencode-style triple DECSET via python chr 27 enables mouse reporting`() {
        val py = "import sys;e=chr(27);" +
            "sys.stdout.write(e+'[?1000h'+e+'[?1002h'+e+'[?1006h');" +
            "sys.stdout.flush()"
        val cmd = if (IS_WINDOWS) "python -c \"$py\"\r\n"
            else "python3 -c '$py'\n"
        terminal.sendInput(cmd)

        val mode = pollMouseMode(timeoutMs = 5000)
        assertFalse(mode.contains("NONE"),
            "After sending bubbletea-style sequences via python, mouse mode must be active but was: $mode")
    }

    @Test
    fun `mouse wheel event is consumed when mouse reporting active via PTY`() {
        val cmd = if (IS_WINDOWS)
            "python -c \"import sys;sys.stdout.write(chr(27)+'[?1002h');sys.stdout.flush()\"\r\n"
        else
            "python3 -c 'import sys;sys.stdout.write(chr(27)+\"[?1002h\");sys.stdout.flush()'\n"
        terminal.sendInput(cmd)

        val mode = pollMouseMode(timeoutMs = 5000)
        assertFalse(mode.contains("NONE"), "Sanity: mouse mode must be active but was: $mode")

        val consumed = edtGet {
            val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
            widgetField.isAccessible = true
            val widget = widgetField.get(terminal) as JediTermWidget
            val innerPanel = widget.terminalPanel

            val event = MouseWheelEvent(
                innerPanel, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                100, 100, 100, 100, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.0,
            )

            val isRemote = innerPanel.isRemoteMouseAction(event)
            shouldConsumeRemoteMouseWheelEvent(event, isRemote)
        }

        assertTrue(consumed,
            "Wheel event must be consumed to prevent outer Swing scrolling")
    }

    private fun getTerminalBufferText(): String = edtGet {
        val widgetField = TerminalPanel::class.java.getDeclaredField("termWidget")
        widgetField.isAccessible = true
        val widget = widgetField.get(terminal) as JediTermWidget
        val buffer = widget.terminalTextBuffer
        val sb = StringBuilder()
        for (i in 0 until buffer.height) {
            val line = buffer.getLine(i)
            sb.append(line.text).append("\n")
        }
        sb.toString()
    }

    private fun readMouseMode(): String = edtGet { terminal.readMouseMode() }

    private fun pollMouseMode(timeoutMs: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val mode = readMouseMode()
            if (!mode.contains("NONE")) return mode
            Thread.sleep(100)
        }
        return readMouseMode()
    }

    private fun <T> edtGet(fn: () -> T): T {
        val ref = AtomicReference<T>()
        val err = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            try { ref.set(fn()) } catch (t: Throwable) { err.set(t) } finally { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "EDT call must complete within 10 s")
        err.get()?.let { throw it }
        return ref.get()
    }
}
