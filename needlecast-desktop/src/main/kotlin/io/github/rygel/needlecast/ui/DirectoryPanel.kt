package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectGroup
import io.github.rygel.needlecast.scanner.BuildFileWatcher
import io.github.rygel.needlecast.service.ProjectService
import org.slf4j.LoggerFactory
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
import javax.swing.TransferHandler
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

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
        setCellRenderer(CompactProjectDirectoryRenderer({ activePaths }) { path -> gitStatusCache[path] })
    }

    private var currentGroup: ProjectGroup? = null
    private val projectService = ProjectService(ctx)

    private var buildFileWatcher = BuildFileWatcher { path -> rescheduleProjectScan(path) }
        .also { ctx.register(it) }

    companion object {
        private val logger = LoggerFactory.getLogger(DirectoryPanel::class.java)
    }

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
        list.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    list.transferHandler?.exportAsDrag(list, e, TransferHandler.MOVE)
                }
            }
        })

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
        buildFileWatcher.stop()
        buildFileWatcher = BuildFileWatcher { path -> rescheduleProjectScan(path) }
        ctx.register(buildFileWatcher)
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

    fun getProjectByPath(path: String): DetectedProject? =
        (0 until model.size).map { model.getElementAt(it) }.firstOrNull { it.directory.path == path }

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
        object : SwingWorker<DetectedProject, Void>() {
            override fun doInBackground(): DetectedProject = try {
                ctx.scanner.scan(dir) ?: DetectedProject(dir, emptySet(), emptyList())
            } catch (e: Exception) {
                logger.warn("Failed to scan '${dir.label()}'", e)
                DetectedProject(dir, emptySet(), emptyList(), scanFailed = true)
            }

            override fun done() {
                val result = try { get() } catch (_: Exception) { return }
                model.addElement(result)
                applyFilter(filterText)
                if (!result.scanFailed) {
                    fetchGitStatus(dir.path)
                    buildFileWatcher.watch(dir.path)
                }
            }
        }.execute()
    }

    /**
     * Re-scans the project at [path] after a build file change and updates the model entry
     * in place so the command list stays current without a full group reload.
     */
    private fun rescheduleProjectScan(path: String) {
        val dir = currentGroup?.directories?.firstOrNull { it.path == path } ?: return
        object : SwingWorker<DetectedProject?, Void>() {
            override fun doInBackground(): DetectedProject? =
                try { ctx.scanner.scan(dir) } catch (_: Exception) { null }

            override fun done() {
                val result = try { get() } catch (_: Exception) { null } ?: return
                val idx = (0 until model.size).firstOrNull { model.getElementAt(it).directory.path == path }
                if (idx != null) {
                    model.setElementAt(result, idx)
                } else {
                    model.addElement(result)
                }
                applyFilter(filterText)
            }
        }.execute()
    }

    private fun fetchGitStatus(path: String) {
        object : SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = ctx.gitService.readStatus(path)
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
        currentGroup = projectService.addDirectory(group, dir)
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
            currentGroup = projectService.removeDirectory(group, selected.directory.path)
            model.removeElement(selected)
            applyFilter(filterText)
            onProjectSelected(null)
        }
    }

    private fun rescanAll() {
        val group = currentGroup ?: return
        buildFileWatcher.stop()
        buildFileWatcher = BuildFileWatcher { path -> rescheduleProjectScan(path) }
        ctx.register(buildFileWatcher)
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
        menu.add(JMenuItem("Shell Settings\u2026").apply {
            addActionListener { editShellSettings(project) }
        })
        menu.add(JMenuItem("Edit Environment\u2026").apply {
            addActionListener { editEnv(project) }
        })
        menu.addSeparator()
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

    private fun editShellSettings(project: DetectedProject) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        val dir = project.directory

        val shellField = JTextField(dir.shellExecutable ?: "", 30)
        val startupField = JTextField(dir.startupCommand ?: "", 30)

        val defaultShell = when {
            io.github.rygel.needlecast.scanner.IS_WINDOWS -> "cmd.exe"
            io.github.rygel.needlecast.scanner.IS_MAC     -> "/bin/zsh"
            else                                          -> "/bin/bash"
        }
        val form = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST }

            gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
            add(JLabel("Shell:"), gc)
            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
            add(shellField, gc)

            gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
            add(JLabel("Startup command:"), gc)
            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
            add(startupField, gc)

            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; gc.fill = GridBagConstraints.HORIZONTAL
            add(JLabel("<html><small>" +
                "Shell: e.g. <tt>zsh</tt>, <tt>fish</tt>, <tt>powershell</tt> — " +
                "blank uses system default (<tt>$defaultShell</tt>)<br>" +
                "Startup: sent to the shell on open, e.g. <tt>conda activate ml</tt>" +
                "</small></html>"), gc)
        }

        val result = JOptionPane.showConfirmDialog(
            owner, form,
            "Shell Settings \u2014 ${dir.label()}",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
        )
        if (result == JOptionPane.OK_OPTION) {
            val shell   = shellField.text.trim().takeIf { it.isNotEmpty() }
            val startup = startupField.text.trim().takeIf { it.isNotEmpty() }
            updateProjectDirectory(project) { it.copy(shellExecutable = shell, startupCommand = startup) }
        }
    }

    private fun editEnv(project: DetectedProject) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        EnvEditorDialog(owner, project.directory.label(), project.directory.env) { newEnv ->
            updateProjectDirectory(project) { it.copy(env = newEnv) }
        }.isVisible = true
    }

    /** Applies [transform] to the matching [ProjectDirectory] and persists to config. */
    private fun updateProjectDirectory(project: DetectedProject, transform: (ProjectDirectory) -> ProjectDirectory) {
        val group = currentGroup ?: return
        currentGroup = projectService.updateDirectory(group, project.directory.path, transform)
        val idx = (0 until model.size).firstOrNull { model.getElementAt(it).directory.path == project.directory.path }
        if (idx != null) {
            val newDir = transform(model.getElementAt(idx).directory)
            model.setElementAt(model.getElementAt(idx).copy(directory = newDir), idx)
        }
        applyFilter(filterText)
        list.repaint()
    }

    private fun pickColor(project: DetectedProject) {
        val initial = project.directory.color?.let {
            try { Color.decode(it) } catch (_: Exception) { null }
        }
        val chosen = JColorChooser.showDialog(this, "Choose Project Color", initial) ?: return
        val hex = "#%02X%02X%02X".format(chosen.red, chosen.green, chosen.blue)
        updateProjectDirectory(project) { it.copy(color = hex) }
    }

    private fun setProjectColor(project: DetectedProject, hex: String?) {
        updateProjectDirectory(project) { it.copy(color = hex) }
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
        border = BorderFactory.createEmptyBorder(0, 18, 0, 0)  // indent past activeDot width
    }
    private val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
        isOpaque = false
    }
    // Row 1: ● name  [tags]
    private val nameRow = JPanel(BorderLayout(2, 0)).apply {
        isOpaque = false
        add(activeDot, BorderLayout.WEST)
        add(nameLabel, BorderLayout.CENTER)
        add(tagsPanel, BorderLayout.EAST)
    }
    // Full cell: name row top, branch row below (full width — no tag overlap)
    private val cellPanel = JPanel(BorderLayout(0, 1)).apply {
        isOpaque = false
        add(nameRow, BorderLayout.NORTH)
        add(branchLabel, BorderLayout.CENTER)
    }

    init {
        panel.add(cellPanel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out DetectedProject>,
        value: DetectedProject?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val isActive = value != null && value.directory.path in activePaths()
        activeDot.isVisible = isActive
        nameLabel.text = value?.directory?.label() ?: ""

        val gs = value?.let { gitStatus(it.directory.path) }
        if (gs != null && gs.branch != null) {
            val dirtyMark = if (gs.isDirty) "*" else ""
            branchLabel.text = "${gs.branch}$dirtyMark"
            branchLabel.toolTipText = gs.branch
            branchLabel.foreground = if (gs.isDirty) Color(0xE6A817) else Color(0x888888)
        } else {
            branchLabel.text = " "
            branchLabel.toolTipText = null
        }

        tagsPanel.removeAll()
        if (value != null) {
            if (value.scanFailed) {
                tagsPanel.add(buildTagLabel(null, "⚠", "#B71C1C"))
            } else {
                val tools = value.buildTools
                val tags = if (tools.isEmpty()) listOf(null) else tools.map { it }
                tags.forEach { tool -> tagsPanel.add(buildTagLabel(tool)) }
            }
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

    private fun buildTagLabel(tool: BuildTool?, label: String? = null, color: String? = null): JLabel {
        val text = label ?: tool?.tagLabel ?: "?"
        val hex  = color ?: tool?.tagColor ?: "#757575"
        return JLabel(text).apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 9)
            foreground = Color.WHITE
            background = Color.decode(hex)
            isOpaque = true
            border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
            preferredSize = Dimension(preferredSize.width, 14)
            if (tool == null && label == "⚠") toolTipText = "Scan failed — check logs or rescan"
        }
    }
}

