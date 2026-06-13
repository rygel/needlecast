package io.github.rygel.needlecast.ui.terminal

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.ui.AiCli
import io.github.rygel.needlecast.ui.ShellDetector
import io.github.rygel.needlecast.ui.components.ContextualHintPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.charset.Charset
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val CARD_EMPTY = "__empty__"

internal fun matchProjectPath(
    cwd: String,
    paths: Set<String>,
): String? {
    val normalised = cwd.replace('\\', '/')
    return paths.firstOrNull { p ->
        val np = p.replace('\\', '/')
        normalised == np || normalised.startsWith("$np/")
    }
}

/**
 * Manages one [ProjectTerminalPane] per project path.
 *
 * - [showProject]: switches the visible card without creating a terminal (called on selection change).
 * - [activateProject]: creates the terminal pane for a path if it doesn't exist yet, then shows it.
 * - [deactivateProject]: disposes and removes the terminal pane for a specific path.
 * - [deactivate]: hides all terminals (shows placeholder); used when group changes.
 *
 * Right-clicking the placeholder when a project is selected opens a shell picker popup.
 * Wire [onActivateRequested] to handle the activation with the chosen shell.
 */
class TerminalManager(
    private val ctx: AppContext,
) : JPanel(CardLayout()) {
    private val cardLayout = layout as CardLayout
    private val terminals = mutableMapOf<String, ProjectTerminalPane>()
    private var currentDark = true
    private var shownKey: String? = null
    private var shownDir: ProjectDirectory? = null

    /** Called when the user picks a shell from the placeholder right-click menu. */
    var onActivateRequested: ((ProjectDirectory) -> Unit)? = null

    /** AI CLI tools to show at the top of the shell picker (updated by MainWindow after detection). */
    var availableCliTools: List<AiCli> = emptyList()

    /** Called on the EDT whenever a project's agent status changes. */
    var onProjectStatusChanged: ((path: String, AgentStatus) -> Unit)? = null

    /**
     * Receives a Claude Code lifecycle hook event (cwd + status).
     * Finds the project whose path is a prefix of [cwd] and forwards the status to its Claude session.
     * Must be called on the EDT.
     */
    fun onHookEvent(
        cwd: String,
        status: AgentStatus,
    ) {
        val path = matchProjectPath(cwd, terminals.keys) ?: return
        terminals[path]?.forceStatusOnClaudeTabs(status)
    }

    fun setUseHooksForStatus(enabled: Boolean) {
        terminals.values.forEach { it.setUseHooksForStatus(enabled) }
    }

    private val placeholderLabel =
        JLabel(MSG_IDLE, SwingConstants.CENTER).apply {
            foreground = javax.swing.UIManager.getColor("Label.disabledForeground")
                ?: Color(0x6A737D)
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
    private var placeholderPanel: JPanel? = null
    private var placeholderMouseListener: MouseAdapter? = null

    init {
        minimumSize = Dimension(0, 0)
        add(buildPlaceholder(), CARD_EMPTY)
        cardLayout.show(this, CARD_EMPTY)
    }

    /** Switch display to [path] without creating a terminal. Shows placeholder if none exists. */
    fun showProject(
        path: String,
        dir: ProjectDirectory? = null,
    ) {
        shownKey = path
        shownDir = dir
        if (terminals.containsKey(path)) {
            cardLayout.show(this, path)
        } else {
            updatePlaceholderContent(if (dir != null) MSG_READY else MSG_IDLE)
            cardLayout.show(this, CARD_EMPTY)
        }
    }

    /** Create a terminal pane for [path] if missing, then show it. */
    fun activateProject(
        path: String,
        extraEnv: Map<String, String> = emptyMap(),
        shellExecutable: String? = null,
        startupCommand: String? = null,
    ) {
        if (!terminals.containsKey(path)) {
            val pane =
                ProjectTerminalPane(
                    path,
                    currentDark,
                    extraEnv,
                    shellExecutable,
                    startupCommand,
                    currentFg,
                    currentBg,
                    currentFontSize,
                    currentFontFamily,
                    currentCharset,
                )
            pane.onStatusChanged = { status -> onProjectStatusChanged?.invoke(path, status) }
            pane.onFontSizeChanged = { size ->
                currentFontSize = size
                terminals.values.forEach { if (it !== pane) it.applyFontSize(size) }
                onFontSizeChanged?.invoke(size)
            }
            pane.onCharsetChanged = { charset ->
                currentCharset = charset
                terminals.values.forEach { if (it !== pane) it.restartAllWithCharset(charset) }
                onCharsetChanged?.invoke(charset)
            }
            terminals[path] = pane
            add(pane, path)
        }
        shownKey = path
        cardLayout.show(this, path)
        SwingUtilities.invokeLater { terminals[path]?.requestFocusOnActive() }
    }

    /** Dispose and remove the terminal pane for [path]. Shows placeholder if it was visible. */
    fun deactivateProject(path: String) {
        val pane = terminals.remove(path) ?: return
        onProjectStatusChanged?.invoke(path, AgentStatus.NONE)
        remove(pane)
        pane.dispose()
        if (shownKey == path) {
            cardLayout.show(this, CARD_EMPTY)
        }
        revalidate()
        repaint()
    }

    /** Hide all terminals (group changed, no selection). */
    fun deactivate() {
        shownKey = null
        shownDir = null
        updatePlaceholderContent(MSG_IDLE)
        cardLayout.show(this, CARD_EMPTY)
    }

    fun isActive(path: String): Boolean = terminals.containsKey(path)

    fun activePaths(): Set<String> = terminals.keys.toSet()

    fun requestFocusOnActive() {
        shownKey?.let { terminals[it]?.requestFocusOnActive() }
    }

    fun sendInput(text: String) {
        shownKey?.let { terminals[it]?.sendInputToActive(text) }
    }

    fun applyTheme(dark: Boolean) {
        currentDark = dark
        terminals.values.forEach { it.applyTheme(dark) }
        val bg =
            javax.swing.UIManager.getColor("TextArea.background")
                ?: javax.swing.UIManager.getColor("Panel.background")
        if (bg != null) {
            placeholderPanel?.background = bg
            placeholderLabel.foreground = javax.swing.UIManager.getColor("Label.disabledForeground")
                ?: Color(0x6A737D)
        }
        updatePlaceholderContent(if (shownDir != null) MSG_READY else MSG_IDLE)
    }

    private var currentFg: java.awt.Color? = null
    private var currentBg: java.awt.Color? = null
    private var currentFontSize: Int = 13
    private var currentFontFamily: String? = null
    private var currentCharset: Charset = Charsets.UTF_8

    var onFontSizeChanged: ((Int) -> Unit)? = null
    var onCharsetChanged: ((Charset) -> Unit)? = null

    fun applyTerminalColors(
        fg: java.awt.Color?,
        bg: java.awt.Color?,
    ) {
        currentFg = fg
        currentBg = bg
        terminals.values.forEach { it.applyTerminalColors(fg, bg) }
    }

    fun applyFontSize(size: Int) {
        currentFontSize = size
        terminals.values.forEach { it.applyFontSize(size) }
    }

    fun applyFontFamily(name: String?) {
        currentFontFamily = name
        terminals.values.forEach { it.applyFontFamily(name) }
    }

    internal val activePane: ProjectTerminalPane? get() = terminals[shownKey]

    fun zoomIn() {
        activePane?.zoomActive(+1)
    }

    fun zoomOut() {
        activePane?.zoomActive(-1)
    }

    fun zoomReset() {
        activePane?.zoomReset()
    }

    fun applyCharset(charset: Charset) {
        currentCharset = charset
        terminals.values.forEach { it.restartAllWithCharset(charset) }
    }

    fun dispose() {
        terminals.values.forEach { it.dispose() }
        terminals.clear()
    }

    private fun buildPlaceholder(): JPanel {
        val panel =
            JPanel(BorderLayout()).apply {
                background = javax.swing.UIManager.getColor("TextArea.background")
                    ?: javax.swing.UIManager.getColor("Panel.background")
                    ?: Color(0x1E1E1E)
            }
        val listener =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (SwingUtilities.isRightMouseButton(e) && shownDir != null) {
                        val pt = SwingUtilities.convertPoint(e.component, e.point, panel)
                        showShellMenu(panel, pt.x, pt.y)
                    }
                }
            }
        panel.addMouseListener(listener)
        placeholderLabel.addMouseListener(listener)
        placeholderMouseListener = listener
        placeholderPanel = panel
        updatePlaceholderContent(MSG_IDLE)
        return panel
    }

    private fun updatePlaceholderContent(msg: String) {
        val panel = placeholderPanel ?: return
        val current = (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
        if (current != null) panel.remove(current)

        val isIdle = msg == MSG_IDLE
        val hintId = if (isIdle) "terminal-idle" else "terminal-ready"
        val headline = if (isIdle) "No project selected" else "Ready"
        val desc =
            if (isIdle) {
                "Double-click a project in the tree to open a terminal, or press Ctrl+P to search."
            } else {
                "Right-click to open a terminal with your preferred shell."
            }

        if (ctx.config.showContextualHints && hintId !in ctx.config.dismissedHints) {
            val hint =
                ContextualHintPanel(hintId, headline, desc, onDismiss = { id ->
                    ctx.updateConfig(ctx.config.copy(dismissedHints = ctx.config.dismissedHints + id))
                    SwingUtilities.invokeLater {
                        val p = placeholderPanel ?: return@invokeLater
                        val c = (p.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
                        if (c != null) p.remove(c)
                        placeholderLabel.text = msg
                        p.add(placeholderLabel, BorderLayout.CENTER)
                        p.revalidate()
                        p.repaint()
                    }
                })
            placeholderMouseListener?.let { hint.addMouseListener(it) }
            panel.add(hint, BorderLayout.CENTER)
        } else {
            placeholderLabel.text = msg
            panel.add(placeholderLabel, BorderLayout.CENTER)
        }
        panel.revalidate()
        panel.repaint()
    }

    // ── Shell picker ─────────────────────────────────────────────────────────

    private fun showShellMenu(
        invoker: JPanel,
        x: Int,
        y: Int,
    ) {
        val dir = shownDir ?: return
        val menu = JPopupMenu()

        // AI CLI tools at the top
        if (availableCliTools.isNotEmpty()) {
            availableCliTools.forEach { cli ->
                menu.add(
                    JMenuItem(cli.name).apply {
                        toolTipText = cli.description
                        addActionListener {
                            // Open terminal with project's shell but launch the CLI as startup command
                            onActivateRequested?.invoke(dir.copy(startupCommand = cli.command))
                        }
                    },
                )
            }
            menu.addSeparator()
        }

        // Project-configured custom shell
        val customShell = dir.shellExecutable?.trim()?.takeIf { it.isNotEmpty() }
        if (customShell != null) {
            menu.add(
                JMenuItem("Custom shell: $customShell").apply {
                    font = font.deriveFont(Font.BOLD)
                    addActionListener { onActivateRequested?.invoke(dir) }
                },
            )
            menu.addSeparator()
        }

        // System shells — delegate to the shared ShellDetector
        val shells = ShellDetector.detect()
        if (shells.isEmpty()) {
            menu.add(JMenuItem("No shells detected").apply { isEnabled = false })
        } else {
            shells.forEach { shell ->
                menu.add(
                    JMenuItem(shell.displayName).apply {
                        addActionListener {
                            onActivateRequested?.invoke(dir.copy(shellExecutable = shell.command))
                        }
                    },
                )
            }
        }

        menu.show(invoker, x, y)
    }

    companion object {
        private const val MSG_IDLE = "Select a project to open a terminal"
        private const val MSG_READY = "Right-click to open a terminal"
    }
}
