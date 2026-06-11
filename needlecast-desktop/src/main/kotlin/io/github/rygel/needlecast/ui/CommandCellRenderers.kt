package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandHistoryEntry
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class CommandCellRenderer : ListCellRenderer<CommandDescriptor> {
    private val panel =
        JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            isOpaque = true
        }
    private val badgeLabel =
        JLabel().apply {
            font = font.deriveFont(Font.BOLD, 10f)
            border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
            isOpaque = true
        }
    private val nameLabel = JLabel()

    init {
        panel.add(badgeLabel, BorderLayout.WEST)
        panel.add(nameLabel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out CommandDescriptor>,
        value: CommandDescriptor?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val tool = value?.buildTool
        if (tool != null) {
            badgeLabel.text = tool.tagLabel
            val bgColor =
                try {
                    java.awt.Color.decode(tool.tagColor)
                } catch (_: Exception) {
                    java.awt.Color.GRAY
                }
            badgeLabel.background = bgColor
            badgeLabel.foreground = java.awt.Color.WHITE
            badgeLabel.isVisible = true
        } else {
            badgeLabel.isVisible = false
        }
        nameLabel.text = (value?.label ?: "").toHtmlLabel()
        nameLabel.toolTipText =
            if (value?.isSupported == true) {
                value.argv.joinToString(" ")
            } else {
                "This run configuration type is not directly executable"
            }
        nameLabel.foreground =
            when {
                isSelected -> list.selectionForeground
                value?.isSupported == false -> java.awt.Color.GRAY
                else -> list.foreground
            }
        panel.background = if (isSelected) list.selectionBackground else list.background
        nameLabel.background = panel.background
        nameLabel.isOpaque = false
        return panel
    }
}

internal val timeFmt = SimpleDateFormat("HH:mm")

class HistoryCellRenderer : ListCellRenderer<CommandHistoryEntry> {
    private val panel =
        JPanel(BorderLayout(6, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
    private val nameLabel = JLabel().apply { font = font.deriveFont(Font.PLAIN, 11f) }
    private val metaLabel = JLabel().apply { font = font.deriveFont(Font.PLAIN, 9f) }

    init {
        panel.add(nameLabel, BorderLayout.CENTER)
        panel.add(metaLabel, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out CommandHistoryEntry>,
        value: CommandHistoryEntry?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        nameLabel.text = (value?.label ?: "").toHtmlLabel()
        metaLabel.text = value?.let {
            val time = timeFmt.format(Date(it.ranAt))
            val codeColor = if (it.exitCode == 0) "#4CAF50" else "#F44336"
            "<html><font color='$codeColor'>exit ${it.exitCode}</font> $time</html>"
        } ?: ""
        val bg = if (isSelected) list.selectionBackground else list.background
        nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
        panel.background = bg
        nameLabel.background = bg
        metaLabel.background = bg
        panel.isOpaque = true
        return panel
    }
}

internal fun String.toHtmlLabel(): String = "<html>${replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</html>"
