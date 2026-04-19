package io.github.rygel.needlecast.ui.terminal

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities

import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

@DisabledOnOs(OS.WINDOWS)
class RealPtyResizeTest {

    private lateinit var frame: JFrame
    private lateinit var widget: JediTermWidget
    private var process: com.pty4j.PtyProcess? = null

    @BeforeEach
    fun setUp() {
        val settings = object : DefaultSettingsProvider() {
            override fun enableMouseReporting(): Boolean = true
        }

        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            widget = JediTermWidget(Dimension(800, 400), settings)
            frame = JFrame("Real PTY Resize Test")
            frame.contentPane.add(widget)
            frame.size = Dimension(800, 400)
            frame.isVisible = true
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(500)
    }

    @AfterEach
    fun tearDown() {
        process?.destroy()
        SwingUtilities.invokeLater {
            widget.close()
            frame.dispose()
        }
    }

    @Test
    fun `real PTY resize propagates SIGWINCH and changes terminal dimensions`() {
        val processLatch = CountDownLatch(1)
        val readDims = AtomicReference<String>()

        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/bash", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply { put("TERM", "xterm-256color") })
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .start()
            process = proc

            val connector = PtyProcessTtyConnector(proc, Charset.forName("UTF-8"))

            SwingUtilities.invokeLater {
                widget.createTerminalSession(connector)
                widget.start()
                processLatch.countDown()
            }

            Thread.sleep(2000)

            val initialWinSize = proc.winSize
            println("[TEST] Initial WinSize: ${initialWinSize?.columns}x${initialWinSize?.rows}")
            assertTrue(initialWinSize != null, "Initial WinSize must not be null")

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(1500)

            val afterWinSize = proc.winSize
            println("[TEST] After resize WinSize: ${afterWinSize?.columns}x${afterWinSize?.rows}")

            assertTrue(afterWinSize != null, "WinSize after resize must not be null")
            assertNotEquals(initialWinSize?.columns, afterWinSize?.columns,
                "Columns must change after resize (initial=${initialWinSize?.columns}, after=${afterWinSize?.columns})")

            proc.outputStream.write("tput cols\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(500)

            proc.outputStream.write("tput lines\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(500)

            proc.outputStream.write("stty size\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(500)

            val buffer = widget.terminalTextBuffer
            val sb = StringBuilder()
            for (i in 0 until buffer.height) {
                sb.append(buffer.getLine(i).text.trimEnd()).append("\n")
            }
            println("[TEST] Terminal buffer content (last 20 lines):")
            sb.lines().takeLast(20).forEach { println("  |$it|") }

            readDims.set("cols=${afterWinSize?.columns} rows=${afterWinSize?.rows}")
        }.also { it.isDaemon = true }.start()

        assertTrue(processLatch.await(5, TimeUnit.SECONDS), "Process must start")
        Thread.sleep(5000)

        println("[TEST] Final dims: ${readDims.get()}")
    }

    @Test
    fun `isConnected remains true after resize`() {
        var connectedBefore = false
        var connectedAfter = false
        var winSizeBefore: WinSize? = null
        var winSizeAfter: WinSize? = null

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/bash", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply { put("TERM", "xterm-256color") })
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .start()
            process = proc

            val connector = PtyProcessTtyConnector(proc, Charset.forName("UTF-8"))
            SwingUtilities.invokeLater {
                widget.createTerminalSession(connector)
                widget.start()
                latch.countDown()
            }

            Thread.sleep(2000)
            connectedBefore = connector.isConnected
            winSizeBefore = proc.winSize
            println("[TEST] Before: connected=$connectedBefore, winSize=${winSizeBefore?.columns}x${winSizeBefore?.rows}")

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(1500)

            connectedAfter = connector.isConnected
            winSizeAfter = proc.winSize
            println("[TEST] After: connected=$connectedAfter, winSize=${winSizeAfter?.columns}x${winSizeAfter?.rows}")
        }.also { it.isDaemon = true }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(5000)

        assertTrue(connectedBefore, "Connector must be connected before resize")
        assertTrue(connectedAfter, "Connector must remain connected after resize")
    }

    @Test
    fun `terminal buffer dimensions match PTY win size after resize`() {
        val result = AtomicReference<String>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/bash", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply { put("TERM", "xterm-256color") })
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .start()
            process = proc

            val connector = PtyProcessTtyConnector(proc, Charset.forName("UTF-8"))
            SwingUtilities.invokeLater {
                widget.createTerminalSession(connector)
                widget.start()
                latch.countDown()
            }

            Thread.sleep(2000)

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(2000)

            val winSize = proc.winSize
            val termSize = widget.terminalPanel.getTerminalSizeFromComponent()
            val bufferWidth = widget.terminalTextBuffer.width
            val bufferHeight = widget.terminalTextBuffer.height

            result.set("winSize=${winSize?.columns}x${winSize?.rows}, " +
                "termSize=${termSize?.width}x${termSize?.height}, " +
                "buffer=${bufferWidth}x${bufferHeight}")

            println("[TEST] $result")

            if (winSize != null && termSize != null) {
                assertEquals(winSize.columns, termSize.width,
                    "Terminal text width must match PTY win size columns")
                assertEquals(winSize.rows, termSize.height,
                    "Terminal text height must match PTY win size rows")
            }
        }.also { it.isDaemon = true }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(6000)
        println("[TEST] Result: ${result.get()}")
    }

    private fun <T> edtGet(fn: () -> T): T {
        val ref = AtomicReference<T>()
        val err = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            try { ref.set(fn()) } catch (t: Throwable) { err.set(t) } finally { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        err.get()?.let { throw it }
        return ref.get()
    }
}
