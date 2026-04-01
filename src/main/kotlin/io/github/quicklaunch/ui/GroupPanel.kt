package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.ProjectGroup
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

class GroupPanel(
    private val ctx: AppContext,
    private val onGroupSelected: (ProjectGroup?) -> Unit,
    private val onDirectoryDropped: (DirectoryTransfer, ProjectGroup) -> Unit = { _, _ -> },
) : JPanel(BorderLayout()) {

    private val model = DefaultListModel<ProjectGroup>()
    private val list = JList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(GroupCellRenderer())
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

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val idx = list.locationToIndex(e.point)
                    if (idx >= 0) {
                        list.selectedIndex = idx
                        showGroupContextMenu(model.getElementAt(idx), e.x, e.y)
                    }
                }
            }
        })
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

    private fun showGroupContextMenu(group: ProjectGroup, x: Int, y: Int) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("Set Color\u2026").apply {
            addActionListener { pickColor(group) }
        })
        if (group.color != null) {
            menu.add(JMenuItem("Clear Color").apply {
                addActionListener { setGroupColor(group, null) }
            })
        }
        menu.show(list, x, y)
    }

    private fun pickColor(group: ProjectGroup) {
        val initial = group.color?.let {
            try { Color.decode(it) } catch (_: Exception) { null }
        }
        val chosen = JColorChooser.showDialog(this, "Choose Group Color", initial) ?: return
        val hex = "#%02X%02X%02X".format(chosen.red, chosen.green, chosen.blue)
        setGroupColor(group, hex)
    }

    private fun setGroupColor(group: ProjectGroup, hex: String?) {
        val idx = (0 until model.size).firstOrNull { model.getElementAt(it).id == group.id } ?: return
        val updated = model.getElementAt(idx).copy(color = hex)
        model.setElementAt(updated, idx)
        persistGroups()
        list.repaint()
    }
}

private class GroupCellRenderer : ListCellRenderer<ProjectGroup> {

    private val colorStripe = JPanel().apply {
        preferredSize = Dimension(4, 0)
        isOpaque = true
    }
    private val label = JLabel().apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }
    private val outerPanel = JPanel(BorderLayout()).apply {
        isOpaque = true
    }

    init {
        outerPanel.add(colorStripe, BorderLayout.WEST)
        outerPanel.add(label, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out ProjectGroup>,
        value: ProjectGroup?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        label.text = if (value != null) "${value.name}  (${value.directories.size})" else ""

        val bg = if (isSelected) list.selectionBackground else list.background
        outerPanel.background = bg
        label.background = bg
        label.foreground = if (isSelected) list.selectionForeground else list.foreground
        label.isOpaque = true

        val colorHex = value?.color
        colorStripe.isVisible = colorHex != null
        if (colorHex != null) {
            colorStripe.background = try { Color.decode(colorHex) } catch (_: Exception) { Color.GRAY }
        }

        return outerPanel
    }
}
