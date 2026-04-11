package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.DocCategory
import io.github.rygel.needlecast.model.DocTarget
import io.github.rygel.needlecast.service.DocRegistry
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * Dockable panel that discovers generated documentation for the active project
 * and opens it in the system browser.
 *
 * Entries are grouped by [DocCategory]. Available doc sets (output files exist
 * on disk) are shown normally; unavailable ones are greyed out with a tooltip
 * showing the command needed to generate them.
 *
 * Double-click or "Open in Browser" launches [Desktop.browse].
 */
class DocViewerPanel : JPanel(BorderLayout()) {

    // ── Row model ─────────────────────────────────────────────────────────────

    private sealed class DocRow {
        data class Header(val category: DocCategory) : DocRow()
        data class Entry(val target: DocTarget, val available: Boolean) : DocRow()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private val listModel     = DefaultListModel<DocRow>()
    private val list          = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(DocRowRenderer())
    }
    private val openButton    = JButton("Open in Browser").apply { isEnabled = false }
    private val refreshButton = JButton("\u21BB  Refresh")    // ↻

    private var currentProject: DetectedProject? = null

    // ─────────────────────────────────────────────────────────────────────────

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            isOpaque = false
            add(refreshButton)
        }
        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            isOpaque = false
            add(openButton)
        }

        add(toolbar,           BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)
        add(buttonBar,         BorderLayout.SOUTH)

        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = list.selectedValue
                openButton.isEnabled = row is DocRow.Entry && row.available
            }
        }

        openButton.addActionListener    { openSelected() }
        refreshButton.addActionListener { reload() }

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) openSelected()
            }
        })
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadProject(project: DetectedProject?) {
        currentProject = project
        reload()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun reload() {
        listModel.clear()
        openButton.isEnabled = false

        val project = currentProject ?: return
        val projectDir = File(project.directory.path)
        val targets = DocRegistry.targetsFor(project.buildTools)

        for (category in DocCategory.entries) {
            val inCategory = targets.filter { it.category == category }
            if (inCategory.isEmpty()) continue

            val rows = inCategory
                .map { target -> DocRow.Entry(target, File(projectDir, target.relativePath).exists()) }
                .sortedWith(compareBy({ !it.available }, { it.target.label }))

            listModel.addElement(DocRow.Header(category))
            rows.forEach { listModel.addElement(it) }
        }
    }

    private fun openSelected() {
        val row = list.selectedValue as? DocRow.Entry ?: return
        if (!row.available) return
        val file = File(currentProject?.directory?.path ?: return, row.target.relativePath)
        try {
            Desktop.getDesktop().browse(file.toURI())
        } catch (ex: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Could not open browser:\n${ex.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private inner class DocRowRenderer : ListCellRenderer<DocRow> {

        private val headerLabel = JLabel().apply {
            border = BorderFactory.createEmptyBorder(6, 6, 2, 6)
            font = font.deriveFont(Font.BOLD, 11f)
            isOpaque = true
        }

        private val entryPanel = JPanel(BorderLayout(6, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 18, 2, 6)
            isOpaque = true
        }
        private val entryLabel = JLabel()
        private val hintLabel  = JLabel().apply {
            font = font.deriveFont(Font.ITALIC, 10f)
            foreground = Color.GRAY
        }

        init {
            entryPanel.add(entryLabel, BorderLayout.CENTER)
            entryPanel.add(hintLabel,  BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out DocRow>, value: DocRow?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            return when (val row = value) {
                is DocRow.Header -> {
                    headerLabel.text       = row.category.displayName
                    headerLabel.background = list.background
                    headerLabel.foreground = list.foreground
                    headerLabel
                }
                is DocRow.Entry -> {
                    val symbol = if (row.available) "\u25CF" else "\u25CB"  // ● or ○
                    entryLabel.text       = "$symbol  ${row.target.label}"
                    entryLabel.foreground = if (row.available) {
                        if (isSelected) list.selectionForeground else list.foreground
                    } else {
                        Color.GRAY
                    }
                    hintLabel.text        = if (row.available) "" else row.target.hint
                    val bg = if (isSelected && row.available) list.selectionBackground else list.background
                    entryPanel.background = bg
                    entryLabel.background = bg
                    hintLabel.background  = bg
                    entryPanel.toolTipText = if (row.available) null else "Generate with: ${row.target.hint}"
                    entryPanel
                }
                null -> JLabel()
            }
        }
    }
}
