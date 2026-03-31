package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.ProjectGroup
import java.awt.BorderLayout
import java.awt.Font
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToolBar
import javax.swing.ListSelectionModel

class GroupPanel(
    private val ctx: AppContext,
    private val onGroupSelected: (ProjectGroup?) -> Unit,
    private val onDirectoryDropped: (DirectoryTransfer, ProjectGroup) -> Unit = { _, _ -> },
) : JPanel(BorderLayout()) {

    private val model = DefaultListModel<ProjectGroup>()
    private val list = JList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer { _, value, _, isSelected, cellHasFocus ->
            val label = JLabel(if (value != null) "${value.name}  (${value.directories.size})" else "")
            label.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            if (isSelected) {
                label.isOpaque = true
                label.background = javax.swing.UIManager.getColor("List.selectionBackground")
                label.foreground = javax.swing.UIManager.getColor("List.selectionForeground")
            }
            label
        }
    }

    init {
        val header = JLabel("Groups").apply {
            border = BorderFactory.createEmptyBorder(4, 6, 2, 6)
            font = font.deriveFont(Font.BOLD)
        }

        val addButton = JButton("+").apply {
            toolTipText = "Add group"
            isFocusPainted = false
        }
        val removeButton = JButton("-").apply {
            toolTipText = "Remove group"
            isFocusPainted = false
        }

        val toolbar = JToolBar().apply {
            isFloatable = false
            add(addButton)
            add(removeButton)
        }

        val topPanel = JPanel(BorderLayout()).apply {
            add(header, BorderLayout.WEST)
            add(toolbar, BorderLayout.EAST)
        }

        // Set up drop handler
        list.transferHandler = GroupDropHandler(
            getGroup = { list.selectedValue },
            onDrop = { transfer, targetGroup -> onDirectoryDropped(transfer, targetGroup) },
        )

        list.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                onGroupSelected(list.selectedValue)
            }
        }

        add(topPanel, BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)

        // Populate from config
        ctx.config.groups.forEach { model.addElement(it) }

        // Restore last selected group
        val lastId = ctx.config.lastSelectedGroupId
        if (lastId != null) {
            val idx = (0 until model.size).firstOrNull { model.getElementAt(it).id == lastId } ?: -1
            if (idx >= 0) list.selectedIndex = idx
        } else if (model.size > 0) {
            list.selectedIndex = 0
        }

        addButton.addActionListener { addGroup() }
        removeButton.addActionListener { removeGroup() }
    }

    fun selectedGroupId(): String? = list.selectedValue?.id

    fun selectGroup(id: String) {
        val idx = (0 until model.size).firstOrNull { model.getElementAt(it).id == id } ?: return
        list.selectedIndex = idx
    }

    fun refreshGroups(groups: List<ProjectGroup>) {
        val selectedId = list.selectedValue?.id
        model.clear()
        groups.forEach { model.addElement(it) }
        val idx = (0 until model.size).firstOrNull { model.getElementAt(it).id == selectedId } ?: -1
        if (idx >= 0) list.selectedIndex = idx
    }

    fun restoreSelection() {
        val lastId = ctx.config.lastSelectedGroupId ?: return
        val idx = (0 until model.size).firstOrNull { model.getElementAt(it).id == lastId } ?: return
        if (idx >= 0) list.selectedIndex = idx
    }

    private fun addGroup() {
        val name = JOptionPane.showInputDialog(this, "Group name:", "Add Group", JOptionPane.PLAIN_MESSAGE)
            ?.trim() ?: return
        if (name.isBlank()) return
        val group = ProjectGroup(id = UUID.randomUUID().toString(), name = name)
        model.addElement(group)
        list.selectedIndex = model.size - 1
        persistGroups()
    }

    private fun removeGroup() {
        val selected = list.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Remove group '${selected.name}' and all its directories?",
            "Remove Group",
            JOptionPane.OK_CANCEL_OPTION,
        )
        if (confirm == JOptionPane.OK_OPTION) {
            model.removeElement(selected)
            onGroupSelected(null)
            persistGroups()
        }
    }

    private fun persistGroups() {
        val groups = (0 until model.size).map { model.getElementAt(it) }
        ctx.updateConfig(ctx.config.copy(groups = groups))
    }
}
