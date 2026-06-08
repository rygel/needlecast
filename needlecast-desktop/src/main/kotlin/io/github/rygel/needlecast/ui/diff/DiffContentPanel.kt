package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ScrollPaneConstants

class DiffContentPanel : JPanel(BorderLayout()) {
    enum class ViewMode { SIDE_BY_SIDE, UNIFIED }

    var viewMode: ViewMode = ViewMode.SIDE_BY_SIDE
        private set

    val leftPane = DiffEditorPane(DiffEditorPane.Side.OLD)
    val rightPane = DiffEditorPane(DiffEditorPane.Side.NEW)
    private val unifiedPane = DiffEditorPane(DiffEditorPane.Side.UNIFIED)

    val leftScroll =
        JScrollPane(leftPane).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            border = BorderFactory.createEmptyBorder()
        }
    val rightScroll =
        JScrollPane(rightPane).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            border = BorderFactory.createEmptyBorder()
        }
    private val unifiedScroll =
        JScrollPane(unifiedPane).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            border = BorderFactory.createEmptyBorder()
        }

    private val leftGutter = DiffLineNumberGutter(leftPane, leftScroll)
    private val rightGutter = DiffLineNumberGutter(rightPane, rightScroll)
    private val unifiedGutter = DiffLineNumberGutter(unifiedPane, unifiedScroll)

    private val syncScroll = SynchronizedScrollListener(leftScroll, rightScroll)

    private var currentResult: DiffResult? = null
    private var currentFileIndex: Int = 0

    init {
        leftScroll.setRowHeaderView(leftGutter)
        rightScroll.setRowHeaderView(rightGutter)
        unifiedScroll.setRowHeaderView(unifiedGutter)
        syncScroll.install()
        showSideBySide()
    }

    fun setViewMode(mode: ViewMode) {
        if (mode == viewMode) return
        viewMode = mode
        if (mode == ViewMode.SIDE_BY_SIDE) showSideBySide() else showUnified()
        redisplay()
    }

    fun display(
        result: DiffResult,
        fileIndex: Int = 0,
    ) {
        currentResult = result
        currentFileIndex = fileIndex
        redisplay()
    }

    fun displayEmpty(message: String) {
        currentResult = null
        renderPane(emptyList(), DiffEditorPane.Side.OLD)
        if (viewMode == ViewMode.SIDE_BY_SIDE) {
            renderPane(emptyList(), DiffEditorPane.Side.NEW)
        }
    }

    fun getHunkLinePositions(): List<Pair<Int, Int>> {
        val result = currentResult ?: return emptyList()
        if (currentFileIndex >= result.files.size) return emptyList()
        val file = result.files[currentFileIndex]
        val positions = mutableListOf<Pair<Int, Int>>()
        var lineIdx = 0
        for (hunk in file.hunks) {
            val startLine = lineIdx
            lineIdx += hunk.lines.size
            positions.add(startLine to lineIdx)
        }
        return positions
    }

    private fun redisplay() {
        val result =
            currentResult ?: run {
                displayEmpty("No diff")
                return
            }
        if (result.files.isEmpty()) {
            displayEmpty("No changes")
            return
        }
        if (currentFileIndex >= result.files.size) currentFileIndex = 0
        val file = result.files[currentFileIndex]
        if (file.binary) {
            displayEmpty("(binary file)")
            return
        }

        if (viewMode == ViewMode.SIDE_BY_SIDE) {
            val split = splitLinesForSideBySide(file.hunks.flatMap { it.lines })
            renderPane(split.left, DiffEditorPane.Side.OLD)
            renderPane(split.right, DiffEditorPane.Side.NEW)
            updateGutter(split.left, leftGutter, DiffEditorPane.Side.OLD)
            updateGutter(split.right, rightGutter, DiffEditorPane.Side.NEW)
        } else {
            val allLines = file.hunks.flatMap { it.lines }
            renderPane(allLines, DiffEditorPane.Side.UNIFIED)
            updateGutter(allLines, unifiedGutter, DiffEditorPane.Side.UNIFIED)
        }
    }

    private fun showSideBySide() {
        removeAll()
        val split =
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll).apply {
                resizeWeight = 0.5
                dividerSize = 2
                border = BorderFactory.createEmptyBorder()
            }
        add(split, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun showUnified() {
        removeAll()
        add(unifiedScroll, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun renderPane(
        lines: List<DiffLine>,
        side: DiffEditorPane.Side,
    ) {
        val pane =
            when (side) {
                DiffEditorPane.Side.OLD -> leftPane
                DiffEditorPane.Side.NEW -> rightPane
                DiffEditorPane.Side.UNIFIED -> unifiedPane
            }
        pane.renderLines(lines)
    }

    private fun updateGutter(
        lines: List<DiffLine>,
        gutter: DiffLineNumberGutter,
        side: DiffEditorPane.Side,
    ) {
        val infos =
            lines.map { line ->
                DiffLineNumberGutter.LineInfo(
                    number =
                        when (side) {
                            DiffEditorPane.Side.OLD -> line.oldLineNum
                            DiffEditorPane.Side.NEW -> line.newLineNum
                            DiffEditorPane.Side.UNIFIED -> line.oldLineNum ?: line.newLineNum
                        },
                    type = line.type,
                )
            }
        gutter.setLineInfos(infos)
    }

    internal data class SideBySideSplit(
        val left: List<DiffLine>,
        val right: List<DiffLine>,
    )

    internal fun splitLinesForSideBySide(lines: List<DiffLine>): SideBySideSplit {
        val left = mutableListOf<DiffLine>()
        val right = mutableListOf<DiffLine>()
        var i = 0
        while (i < lines.size) {
            when (lines[i].type) {
                DiffLineType.CONTEXT -> {
                    left.add(lines[i])
                    right.add(lines[i])
                    i++
                }
                DiffLineType.REMOVED -> {
                    val removedStart = i
                    while (i < lines.size && lines[i].type == DiffLineType.REMOVED) {
                        left.add(lines[i])
                        i++
                    }
                    val addedStart = i
                    val removedCount = i - removedStart
                    while (i < lines.size && lines[i].type == DiffLineType.ADDED) {
                        right.add(lines[i])
                        i++
                    }
                    val addedCount = i - addedStart
                    val padCount = removedCount - addedCount
                    if (padCount > 0) {
                        repeat(padCount) { right.add(DiffLine(DiffLineType.CONTEXT, null, null, "")) }
                    } else if (padCount < 0) {
                        repeat(-padCount) { left.add(DiffLine(DiffLineType.CONTEXT, null, null, "")) }
                    }
                }
                DiffLineType.ADDED -> {
                    right.add(lines[i])
                    left.add(DiffLine(DiffLineType.CONTEXT, null, null, ""))
                    i++
                }
            }
        }
        return SideBySideSplit(left, right)
    }
}
