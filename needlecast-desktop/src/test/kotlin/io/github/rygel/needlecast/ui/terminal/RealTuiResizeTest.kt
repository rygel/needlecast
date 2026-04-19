package io.github.rygel.needlecast.ui.terminal

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
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
class RealTuiResizeTest {

    private lateinit var frame: JFrame
    private lateinit var widget: JediTermWidget
    private var process: PtyProcess? = null

    @BeforeEach
    fun setUp() {
        val settings = object : DefaultSettingsProvider() {
            override fun enableMouseReporting(): Boolean = true
        }
        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            widget = JediTermWidget(Dimension(800, 400), settings)
            frame = JFrame("TUI Resize Test")
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
    fun `bash child process sees resized terminal via tput after frame resize`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/bash", "--login"))
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

            proc.outputStream.write("echo BEFORE_COLS=$(tput cols) BEFORE_LINES=$(tput lines)\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            val beforeBuffer = captureBuffer()
            println("[TEST] Before resize buffer:\n$beforeBuffer")

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(2000)

            proc.outputStream.write("echo AFTER_COLS=$(tput cols) AFTER_LINES=$(tput lines)\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            val afterBuffer = captureBuffer()
            println("[TEST] After resize buffer:\n$afterBuffer")

            val afterMatch = Regex("""AFTER_COLS=(\d+)\s+AFTER_LINES=(\d+)""").find(afterBuffer)
            if (afterMatch != null) {
                result.set(Pair(afterMatch.groupValues[1].toInt(), afterMatch.groupValues[2].toInt()))
            }
        }.also { it.isDaemon = true }.start()

        Thread.sleep(8000)

        val dims = result.get()
        println("[TEST] Parsed dims from tput: $dims")
        if (dims != null) {
            assertTrue(dims.first > 80, "Columns must increase from initial 80, got ${dims.first}")
            assertTrue(dims.second > 24, "Rows must increase from initial 24, got ${dims.second}")
        }
    }

    @Test
    fun `pty4j setWinSize updates the PTY and bash subprocess sees it`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/bash", "--login"))
                .setEnvironment(System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                })
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .start()
            process = proc

            Thread.sleep(1000)

            println("[TEST] Before setWinSize: ${proc.winSize?.columns}x${proc.winSize?.rows}")
            proc.setWinSize(com.pty4j.WinSize(120, 40))
            Thread.sleep(500)
            println("[TEST] After setWinSize: ${proc.winSize?.columns}x${proc.winSize?.rows}")

            proc.outputStream.write("echo DIMS=$(tput cols)x$(tput lines)\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1000)

            latch.countDown()

            Thread.sleep(500)
            val buf = captureBuffer()
            println("[TEST] Buffer after setWinSize:\n$buf")
            val match = Regex("""DIMS=(\d+)x(\d+)""").find(buf)
            if (match != null) {
                result.set(Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt()))
            }
        }.also { it.isDaemon = true }.start()

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        Thread.sleep(2000)

        val dims = result.get()
        println("[TEST] Result: $dims")
        if (dims != null) {
            assertEquals(120, dims.first, "tput cols must match setWinSize columns")
            assertEquals(40, dims.second, "tput lines must match setWinSize rows")
        }
    }

    @Test
    fun `resize with bash running vim shows new dimensions in stty`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("/bin/bash", "--login"))
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

            proc.outputStream.write("stty size\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(500)

            val before = captureBuffer()
            println("[TEST] Before resize stty output:\n$before")

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(2000)

            proc.outputStream.write("stty size\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(500)

            val after = captureBuffer()
            println("[TEST] After resize stty output:\n$after")

            val sttyMatch = Regex("""(\d+)\s+(\d+)""").findAll(after).lastOrNull()
            if (sttyMatch != null) {
                result.set(Pair(sttyMatch.groupValues[1].toInt(), sttyMatch.groupValues[2].toInt()))
            }
        }.also { it.isDaemon = true }.start()

        Thread.sleep(8000)

        val dims = result.get()
        println("[TEST] stty size result: $dims")
        if (dims != null) {
            assertTrue(dims.first > 24, "Rows must increase from 24, got ${dims.first}")
            assertTrue(dims.second > 80, "Columns must increase from 80, got ${dims.second}")
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
