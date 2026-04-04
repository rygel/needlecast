package io.github.rygel.needlecast.ui

import io.github.andrewauclair.moderndocking.Dockable
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * Thin wrapper that adapts any [JComponent] to the [Dockable] interface expected
 * by ModernDocking. Each panel in [MainWindow] is wrapped in one of these so that
 * the panels themselves don't need to know about docking.
 *
 * Tab placement is controlled globally via [io.github.andrewauclair.moderndocking.settings.Settings.setDefaultTabPreference]
 * rather than per-dockable, because ModernDocking reads the global default in [DockedTabbedPanel.addPanel].
 *
 * @param content   The actual panel to display.
 * @param id        Unique persistent ID used by ModernDocking for layout persistence.
 * @param title     Text shown in the dockable's tab / header.
 * @param closable  Whether the user can close (undock) this panel via its header × button.
 */
class DockablePanel(
    content: JComponent,
    private val id: String,
    private val title: String,
    private val closable: Boolean = true,
) : JPanel(BorderLayout()), Dockable {

    init {
        add(content)
        // Allow docking framework to shrink panels as small as needed
        minimumSize = java.awt.Dimension(0, 0)
    }

    /** Exposed for tests — same value as [getPersistentID]. */
    val dockableId: String get() = id

    override fun getPersistentID(): String = id
    override fun getTabText(): String = title
    override fun isClosable(): Boolean = closable
    override fun isWrappableInScrollpane(): Boolean = false

    fun setHoverHighlight(on: Boolean) {
        border = if (on) {
            val color = UIManager.getColor("Component.focusColor")
                ?: UIManager.getColor("TabbedPane.focusColor")
                ?: java.awt.Color(0x4A90D9)
            BorderFactory.createLineBorder(color, 2)
        } else {
            null
        }
        repaint()
    }
}
