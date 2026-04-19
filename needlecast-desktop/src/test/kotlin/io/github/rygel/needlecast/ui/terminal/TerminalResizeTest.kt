package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.Questioner
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.ComponentEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

class TerminalResizeTest {

    private lateinit var frame: JFrame
    private lateinit var widget: JediTermWidget
    private lateinit var connector: ResizeTrackingConnector
    private val resizeCount = AtomicInteger(0)
    private val lastResizeDims = AtomicReference<Dimension>()

    @BeforeEach
    fun setUp() {
        val settings = object : DefaultSettingsProvider() {
            override fun enableMouseReporting(): Boolean = true
        }

        connector = ResizeTrackingConnector(resizeCount, lastResizeDims)

        var setupLatch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            widget = JediTermWidget(Dimension(800, 400), settings)
            widget.createTerminalSession(connector)
            widget.start()

            frame = JFrame("Resize Test")
            frame.contentPane.add(widget)
            frame.size = Dimension(800, 400)
            frame.isVisible = true

            setupLatch.countDown()
        }
        assertTrue(setupLatch.await(5, TimeUnit.SECONDS), "Setup must complete within 5s")

        Thread.sleep(500)
        resizeCount.set(0)
        lastResizeDims.set(null)
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeLater {
            widget.close()
            frame.dispose()
        }
    }

    @Test
    fun `resizing JFrame triggers TtyConnector resize`() {
        for (i in 1..5) {
            val w = 800 + i * 50
            val h = 400 + i * 60
            SwingUtilities.invokeLater { frame.size = Dimension(w, h) }
            Thread.sleep(300)
        }
        Thread.sleep(1000)

        val count = resizeCount.get()
        println("[TEST] TtyConnector.resize called $count times")
        println("[TEST] Last resize dims: ${lastResizeDims.get()}")

        assertTrue(count > 0,
            "TtyConnector.resize must be called at least once after resizing, but was called $count times")
    }

    @Test
    fun `resizing inner panel triggers TtyConnector resize`() {
        val panel = widget.terminalPanel
        val initialSize = edtGet { panel.size }
        println("[TEST] Initial inner panel size: $initialSize")

        for (i in 1..5) {
            val w = initialSize.width + i * 50
            val h = initialSize.height + i * 60
            edtRun {
                panel.size = Dimension(w, h)
                panel.invalidate()
                panel.revalidate()
                panel.dispatchEvent(ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED))
            }
            Thread.sleep(300)
        }
        Thread.sleep(1000)

        val count = resizeCount.get()
        println("[TEST] TtyConnector.resize called $count times after direct panel resize")
        println("[TEST] Last resize dims: ${lastResizeDims.get()}")

        assertTrue(count > 0,
            "TtyConnector.resize must be called after directly resizing inner panel")
    }

    @Test
    fun `resizing JFrame changes inner panel terminal dimensions`() {
        val before = edtGet { widget.terminalPanel.getTerminalSizeFromComponent() }
        println("[TEST] Before resize: terminalSize=$before")

        SwingUtilities.invokeLater { frame.size = Dimension(1000, 600) }
        Thread.sleep(1000)

        val after = edtGet { widget.terminalPanel.getTerminalSizeFromComponent() }
        println("[TEST] After resize: terminalSize=$after")

        assertTrue(after != null, "terminalSize must not be null after resize")
        if (before != null && after != null) {
            assertTrue(after.width != before.width || after.height != before.height,
                "Terminal dimensions must change after resizing: before=$before after=$after")
        }
    }

    @Test
    fun `ObservingTtyConnector delegates resize to inner connector`() {
        val innerCount = AtomicInteger(0)
        val innerDims = AtomicReference<Dimension>()
        val inner = ResizeTrackingConnector(innerCount, innerDims)
        val observing = ObservingTtyConnector(inner) {}
        val dims = Dimension(100, 40)

        observing.resize(dims)
        Thread.sleep(100)

        assertEquals(1, innerCount.get(),
            "ObservingTtyConnector must delegate resize to inner connector")
        assertEquals(100, innerDims.get()?.width)
        assertEquals(40, innerDims.get()?.height)
    }

    @Test
    fun `widget hierarchy - inner panel is descendant of widget`() {
        val inner = edtGet { widget.terminalPanel }
        assertTrue(SwingUtilities.isDescendingFrom(inner, widget),
            "Inner TerminalPanel must be a descendant of JediTermWidget for resize events to propagate")
    }

    @Test
    fun `terminal panel component listener chain`() {
        val inner = edtGet { widget.terminalPanel }
        val widgetSize = edtGet { widget.size }
        val innerSize = edtGet { inner.size }
        val innerBounds = edtGet { inner.bounds }
        println("[TEST] Widget size: $widgetSize")
        println("[TEST] Inner panel size: $innerSize")
        println("[TEST] Inner panel bounds: $innerBounds")
        println("[TEST] Inner panel parent: ${inner.parent?.javaClass?.simpleName}")
        println("[TEST] Widget layout: ${widget.layout?.javaClass?.simpleName}")

        assertTrue(innerSize.width > 0 && innerSize.height > 0,
            "Inner panel must have non-zero size, got $innerSize")
    }

    private fun <T> edtGet(fn: () -> T): T {
        val ref = AtomicReference<T>()
        val err = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            try { ref.set(fn()) } catch (t: Throwable) { err.set(t) } finally { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "EDT call must complete")
        err.get()?.let { throw it }
        return ref.get()
    }

    private fun edtRun(fn: () -> Unit) {
        val err = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            try { fn() } catch (t: Throwable) { err.set(t) } finally { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "EDT call must complete")
        err.get()?.let { throw it }
    }

    class ResizeTrackingConnector(
        private val resizeCounter: AtomicInteger,
        private val lastDims: AtomicReference<Dimension>,
    ) : TtyConnector {
        val initializedLatch = CountDownLatch(1)
        private val buffer = StringBuilder()
        private val lock = Object()
        private var closed = false

        override fun init(questioner: Questioner): Boolean {
            println("[ResizeTrackingConnector] init() called")
            initializedLatch.countDown()
            return true
        }

        override fun resize(termWinSize: Dimension) {
            println("[ResizeTrackingConnector] resize() called: ${termWinSize.width}x${termWinSize.height}")
            resizeCounter.incrementAndGet()
            lastDims.set(Dimension(termWinSize))
        }

        override fun close() {
            synchronized(lock) { closed = true; lock.notifyAll() }
        }

        override fun getName(): String = "test"

        override fun read(buf: CharArray, offset: Int, length: Int): Int {
            synchronized(lock) {
                while (buffer.isEmpty() && !closed) {
                    try { lock.wait() } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                if (buffer.isEmpty()) return -1
                val n = minOf(length, buffer.length)
                for (i in 0 until n) buf[offset + i] = buffer[i]
                buffer.delete(0, n)
                return n
            }
        }

        override fun write(bytes: ByteArray) {}
        override fun write(text: String) {}
        override fun isConnected(): Boolean = !closed
        override fun ready(): Boolean = synchronized(lock) { buffer.isNotEmpty() }
        override fun waitFor(): Int = 0

        fun feed(data: String) {
            synchronized(lock) { buffer.append(data); lock.notifyAll() }
        }
    }
}
