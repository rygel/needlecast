package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.ProjectDirectory
import io.github.quicklaunch.model.ProjectGroup
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * Ctrl+P style floating dialog that lets the user fuzzy-search across all projects in all groups.
 * [onSelect] is invoked with (groupId, projectPath) when the user confirms a selection.
 */
class ProjectSwitcherDialog(
    owner: Window,
    private val ctx: AppContext,
    private val onSelect: (groupId: String, path: String) -> Unit,
) : JDialog(owner, ModalityType.APPLICATION_MODAL) {

    private data class Entry(val group: ProjectGroup, val dir: ProjectDirectory) {
        val label: String get() = dir.label()
        val subtitle: String get() = "${group.name}  •  ${dir.path}"
    }

    private val allEntries: List<Entry> = ctx.config.groups.flatMap { g ->
        g.directories.map { Entry(g, it) }
    }

    private val listModel = DefaultListModel<Entry>()
    private val resultList = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(EntryCellRenderer())
        fixedCellHeight = 44
    }
    private val searchField = JTextField()

    init {
        title = "Go to Project"
        isUndecorated = true
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val panel = JPanel(BorderLayout(0, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(0x444444), 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6),
            )
        }

        searchField.apply {
            font = font.deriveFont(14f)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(0x555555)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6),
            )
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ESCAPE -> dispose()
                        KeyEvent.VK_ENTER  -> confirmSelection()
                        KeyEvent.VK_DOWN   -> {
                            if (listModel.size > 0) {
                                val next = minOf((resultList.selectedIndex + 1), listModel.size - 1)
                                resultList.selectedIndex = next
                                resultList.ensureIndexIsVisible(next)
                            }
                            e.consume()
                        }
                        KeyEvent.VK_UP     -> {
                            if (listModel.size > 0) {
                                val prev = maxOf((resultList.selectedIndex - 1), 0)
                                resultList.selectedIndex = prev
                                resultList.ensureIndexIsVisible(prev)
                            }
                            e.consume()
                        }
                    }
                }
            })
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = updateFilter()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = updateFilter()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
            })
        }

        resultList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) confirmSelection()
            }
        })

        val scroll = JScrollPane(resultList).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(560, 300)
        }

        panel.add(searchField, BorderLayout.NORTH)
        panel.add(scroll, BorderLayout.CENTER)
        contentPane = panel

        pack()
        setLocationRelativeTo(owner)

        // populate with all entries initially
        updateFilter()
    }

    private fun updateFilter() {
        val query = searchField.text.trim().lowercase()
        listModel.clear()
        allEntries
            .filter { query.isEmpty() || it.label.lowercase().contains(query) || it.dir.path.lowercase().contains(query) }
            .forEach { listModel.addElement(it) }
        if (listModel.size > 0) resultList.selectedIndex = 0
    }

    private fun confirmSelection() {
        val entry = resultList.selectedValue ?: return
        dispose()
        SwingUtilities.invokeLater { onSelect(entry.group.id, entry.dir.path) }
    }

    private inner class EntryCellRenderer : ListCellRenderer<Entry> {
        private val panel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
        }
        private val nameLabel = JLabel().apply { font = font.deriveFont(Font.BOLD, 13f) }
        private val subLabel  = JLabel().apply { font = font.deriveFont(Font.PLAIN, 10f) }

        init {
            panel.add(nameLabel, BorderLayout.NORTH)
            panel.add(subLabel,  BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out Entry>, value: Entry?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            nameLabel.text = value?.label ?: ""
            subLabel.text  = value?.subtitle ?: ""
            val bg = if (isSelected) list.selectionBackground else list.background
            panel.background = bg
            nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            subLabel.foreground  = if (isSelected) list.selectionForeground.darker() else java.awt.Color(0x888888)
            panel.isOpaque = true
            return panel
        }
    }
}
