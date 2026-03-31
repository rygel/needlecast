package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.GitStatus
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
import javax.swing.JColorChooser
import javax.swing.JTextField
import javax.swing.JFileChooser
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
    private val gitStatusCache = mutableMapOf<String, GitStatus>()
    /** Set by [selectByPath] when the target project hasn't finished scanning yet. */
    private var pendingSelectPath: String? = null

    private val list = object : JList<DetectedProject>(filteredModel) {
        override fun getToolTipText(event: java.awt.event.MouseEvent): String? {
            val idx = locationToIndex(event.point)
            if (idx < 0) return null
            return model.getElementAt(idx)?.directory?.path
        }
    }.apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        if (compact) fixedCellHeight = 28
        setCellRenderer(CompactProjectDirectoryRenderer({ activePaths }) { path -> gitStatusCache[path] })
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

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val idx = list.locationToIndex(e.point)
                    if (idx >= 0) {
                        list.selectedIndex = idx
                        showProjectContextMenu(list.model.getElementAt(idx), e.x, e.y)
                    }
                }
            }
        })

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
        gitStatusCache.clear()
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
        // Apply pending selection from switchToProject if the target has now been scanned
        val pending = pendingSelectPath
        if (pending != null) {
            val idx = (0 until filteredModel.size).firstOrNull { filteredModel.getElementAt(it).directory.path == pending }
            if (idx != null) {
                list.selectedIndex = idx
                list.ensureIndexIsVisible(idx)
                pendingSelectPath = null
            }
        }
    }

    fun requestFocusOnList() = list.requestFocusInWindow()

    /**
     * Selects the project with [path] in the list. If not yet scanned, stores it and applies
     * the selection when the project appears after the async scan completes.
     */
    fun selectByPath(path: String) {
        val idx = (0 until filteredModel.size).firstOrNull { filteredModel.getElementAt(it).directory.path == path }
        if (idx != null) {
            list.selectedIndex = idx
            list.ensureIndexIsVisible(idx)
            pendingSelectPath = null
        } else {
            pendingSelectPath = path
        }
    }

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
                fetchGitStatus(dir.path)
            }
        }.execute()
    }

    private fun fetchGitStatus(path: String) {
        object : SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = GitStatus.read(path)
            override fun done() {
                val status = try { get() } catch (_: Exception) { return }
                gitStatusCache[path] = status
                list.repaint()
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
        gitStatusCache.clear()
        updateButtonStates(null)
        onProjectSelected(null)
        group.directories.forEach { dir -> scanAndAdd(dir) }
    }

    private fun showProjectContextMenu(project: DetectedProject, x: Int, y: Int) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("Set Color\u2026").apply {
            addActionListener { pickColor(project) }
        })
        if (project.directory.color != null) {
            menu.add(JMenuItem("Clear Color").apply {
                addActionListener { setProjectColor(project, null) }
            })
        }
        menu.show(list, x, y)
    }

    private fun pickColor(project: DetectedProject) {
        val initial = project.directory.color?.let {
            try { Color.decode(it) } catch (_: Exception) { null }
        }
        val chosen = JColorChooser.showDialog(this, "Choose Project Color", initial) ?: return
        val hex = "#%02X%02X%02X".format(chosen.red, chosen.green, chosen.blue)
        setProjectColor(project, hex)
    }

    private fun setProjectColor(project: DetectedProject, hex: String?) {
        val group = currentGroup ?: return
        val updatedDirs = group.directories.map {
            if (it.path == project.directory.path) it.copy(color = hex) else it
        }
        val updatedGroup = group.copy(directories = updatedDirs)
        saveGroupUpdate(updatedGroup)
        // Refresh the in-memory model entry
        val idx = (0 until model.size).firstOrNull { model.getElementAt(it).directory.path == project.directory.path }
        if (idx != null) {
            val updated = model.getElementAt(idx).let { it.copy(directory = it.directory.copy(color = hex)) }
            model.setElementAt(updated, idx)
        }
        applyFilter(filterText)
        list.repaint()
    }

    private fun saveGroupUpdate(updatedGroup: ProjectGroup) {
        currentGroup = updatedGroup
        val updatedGroups = ctx.config.groups.map { if (it.id == updatedGroup.id) updatedGroup else it }
        ctx.updateConfig(ctx.config.copy(groups = updatedGroups))
    }
}

private class CompactProjectDirectoryRenderer(
    private val activePaths: () -> Set<String>,
    private val gitStatus: (String) -> GitStatus?,
) : ListCellRenderer<DetectedProject> {

    private val colorStripe = JPanel().apply {
        preferredSize = Dimension(4, 0)
        isOpaque = true
    }
    private val panel = JPanel(BorderLayout(4, 0)).apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }
    private val outerPanel = JPanel(BorderLayout()).apply {
        isOpaque = true
    }

    init {
        outerPanel.add(colorStripe, BorderLayout.WEST)
        outerPanel.add(panel, BorderLayout.CENTER)
    }
    private val nameLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 12f)
    }
    private val activeDot = JLabel("\u25CF").apply {   // ●
        font = font.deriveFont(Font.PLAIN, 10f)
        foreground = Color(0x4CAF50)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 4)
    }
    private val branchLabel = JLabel().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        foreground = Color(0x888888)
        border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
    }
    private val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
        isOpaque = false
    }
    private val nameRow = JPanel(BorderLayout(0, 0)).apply {
        isOpaque = false
        add(nameLabel, BorderLayout.CENTER)
        add(branchLabel, BorderLayout.EAST)
    }
    private val leftPanel = JPanel(BorderLayout(2, 0)).apply {
        isOpaque = false
        add(activeDot, BorderLayout.WEST)
        add(nameRow, BorderLayout.CENTER)
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

        val gs = value?.let { gitStatus(it.directory.path) }
        if (gs != null && gs.branch != null) {
            val dirtyMark = if (gs.isDirty) "*" else ""
            branchLabel.text = "\uE0A0 ${gs.branch}$dirtyMark"  // nerd-font branch glyph, falls back gracefully
            branchLabel.foreground = if (gs.isDirty) Color(0xE6A817) else Color(0x888888)
            branchLabel.isVisible = true
        } else {
            branchLabel.isVisible = false
        }

        tagsPanel.removeAll()
        if (value != null) {
            val tools = value.buildTools
            val tags = if (tools.isEmpty()) listOf(null) else tools.map { it }
            tags.forEach { tool -> tagsPanel.add(buildTagLabel(tool)) }
        }

        val bg = if (isSelected) list.selectionBackground else list.background
        outerPanel.background = bg
        panel.background = bg
        nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
        panel.isOpaque = true

        val colorHex = value?.directory?.color
        colorStripe.isVisible = colorHex != null
        if (colorHex != null) {
            colorStripe.background = try { Color.decode(colorHex) } catch (_: Exception) { Color.GRAY }
        }

        return outerPanel
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
