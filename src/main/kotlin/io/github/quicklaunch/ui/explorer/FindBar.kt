package io.github.quicklaunch.ui.explorer

import org.fife.ui.rtextarea.SearchContext
import org.fife.ui.rtextarea.SearchEngine
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Inline find/replace bar that sits at the bottom of the editor.
 * Ctrl+F shows find mode; Ctrl+H shows find+replace mode. Escape hides it.
 */
class FindBar(private val editor: RSyntaxTextArea) : JPanel(BorderLayout()) {

    private val searchField   = JTextField(18)
    private val replaceField  = JTextField(18)
    private val matchCase     = JCheckBox("Aa")
    private val wholeWord     = JCheckBox("\\b")
    private val regex         = JCheckBox(".*")
    private val statusLabel   = JLabel(" ")
    private val replaceRow    = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY),
            BorderFactory.createEmptyBorder(3, 6, 3, 6),
        )

        val findRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Find:"))
            add(searchField)
            add(JButton("\u25B2").apply { toolTipText = "Previous (Shift+Enter)"; addActionListener { find(forward = false) } })
            add(JButton("\u25BC").apply { toolTipText = "Next (Enter)";           addActionListener { find(forward = true) } })
            add(matchCase)
            add(wholeWord)
            add(regex)
            add(statusLabel)
            add(JButton("\u2715").apply { toolTipText = "Close (Escape)"; addActionListener { hideBar() } })
        }

        replaceRow.apply {
            add(JLabel("Replace:"))
            add(replaceField)
            add(JButton("Replace").apply     { addActionListener { replaceNext() } })
            add(JButton("Replace All").apply { addActionListener { replaceAll()  } })
        }

        val center = JPanel(BorderLayout(0, 2)).apply {
            add(findRow,    BorderLayout.NORTH)
            add(replaceRow, BorderLayout.SOUTH)
        }
        add(center, BorderLayout.CENTER)

        // Enter / Shift+Enter to step through matches
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown -> find(forward = false)
                    e.keyCode == KeyEvent.VK_ENTER                  -> find(forward = true)
                    e.keyCode == KeyEvent.VK_ESCAPE                 -> hideBar()
                }
            }
        })
        replaceField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) hideBar()
            }
        })

        matchCase.toolTipText = "Match case"
        wholeWord.toolTipText = "Whole word"
        regex.toolTipText     = "Regular expression"
        regex.addActionListener { wholeWord.isEnabled = !regex.isSelected }
    }

    fun showBar(replaceMode: Boolean) {
        replaceRow.isVisible = replaceMode
        isVisible = true
        val selected = editor.selectedText
        if (!selected.isNullOrEmpty()) searchField.text = selected
        searchField.requestFocusInWindow()
        searchField.selectAll()
    }

    fun hideBar() {
        isVisible = false
        editor.requestFocusInWindow()
        SearchEngine.markAll(editor, SearchContext(""))  // clear highlights
        statusLabel.text = " "
    }

    private fun context(forward: Boolean) = SearchContext(searchField.text).apply {
        matchCase           = this@FindBar.matchCase.isSelected
        wholeWord           = this@FindBar.wholeWord.isSelected && !regex.isSelected
        isRegularExpression = regex.isSelected
        searchForward       = forward
        markAll             = true
    }

    private fun find(forward: Boolean) {
        val text = searchField.text
        if (text.isEmpty()) { statusLabel.text = " "; return }
        try {
            val result = SearchEngine.find(editor, context(forward))
            if (result.wasFound()) {
                statusLabel.foreground = java.awt.Color(0x4CAF50)
                statusLabel.text = " "
            } else {
                statusLabel.foreground = java.awt.Color(0xF44336)
                statusLabel.text = "Not found"
            }
        } catch (_: Exception) {
            statusLabel.foreground = java.awt.Color(0xF44336)
            statusLabel.text = "Bad pattern"
        }
    }

    private fun replaceNext() {
        val text = searchField.text
        if (text.isEmpty()) return
        try {
            val ctx = context(forward = true).apply { replaceWith = replaceField.text }
            val result = SearchEngine.replace(editor, ctx)
            if (result.wasFound()) {
                statusLabel.foreground = java.awt.Color(0x4CAF50)
                statusLabel.text = "Replaced"
            } else {
                statusLabel.foreground = java.awt.Color(0xF44336)
                statusLabel.text = "Not found"
            }
        } catch (_: Exception) {
            statusLabel.foreground = java.awt.Color(0xF44336)
            statusLabel.text = "Bad pattern"
        }
    }

    private fun replaceAll() {
        val text = searchField.text
        if (text.isEmpty()) return
        try {
            val ctx = context(forward = true).apply { replaceWith = replaceField.text }
            val result = SearchEngine.replaceAll(editor, ctx)
            statusLabel.foreground = java.awt.Color(0x4CAF50)
            statusLabel.text = "${result.count} replacement(s)"
        } catch (_: Exception) {
            statusLabel.foreground = java.awt.Color(0xF44336)
            statusLabel.text = "Bad pattern"
        }
    }
}
