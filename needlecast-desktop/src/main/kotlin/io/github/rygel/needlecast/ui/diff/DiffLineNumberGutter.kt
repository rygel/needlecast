package io.github.rygel.needlecast.ui.diff

import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max

class DiffLineNumberGutter(
    private val textPane: DiffEditorPane,
    private val scrollPane: JScrollPane,
) : JComponent() {
    data class LineInfo(
        val number: Int?,
        val type: DiffLineType,
    )

    private var lineInfos = listOf<LineInfo>()
    private var maxDigits = 4

    private val gutterWidth get() = maxDigits * charWidth + PADDING * 2
    private val charWidth: Int get() = textPane.getFontMetrics(textPane.font).charWidth('0')

    private val docListener =
        object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateFromDocument()

            override fun removeUpdate(e: DocumentEvent?) = updateFromDocument()

            override fun changedUpdate(e: DocumentEvent?) = updateFromDocument()
        }

    private val viewportListener = ChangeListener { repaint() }

    init {
        isOpaque = false
        textPane.document.addDocumentListener(docListener)
        scrollPane.viewport.addChangeListener(viewportListener)
    }

    fun setLineInfos(infos: List<LineInfo>) {
        lineInfos = infos
        maxDigits = max(4, infos.mapNotNull { it.number }.maxOfOrNull { it.toString().length } ?: 4)
        revalidate()
        repaint()
    }

    override fun getPreferredSize() = java.awt.Dimension(gutterWidth, textPane.preferredSize.height.toInt())

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val font = Font(Font.MONOSPACED, Font.PLAIN, textPane.font.size)
        g2.font = font
        val fm = g2.getFontMetrics(font)
        val viewRect = scrollPane.viewport.viewRect
        val yOffset = -viewRect.y

        val root = textPane.styledDocument.defaultRootElement
        for (i in lineInfos.indices) {
            if (i >= root.elementCount) break
            val elem = root.getElement(i)
            val y =
                yOffset + (
                    try {
                        textPane.modelToView(elem.startOffset).y.toInt()
                    } catch (_: Exception) {
                        continue
                    }
                )

            if (y + fm.height < 0 || y > height) continue

            val info = lineInfos[i]
            if (info.number != null) {
                g2.color = DiffColors.lineNumberColor
                val text = info.number.toString()
                val textX = width - PADDING - fm.stringWidth(text)
                g2.drawString(text, textX, y + fm.ascent)
            }
        }
    }

    private fun updateFromDocument() {
        SwingUtilities.invokeLater {
            revalidate()
            repaint()
        }
    }

    companion object {
        private const val PADDING = 6
    }
}
