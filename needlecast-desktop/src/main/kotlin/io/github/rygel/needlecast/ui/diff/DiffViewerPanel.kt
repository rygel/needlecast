package io.github.rygel.needlecast.ui.diff

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ui.components.DynamicHelpPopup
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JToggleButton
import javax.swing.KeyStroke

class DiffViewerPanel(
    val fileOpener: ((String) -> Unit)? = null,
    private val ctx: AppContext? = null,
) : JPanel(BorderLayout()) {

    private val fileTree = DiffFileTree()
    internal val contentPanel = DiffContentPanel()
    private val overviewBar = DiffOverviewBar(contentPanel.leftScroll)
    private val searchBar = DiffSearchBar()

    private val sideBySideToggle = JToggleButton("Side-by-side").apply {
        isSelected = true
        isFocusable = false
    }
    private val unifiedToggle = JToggleButton("Unified").apply {
        isFocusable = false
    }
    private val prevChangeButton = JButton("\u25C0 Change").apply {
        toolTipText = "Previous change"
        isFocusable = false
    }
    private val nextChangeButton = JButton("Change \u25B6").apply {
        toolTipText = "Next change"
        isFocusable = false
    }

    private val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
        add(sideBySideToggle)
        add(unifiedToggle)
        add(prevChangeButton)
        add(nextChangeButton)
    }

    private var currentResult: DiffResult? = null
    private var currentFileIndex: Int = 0
    private var currentHunkIndex: Int = -1

    init {
        name = "diff-viewer"
        minimumSize = Dimension(0, 0)

        ButtonGroup().apply { add(sideBySideToggle); add(unifiedToggle) }

        sideBySideToggle.addActionListener {
            contentPanel.setViewMode(DiffContentPanel.ViewMode.SIDE_BY_SIDE)
            rewireOverviewBar()
        }
        unifiedToggle.addActionListener {
            contentPanel.setViewMode(DiffContentPanel.ViewMode.UNIFIED)
            rewireOverviewBar()
        }

        prevChangeButton.addActionListener { navigateChange(-1) }
        nextChangeButton.addActionListener { navigateChange(1) }

        fileTree.onFileSelected = { index -> selectFile(index) }
        fileTree.onFileDoubleClicked = { path -> fileOpener?.invoke(path) }
        searchBar.onClose = { searchBar.deactivate() }

        searchBar.setTargetPanes(listOf(contentPanel.leftPane, contentPanel.rightPane))

        val legendBar = buildLegend()
        val northPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(legendBar, BorderLayout.SOUTH)
        }

        val fileTreeScroll = JScrollPane(fileTree).apply {
            preferredSize = Dimension(200, 0)
            minimumSize = Dimension(100, 0)
            border = BorderFactory.createEmptyBorder()
        }

        val centerSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            fileTreeScroll,
            contentPanel,
        ).apply {
            resizeWeight = 0.0
            dividerSize = 2
            border = BorderFactory.createEmptyBorder()
        }

        val contentWithOverview = JPanel(BorderLayout()).apply {
            add(centerSplit, BorderLayout.CENTER)
            add(overviewBar, BorderLayout.EAST)
        }

        add(toolbar, BorderLayout.NORTH)
        add(contentWithOverview, BorderLayout.CENTER)
        add(searchBar, BorderLayout.SOUTH)

        searchBar.isVisible = false

        registerKeyboardShortcuts()
    }

    fun display(result: DiffResult) {
        currentResult = result
        currentFileIndex = 0
        currentHunkIndex = -1
        fileTree.setFiles(result.files)
        if (result.files.isNotEmpty()) {
            contentPanel.display(result, 0)
            updateOverviewBar()
        } else {
            contentPanel.displayEmpty("No changes")
        }
        if (ctx != null) {
            DynamicHelpPopup(ctx, "diff-first-open", "Green = added, Red = removed. Click a commit to see its diff.", toolbar).showIfNotSeen()
        }
    }

    fun displayEmpty(message: String) {
        currentResult = null
        contentPanel.displayEmpty(message)
        fileTree.setFiles(emptyList())
    }

    private fun selectFile(index: Int) {
        val result = currentResult ?: return
        if (index < 0 || index >= result.files.size) return
        currentFileIndex = index
        currentHunkIndex = -1
        contentPanel.display(result, index)
        updateOverviewBar()
    }

    private fun navigateChange(direction: Int) {
        val positions = contentPanel.getHunkLinePositions()
        if (positions.isEmpty()) return

        if (currentHunkIndex < 0) {
            currentHunkIndex = if (direction > 0) 0 else positions.size - 1
        } else {
            currentHunkIndex += direction
            if (currentHunkIndex < 0) currentHunkIndex = positions.size - 1
            if (currentHunkIndex >= positions.size) currentHunkIndex = 0
        }

        val (startLine, _) = positions[currentHunkIndex]
        contentPanel.leftPane.scrollToLine(startLine)
        contentPanel.rightPane.scrollToLine(startLine)
    }

    private fun updateOverviewBar() {
        val result = currentResult ?: return
        if (currentFileIndex >= result.files.size) return
        val file = result.files[currentFileIndex]
        val lines = if (contentPanel.viewMode == DiffContentPanel.ViewMode.UNIFIED) {
            file.hunks.flatMap { it.lines }
        } else {
            val split = contentPanel.splitLinesForSideBySide(file.hunks.flatMap { it.lines })
            split.left
        }
        overviewBar.setDiffData(lines)
    }

    private fun rewireOverviewBar() {
        updateOverviewBar()
    }

    private fun registerKeyboardShortcuts() {
        registerKeyboardAction(
            { searchBar.activate() },
            "openSearch",
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
        )
    }

    private fun buildLegend(): JPanel {
        val dismissed = ctx?.config?.diffLegendDismissed == true
        val legend = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(0x44, 0x44, 0x44)),
                BorderFactory.createEmptyBorder(2, 4, 2, 4),
            )
            add(legendSwatch(DiffColors.addedBackground, "Added"))
            add(legendSwatch(DiffColors.removedBackground, "Removed"))
            add(legendSwatch(DiffColors.searchHighlight, "Search match"))
            add(JButton("\u00D7").apply {
                toolTipText = "Dismiss legend"
                isFocusable = false
                isContentAreaFilled = false
                border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
                margin = java.awt.Insets(0, 0, 0, 0)
                font = font.deriveFont(12f)
                addActionListener {
                    isVisible = false
                    ctx?.let { c -> c.updateConfig(c.config.copy(diffLegendDismissed = true)) }
                }
            })
        }
        legend.isVisible = !dismissed
        return legend
    }

    private fun legendSwatch(color: Color, label: String): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(JPanel().apply {
                preferredSize = Dimension(12, 12)
                background = color
                toolTipText = label
            })
            add(JLabel(label).apply { font = font.deriveFont(11f) })
        }
    }
}
