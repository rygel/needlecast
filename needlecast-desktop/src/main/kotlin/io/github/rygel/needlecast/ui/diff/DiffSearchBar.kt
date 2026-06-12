package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import org.slf4j.LoggerFactory
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.text.DefaultHighlighter

class DiffSearchBar : JPanel(BorderLayout()) {
    private val logger = LoggerFactory.getLogger(DiffSearchBar::class.java)
    private val searchField =
        JTextField().apply {
            preferredSize = Dimension(200, 28)
            maximumSize = Dimension(400, 28)
        }
    private val countLabel = JLabel("")
    private val prevButton =
        JButton("\u25C0").apply {
            toolTipText = "Previous match"
            isFocusable = false
        }
    private val nextButton =
        JButton("\u25B6").apply {
            toolTipText = "Next match"
            isFocusable = false
        }
    private val closeButton =
        JButton("\u2715").apply {
            toolTipText = "Close (Escape)"
            isFocusable = false
        }

    private var targetPanes: List<DiffEditorPane> = emptyList()
    private var highlights = mutableListOf<List<Pair<Int, Int>>>()
    private var currentMatchIndex = -1
    private var totalMatches = 0

    var onClose: (() -> Unit)? = null

    init {
        border =
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, java.awt.Color(0x3C, 0x3C, 0x3C)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8),
            )

        val leftPanel =
            JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(JLabel("Find:"))
                add(searchField)
                add(prevButton)
                add(nextButton)
                add(countLabel)
            }
        val rightPanel =
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                add(closeButton)
            }

        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)

        searchField.addKeyListener(
            object : KeyAdapter() {
                override fun keyReleased(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) nextMatch()
                    if (e.keyCode == KeyEvent.VK_ESCAPE) {
                        onClose?.invoke()
                        return
                    }
                    performSearch()
                }
            },
        )

        nextButton.addActionListener { nextMatch() }
        prevButton.addActionListener { prevMatch() }
        closeButton.addActionListener { onClose?.invoke() }
    }

    fun setTargetPanes(panes: List<DiffEditorPane>) {
        targetPanes = panes
    }

    fun activate() {
        isVisible = true
        searchField.text = ""
        countLabel.text = ""
        currentMatchIndex = -1
        totalMatches = 0
        searchField.requestFocusInWindow()
    }

    fun deactivate() {
        clearHighlights()
        isVisible = false
    }

    private fun performSearch() {
        clearHighlights()
        val query = searchField.text
        if (query.isEmpty()) {
            countLabel.text = ""
            return
        }

        val painter = DefaultHighlighter.DefaultHighlightPainter(DiffColors.searchHighlight)
        highlights.clear()
        totalMatches = 0

        for (pane in targetPanes) {
            val paneHighlights = mutableListOf<Pair<Int, Int>>()
            val doc = pane.styledDocument
            val text = doc.getText(0, doc.length)
            var pos = 0
            while (pos < text.length) {
                val idx = text.indexOf(query, pos, ignoreCase = false)
                if (idx < 0) break
                try {
                    pane.highlighter.addHighlight(idx, idx + query.length, painter)
                    paneHighlights.add(idx to (idx + query.length))
                    totalMatches++
                } catch (e: Exception) {
                    logger.warn("Failed to add search highlight", e)
                }
                pos = idx + 1
            }
            highlights.add(paneHighlights)
        }

        if (totalMatches > 0) {
            currentMatchIndex = 0
            countLabel.text = "1 of $totalMatches"
            updateTargetPaneTooltips()
        } else {
            currentMatchIndex = -1
            countLabel.text = "No matches"
            clearTargetPaneTooltips()
        }
    }

    private fun nextMatch() {
        if (totalMatches == 0) return
        currentMatchIndex = (currentMatchIndex + 1) % totalMatches
        countLabel.text = "${currentMatchIndex + 1} of $totalMatches"
        scrollToMatch(currentMatchIndex)
        updateTargetPaneTooltips()
    }

    private fun prevMatch() {
        if (totalMatches == 0) return
        currentMatchIndex = if (currentMatchIndex <= 0) totalMatches - 1 else currentMatchIndex - 1
        countLabel.text = "${currentMatchIndex + 1} of $totalMatches"
        scrollToMatch(currentMatchIndex)
        updateTargetPaneTooltips()
    }

    private fun scrollToMatch(matchIndex: Int) {
        var offset = 0
        for (paneIdx in targetPanes.indices) {
            val paneHighlights = highlights.getOrNull(paneIdx) ?: continue
            if (matchIndex < offset + paneHighlights.size) {
                val (start, end) = paneHighlights[matchIndex - offset]
                targetPanes[paneIdx].caretPosition = start
                try {
                    val rect = targetPanes[paneIdx].modelToView(start)
                    if (rect != null) targetPanes[paneIdx].scrollRectToVisible(rect)
                } catch (e: Exception) {
                    logger.warn("Failed to scroll to search match", e)
                }
                return
            }
            offset += paneHighlights.size
        }
    }

    private fun clearHighlights() {
        for (pane in targetPanes) {
            pane.highlighter.removeAllHighlights()
        }
        highlights.clear()
        clearTargetPaneTooltips()
    }

    private fun updateTargetPaneTooltips() {
        for (pane in targetPanes) {
            pane.toolTipText = "Search match ${currentMatchIndex + 1} of $totalMatches"
        }
    }

    private fun clearTargetPaneTooltips() {
        for (pane in targetPanes) {
            pane.toolTipText = null
        }
    }
}
