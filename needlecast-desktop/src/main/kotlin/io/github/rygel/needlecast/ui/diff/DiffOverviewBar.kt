package io.github.rygel.needlecast.ui.diff

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JScrollPane

class DiffOverviewBar(
    private val scrollPane: JScrollPane,
) : JComponent() {
    private data class ChangeBlock(
        val startLine: Int,
        val endLine: Int,
        val type: DiffLineType,
    )

    private var totalLines: Int = 0
    private var changeBlocks = listOf<ChangeBlock>()

    init {
        preferredSize = java.awt.Dimension(OVERVIEW_WIDTH, 0)
        object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = jumpToY(e.y)

            override fun mousePressed(e: MouseEvent) = jumpToY(e.y)

            override fun mouseDragged(e: MouseEvent) = jumpToY(e.y)
        }.also {
            addMouseListener(it)
            addMouseMotionListener(it)
        }
    }

    fun setDiffData(lines: List<DiffLine>) {
        totalLines = lines.size
        val blocks = mutableListOf<ChangeBlock>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].type != DiffLineType.CONTEXT) {
                val start = i
                val type = lines[i].type
                while (i < lines.size && lines[i].type == type) i++
                blocks.add(ChangeBlock(start, i, type))
            } else {
                i++
            }
        }
        changeBlocks = blocks
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (totalLines == 0 || height == 0) return
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (block in changeBlocks) {
            val y1 = (block.startLine.toLong() * height / totalLines).toInt()
            val y2 = (block.endLine.toLong() * height / totalLines).toInt()
            g2.color =
                when (block.type) {
                    DiffLineType.ADDED -> DiffColors.overviewAdded
                    DiffLineType.REMOVED -> DiffColors.overviewRemoved
                    DiffLineType.CONTEXT -> java.awt.Color(0, 0, 0, 0)
                }
            g2.fillRect(2, y1, width - 4, (y2 - y1).coerceAtLeast(2))
        }

        val viewRect = scrollPane.viewport.viewRect
        val contentHeight = scrollPane.viewport.view?.height ?: 0
        if (contentHeight > 0) {
            val vpStart = (viewRect.y.toLong() * height / contentHeight).toInt()
            val vpEnd = ((viewRect.y + viewRect.height).toLong() * height / contentHeight).toInt()
            g2.color = DiffColors.overviewViewport
            g2.drawRect(0, vpStart, width - 1, (vpEnd - vpStart).coerceAtLeast(4))
        }
    }

    private fun jumpToY(y: Int) {
        val contentHeight = scrollPane.viewport.view?.height ?: return
        if (height == 0 || contentHeight == 0) return
        val targetScrollY = (y.toLong() * contentHeight / height).toInt()
        val bar = scrollPane.verticalScrollBar
        bar.value = targetScrollY.coerceIn(0, bar.maximum - bar.visibleAmount)
    }

    companion object {
        private const val OVERVIEW_WIDTH = 18
    }
}
