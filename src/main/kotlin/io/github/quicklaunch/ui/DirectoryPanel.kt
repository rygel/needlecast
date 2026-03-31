package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProjectDirectory
import io.github.quicklaunch.model.ProjectGroup
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JTextField
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

class DirectoryPanel(
    private val ctx: AppContext,
    private val compact: Boolean = false,
    private val onProjectSelected: (DetectedProject?) -> Unit,
    private val onActivate: (DetectedProject) -> Unit = {},
    private val onDeactivate: (DetectedProject) -> Unit = {},
) : JPanel(BorderLayout()) {

    private val model = DefaultListModel<DetectedProject>()
    private val filteredModel = DefaultListModel<DetectedProject>()
    private var filterText = ""
    private var activePaths: Set<String> = emptySet()

    private val list = object : JList<DetectedProject>(filteredModel) {
        override fun getToolTipText(event: java.awt.event.MouseEvent): String? {
            val idx = locationToIndex(event.point)
            if (idx < 0) return null
            return model.getElementAt(idx)?.directory?.path
        }
    }.apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        if (compact) fixedCellHeight = 28
        setCellRenderer(CompactProjectDirectoryRenderer { activePaths })
    }

    private var currentGroup: ProjectGroup? = null

    private val activateButton = JButton("\u25B6").apply {
        toolTipText = "Activate terminal for this project"
        isFocusPainted = false
        isEnabled = false
    }
    private val deactivateButton = JButton("\u23F9").apply {
        toolTipText = "Deactivate terminal for this project"
        isFocusPainted = false
        isEnabled = false
    }

    init {
        val header = JLabel("Projects").apply {
            border = BorderFactory.createEmptyBorder(4, 6, 2, 6)
            font = font.deriveFont(Font.BOLD)
        }

        val addButton = JButton("+").apply {
            toolTipText = "Add directory"
            isFocusPainted = false
        }
        val removeButton = JButton("-").apply {
            toolTipText = "Remove directory"
            isFocusPainted = false
        }
        val rescanButton = JButton("\u21BB").apply {
            toolTipText = "Rescan all directories"
            isFocusPainted = false
        }

        val toolbar = JToolBar().apply {
            isFloatable = false
            add(addButton)
            add(removeButton)
            add(rescanButton)
            addSeparator()
            add(activateButton)
            add(deactivateButton)
        }

        val topPanel = JPanel(BorderLayout()).apply {
            add(header, BorderLayout.WEST)
            add(toolbar, BorderLayout.EAST)
        }

        val filterField = JTextField().apply {
            toolTipText = "Filter projects"
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = applyFilter(text)
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = applyFilter(text)
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
            })
        }

        list.transferHandler = DirectoryDragHandler(
            getSourceGroupId = { currentGroup?.id },
            getSelectedProject = { list.selectedValue },
        )
        list.dragEnabled = true

        list.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val project = list.selectedValue
                updateButtonStates(project)
                onProjectSelected(project)
            }
        }

        activateButton.addActionListener {
            val project = list.selectedValue ?: return@addActionListener
            onActivate(project)
            activePaths = activePaths + project.directory.path
            updateButtonStates(project)
            list.repaint()
        }

        deactivateButton.addActionListener {
            val project = list.selectedValue ?: return@addActionListener
            onDeactivate(project)
            activePaths = activePaths - project.directory.path
            updateButtonStates(project)
            list.repaint()
        }

        val northPanel = JPanel(BorderLayout(0, 2)).apply {
            add(topPanel, BorderLayout.NORTH)
            add(filterField, BorderLayout.SOUTH)
        }

        add(northPanel, BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)

        addButton.addActionListener { addDirectory() }
        removeButton.addActionListener { removeDirectory() }
        rescanButton.addActionListener { rescanAll() }
    }

    fun loadGroup(group: ProjectGroup?) {
        currentGroup = group
        model.clear()
        filteredModel.clear()
        activePaths = emptySet()
        updateButtonStates(null)
        onProjectSelected(null)
        group?.directories?.forEach { dir -> scanAndAdd(dir) }
    }

    fun removeProjectByPath(path: String) {
        val toRemove = (0 until model.size).map { model.getElementAt(it) }
            .firstOrNull { it.directory.path == path } ?: return
        model.removeElement(toRemove)
        activePaths = activePaths - path
        currentGroup = currentGroup?.let { grp ->
            grp.copy(directories = grp.directories.filter { it.path != path })
        }
        applyFilter(filterText)
        onProjectSelected(null)
    }

    private fun applyFilter(text: String) {
        filterText = text.trim().lowercase()
        filteredModel.clear()
        (0 until model.size).map { model.getElementAt(it) }
            .filter { filterText.isEmpty() || it.directory.label().lowercase().contains(filterText) }
            .forEach { filteredModel.addElement(it) }
    }

    fun requestFocusOnList() = list.requestFocusInWindow()

    fun triggerRescan() = rescanAll()

    fun triggerActivateTerminal() {
        val project = list.selectedValue ?: return
        if (project.directory.path !in activePaths) {
            onActivate(project)
            activePaths = activePaths + project.directory.path
            updateButtonStates(project)
            list.repaint()
        }
    }

    /** Called by MainWindow to sync active state after external changes. */
    fun setActivePaths(paths: Set<String>) {
        activePaths = paths
        updateButtonStates(list.selectedValue)
        list.repaint()
    }

    private fun updateButtonStates(project: DetectedProject?) {
        val path = project?.directory?.path
        activateButton.isEnabled = path != null && path !in activePaths
        deactivateButton.isEnabled = path != null && path in activePaths
    }

    private fun scanAndAdd(dir: ProjectDirectory) {
        object : SwingWorker<DetectedProject?, Void>() {
            override fun doInBackground(): DetectedProject? =
                try { ctx.scanner.scan(dir) } catch (_: Exception) { null }

            override fun done() {
                val result = try { get() } catch (_: Exception) { null } ?: return
                model.addElement(result)
                applyFilter(filterText)
            }
        }.execute()
    }

    private fun addDirectory() {
        val group = currentGroup ?: return
        val startDir = list.selectedValue?.directory?.path?.let { File(it).parentFile }
            ?: File(System.getProperty("user.home"))
        val chooser = JFileChooser(startDir).apply {
            dialogTitle = "Select Project Directory"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val dir = ProjectDirectory(path = chooser.selectedFile.absolutePath)
        val updatedGroup = group.copy(directories = group.directories + dir)
        saveGroupUpdate(updatedGroup)
        scanAndAdd(dir)
    }

    private fun removeDirectory() {
        val group = currentGroup ?: return
        val selected = list.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Remove '${selected.directory.label()}' from group?",
            "Remove Directory",
            JOptionPane.OK_CANCEL_OPTION,
        )
        if (confirm == JOptionPane.OK_OPTION) {
            val updatedGroup = group.copy(
                directories = group.directories.filter { it.path != selected.directory.path }
            )
            saveGroupUpdate(updatedGroup)
            model.removeElement(selected)
            applyFilter(filterText)
            onProjectSelected(null)
        }
    }

    private fun rescanAll() {
        val group = currentGroup ?: return
        model.clear()
        filteredModel.clear()
        activePaths = emptySet()
        updateButtonStates(null)
        onProjectSelected(null)
        group.directories.forEach { dir -> scanAndAdd(dir) }
    }

    private fun saveGroupUpdate(updatedGroup: ProjectGroup) {
        currentGroup = updatedGroup
        val updatedGroups = ctx.config.groups.map { if (it.id == updatedGroup.id) updatedGroup else it }
        ctx.updateConfig(ctx.config.copy(groups = updatedGroups))
    }
}

private class CompactProjectDirectoryRenderer(
    private val activePaths: () -> Set<String>,
) : ListCellRenderer<DetectedProject> {

    private val panel = JPanel(BorderLayout(4, 0)).apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }
    private val nameLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 12f)
    }
    private val activeDot = JLabel("\u25CF").apply {   // ●
        font = font.deriveFont(Font.PLAIN, 10f)
        foreground = Color(0x4CAF50)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 4)
    }
    private val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
        isOpaque = false
    }
    private val leftPanel = JPanel(BorderLayout(2, 0)).apply {
        isOpaque = false
        add(activeDot, BorderLayout.WEST)
        add(nameLabel, BorderLayout.CENTER)
    }

    init {
        panel.add(leftPanel, BorderLayout.CENTER)
        panel.add(tagsPanel, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out DetectedProject>,
        value: DetectedProject?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        nameLabel.text = value?.directory?.label() ?: ""
        val isActive = value != null && value.directory.path in activePaths()
        activeDot.isVisible = isActive

        tagsPanel.removeAll()
        if (value != null) {
            val tools = value.buildTools
            val tags = if (tools.isEmpty()) listOf(null) else tools.map { it }
            tags.forEach { tool -> tagsPanel.add(buildTagLabel(tool)) }
        }

        if (isSelected) {
            panel.background = list.selectionBackground
            nameLabel.foreground = list.selectionForeground
        } else {
            panel.background = list.background
            nameLabel.foreground = list.foreground
        }
        panel.isOpaque = true
        return panel
    }

    private fun buildTagLabel(tool: BuildTool?): JLabel {
        val (text, hex) = when (tool) {
            BuildTool.MAVEN        -> "mvn"    to "#2E7D32"
            BuildTool.GRADLE       -> "gradle" to "#1565C0"
            BuildTool.DOTNET       -> ".net"   to "#6A0DAD"
            BuildTool.INTELLIJ_RUN -> "run"    to "#E65100"
            BuildTool.NPM          -> "npm"    to "#CB3837"
            null                   -> "?"      to "#757575"
        }
        return JLabel(text).apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 9)
            foreground = Color.WHITE
            background = Color.decode(hex)
            isOpaque = true
            border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
            preferredSize = Dimension(preferredSize.width, 14)
        }
    }
}
