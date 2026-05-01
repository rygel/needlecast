package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.model.SkillEntry
import io.github.rygel.needlecast.ui.RemixIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SkillsPanel(
    private val ctx: AppContext,
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<SkillEntry>()
    private val skillList = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(SkillCellRenderer())
    }
    private val searchField = JTextField().apply {
        toolTipText = "Filter skills by name or description"
    }
    private val descArea = JTextArea(4, 30).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val newButton = JButton(RemixIcons.icon("ri-add-line", 16))
    private val editButton = JButton(RemixIcons.icon("ri-edit-line", 16))
    private val deleteButton = JButton(RemixIcons.icon("ri-delete-bin-line", 16))
    private val deployButton = JButton("Deploy")
    private var currentProject: DetectedProject? = null
    private var deployedNames: Set<String> = emptySet()
    private var allSkills: List<SkillEntry> = emptyList()

    init {
        newButton.toolTipText = "Create new skill"
        editButton.toolTipText = "Edit selected skill"
        deleteButton.toolTipText = "Delete selected skill"
        deployButton.toolTipText = "No project selected"

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            add(newButton)
            add(editButton)
            add(deleteButton)
            add(Box.createHorizontalStrut(8))
            add(JLabel("Search:"))
            add(searchField.apply { preferredSize = java.awt.Dimension(140, 26) })
        }

        val listScroll = JScrollPane(skillList)
        val descScroll = JScrollPane(descArea)
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, descScroll).apply {
            dividerLocation = 200
            resizeWeight = 0.7
        }

        val bottomBar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(deployButton)
        }

        add(toolbar, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
        add(bottomBar, BorderLayout.SOUTH)

        skillList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) onSelectionChanged()
        }

        newButton.addActionListener { createNewSkill() }
        editButton.addActionListener { editSelectedSkill() }
        deleteButton.addActionListener { deleteSelectedSkill() }
        deployButton.addActionListener { toggleDeploy() }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })

        refreshList()
    }

    fun loadProject(project: DetectedProject?) {
        currentProject = project
        refreshDeployStatus()
    }

    private fun refreshList() {
        allSkills = ctx.skillLibraryStore.loadLibrary()
        applyFilter()
    }

    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        val selected = skillList.selectedValue
        listModel.clear()
        allSkills
            .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.description.lowercase().contains(query) }
            .forEach { listModel.addElement(it) }
        if (selected != null) {
            val idx = (0 until listModel.size).firstOrNull { listModel.getElementAt(it) == selected }
            if (idx != null) skillList.selectedIndex = idx
        }
    }

    private fun refreshDeployStatus() {
        val project = currentProject
        val targetDir = project?.directory?.skillTargetDir
        deployedNames = if (project != null && targetDir != null) {
            ctx.skillLibraryStore.deployedSkills(project.directory.path, targetDir).toSet()
        } else {
            emptySet()
        }
        skillList.repaint()
        updateDeployButton()
    }

    private fun onSelectionChanged() {
        val sel = skillList.selectedValue
        descArea.text = sel?.description ?: ""
        descArea.caretPosition = 0
        editButton.isEnabled = sel != null
        deleteButton.isEnabled = sel != null
        updateDeployButton()
    }

    private fun updateDeployButton() {
        val project = currentProject
        val sel = skillList.selectedValue
        if (project == null) {
            deployButton.text = "Deploy"
            deployButton.isEnabled = false
            deployButton.toolTipText = "No project selected"
            return
        }
        if (project.directory.skillTargetDir == null) {
            deployButton.text = "Set Target Dir"
            deployButton.isEnabled = sel != null
            deployButton.toolTipText = "Configure a skill target directory for this project"
            return
        }
        val deployed = sel != null && sel.name in deployedNames
        deployButton.text = if (deployed) "Undeploy" else "Deploy"
        deployButton.isEnabled = sel != null
        deployButton.toolTipText = if (sel == null) "Select a skill" else null
    }

    private fun toggleDeploy() {
        val sel = skillList.selectedValue ?: return
        var project = currentProject ?: return
        var targetDir = project.directory.skillTargetDir

        if (targetDir == null) {
            val input = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Skill target directory (relative to project root):",
                "Configure Skill Target",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                ".claude/skills",
            ) as? String ?: return
            targetDir = input.trim()
            if (targetDir.isBlank()) return
            val updatedDir = project.directory.copy(skillTargetDir = targetDir)
            ctx.updateConfig(ctx.config.copy(
                projectTree = updateProjectInTree(ctx.config.projectTree, project.directory.path, updatedDir),
                groups = ctx.config.groups.map { group ->
                    group.copy(directories = group.directories.map {
                        if (it.path == project.directory.path) updatedDir else it
                    })
                },
            ))
            project = project.copy(directory = updatedDir)
            currentProject = project
        }

        targetDir = targetDir!!

        if (sel.name in deployedNames) {
            ctx.skillLibraryStore.undeploy(sel.name, project.directory.path, targetDir)
        } else {
            ctx.skillLibraryStore.deploy(sel.name, project.directory.path, targetDir)
        }
        refreshDeployStatus()
    }

    private fun createNewSkill() {
        val owner = SwingUtilities.getWindowAncestor(this) as? Window ?: return
        val dialog = SkillEditDialog(owner, "New Skill")
        dialog.isVisible = true
        val (entry, body) = dialog.result ?: return
        ctx.skillLibraryStore.save(entry, body)
        refreshList()
        val idx = (0 until listModel.size).firstOrNull { listModel.getElementAt(it).name == entry.name }
        if (idx != null) skillList.selectedIndex = idx
    }

    private fun editSelectedSkill() {
        val sel = skillList.selectedValue ?: return
        val owner = SwingUtilities.getWindowAncestor(this) as? Window ?: return
        val dialog = SkillEditDialog(owner, "Edit Skill", sel)
        dialog.isVisible = true
        val (entry, body) = dialog.result ?: return
        ctx.skillLibraryStore.save(entry, body)
        refreshList()
        val idx = (0 until listModel.size).firstOrNull { listModel.getElementAt(it).name == entry.name }
        if (idx != null) skillList.selectedIndex = idx
    }

    private fun deleteSelectedSkill() {
        val sel = skillList.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete skill \"${sel.name}\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (confirm != JOptionPane.YES_OPTION) return
        ctx.skillLibraryStore.delete(sel.name)
        refreshList()
    }

    private inner class SkillCellRenderer : ListCellRenderer<SkillEntry> {
        private val label = JLabel().apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            isOpaque = true
        }

        override fun getListCellRendererComponent(
            list: JList<out SkillEntry>,
            value: SkillEntry?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            if (value == null) return label
            val deployed = value.name in deployedNames
            label.text = value.name
            label.icon = if (deployed) RemixIcons.icon("ri-checkbox-circle-fill", 12, java.awt.Color(0x4CAF50)) else null
            label.toolTipText = value.description.ifBlank { null }
            label.foreground = if (isSelected) list.selectionForeground else list.foreground
            label.background = if (isSelected) list.selectionBackground else list.background
            if (deployed && !isSelected) {
                label.font = label.font.deriveFont(Font.BOLD)
            } else {
                label.font = label.font.deriveFont(Font.PLAIN)
            }
            return label
        }
    }

    companion object {
        private fun updateProjectInTree(
            tree: List<ProjectTreeEntry>,
            projectPath: String,
            updatedDir: ProjectDirectory,
        ): List<ProjectTreeEntry> = tree.map { entry ->
            when (entry) {
                is ProjectTreeEntry.Folder -> entry.copy(
                    children = updateProjectInTree(entry.children, projectPath, updatedDir),
                )
                is ProjectTreeEntry.Project -> {
                    if (entry.directory.path == projectPath) entry.copy(directory = updatedDir) else entry
                }
            }
        }
    }
}
