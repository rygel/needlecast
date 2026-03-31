package io.github.quicklaunch.ui.terminal

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val CARD_EMPTY = "__empty__"

/**
 * Manages one [TerminalPanel] per project path.
 *
 * - [showProject]: switches the visible card without creating a terminal (called on selection change).
 * - [activateProject]: creates the terminal for a path if it doesn't exist yet, then shows it.
 * - [deactivateProject]: disposes and removes the terminal for a specific path.
 * - [deactivate]: hides all terminals (shows placeholder); used when group changes.
 */
class TerminalManager : JPanel(CardLayout()) {

    private val cardLayout = layout as CardLayout
    private val terminals = mutableMapOf<String, TerminalPanel>()
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

    /** Create a terminal for [path] if missing, then show it. */
    fun activateProject(path: String) {
        if (!terminals.containsKey(path)) {
            val panel = TerminalPanel(initialDir = path, dark = currentDark)
            terminals[path] = panel
            add(panel, path)
        }
        shownKey = path
        cardLayout.show(this, path)
    }

    /** Dispose and remove the terminal for [path]. Shows placeholder if it was visible. */
    fun deactivateProject(path: String) {
        val panel = terminals.remove(path) ?: return
        remove(panel)
        panel.dispose()
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
        shownKey?.let { terminals[it]?.requestFocusInWindow() }
    }

    fun sendInput(text: String) {
        shownKey?.let { terminals[it]?.sendInput(text) }
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
