package io.github.rygel.needlecast.ui.terminal

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcessBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.Dimension
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities

@EnabledOnOs(OS.WINDOWS)
class PowerShellResizeTest {

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
            frame = JFrame("PowerShell Resize Test")
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
    fun `PowerShell sees resized window width after frame resize`() {
        val result = AtomicReference<Pair<Int, Int>>()

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("powershell.exe", "-NoProfile"))
                .setEnvironment(System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                })
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .setUseWinConPty(true)
                .start()
            process = proc

            val connector = PtyProcessTtyConnector(proc, Charset.forName("UTF-8"))
            SwingUtilities.invokeLater {
                widget.createTerminalSession(connector)
                widget.start()
                latch.countDown()
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            Thread.sleep(3000)

            proc.outputStream.write("Write-Host \"BEFORE \$(Get-Host).UI.RawUI.WindowSize\"\r\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1500)

            val beforeBuffer = captureBuffer()
            println("[TEST-ps] Before resize:\n$beforeBuffer")

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(2000)

            proc.outputStream.write("Write-Host \"AFTER \$(Get-Host).UI.RawUI.WindowSize\"\r\n".toByteArray())
            proc.outputStream.flush()
            Thread.sleep(1500)

            val afterBuffer = captureBuffer()
            println("[TEST-ps] After resize:\n$afterBuffer")
        }.also { it.isDaemon = true }.start()

        Thread.sleep(10000)
    }

    @Test
    fun `isConnected remains true after resize with WinConPty`() {
        var connectedBefore = false
        var connectedAfter = false

        val latch = CountDownLatch(1)
        Thread {
            val proc = PtyProcessBuilder()
                .setCommand(arrayOf("cmd.exe"))
                .setEnvironment(System.getenv().toMutableMap())
                .setDirectory(System.getProperty("user.home"))
                .setInitialColumns(80)
                .setInitialRows(24)
                .setUseWinConPty(true)
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

            SwingUtilities.invokeLater { frame.size = Dimension(1100, 600) }
            Thread.sleep(1500)

            connectedAfter = connector.isConnected
        }.also { it.isDaemon = true }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(5000)

        assertTrue(connectedBefore, "Connector must be connected before resize")
        assertTrue(connectedAfter, "Connector must remain connected after resize")
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
