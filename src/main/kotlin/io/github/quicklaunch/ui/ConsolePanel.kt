package io.github.quicklaunch.ui

import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class ConsolePanel : JPanel(BorderLayout()) {

    private val textArea = JTextArea().apply {
        isEditable = false
        font = Font(monoFont(), Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = false
    }

    init {
        val header = JLabel("Output").apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            font = font.deriveFont(Font.BOLD)
        }
        val scrollPane = JScrollPane(textArea)
        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
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
        }
    }

    private fun monoFont(): String {
        val available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        return listOf("JetBrains Mono", "Cascadia Code", "Consolas", "Courier New").firstOrNull { it in available }
            ?: Font.MONOSPACED
    }
}
