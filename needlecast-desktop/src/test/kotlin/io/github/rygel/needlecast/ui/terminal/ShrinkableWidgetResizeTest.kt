package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ShrinkableWidgetResizeTest {

    private lateinit var frame: JFrame
    private lateinit var container: JPanel

    private class ShrinkableWidget(settings: DefaultSettingsProvider) : JediTermWidget(settings) {
        override fun getMinimumSize(): Dimension = Dimension(0, 0)
        override fun getPreferredSize(): Dimension = Dimension(1, 1)
    }

    @BeforeEach
    fun setUp() {
        val settings = object : DefaultSettingsProvider() {
            override fun enableMouseReporting(): Boolean = true
        }

        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            val widget = ShrinkableWidget(settings)
            val inner = widget.terminalPanel

            val connector = TerminalResizeTest.ResizeTrackingConnector(
                java.util.concurrent.atomic.AtomicInteger(),
                java.util.concurrent.atomic.AtomicReference(),
            )
            widget.createTerminalSession(connector)
            widget.start()

            container = object : JPanel(BorderLayout()) {
                override fun getPreferredSize(): Dimension = Dimension(1, 1)
                override fun getMinimumSize(): Dimension = Dimension(0, 0)
            }
            container.add(widget, BorderLayout.CENTER)

            val outer = JPanel(BorderLayout())
            outer.minimumSize = Dimension(0, 0)
            outer.add(container, BorderLayout.CENTER)

            frame = JFrame("Shrinkable Widget Test")
            frame.contentPane.add(outer)
            frame.size = Dimension(800, 400)
            frame.isVisible = true

            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(500)
    }

    @Test
    fun `ShrinkableJediTermWidget inner panel resizes when frame resizes`() {
        val innerRef = AtomicReference<java.awt.Component>()
        val beforeRef = AtomicReference<Dimension>()
        val afterRef = AtomicReference<Dimension>()

        SwingUtilities.invokeLater {
            val innerPanel = frame.contentPane.getComponent(0)
            var c: java.awt.Component = innerPanel
            while (c !is com.jediterm.terminal.ui.TerminalPanel && c is java.awt.Container && c.componentCount > 0) {
                c = (c as java.awt.Container).getComponent(0)
            }
            innerRef.set(c)
            beforeRef.set(Dimension(c.width, c.height))
        }
        Thread.sleep(300)

        SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
        Thread.sleep(1500)

        SwingUtilities.invokeLater {
            val inner = innerRef.get()
            afterRef.set(Dimension(inner.width, inner.height))
        }
        Thread.sleep(300)

        val before = beforeRef.get()
        val after = afterRef.get()
        println("[TEST] Inner panel before: $before, after: $after")

        assertTrue(after != null && after.width > 0 && after.height > 0,
            "Inner panel must have positive size after resize")
        assertNotEquals(before, after,
            "Inner panel dimensions must change when frame resizes")
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeLater { frame.dispose() }
    }
}
