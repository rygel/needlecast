package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.ui.terminal.ClaudeUsageData
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
    private val quotaLabel = JLabel().apply {
        isVisible = false
        border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
    }
    private val updateBadge = JLabel().apply {
        isVisible = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(0, 8, 0, 6)
    }

    init {
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY)
        add(label, BorderLayout.WEST)
        add(quotaLabel, BorderLayout.CENTER)
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

    fun updateQuota(data: ClaudeUsageData?) {
        if (data == null) {
            quotaLabel.isVisible = false
            return
        }

        val fiveH = data.fiveHourPercent
        val sevenD = data.sevenDayPercent

        if (fiveH == null && sevenD == null) {
            quotaLabel.isVisible = false
            return
        }

        val parts = mutableListOf<String>()
        if (fiveH != null) parts.add("5h: ${"%.0f".format(fiveH)}%")
        if (sevenD != null) parts.add("7d: ${"%.0f".format(sevenD)}%")
        quotaLabel.text = parts.joinToString(" | ")

        val worstPct = listOfNotNull(fiveH, sevenD).maxOrNull() ?: 0.0
        quotaLabel.foreground = when {
            worstPct >= 90 -> Color(0xF44336)
            worstPct >= 70 -> Color(0xFF9800)
            else -> UIManager.getColor("Label.foreground") ?: Color(0x4CAF50)
        }

        val tooltipParts = mutableListOf<String>()
        if (fiveH != null) tooltipParts.add("5-hour window: ${"%.1f".format(fiveH)}%${data.fiveHourResetsAt?.let { " (resets $it)" } ?: ""}")
        if (sevenD != null) tooltipParts.add("7-day window: ${"%.1f".format(sevenD)}%${data.sevenDayResetsAt?.let { " (resets $it)" } ?: ""}")
        if (data.sevenDaySonnetPercent != null) tooltipParts.add("7d Sonnet: ${"%.1f".format(data.sevenDaySonnetPercent)}%")
        if (data.sevenDayOpusPercent != null) tooltipParts.add("7d Opus: ${"%.1f".format(data.sevenDayOpusPercent)}%")
        quotaLabel.toolTipText = "<html>${tooltipParts.joinToString("<br>")}</html>"

        quotaLabel.isVisible = true
    }

    fun hideQuota() {
        quotaLabel.isVisible = false
    }
}
