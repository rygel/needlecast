package io.github.rygel.needlecast.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.text.DefaultHighlighter

class ConsolePanel : JPanel(BorderLayout()) {

    private val textArea = JTextArea().apply {
        isEditable = false
        font = Font(monoFont(), Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = false
    }

    private fun buildOutputContextMenu(): javax.swing.JPopupMenu {
        val area = textArea
        return javax.swing.JPopupMenu().apply {
            add(javax.swing.JMenuItem("Copy").apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_C, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
                addActionListener { area.copy() }
            })
            add(javax.swing.JMenuItem("Select All").apply {
                accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_A, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
                addActionListener { area.selectAll() }
            })
            addSeparator()
            add(javax.swing.JMenuItem("Clear").apply {
                addActionListener { clear() }
            })
        }
    }

    private val searchBar = ConsoleSearchBar(textArea)

    init {
        minimumSize = java.awt.Dimension(0, 0)
        textArea.componentPopupMenu = buildOutputContextMenu()

        val header = JLabel("Output").apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            font = font.deriveFont(Font.BOLD)
        }
        val scrollPane = JScrollPane(textArea).apply { minimumSize = java.awt.Dimension(0, 0) }
        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        searchBar.isVisible = false
        add(searchBar, BorderLayout.SOUTH)

        // Ctrl+F opens the search bar
        val ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        textArea.inputMap.put(ctrlF, "show-search")
        textArea.actionMap.put("show-search", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) = searchBar.showBar()
        })
    }

    fun appendLine(text: String) {
        SwingUtilities.invokeLater {
            textArea.append("$text\n")
            textArea.caretPosition = textArea.document.length
        }
    }

    fun clear() {
        SwingUtilities.invokeLater {
            textArea.text = ""
            searchBar.clearHighlights()
        }
    }

    private fun monoFont(): String {
        val available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        return listOf("JetBrains Mono", "Cascadia Code", "Consolas", "Courier New").firstOrNull { it in available }
            ?: Font.MONOSPACED
    }
}

private class ConsoleSearchBar(private val textArea: JTextArea) : JPanel(BorderLayout()) {

    private val searchField = JTextField(20)
    private val statusLabel = JLabel(" ")
    private val matchPainter = DefaultHighlighter.DefaultHighlightPainter(Color(0xFFD700).also { })
    private val currentPainter = DefaultHighlighter.DefaultHighlightPainter(Color(0xFF8C00))

    /** Offsets of all current matches: Pair(start, end) */
    private var matches: List<Pair<Int, Int>> = emptyList()
    private var currentMatch = -1

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(3, 6, 3, 6),
        )

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Find:"))
            add(searchField)
            add(JButton("\u25B2").apply { toolTipText = "Previous (Shift+Enter)"; addActionListener { step(-1) } })
            add(JButton("\u25BC").apply { toolTipText = "Next (Enter)";           addActionListener { step(+1) } })
            add(statusLabel)
            add(JButton("\u2715").apply { toolTipText = "Close (Escape)"; addActionListener { hideBar() } })
        }
        add(row, BorderLayout.CENTER)

        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == VK_ESCAPE              -> hideBar()
                    e.keyCode == VK_ENTER && e.isShiftDown -> step(-1)
                    e.keyCode == VK_ENTER               -> step(+1)
                }
            }
        })
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = rebuildMatches()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = rebuildMatches()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
        })
    }

    fun showBar() {
        isVisible = true
        searchField.requestFocusInWindow()
        searchField.selectAll()
        rebuildMatches()
    }

    fun hideBar() {
        isVisible = false
        clearHighlights()
        textArea.requestFocusInWindow()
    }

    fun clearHighlights() {
        textArea.highlighter.removeAllHighlights()
        matches = emptyList()
        currentMatch = -1
        statusLabel.text = " "
    }

    private fun rebuildMatches() {
        textArea.highlighter.removeAllHighlights()
        val query = searchField.text
        if (query.isEmpty()) {
            matches = emptyList()
            currentMatch = -1
            statusLabel.text = " "
            return
        }
        val text = textArea.text.lowercase()
        val q = query.lowercase()
        val found = mutableListOf<Pair<Int, Int>>()
        var idx = 0
        while (true) {
            val pos = text.indexOf(q, idx)
            if (pos < 0) break
            found += pos to (pos + q.length)
            textArea.highlighter.addHighlight(pos, pos + q.length, matchPainter)
            idx = pos + 1
        }
        matches = found
        currentMatch = -1
        if (found.isEmpty()) {
            statusLabel.foreground = Color(0xF44336)
            statusLabel.text = "Not found"
        } else {
            statusLabel.foreground = Color(0x4CAF50)
            statusLabel.text = "${found.size} match${if (found.size == 1) "" else "es"}"
            step(+1)
        }
    }

    private fun step(direction: Int) {
        if (matches.isEmpty()) return
        currentMatch = Math.floorMod(currentMatch + direction, matches.size)
        // Re-apply all highlights, then overlay the current match
        textArea.highlighter.removeAllHighlights()
        val q = searchField.text.lowercase()
        matches.forEachIndexed { i, (s, e) ->
            val painter = if (i == currentMatch) currentPainter else matchPainter
            textArea.highlighter.addHighlight(s, e, painter)
        }
        val (start, end) = matches[currentMatch]
        textArea.caretPosition = end
        textArea.scrollRectToVisible(textArea.modelToView2D(start).bounds)
        statusLabel.foreground = Color(0x4CAF50)
        statusLabel.text = "${currentMatch + 1} / ${matches.size}"
    }
}
