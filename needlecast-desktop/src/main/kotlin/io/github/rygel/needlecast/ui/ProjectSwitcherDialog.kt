package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * Ctrl+P style floating dialog that lets the user fuzzy-search across all projects in the tree.
 * [onSelect] is invoked with (ignored, projectPath) when the user confirms a selection.
 */
class ProjectSwitcherDialog(
    owner: Window,
    private val ctx: AppContext,
    private val onSelect: (groupId: String, path: String) -> Unit,
) : JDialog(owner, ModalityType.APPLICATION_MODAL) {

    data class Entry(val dir: ProjectDirectory, val folderPath: String) {
        val label: String    get() = dir.label()
        val subtitle: String get() = if (folderPath.isEmpty()) dir.path else "$folderPath  •  ${dir.path}"
    }

    private val allEntries: List<Entry> = collectProjects(loadProjectTree(), "")

    private val listModel  = DefaultListModel<Entry>()
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
                override fun insertUpdate(e: javax.swing.event.DocumentEvent)  = updateFilter()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent)  = updateFilter()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
            })
        }

        resultList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 2) confirmSelection() }
        })

        val scroll = JScrollPane(resultList).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(560, 300)
        }

        panel.add(searchField, BorderLayout.NORTH)
        panel.add(scroll,      BorderLayout.CENTER)
        contentPane = panel

        bindKeys()
        pack()
        setLocationRelativeTo(owner)
        updateFilter()
        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                searchField.requestFocusInWindow()
            }
        })
    }

    private fun loadProjectTree(): List<ProjectTreeEntry> {
        val cfg = ctx.config
        if (cfg.projectTree.isNotEmpty()) return cfg.projectTree
        if (cfg.groups.isEmpty()) return emptyList()
        val migrated = cfg.groups.map { group ->
            ProjectTreeEntry.Folder(
                id = group.id,
                name = group.name,
                color = group.color,
                children = group.directories.map { dir -> ProjectTreeEntry.Project(directory = dir) },
            )
        }
        ctx.updateConfig(cfg.copy(projectTree = migrated))
        return migrated
    }

    private fun updateFilter() {
        listModel.clear()
        filterEntries(allEntries, searchField.text).forEach { listModel.addElement(it) }
        if (listModel.size > 0) resultList.selectedIndex = 0
    }

    private fun bindKeys() {
        val input = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val actions = rootPane.actionMap

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close")
        actions.put("close", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = dispose()
        })

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirm")
        actions.put("confirm", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = confirmSelection()
        })

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down")
        actions.put("down", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (listModel.size == 0) return
                val next = minOf(resultList.selectedIndex + 1, listModel.size - 1)
                resultList.selectedIndex = next
                resultList.ensureIndexIsVisible(next)
            }
        })

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up")
        actions.put("up", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (listModel.size == 0) return
                val prev = maxOf(resultList.selectedIndex - 1, 0)
                resultList.selectedIndex = prev
                resultList.ensureIndexIsVisible(prev)
            }
        })
    }

    private fun confirmSelection() {
        val entry = resultList.selectedValue ?: return
        dispose()
        onSelect("", entry.dir.path)
    }

    private inner class EntryCellRenderer : ListCellRenderer<Entry> {
        private val panel     = JPanel(BorderLayout(0, 2)).apply { border = BorderFactory.createEmptyBorder(6, 8, 6, 8) }
        private val nameLabel = JLabel().apply { font = font.deriveFont(Font.BOLD, 13f) }
        private val subLabel  = JLabel().apply { font = font.deriveFont(Font.PLAIN, 10f) }

        init { panel.add(nameLabel, BorderLayout.NORTH); panel.add(subLabel, BorderLayout.SOUTH) }

        override fun getListCellRendererComponent(
            list: JList<out Entry>, value: Entry?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
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

/** Recursively collect all project entries from the tree, tracking folder path breadcrumb. */
private fun collectProjects(entries: List<ProjectTreeEntry>, folderPath: String): List<ProjectSwitcherDialog.Entry> =
    entries.flatMap { entry ->
        when (entry) {
            is ProjectTreeEntry.Project -> listOf(ProjectSwitcherDialog.Entry(entry.directory, folderPath))
            is ProjectTreeEntry.Folder  -> {
                val path = if (folderPath.isEmpty()) entry.name else "$folderPath / ${entry.name}"
                collectProjects(entry.children, path)
            }
        }
    }

/** Pure filter used by [ProjectSwitcherDialog] and its tests. */
internal fun filterEntries(
    entries: List<ProjectSwitcherDialog.Entry>,
    query: String,
): List<ProjectSwitcherDialog.Entry> {
    val q = query.trim().lowercase()
    return if (q.isEmpty()) entries
           else entries.filter { it.label.lowercase().contains(q) || it.dir.path.lowercase().contains(q) }
}
