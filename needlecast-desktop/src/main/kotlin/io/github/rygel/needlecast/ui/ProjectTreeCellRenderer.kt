package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

private val logger = LoggerFactory.getLogger("ProjectTreeCellRenderer")

internal class ProjectTreeCellRenderer(
    private val tree: JTree,
    private val activePaths: () -> Set<String>,
    private val agentStatuses: Map<String, AgentStatus>,
    private val blinkOn: () -> Boolean,
    private val missingPaths: Set<String>,
    private val gitStatusCache: Map<String, GitStatus>,
    private val scanResults: Map<String, DetectedProject>,
    private val isPrivacyModeEnabled: () -> Boolean,
) : TreeCellRenderer {
    private val colorStripe =
        JPanel().apply {
            preferredSize = Dimension(4, 0)
            isOpaque = true
        }
    private val nameLabel =
        JLabel().apply {
            icon = RemixIcons.icon("ri-play-circle-line", 12)
            font = font.deriveFont(Font.BOLD, 12f)
            minimumSize = Dimension(0, 0)
        }
    private val missingIcon =
        JLabel().apply {
            icon = RemixIcons.icon("ri-error-warning-line", 12)
            border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            toolTipText = "Directory not found"
            isVisible = false
        }
    private val activeDot =
        JLabel("\u25CF").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            foreground = Color(0x4CAF50)
            border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
        }
    private val agentLed = LedIndicator()
    private val lockLabel =
        JLabel().apply {
            icon = RemixIcons.icon("ri-lock-line", 12)
            isVisible = false
        }
    private val dotsPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(activeDot)
            add(agentLed)
            add(lockLabel)
        }
    private val branchLabel =
        JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            foreground = Color(0x888888)
            minimumSize = Dimension(0, 0)
        }
    private val tagsPanel =
        object : JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)) {
            override fun getPreferredSize(): Dimension {
                var w = 0
                val h = components.maxOfOrNull { it.preferredSize.height } ?: 0
                val gap = (layout as FlowLayout).hgap
                for (i in 0 until componentCount) {
                    if (i > 0) w += gap
                    w += components[i].preferredSize.width
                }
                val forced = projectPanel.forcedWidth
                val width = if (forced > 0) forced else w
                return Dimension(width, h)
            }

            override fun getMaximumSize(): Dimension = Dimension(Short.MAX_VALUE.toInt(), 16)

            override fun doLayout() {
                val gap = (layout as FlowLayout).hgap
                var x = width
                val buildToolStart = componentCount - buildToolBadgeCount
                for (i in componentCount - 1 downTo 0) {
                    val c = components[i]
                    val pref = c.preferredSize
                    val nextX = x - pref.width
                    val isBuildTool = i >= buildToolStart
                    if (nextX < 0 && !isBuildTool) {
                        c.setBounds(0, 0, 0, 0)
                        c.isVisible = false
                    } else {
                        c.isVisible = true
                        val startX = nextX.coerceAtLeast(0)
                        c.setBounds(startX, 0, pref.width, height.coerceAtLeast(pref.height))
                        x = startX - gap
                    }
                }
            }
        }.apply {
            isOpaque = false
        }
    private val nameRow =
        JPanel(BorderLayout(2, 0)).apply {
            isOpaque = false
            add(dotsPanel, BorderLayout.WEST)
            add(nameLabel, BorderLayout.CENTER)
            add(missingIcon, BorderLayout.EAST)
        }
    private val bottomRow =
        object : JPanel(BorderLayout(4, 0)) {
            override fun getPreferredSize(): Dimension {
                val base = super.getPreferredSize()
                val vp = tree.parent as? javax.swing.JViewport
                val vpWidth = vp?.width ?: tree.width
                val width = if (vpWidth > 0) vpWidth else base.width
                return Dimension(width, base.height)
            }
        }.apply {
            isOpaque = false
            add(branchLabel, BorderLayout.WEST)
            add(tagsPanel, BorderLayout.CENTER)
        }
    private val cellPanel =
        JPanel(BorderLayout(0, 1)).apply {
            isOpaque = false
            add(nameRow, BorderLayout.NORTH)
            add(bottomRow, BorderLayout.CENTER)
        }
    private val innerPanel =
        JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
        }
    private val projectPanel =
        object : JPanel(BorderLayout()) {
            var forcedWidth: Int = 0

            override fun getPreferredSize(): Dimension {
                val base = super.getPreferredSize()
                val w = if (forcedWidth > 0) forcedWidth else base.width
                return Dimension(w, base.height)
            }

            override fun getMinimumSize(): Dimension = getPreferredSize()

            override fun getMaximumSize(): Dimension = getPreferredSize()
        }.apply { isOpaque = true }

    private val folderLabel =
        JLabel().apply {
            border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
        }

    private var lastTagsCacheKey: String? = null
    private var buildToolBadgeCount = 0

    init {
        innerPanel.add(cellPanel, BorderLayout.CENTER)
        projectPanel.add(colorStripe, BorderLayout.WEST)
        projectPanel.add(innerPanel, BorderLayout.CENTER)
    }

    override fun getTreeCellRendererComponent(
        t: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val node = value as? DefaultMutableTreeNode
        val bg = if (selected) (UIManager.getColor("Tree.selectionBackground") ?: t.background) else t.background
        val fg = if (selected) (UIManager.getColor("Tree.selectionForeground") ?: t.foreground) else t.foreground

        return when (val entry = node?.userObject) {
            is ProjectTreeEntry.Folder -> {
                folderLabel.text = entry.name
                folderLabel.icon = UIManager.getIcon(if (expanded) "Tree.openIcon" else "Tree.closedIcon")
                folderLabel.foreground = fg
                folderLabel.background = bg
                folderLabel.isOpaque = true
                val c =
                    entry.color?.let {
                        try {
                            Color.decode(it)
                        } catch (e: Exception) {
                            logger.debug("Failed to decode folder color", e)
                            null
                        }
                    }
                folderLabel.border =
                    if (c != null) {
                        BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 4, 0, 0, c),
                            BorderFactory.createEmptyBorder(3, 4, 3, 6),
                        )
                    } else {
                        BorderFactory.createEmptyBorder(3, 6, 3, 6)
                    }
                folderLabel
            }

            is ProjectTreeEntry.Project -> {
                val isActive = entry.directory.path in activePaths()
                activeDot.isVisible = isActive
                val ledStatus = agentStatuses[entry.directory.path] ?: AgentStatus.NONE
                agentLed.isVisible = ledStatus != AgentStatus.NONE
                agentLed.status = ledStatus
                agentLed.blinkOn = blinkOn()
                val isMissing = entry.directory.path in missingPaths
                missingIcon.isVisible = isMissing
                lockLabel.isVisible = entry.directory.isPrivate
                nameLabel.text = entry.directory.label(isPrivacyModeEnabled())
                nameLabel.foreground = if (isMissing && !selected) Color(0xE53935) else fg

                val gs = gitStatusCache[entry.directory.path]
                val isPrivateRedacted = entry.directory.isPrivate && isPrivacyModeEnabled()
                if (gs?.branch != null && !isPrivateRedacted) {
                    branchLabel.text = "${gs.branch}${if (gs.isDirty) "*" else ""}"
                    branchLabel.toolTipText = gs.branch
                    branchLabel.foreground = if (gs.isDirty) Color(0xE6A817) else Color(0x888888)
                } else {
                    branchLabel.text = " "
                    branchLabel.toolTipText = null
                }

                val scanned = scanResults[entry.directory.path]
                val tagsKey = "${entry.directory.path}|${scanned?.buildTools?.joinToString { it.tagLabel }}|${entry.tags.joinToString()}"
                if (tagsKey != lastTagsCacheKey) {
                    lastTagsCacheKey = tagsKey
                    tagsPanel.removeAll()
                    buildToolBadgeCount = 0
                    when {
                        scanned == null -> {}

                        scanned.scanFailed -> {
                            tagsPanel.add(JLabel(RemixIcons.icon("ri-error-warning-line", 12, Color(0xB71C1C))))
                            buildToolBadgeCount = 1
                        }

                        else -> {
                            entry.tags.forEach { tag -> tagsPanel.add(badge(tag, "#546E7A")) }
                            scanned.buildTools.forEach { tool -> tagsPanel.add(badge(tool.tagLabel, tool.tagColor)) }
                            buildToolBadgeCount = scanned.buildTools.size
                        }
                    }
                }

                val colorHex = entry.directory.color
                colorStripe.isVisible = colorHex != null
                if (colorHex != null) {
                    colorStripe.background =
                        try {
                            Color.decode(colorHex)
                        } catch (e: Exception) {
                            logger.debug("Failed to decode stripe color", e)
                            Color.GRAY
                        }
                }

                projectPanel.background = bg
                innerPanel.background = bg

                val vp = tree.parent as? javax.swing.JViewport
                val vpWidth = vp?.width ?: tree.width
                if (vpWidth > 0) {
                    val insets = tree.insets
                    val leftInset = insets?.left ?: 0
                    val rightInset = insets?.right ?: 0
                    val treeUI = tree.ui as? javax.swing.plaf.basic.BasicTreeUI
                    val totalIndent =
                        treeUI?.let {
                            val left = it.leftChildIndent
                            val right = it.rightChildIndent
                            val depth = node?.level ?: 0
                            depth * (left + right)
                        } ?: 0
                    val startX = leftInset + totalIndent
                    projectPanel.forcedWidth = (vpWidth - startX - rightInset).coerceAtLeast(50)
                }

                projectPanel
            }

            else -> {
                JLabel(value?.toString() ?: "").apply {
                    foreground = fg
                    background = bg
                    isOpaque = true
                }
            }
        }
    }
}
