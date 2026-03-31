package io.github.quicklaunch.ui.terminal

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingConstants

private const val CARD_EMPTY = "__empty__"

/**
 * Manages one [ProjectTerminalPane] per project path.
 *
 * - [showProject]: switches the visible card without creating a terminal (called on selection change).
 * - [activateProject]: creates the terminal pane for a path if it doesn't exist yet, then shows it.
 * - [deactivateProject]: disposes and removes the terminal pane for a specific path.
 * - [deactivate]: hides all terminals (shows placeholder); used when group changes.
 */
class TerminalManager : JPanel(CardLayout()) {

    private val cardLayout = layout as CardLayout
    private val terminals = mutableMapOf<String, ProjectTerminalPane>()
    private var currentDark = true
    private var shownKey: String? = null

    init {
        add(buildPlaceholder(), CARD_EMPTY)
        cardLayout.show(this, CARD_EMPTY)
    }

    /** Switch display to [path] without creating a terminal. Shows placeholder if none exists. */
    fun showProject(path: String) {
        shownKey = path
        if (terminals.containsKey(path)) {
            cardLayout.show(this, path)
        } else {
            cardLayout.show(this, CARD_EMPTY)
        }
    }

    /** Create a terminal pane for [path] if missing, then show it. */
    fun activateProject(path: String) {
        if (!terminals.containsKey(path)) {
            val pane = ProjectTerminalPane(path, currentDark)
            terminals[path] = pane
            add(pane, path)
        }
        shownKey = path
        cardLayout.show(this, path)
    }

    /** Dispose and remove the terminal pane for [path]. Shows placeholder if it was visible. */
    fun deactivateProject(path: String) {
        val pane = terminals.remove(path) ?: return
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
    }

    fun dispose() {
        terminals.values.forEach { it.dispose() }
        terminals.clear()
    }

    private fun buildPlaceholder(): JPanel = JPanel(BorderLayout()).apply {
        background = Color(0x1E1E1E)
        add(JLabel("Activate a project to open a terminal", SwingConstants.CENTER).apply {
            foreground = Color(0x6A737D)
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }, BorderLayout.CENTER)
    }
}

/**
 * A tabbed container for one or more [TerminalPanel] instances belonging to a single project.
 * A "+" toolbar button adds a new terminal tab. Tabs can be closed (except the last one).
 */
private class ProjectTerminalPane(
    private val path: String,
    private var isDark: Boolean,
) : JPanel(BorderLayout()) {

    private val tabs = JTabbedPane()
    private var tabCounter = 0

    init {
        val addButton = JButton("+").apply {
            toolTipText = "New terminal tab"
            isFocusable = false
            addActionListener { addTerminalTab() }
        }
        val toolbar = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            isOpaque = false
            add(addButton)
        }
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(tabs.apply { isOpaque = false }, BorderLayout.CENTER)
            // Overlay a small "+" button in the top-right corner of the tabs
        }
        // Put add button east of the tab bar by wrapping in a panel
        val topBar = JPanel(BorderLayout()).apply {
            add(JLabel(), BorderLayout.CENTER) // spacer
            add(toolbar, BorderLayout.EAST)
        }
        add(topBar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
        addTerminalTab()
    }

    private fun addTerminalTab() {
        tabCounter++
        val terminal = TerminalPanel(initialDir = path, dark = isDark)
        val title = "Terminal $tabCounter"
        val idx = tabs.tabCount
        tabs.addTab(title, terminal)
        tabs.setTabComponentAt(idx, TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
            closeTab(terminal)
        })
        tabs.selectedIndex = idx
        terminal.requestFocusInWindow()
    }

    private fun closeTab(terminal: TerminalPanel) {
        val idx = tabs.indexOfComponent(terminal)
        if (idx < 0 || tabs.tabCount <= 1) return
        tabs.removeTabAt(idx)
        terminal.dispose()
    }

    fun requestFocusOnActive() {
        (tabs.selectedComponent as? TerminalPanel)?.requestFocusInWindow()
    }

    fun sendInputToActive(text: String) {
        (tabs.selectedComponent as? TerminalPanel)?.sendInput(text)
    }

    fun applyTheme(dark: Boolean) {
        isDark = dark
        for (i in 0 until tabs.tabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyTheme(dark)
        }
    }

    fun dispose() {
        for (i in 0 until tabs.tabCount) {
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
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        add(JLabel(title))
        add(JButton("\u2715").apply {
            toolTipText = "Close tab"
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false
            font = font.deriveFont(9f)
            addActionListener { if (canClose()) onClose() }
        })
    }
}
