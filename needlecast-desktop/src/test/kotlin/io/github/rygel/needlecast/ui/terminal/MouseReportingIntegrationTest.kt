package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.Questioner
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MouseReportingIntegrationTest {

    private lateinit var widget: JediTermWidget
    private lateinit var connector: MockTtyConnector

    @BeforeEach
    fun setUp() {
        val settings = object : DefaultSettingsProvider() {
            override fun enableMouseReporting(): Boolean = true
            override fun forceActionOnMouseReporting(): Boolean = false
        }
        widget = JediTermWidget(Dimension(800, 600), settings)
        connector = MockTtyConnector()
        widget.createTerminalSession(connector)
        widget.start()
        assertTrue(connector.initializedLatch.await(5, TimeUnit.SECONDS),
            "TtyConnector must be initialized within 5 s")
    }

    @AfterEach
    fun tearDown() {
        widget.close()
    }

    @Test
    fun `DECSET 1002h enables mouse button event tracking mode`() {
        connector.feed("\u001B[?1002h")
        val mode = pollMouseMode(timeoutMs = 2000)
        assertTrue(
            mode != MouseMode.MOUSE_REPORTING_NONE,
            "After feeding \\e[?1002h, mouse mode should be a reporting mode but was $mode"
        )
    }

    @Test
    fun `opencode bubbletea sequence 1000h plus 1002h plus 1006h enables mouse reporting`() {
        connector.feed("\u001B[?1000h\u001B[?1002h\u001B[?1006h")
        val mode = pollMouseMode(timeoutMs = 2000)
        assertTrue(
            mode != MouseMode.MOUSE_REPORTING_NONE,
            "After feeding bubbletea mouse enable sequences, mouse mode should be active but was $mode"
        )

        val innerPanel = widget.terminalPanel
        val event = mouseWheelEvent(innerPanel)

        assertTrue(innerPanel.isRemoteMouseAction(event),
            "Mouse wheel must be a remote action when opencode mouse mode is active")
        assertFalse(innerPanel.isLocalMouseAction(event),
            "Mouse wheel must not be a local action — scrollback must not intercept it")
    }

    @Test
    fun `mouse wheel is remote action when mouse reporting is active`() {
        connector.feed("\u001B[?1002h")
        val mode = pollMouseMode(timeoutMs = 2000)
        assertTrue(mode != MouseMode.MOUSE_REPORTING_NONE,
            "Sanity: mouse mode must be set before testing routing, but was $mode")

        val innerPanel = widget.terminalPanel
        val event = mouseWheelEvent(innerPanel)

        assertTrue(innerPanel.isRemoteMouseAction(event),
            "isRemoteMouseAction must return true when mouse reporting is active")
        assertFalse(innerPanel.isLocalMouseAction(event),
            "isLocalMouseAction must return false when mouse reporting is active (prevents scrollback scrolling)")
    }

    @Test
    fun `mouse wheel is local action when mouse reporting is inactive`() {
        val innerPanel = widget.terminalPanel
        val event = mouseWheelEvent(innerPanel)

        assertFalse(innerPanel.isRemoteMouseAction(event),
            "isRemoteMouseAction must return false when no mouse reporting is active")
        assertTrue(innerPanel.isLocalMouseAction(event),
            "isLocalMouseAction must return true when no mouse reporting is active (scrollback scrolls)")
    }

    @Test
    fun `mouse wheel with shift held is local action even when mouse reporting is active`() {
        connector.feed("\u001B[?1002h")
        val mode = pollMouseMode(timeoutMs = 2000)
        assertTrue(mode != MouseMode.MOUSE_REPORTING_NONE,
            "Sanity: mouse mode must be set, but was $mode")

        val innerPanel = widget.terminalPanel
        val event = mouseWheelEvent(innerPanel, modifiersEx = MouseEvent.SHIFT_DOWN_MASK)

        assertFalse(innerPanel.isRemoteMouseAction(event),
            "Shift+wheel must not be a remote action (user override)")
        assertTrue(innerPanel.isLocalMouseAction(event),
            "Shift+wheel must be a local action (scrollback override)")
    }

    @Test
    fun `remote mouse wheel event is consumed by TerminalPanel routing`() {
        connector.feed("\u001B[?1002h")
        val mode = pollMouseMode(timeoutMs = 2000)
        assertTrue(mode != MouseMode.MOUSE_REPORTING_NONE,
            "Sanity: mouse mode must be set, but was $mode")

        val innerPanel = widget.terminalPanel
        val event = mouseWheelEvent(innerPanel)
        val isRemote = innerPanel.isRemoteMouseAction(event)

        assertTrue(isRemote, "Sanity: event must be remote for consume check")
        assertTrue(
            shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = true),
            "shouldConsumeRemoteMouseWheelEvent must return true to prevent outer Swing scrolling"
        )
    }

    private fun pollMouseMode(timeoutMs: Long): MouseMode {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val field = widget.terminalPanel.javaClass.getDeclaredField("myMouseMode")
                field.isAccessible = true
                val mode = field.get(widget.terminalPanel) as MouseMode
                if (mode != MouseMode.MOUSE_REPORTING_NONE) return mode
            } catch (_: Exception) {}
            Thread.sleep(50)
        }
        return MouseMode.MOUSE_REPORTING_NONE
    }

    private fun mouseWheelEvent(
        source: java.awt.Component,
        modifiersEx: Int = 0,
    ): MouseWheelEvent = MouseWheelEvent(
        source,
        MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        modifiersEx,
        100,
        100,
        100,
        100,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        3,
        1,
        1.0,
    )

    class MockTtyConnector : TtyConnector {
        val initializedLatch = CountDownLatch(1)
        private val buffer = StringBuilder()
        private val lock = Object()
        private var closed = false
        private val writeCapture = StringBuilder()
        val writtenToPty: String get() = writeCapture.toString()

        override fun init(questioner: Questioner): Boolean {
            initializedLatch.countDown()
            return true
        }

        override fun close() {
            synchronized(lock) {
                closed = true
                lock.notifyAll()
            }
        }

        override fun getName(): String = "mock"

        override fun read(buf: CharArray, offset: Int, length: Int): Int {
            synchronized(lock) {
                while (buffer.isEmpty() && !closed) {
                    try {
                        lock.wait()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                if (buffer.isEmpty()) return -1
                val n = minOf(length, buffer.length)
                for (i in 0 until n) {
                    buf[offset + i] = buffer[i]
                }
                buffer.delete(0, n)
                return n
            }
        }

        override fun write(bytes: ByteArray) {
            writeCapture.append(String(bytes, Charsets.UTF_8))
        }

        override fun write(text: String) {
            writeCapture.append(text)
        }

        override fun isConnected(): Boolean = !closed

        override fun ready(): Boolean = synchronized(lock) { buffer.isNotEmpty() }

        override fun waitFor(): Int = 0

        override fun resize(d: Dimension) {}

        @Suppress("DEPRECATION")
        override fun resize(d: Dimension, pixels: Dimension) {}

        fun feed(data: String) {
            synchronized(lock) {
                buffer.append(data)
                lock.notifyAll()
            }
        }
    }
}
