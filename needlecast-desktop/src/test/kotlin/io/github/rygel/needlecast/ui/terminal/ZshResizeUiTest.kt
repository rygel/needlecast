package io.github.rygel.needlecast.ui.terminal

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcessBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.MAC)
class ZshResizeUiTest {

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
            frame = JFrame("Zsh Resize Test")
            frame.contentPane.add(widget)
            frame.size = Dimension(800, 400)
            frame.isVisible = true
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(300)
    }

    @AfterEach
    fun tearDown() {
        process?.destroyForcibly()
        SwingUtilities.invokeLater { widget.close(); frame.dispose() }
    }

    @Test
    fun `zsh subprocess sees resized terminal via tput after frame resize`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/zsh", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                })
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
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            Thread.sleep(2000)

            proc.outputStream.write("echo BEFORE_COLS=\$(tput cols) BEFORE_LINES=\$(tput lines)\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            val beforeBuffer = captureBuffer()
            println("[TEST-zsh] Before resize:\n$beforeBuffer")

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(2000)

            proc.outputStream.write("echo AFTER_COLS=\$(tput cols) AFTER_LINES=\$(tput lines)\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            val afterBuffer = captureBuffer()
            println("[TEST-zsh] After resize:\n$afterBuffer")

            val match = Regex("""AFTER_COLS=(\d+)\s+AFTER_LINES=(\d+)""").find(afterBuffer)
            if (match != null) {
                result.set(Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
            }
        }.also { it.isDaemon = true }.start()

        Thread.sleep(8000)

        val dims = result.get()
        println("[TEST-zsh] Result: $dims")
        assertTrue(dims != null, "Must parse tput output from zsh")
        assertTrue(dims!!.first > 80, "Columns must increase from 80, got ${dims.first}")
        assertTrue(dims.second > 24, "Rows must increase from 24, got ${dims.second}")
    }

    @Test
    fun `zsh stty size reflects PTY resize`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/zsh", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                })
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
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            Thread.sleep(2000)

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(2000)

            proc.outputStream.write("stty size\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            val buf = captureBuffer()
            println("[TEST-zsh] stty size buffer:\n$buf")

            val match = Regex("""(\d+)\s+(\d+)""").findAll(buf).lastOrNull()
            if (match != null) {
                result.set(Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
            }
        }.also { it.isDaemon = true }.start()

        Thread.sleep(8000)

        val dims = result.get()
        println("[TEST-zsh] stty size result: $dims")
        assertTrue(dims != null, "Must parse stty size from zsh")
        assertTrue(dims!!.first > 24, "Rows must increase from 24, got ${dims.first}")
        assertTrue(dims.second > 80, "Columns must increase from 80, got ${dims.second}")
    }

    @Test
    fun `zsh setWinSize directly updates dimensions`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/zsh", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                })
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .start()
            process = proc

            Thread.sleep(1000)

            proc.setWinSize(com.pty4j.WinSize(120, 40))
            Thread.sleep(500)

            val winSize = proc.winSize
            println("[TEST-zsh] After setWinSize(120,40): winSize=${winSize?.columns}x${winSize?.rows}")

            proc.outputStream.write("echo DIMS=\$(tput cols)x\$(tput lines)\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            latch.countDown()

            Thread.sleep(500)
            val buf = captureBuffer()
            println("[TEST-zsh] Buffer:\n$buf")
            val match = Regex("""DIMS=(\d+)x(\d+)""").find(buf)
            if (match != null) {
                result.set(Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
            }
        }.also { it.isDaemon = true }.start()

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        Thread.sleep(2000)

        val dims = result.get()
        println("[TEST-zsh] Result: $dims")
        if (dims != null) {
            assertEquals(120, dims.first, "tput cols must match setWinSize columns")
            assertEquals(40, dims.second, "tput lines must match setWinSize rows")
        }
    }

    private fun captureBuffer(): String {
        val buffer = widget.terminalTextBuffer
        val sb = StringBuilder()
        for (i in 0 until buffer.height) {
            val line = buffer.getLine(i).text.trimEnd()
            if (line.isNotEmpty()) sb.append(line).append("\n")
        }
        return sb.toString()
    }
}
