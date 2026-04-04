package io.github.rygel.needlecast.ui.terminal

import com.jediterm.pty.PtyProcessTtyConnector
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.ui.JediTermWidget
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.nio.charset.Charset
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Braille spinner characters used by many CLI tools while "thinking". */
private val SPINNER_CHARS = setOf('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')

class TerminalPanel(
    initialDir: String = System.getProperty("user.home"),
    dark: Boolean = true,
    extraEnv: Map<String, String> = emptyMap(),
    private val shellExecutable: String? = null,
    private val startupCommand: String? = null,
    initialFg: java.awt.Color? = null,
    initialBg: java.awt.Color? = null,
    initialFontSize: Int = 13,
) : JPanel(BorderLayout()) {

    private val logger = LoggerFactory.getLogger(TerminalPanel::class.java)

    private val settingsProvider = QuickLaunchTerminalSettings(dark = dark, initialFg = initialFg, initialBg = initialBg, initialFontSize = initialFontSize)
    private val termWidget = ShrinkableJediTermWidget(settingsProvider)
    private val termContainer = object : JPanel(BorderLayout()) {
        override fun getPreferredSize(): Dimension = Dimension(1, 1)
        override fun getMinimumSize(): Dimension = Dimension(0, 0)
    }
    private var currentDir: String = initialDir
    private var ptyProcess: PtyProcess? = null
    private val extraEnv: Map<String, String> = extraEnv

    /**
     * True when this terminal was started with the Claude Code CLI as the startup command.
     * When true, PTY-quiescence heuristics are disabled in favour of Claude Code lifecycle hooks.
     */
    val isClaudeSession: Boolean = startupCommand?.trim()?.let {
        it == "claude" || it.startsWith("claude ") || it.startsWith("claude\t")
    } ?: false

    var onStatusChanged: ((AgentStatus) -> Unit)? = null
    /** Fired when the font size changes via Ctrl+scroll. Argument is the new absolute size. */
    var onFontSizeChanged: ((Int) -> Unit)? = null
    private var currentStatus: AgentStatus = AgentStatus.NONE

    // ── Heuristic state (non-Claude sessions only) ────────────────────────────

    /** Timestamp of the last bytes written to the PTY input (user input / sendInput). */
    @Volatile private var lastInputMs: Long = 0L
    /** Last raw output chunk, retained for spinner detection. */
    @Volatile private var lastChunk: String = ""

    /**
     * 2 s silence → WAITING.
     * Restarted on every output event; fires once (repeats = false).
     * Not used for Claude sessions.
     */
    private val silenceTimer = Timer(2000) { transitionTo(AgentStatus.WAITING) }.apply { isRepeats = false }

    init {
        minimumSize = Dimension(0, 0)
        termContainer.minimumSize = Dimension(0, 0)
        termContainer.add(termWidget, BorderLayout.CENTER)
        add(termContainer, BorderLayout.CENTER)
        termWidget.addMouseWheelListener(object : MouseWheelListener {
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                if (e.isControlDown) {
                    changeFontSize(if (e.wheelRotation < 0) +1 else -1)
                    e.consume()
                }
            }
        })
        termWidget.onPasteRequested = {
            val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val text = try { cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String } catch (_: Exception) { null }
            if (text != null) sendInput(text)
        }
        termWidget.onTextInput = { text -> sendInput(text) }
        startShell()
    }

    fun changeFontSize(delta: Int) {
        settingsProvider.changeFontSize(delta)
        termWidget.repaint()
        onFontSizeChanged?.invoke(settingsProvider.fontSize)
    }

    fun applyFontSize(size: Int) {
        settingsProvider.setFontSize(size)
        termWidget.repaint()
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
        lastInputMs = System.currentTimeMillis()
        try {
            ptyProcess?.outputStream?.write(text.toByteArray(Charsets.UTF_8))
            ptyProcess?.outputStream?.flush()
        } catch (_: Exception) {}
    }

    fun applyTheme(dark: Boolean) {
        settingsProvider.applyDark(dark)
        // Derive bg/fg from the active FlatLaf theme via UIManager so the terminal
        // matches the current theme automatically. TextArea colors best represent
        // editor/code surfaces, which is semantically correct for a terminal.
        val themeBg = javax.swing.UIManager.getColor("TextArea.background")
            ?: javax.swing.UIManager.getColor("Panel.background")
        val themeFg = javax.swing.UIManager.getColor("TextArea.foreground")
            ?: javax.swing.UIManager.getColor("Panel.foreground")
        settingsProvider.applyThemeColors(themeFg, themeBg)
        pushStyleToJediTerm(settingsProvider.getDefaultStyle())
        termWidget.repaint()
    }

    fun applyColors(fg: java.awt.Color?, bg: java.awt.Color?) {
        settingsProvider.applyColors(fg, bg)
        pushStyleToJediTerm(settingsProvider.getDefaultStyle())
        termWidget.repaint()
    }

    /**
     * JediTerm caches the default style in a [StyleState] at session-creation time.
     * After a live color change we must also update that cache, otherwise existing
     * terminal sessions keep rendering with the old colors.
     */
    private fun pushStyleToJediTerm(style: TextStyle) {
        try {
            val field = termWidget.javaClass.getDeclaredField("myStyleState")
            field.isAccessible = true
            (field.get(termWidget) as? StyleState)?.setDefaultStyle(style)
        } catch (_: Exception) {}
    }

    /** Overrides the current status directly — used by [ClaudeHookServer] events. */
    fun forceStatus(status: AgentStatus) {
        SwingUtilities.invokeLater { transitionTo(status) }
    }

    fun dispose() {
        silenceTimer.stop()
        transitionTo(AgentStatus.NONE)
        termWidget.close()
    }

    private fun transitionTo(status: AgentStatus) {
        if (currentStatus == status) return
        currentStatus = status
        onStatusChanged?.invoke(status)
    }

    /**
     * If true, status detection for Claude sessions is driven by [ClaudeHookServer]
     * via [forceStatus] and output-based heuristics are skipped. When false (default),
     * the same output-polling heuristics are used for all sessions including Claude.
     */
    var useHooksForStatus: Boolean = false

    /**
     * Called from the JediTerm reader thread whenever output bytes arrive.
     * Skipped for Claude sessions only when hooks are driving status.
     */
    private fun handleOutput(chunk: String) {
        if (isClaudeSession && useHooksForStatus) return

        lastChunk = chunk
        val now = System.currentTimeMillis()

        SwingUtilities.invokeLater {
            // Ignore output within 100 ms of user input — likely terminal echo.
            if (now - lastInputMs < 100L) return@invokeLater

            // If the last chunk contains spinner chars the agent is still "thinking" — don't
            // start the silence timer yet; wait until it goes quiet without a spinner.
            val hasSpinner = lastChunk.any { it in SPINNER_CHARS }
            if (hasSpinner) {
                if (currentStatus != AgentStatus.THINKING) transitionTo(AgentStatus.THINKING)
                silenceTimer.stop()
            } else {
                silenceTimer.restart()
                if (currentStatus != AgentStatus.THINKING) transitionTo(AgentStatus.THINKING)
            }
        }
    }

    private fun startShell() {
        Thread {
            try {
                val cmd = resolveShellCommand()
                val env = System.getenv().toMutableMap().apply {
                    put("TERM", "xterm-256color")
                    putAll(extraEnv)
                }
                ptyProcess = PtyProcessBuilder()
                    .setCommand(cmd)
                    .setEnvironment(env)
                    .setDirectory(currentDir)
                    .setInitialColumns(120)
                    .setInitialRows(30)
                    .start()
                val connector = PtyProcessTtyConnector(ptyProcess!!, Charset.forName("UTF-8"))
                val observed = ObservingTtyConnector(connector) { chunk -> handleOutput(chunk) }
                val session = termWidget.createTerminalSession(observed)
                session.start()
                SwingUtilities.invokeLater { transitionTo(AgentStatus.WAITING) }
                if (!startupCommand.isNullOrBlank()) {
                    val line = if (IS_WINDOWS) "$startupCommand\r\n" else "$startupCommand\n"
                    ptyProcess!!.outputStream.write(line.toByteArray(Charsets.UTF_8))
                    ptyProcess!!.outputStream.flush()
                }
            } catch (e: Exception) {
                logger.error("Failed to start shell process in terminal", e)
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun resolveShellCommand(): Array<String> {
        val custom = shellExecutable?.trim()?.takeIf { it.isNotEmpty() }
            ?: return if (IS_WINDOWS) arrayOf("cmd.exe") else arrayOf("/bin/bash", "--login")
        return tokenize(custom)
    }

    private fun tokenize(s: String): Array<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        for (c in s) {
            when {
                c == '"'                     -> inQuote = !inQuote
                c.isWhitespace() && !inQuote -> { if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() } }
                else                         -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) tokens += sb.toString()
        return tokens.toTypedArray()
    }

    private class ShrinkableJediTermWidget(settings: QuickLaunchTerminalSettings) : JediTermWidget(settings) {
        var onPasteRequested: (() -> Unit)? = null
        var onTextInput: ((String) -> Unit)? = null

        override fun getMinimumSize(): Dimension = Dimension(0, 0)
        override fun getPreferredSize(): Dimension = Dimension(1, 1)

        init {
            // Catch text from input methods (voice-to-text, IME, etc.)
            enableInputMethods(true)
            addInputMethodListener(object : java.awt.event.InputMethodListener {
                override fun inputMethodTextChanged(e: java.awt.event.InputMethodEvent) {
                    val text = e.text
                    if (text != null) {
                        val sb = StringBuilder()
                        var c = text.first()
                        while (c != java.text.CharacterIterator.DONE) {
                            sb.append(c)
                            c = text.next()
                        }
                        if (sb.isNotEmpty()) onTextInput?.invoke(sb.toString())
                    }
                    e.consume()
                }
                override fun caretPositionChanged(e: java.awt.event.InputMethodEvent) {}
            })
        }

        override fun processKeyEvent(e: java.awt.event.KeyEvent) {
            if (e.id == java.awt.event.KeyEvent.KEY_PRESSED &&
                e.isControlDown && !e.isShiftDown &&
                e.keyCode == java.awt.event.KeyEvent.VK_V) {
                onPasteRequested?.invoke()
                e.consume()
                return
            }
            super.processKeyEvent(e)
        }
    }
}
