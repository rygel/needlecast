package io.github.quicklaunch.ui.terminal

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.ui.JediTermWidget
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import io.github.quicklaunch.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.nio.charset.Charset
import javax.swing.JPanel

class TerminalPanel(
    initialDir: String = System.getProperty("user.home"),
    dark: Boolean = true,
) : JPanel(BorderLayout()) {

    private val settingsProvider = QuickLaunchTerminalSettings(dark = dark)
    private val termWidget = JediTermWidget(settingsProvider)
    private var currentDir: String = initialDir
    private var ptyProcess: PtyProcess? = null

    init {
        add(termWidget, BorderLayout.CENTER)
        startShell()
    }

    fun setDirectory(dir: String) {
        currentDir = dir
        val process = ptyProcess ?: return
        try {
            val escaped = dir.replace("\\", "\\\\").replace("\"", "\\\"")
            val cmd = if (IS_WINDOWS) "cd /d \"$escaped\"\r\n" else "cd \"$escaped\"\n"
            process.outputStream.write(cmd.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
        } catch (_: Exception) {}
    }

    fun sendInput(text: String) {
        try {
            ptyProcess?.outputStream?.write(text.toByteArray(Charsets.UTF_8))
            ptyProcess?.outputStream?.flush()
        } catch (_: Exception) {}
    }

    fun applyTheme(dark: Boolean) {
        settingsProvider.applyDark(dark)
        termWidget.repaint()
    }

    fun dispose() {
        termWidget.close()
    }

    private fun startShell() {
        Thread {
            try {
                val cmd = if (IS_WINDOWS) arrayOf("cmd.exe") else arrayOf("/bin/bash", "--login")
                val env = System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                }
                ptyProcess = PtyProcessBuilder()
                    .setCommand(cmd)
                    .setEnvironment(env)
                    .setDirectory(currentDir)
                    .setInitialColumns(120)
                    .setInitialRows(30)
                    .start()
                val connector = PtyProcessTtyConnector(ptyProcess!!, Charset.forName("UTF-8"))
                val session = termWidget.createTerminalSession(connector)
                session.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.also { it.isDaemon = true }.start()
    }
}
