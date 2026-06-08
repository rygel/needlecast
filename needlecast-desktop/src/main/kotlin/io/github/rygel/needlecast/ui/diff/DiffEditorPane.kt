package io.github.rygel.needlecast.ui.diff

import java.awt.Font
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.MutableAttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class DiffEditorPane(
    val side: Side,
) : JTextPane() {
    enum class Side { OLD, NEW, UNIFIED }

    private val lineTypes = mutableListOf<DiffLineType>()

    val lineCount: Int get() = lineTypes.size

    init {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        margin = Insets(0, 0, 0, 0)
    }

    fun renderLines(lines: List<DiffLine>) {
        val doc = styledDocument
        doc.remove(0, doc.length)
        lineTypes.clear()

        val attrs = SimpleAttributeSet()
        StyleConstants.setFontFamily(attrs, Font.MONOSPACED)
        StyleConstants.setFontSize(attrs, 12)

        for ((index, line) in lines.withIndex()) {
            lineTypes.add(line.type)

            val lineAttrs = SimpleAttributeSet()
            StyleConstants.setFontFamily(lineAttrs, Font.MONOSPACED)
            StyleConstants.setFontSize(lineAttrs, 12)

            when (line.type) {
                DiffLineType.ADDED -> {
                    StyleConstants.setBackground(lineAttrs, DiffColors.addedBackground)
                    StyleConstants.setForeground(lineAttrs, DiffColors.addedForeground)
                }
                DiffLineType.REMOVED -> {
                    StyleConstants.setBackground(lineAttrs, DiffColors.removedBackground)
                    StyleConstants.setForeground(lineAttrs, DiffColors.removedForeground)
                }
                DiffLineType.CONTEXT -> {
                    StyleConstants.setForeground(lineAttrs, DiffColors.contextForeground)
                }
            }

            if (line.wordDiffs.isNotEmpty() && line.content.isNotEmpty()) {
                appendWithWordDiffs(doc, line, lineAttrs)
            } else {
                try {
                    doc.insertString(doc.length, line.content, lineAttrs)
                } catch (_: BadLocationException) {
                }
            }

            if (index < lines.size - 1) {
                try {
                    doc.insertString(doc.length, "\n", attrs)
                } catch (_: BadLocationException) {
                }
            }
        }
    }

    private fun appendWithWordDiffs(
        doc: StyledDocument,
        line: DiffLine,
        baseAttrs: MutableAttributeSet,
    ) {
        val content = line.content
        val isAdded = line.type == DiffLineType.ADDED
        val inlineColor = if (isAdded) DiffColors.addedInline else DiffColors.removedInline

        val diffTexts = line.wordDiffs.map { it.text }
        var pos = 0
        var diffIdx = 0

        val sb = StringBuilder()
        while (pos < content.length && diffIdx < diffTexts.size) {
            val nextDiff = content.indexOf(diffTexts[diffIdx], pos)
            if (nextDiff < 0) {
                sb.append(content.substring(pos))
                pos = content.length
                break
            }
            if (nextDiff > pos) {
                sb.append(content.substring(pos, nextDiff))
            }
            try {
                doc.insertString(doc.length, sb.toString(), baseAttrs)
            } catch (_: BadLocationException) {
            }
            sb.clear()

            val diffAttrs = SimpleAttributeSet(baseAttrs)
            StyleConstants.setBackground(diffAttrs, inlineColor)

            try {
                doc.insertString(doc.length, diffTexts[diffIdx], diffAttrs)
            } catch (_: BadLocationException) {
            }

            pos = nextDiff + diffTexts[diffIdx].length
            diffIdx++
        }

        if (pos < content.length) {
            sb.append(content.substring(pos))
        }
        if (sb.isNotEmpty()) {
            try {
                doc.insertString(doc.length, sb.toString(), baseAttrs)
            } catch (_: BadLocationException) {
            }
        }
    }

    fun getLineBounds(lineIndex: Int): Rectangle? {
        if (lineIndex < 0 || lineIndex >= lineTypes.size) return null
        return try {
            val root = styledDocument.defaultRootElement
            if (lineIndex >= root.elementCount) return null
            val elem = root.getElement(lineIndex)
            modelToView(elem.startOffset)
        } catch (_: Exception) {
            null
        }
    }

    fun getLineTypeAt(lineIndex: Int): DiffLineType? = lineTypes.getOrNull(lineIndex)

    fun scrollToLine(lineIndex: Int) {
        val bounds = getLineBounds(lineIndex) ?: return
        scrollRectToVisible(bounds)
    }
}
