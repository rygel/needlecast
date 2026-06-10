package io.github.rygel.needlecast.ui.terminal

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.ui.AiCli
import io.github.rygel.needlecast.ui.RemixIcons
import io.github.rygel.needlecast.ui.ShellDetector
import io.github.rygel.needlecast.ui.components.ContextualHintPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.charset.Charset
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val CARD_EMPTY = "__empty__"

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
        val normalised = cwd.replace('\\', '/')
        // Find the project path that is a prefix of (or equal to) the hook cwd
        val path =
            terminals.keys.firstOrNull { p ->
                val np = p.replace('\\', '/')
                normalised == np || normalised.startsWith("$np/")
            } ?: return
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

/**
 * A container for one or more [TerminalPanel] instances belonging to a single project.
 * A "+" button at the end of the tab row adds new terminal tabs. Tabs can be closed (except the last one).
 */
private class ProjectTerminalPane(
    private val path: String,
    private var isDark: Boolean,
    private val extraEnv: Map<String, String> = emptyMap(),
    private val shellExecutable: String? = null,
    private val startupCommand: String? = null,
    initialFg: java.awt.Color? = null,
    initialBg: java.awt.Color? = null,
    initialFontSize: Int = 13,
    initialFontFamily: String? = null,
    initialCharset: Charset = Charsets.UTF_8,
) : JPanel(BorderLayout()) {
    private var customFg: java.awt.Color? = initialFg
    private var customBg: java.awt.Color? = initialBg
    private var currentFontSize: Int = initialFontSize
    private var currentFontFamily: String? = initialFontFamily
    private var currentCharset: Charset = initialCharset

    var onFontSizeChanged: ((Int) -> Unit)? = null
    var onCharsetChanged: ((Charset) -> Unit)? = null

    fun applyTerminalColors(
        fg: java.awt.Color?,
        bg: java.awt.Color?,
    ) {
        customFg = fg
        customBg = bg
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyColors(fg, bg)
        }
    }

    fun applyFontSize(size: Int) {
        currentFontSize = size
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyFontSize(size)
        }
    }

    fun applyFontFamily(name: String?) {
        currentFontFamily = name
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyFontFamily(name)
        }
    }

    private val tabs = JTabbedPane()
    private var tabCounter = 0
    private var addingTab = false
    private var removingTab = false
    private val realTabCount: Int get() = tabs.tabCount - 1 // last tab is the "+" button

    /** Aggregated status across all tabs: THINKING if any tab is THINKING, else WAITING if any is WAITING, else NONE. */
    var onStatusChanged: ((AgentStatus) -> Unit)? = null
    private val tabStatuses = mutableMapOf<TerminalPanel, AgentStatus>()

    private fun recomputeStatus() {
        val merged =
            when {
                tabStatuses.values.any { it == AgentStatus.THINKING } -> AgentStatus.THINKING
                tabStatuses.values.any { it == AgentStatus.WAITING } -> AgentStatus.WAITING
                else -> AgentStatus.NONE
            }
        onStatusChanged?.invoke(merged)
    }

    init {
        minimumSize = Dimension(0, 0)
        tabs.minimumSize = Dimension(0, 0)
        val addButton =
            JButton("+").apply {
                toolTipText = "New terminal tab"
                isFocusable = false
                addActionListener { addTerminalTab() }
            }
        val restartButton =
            JButton(RemixIcons.icon("ri-refresh-line", 12)).apply {
                toolTipText = "Restart terminal"
                isFocusable = false
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { restartActiveTab() }
            }
        val encodingCombo = JComboBox<String>(ENCODINGS)
        encodingCombo.toolTipText = "Terminal character encoding"
        encodingCombo.isFocusable = false
        encodingCombo.selectedItem = currentCharset.name()
        encodingCombo.addActionListener {
            val selected = encodingCombo.selectedItem as? String ?: return@addActionListener
            val newCharset = tryRun { Charset.forName(selected) } ?: return@addActionListener
            if (newCharset != currentCharset) {
                currentCharset = newCharset
                onCharsetChanged?.invoke(newCharset)
                restartActiveTab()
            }
        }
        val trailingToolbar =
            JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(encodingCombo)
                add(restartButton)
                add(addButton)
            }
        tabs.putClientProperty("JTabbedPane.trailingComponent", trailingToolbar)
        add(tabs, BorderLayout.CENTER)
        tabs.addChangeListener { if (!addingTab && !removingTab && tabs.selectedIndex == realTabCount) addTerminalTab() }
        addTerminalTab()
    }

    private fun addTerminalTab() {
        addingTab = true
        tabCounter++
        val terminal =
            TerminalPanel(
                initialDir = path,
                dark = isDark,
                extraEnv = extraEnv,
                shellExecutable = shellExecutable,
                startupCommand = startupCommand,
                initialFg = customFg,
                initialBg = customBg,
                initialFontSize = currentFontSize,
                initialFontFamily = currentFontFamily,
                charset = currentCharset,
            )
        terminal.onStatusChanged = { status ->
            tabStatuses[terminal] = status
            recomputeStatus()
        }
        terminal.onFontSizeChanged = { size ->
            currentFontSize = size
            for (i in 0 until realTabCount) {
                val t = tabs.getComponentAt(i) as? TerminalPanel ?: continue
                if (t !== terminal) t.applyFontSize(size)
            }
            onFontSizeChanged?.invoke(size)
        }
        removePlusTab()
        val title = "Terminal $tabCounter"
        tabs.addTab(title, terminal)
        val idx = tabs.tabCount - 1
        val header =
            TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
                closeTab(terminal)
            }
        tabs.setTabComponentAt(idx, header)
        terminal.onTerminalTitleChanged = { newTitle ->
            val tidx = tabs.indexOfComponent(terminal)
            if (tidx >= 0) {
                tabs.setTitleAt(tidx, newTitle)
                header.setTitle(newTitle)
            }
        }
        terminal.onBell = { header.flashBell() }
        tabs.selectedIndex = idx
        addPlusTab()
        addingTab = false
        terminal.requestFocusInWindow()
    }

    private fun closeTab(terminal: TerminalPanel) {
        val idx = tabs.indexOfComponent(terminal)
        if (idx < 0 || realTabCount <= 1) return
        removingTab = true
        tabs.removeTabAt(idx)
        removingTab = false
        tabStatuses.remove(terminal)
        recomputeStatus()
        terminal.dispose()
    }

    private fun restartActiveTab() {
        val idx = tabs.selectedIndex
        if (idx < 0 || idx >= realTabCount) return
        val old = tabs.getComponentAt(idx) as? TerminalPanel ?: return
        val title = tabs.getTitleAt(idx)
        old.dispose()
        val replacement =
            TerminalPanel(
                initialDir = path,
                dark = isDark,
                extraEnv = extraEnv,
                shellExecutable = shellExecutable,
                startupCommand = startupCommand,
                initialFg = customFg,
                initialBg = customBg,
                initialFontSize = currentFontSize,
                initialFontFamily = currentFontFamily,
                charset = currentCharset,
            )
        replacement.onStatusChanged = { status ->
            tabStatuses[replacement] = status
            tabStatuses.remove(old)
            recomputeStatus()
        }
        replacement.onFontSizeChanged = { size ->
            currentFontSize = size
            for (i in 0 until realTabCount) {
                val t = tabs.getComponentAt(i) as? TerminalPanel ?: continue
                if (t !== replacement) t.applyFontSize(size)
            }
            onFontSizeChanged?.invoke(size)
        }
        tabs.setComponentAt(idx, replacement)
        tabs.setTitleAt(idx, title)
        val header =
            TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
                closeTab(replacement)
            }
        tabs.setTabComponentAt(idx, header)
        replacement.onTerminalTitleChanged = { newTitle ->
            val tidx = tabs.indexOfComponent(replacement)
            if (tidx >= 0) {
                tabs.setTitleAt(tidx, newTitle)
                header.setTitle(newTitle)
            }
        }
        replacement.onBell = { header.flashBell() }
        replacement.requestFocusInWindow()
    }

    private fun removePlusTab() {
        if (tabs.tabCount > 0 && (tabs.getTabComponentAt(tabs.tabCount - 1) as? JLabel)?.text == "+") {
            tabs.removeTabAt(tabs.tabCount - 1)
        }
    }

    private fun addPlusTab() {
        val plusLabel =
            JLabel("+", SwingConstants.CENTER).apply {
                preferredSize = Dimension(20, 20)
                isFocusable = false
            }
        tabs.addTab("+", JPanel())
        tabs.setTabComponentAt(tabs.tabCount - 1, plusLabel)
    }

    /** Pushes a hook-derived status to all Claude-session tabs (non-Claude tabs use PTY heuristics). */
    fun forceStatusOnClaudeTabs(status: AgentStatus) {
        for (i in 0 until realTabCount) {
            val t = tabs.getComponentAt(i) as? TerminalPanel ?: continue
            if (t.isClaudeSession) t.forceStatus(status)
        }
    }

    fun setUseHooksForStatus(enabled: Boolean) {
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.useHooksForStatus = enabled
        }
    }

    fun requestFocusOnActive() {
        if (tabs.selectedIndex < realTabCount) {
            (tabs.selectedComponent as? TerminalPanel)?.requestFocusInWindow()
        }
    }

    fun sendInputToActive(text: String) {
        if (tabs.selectedIndex < realTabCount) {
            (tabs.selectedComponent as? TerminalPanel)?.sendInput(text)
        }
    }

    fun applyTheme(dark: Boolean) {
        isDark = dark
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyTheme(dark)
        }
    }

    fun restartAllWithCharset(charset: Charset) {
        currentCharset = charset
        for (i in 0 until realTabCount) {
            val old = tabs.getComponentAt(i) as? TerminalPanel ?: continue
            val title = tabs.getTitleAt(i)
            old.dispose()
            val replacement =
                TerminalPanel(
                    initialDir = path,
                    dark = isDark,
                    extraEnv = extraEnv,
                    shellExecutable = shellExecutable,
                    startupCommand = startupCommand,
                    initialFg = customFg,
                    initialBg = customBg,
                    initialFontSize = currentFontSize,
                    initialFontFamily = currentFontFamily,
                    charset = charset,
                )
            replacement.onStatusChanged = { status ->
                tabStatuses[replacement] = status
                tabStatuses.remove(old)
                recomputeStatus()
            }
            replacement.onFontSizeChanged = { size ->
                currentFontSize = size
                for (j in 0 until realTabCount) {
                    val t = tabs.getComponentAt(j) as? TerminalPanel ?: continue
                    if (t !== replacement) t.applyFontSize(size)
                }
                onFontSizeChanged?.invoke(size)
            }
            tabs.setComponentAt(i, replacement)
            tabs.setTitleAt(i, title)
            val header =
                TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
                    closeTab(replacement)
                }
            tabs.setTabComponentAt(i, header)
            replacement.onTerminalTitleChanged = { newTitle ->
                val tidx = tabs.indexOfComponent(replacement)
                if (tidx >= 0) {
                    tabs.setTitleAt(tidx, newTitle)
                    header.setTitle(newTitle)
                }
            }
        }
    }

    fun dispose() {
        tabStatuses.clear()
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.dispose()
        }
    }
}

/** Tab header with label and close button. [canClose] guards against closing the last tab. */
private class TerminalTabHeader(
    title: String,
    private val canClose: () -> Boolean,
    onClose: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {
    private val titleLabel = JLabel(title)
    private var originalTitle: String? = null
    private val bellTimer =
        javax.swing
            .Timer(500) {
                if (originalTitle != null) {
                    titleLabel.text = originalTitle
                    originalTitle = null
                }
            }.apply { isRepeats = false }

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        add(titleLabel)
        add(
            JButton(RemixIcons.icon("ri-close-line", 16)).apply {
                toolTipText = "Close tab"
                preferredSize = Dimension(20, 20)
                isFocusable = false
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { if (canClose()) onClose() }
            },
        )
    }

    fun setTitle(title: String) {
        titleLabel.text = title
        if (originalTitle != null) originalTitle = title
    }

    /** Briefly shows a bell glyph in the tab to indicate a terminal bell was received. */
    fun flashBell() {
        if (bellTimer.isRunning) return
        if (originalTitle == null) originalTitle = titleLabel.text
        titleLabel.text = "🔔 ${titleLabel.text}"
        bellTimer.restart()
    }
}

private val ENCODINGS = arrayOf("UTF-8", "ISO-8859-1", "Windows-1252", "US-ASCII", "GBK", "Big5", "Shift_JIS", "EUC-JP", "KOI8-R", "Windows-1251")

private val logger = LoggerFactory.getLogger("io.github.rygel.needlecast.ui.terminal.TerminalManager")

private inline fun <T> tryRun(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        logger.debug("tryRun failed", e)
        null
    }
