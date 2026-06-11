package io.github.rygel.needlecast.ui.renderers

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.ui.RemixIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class CompactProjectDirectoryRenderer(
    private val activePathsProvider: () -> Set<String>,
    private val gitStatusProvider: (String) -> GitStatus?,
) : ListCellRenderer<DetectedProject> {
    private val colorStripe =
        JPanel().apply {
            preferredSize = Dimension(4, 0)
            isOpaque = true
        }
    private val panel =
        JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
    private val outerPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = true
        }

    init {
        outerPanel.add(colorStripe, BorderLayout.WEST)
        outerPanel.add(panel, BorderLayout.CENTER)
    }

    private val nameLabel =
        JLabel().apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
    private val activeDot =
        JLabel(RemixIcons.icon("ri-checkbox-blank-circle-fill", 10, Color(0x4CAF50))).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 4)
        }
    private val branchLabel =
        JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            foreground = Color(0x888888)
            border = BorderFactory.createEmptyBorder(0, 18, 0, 0)
        }
    private val tagsPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = false
        }

    private val nameRow =
        JPanel(BorderLayout(2, 0)).apply {
            isOpaque = false
            add(activeDot, BorderLayout.WEST)
            add(nameLabel, BorderLayout.CENTER)
            add(tagsPanel, BorderLayout.EAST)
        }

    private val cellPanel =
        JPanel(BorderLayout(0, 1)).apply {
            isOpaque = false
            add(nameRow, BorderLayout.NORTH)
            add(branchLabel, BorderLayout.CENTER)
        }

    init {
        panel.add(cellPanel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out DetectedProject>,
        value: DetectedProject?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val isActive = value != null && value.directory.path in activePathsProvider()
        activeDot.isVisible = isActive
        nameLabel.text = value?.directory?.label() ?: ""

        val gs = value?.let { gitStatusProvider(it.directory.path) }
        if (gs != null && gs.branch != null) {
            val dirtyMark = if (gs.isDirty) "*" else ""
            branchLabel.text = "${gs.branch}$dirtyMark"
            branchLabel.toolTipText = gs.branch
            branchLabel.foreground = if (gs.isDirty) Color(0xE6A817) else Color(0x888888)
        } else {
            branchLabel.text = " "
            branchLabel.toolTipText = null
        }

        tagsPanel.removeAll()
        if (value != null) {
            if (value.scanFailed) {
                tagsPanel.add(
                    JLabel(RemixIcons.icon("ri-error-warning-line", 10, Color(0xB71C1C))).apply {
                        toolTipText = "Scan failed — check logs or rescan"
                    },
                )
            } else {
                val tools = value.buildTools
                val tags = if (tools.isEmpty()) listOf(null) else tools.map { it }
                tags.forEach { tool -> tagsPanel.add(buildTagLabel(tool)) }
            }
        }

        val bg = if (isSelected) list.selectionBackground else list.background
        outerPanel.background = bg
        panel.background = bg
        nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
        panel.isOpaque = true

        val colorHex = value?.directory?.color
        colorStripe.isVisible = colorHex != null
        if (colorHex != null) {
            colorStripe.background =
                try {
                    Color.decode(colorHex)
                } catch (_: Exception) {
                    Color.GRAY
                }
        }

        return outerPanel
    }

    private fun buildTagLabel(tool: BuildTool?): JLabel {
        val text = tool?.tagLabel ?: "?"
        val hex = tool?.tagColor ?: "#757575"
        return JLabel(text).apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 9)
            foreground = Color.WHITE
            background = Color.decode(hex)
            isOpaque = true
            border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
            preferredSize = Dimension(preferredSize.width, 14)
        }
    }

    val activeDotVisible: Boolean get() = activeDot.isVisible
    val colorStripeVisible: Boolean get() = colorStripe.isVisible
    val branchText: String get() = branchLabel.text
    val hasTags: Boolean get() = tagsPanel.components.isNotEmpty()
}
