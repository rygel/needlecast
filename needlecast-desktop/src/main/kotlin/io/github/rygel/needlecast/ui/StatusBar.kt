package io.github.rygel.needlecast.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class StatusBar : JPanel(BorderLayout()) {

    private val label = JLabel(" Ready")
    private val updateBadge = JLabel().apply {
        isVisible = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(0, 8, 0, 6)
    }

    init {
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY)
        add(label, BorderLayout.WEST)
        add(updateBadge, BorderLayout.EAST)
    }

    fun showUpdateAvailable(version: String, onClick: () -> Unit) {
        updateBadge.text = "⬆ $version available  "
        updateBadge.foreground = UIManager.getColor("Component.accentColor") ?: Color(0x00BCD4)
        updateBadge.mouseListeners.forEach { updateBadge.removeMouseListener(it) }
        updateBadge.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
        updateBadge.isVisible = true
        revalidate()
    }

    fun setStatus(msg: String) {
        label.text = " $msg"
    }

    fun setRunning(commandLabel: String) {
        label.text = " Running: $commandLabel"
    }

    fun setFinished(exitCode: Int) {
        label.text = if (exitCode == 0) " Finished successfully (exit 0)"
                     else " Finished with exit code $exitCode"
    }

    fun setReady() {
        label.text = " Ready"
    }
}
