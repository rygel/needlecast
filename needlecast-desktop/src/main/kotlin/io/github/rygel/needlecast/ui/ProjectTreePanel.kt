package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.BuildFileWatcher
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.TransferHandler
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import javax.swing.Timer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class ProjectTreePanel(
    private val ctx: AppContext,
    private val onProjectSelected: (DetectedProject?) -> Unit,
    private val onActivate: (DetectedProject) -> Unit = {},
    private val onDeactivate: (DetectedProject) -> Unit = {},
) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = object : JTree(treeModel) {
        override fun getScrollableTracksViewportWidth(): Boolean = true
    }.apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    private val scanResults   = mutableMapOf<String, DetectedProject>()
    private val gitStatusCache = mutableMapOf<String, GitStatus>()
    private var activePaths: Set<String> = emptySet()
    private var pendingSelectPath: String? = null
    private val agentStatuses = mutableMapOf<String, AgentStatus>()

    /** Pulses the agent dot while any project is THINKING. */
    private var blinkOn = false
    private val blinkTimer = Timer(600) {
        blinkOn = !blinkOn
        tree.repaint()
    }.apply { isRepeats = true }

    private var buildFileWatcher = BuildFileWatcher { path -> rescheduleProjectScan(path) }
        .also { ctx.register(it) }

    /** Captured in mousePressed so createTransferable can find the node even before selection updates. */
    private var dragPressedPath: TreePath? = null

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectTreePanel::class.java)
    }

    init {
        tree.cellRenderer = ProjectTreeCellRenderer()
        tree.dragEnabled = true
        tree.dropMode = DropMode.ON_OR_INSERT
        tree.transferHandler = TreeTransferHandler()

        fun iconBtn(icon: javax.swing.Icon?, text: String, tip: String) = JButton(icon).apply {
            if (icon == null) this.text = text
            toolTipText = tip
            isFocusPainted = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
        }
        val addFolderBtn = iconBtn(UIManager.getIcon("FileView.directoryIcon"), "\uD83D\uDCC1", "Add a folder to organize projects").apply {
            addActionListener { addFolder(selectedFolderNode()) }
        }
        val addProjectBtn = iconBtn(UIManager.getIcon("FileView.fileIcon"), "\uD83D\uDCC4", "Add a project directory").apply {
            addActionListener { addProject(selectedFolderNode()) }
        }
        val rescanBtn = iconBtn(null, "\u21BB", "Rescan all projects (F5)").apply {
            isContentAreaFilled = false
            addActionListener { rescanAll() }
        }

        val filterField = JTextField().apply {
            toolTipText = "Filter projects"
            putClientProperty("JTextField.placeholderText", "Filter\u2026")
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = applyFilter(text)
                override fun removeUpdate(e: DocumentEvent) = applyFilter(text)
                override fun changedUpdate(e: DocumentEvent) {}
            })
        }

        val northPanel = JPanel(BorderLayout(4, 2)).apply {
            border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(addFolderBtn); add(addProjectBtn); add(rescanBtn)
            }
            add(filterField, BorderLayout.CENTER)
            add(btnPanel,    BorderLayout.EAST)
        }

        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            when (val entry = node.userObject) {
                is ProjectTreeEntry.Project -> onProjectSelected(scanResults[entry.directory.path])
                else -> onProjectSelected(null)
            }
        }

        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // getPathForLocation misses clicks in the right-side empty row area;
                    // use the closest path within the row bounds instead.
                    val closest = tree.getClosestPathForLocation(e.x, e.y)
                    val bounds  = if (closest != null) tree.getPathBounds(closest) else null
                    dragPressedPath = if (bounds != null && e.y >= bounds.y && e.y < bounds.y + bounds.height) closest else null
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = tree.getPathForLocation(e.x, e.y)
                    if (path != null) {
                        tree.selectionPath = path
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        showContextMenu(node, e.x, e.y)
                    } else {
                        showRootContextMenu(e.x, e.y)
                    }
                }
            }
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    when (val entry = node.userObject) {
                        is ProjectTreeEntry.Project -> {
                            val detected = scanResults[entry.directory.path] ?: return
                            if (entry.directory.path !in activePaths) {
                                onActivate(detected)
                                activePaths = activePaths + entry.directory.path
                                tree.repaint()
                            }
                        }
                        else -> {}
                    }
                }
            }
        })

        add(northPanel, BorderLayout.NORTH)
        add(JScrollPane(tree).apply {
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)

        loadFromConfig()
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private fun loadFromConfig() {
        rootNode.removeAllChildren()
        migrateOrLoad().forEach { addEntryNode(rootNode, it) }
        treeModel.reload()
        expandAll()
    }

    private fun migrateOrLoad(): List<ProjectTreeEntry> {
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

    private fun addEntryNode(parent: DefaultMutableTreeNode, entry: ProjectTreeEntry) {
        val node = DefaultMutableTreeNode(entry)
        parent.add(node)
        when (entry) {
            is ProjectTreeEntry.Folder  -> entry.children.forEach { addEntryNode(node, it) }
            is ProjectTreeEntry.Project -> scanProject(entry.directory)
        }
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) tree.expandRow(i++)
    }

    // ── Scanning ─────────────────────────────────────────────────────────────

    private fun scanProject(dir: ProjectDirectory) {
        object : SwingWorker<DetectedProject, Void>() {
            override fun doInBackground(): DetectedProject = try {
                ctx.scanner.scan(dir) ?: DetectedProject(dir, emptySet(), emptyList())
            } catch (e: Exception) {
                logger.warn("Failed to scan '${dir.label()}'", e)
                DetectedProject(dir, emptySet(), emptyList(), scanFailed = true)
            }

            override fun done() {
                val result = try { get() } catch (_: Exception) { return }
                scanResults[dir.path] = result
                tree.repaint()
                if (!result.scanFailed) {
                    fetchGitStatus(dir.path)
                    buildFileWatcher.watch(dir.path)
                }
                val pending = pendingSelectPath
                if (pending == dir.path) selectByPath(pending)
            }
        }.execute()
    }

    private fun rescheduleProjectScan(path: String) {
        val dir = findProjectEntry(rootNode, path)?.directory ?: return
        object : SwingWorker<DetectedProject?, Void>() {
            override fun doInBackground(): DetectedProject? =
                try { ctx.scanner.scan(dir) } catch (_: Exception) { null }

            override fun done() {
                val result = try { get() } catch (_: Exception) { null } ?: return
                scanResults[path] = result
                tree.repaint()
            }
        }.execute()
    }

    private fun fetchGitStatus(path: String) {
        object : SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = ctx.gitService.readStatus(path)
            override fun done() {
                val status = try { get() } catch (_: Exception) { return }
                gitStatusCache[path] = status
                tree.repaint()
            }
        }.execute()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun requestFocusOnTree() = tree.requestFocusInWindow()

    fun triggerRescan() = rescanAll()

    fun triggerActivateTerminal() {
        val entry = selectedProjectEntry() ?: return
        val detected = scanResults[entry.directory.path] ?: return
        if (entry.directory.path !in activePaths) {
            onActivate(detected)
            activePaths = activePaths + entry.directory.path
            tree.repaint()
        }
    }

    fun setActivePaths(paths: Set<String>) {
        activePaths = paths
        tree.repaint()
    }

    fun updateProjectStatus(path: String, status: AgentStatus) {
        agentStatuses[path] = status
        if (agentStatuses.values.any { it == AgentStatus.THINKING }) blinkTimer.start()
        else blinkTimer.stop()
        tree.repaint()
    }

    fun selectByPath(path: String) {
        val node = findProjectNode(rootNode, path)
        if (node != null) {
            val tp = treePath(node)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
            pendingSelectPath = null
        } else {
            pendingSelectPath = path
        }
    }

    // ── Tree helpers ─────────────────────────────────────────────────────────

    private fun selectedNode(): DefaultMutableTreeNode? =
        tree.lastSelectedPathComponent as? DefaultMutableTreeNode

    private fun selectedFolderNode(): DefaultMutableTreeNode? {
        val node = selectedNode() ?: return null
        return when (node.userObject) {
            is ProjectTreeEntry.Folder  -> node
            is ProjectTreeEntry.Project -> node.parent as? DefaultMutableTreeNode
            else -> null
        }
    }

    private fun selectedProjectEntry(): ProjectTreeEntry.Project? {
        val node = selectedNode() ?: return null
        return when (val entry = node.userObject) {
            is ProjectTreeEntry.Project -> entry
            else -> null
        }
    }

    private fun findProjectNode(parent: DefaultMutableTreeNode, path: String): DefaultMutableTreeNode? {
        if ((parent.userObject as? ProjectTreeEntry.Project)?.directory?.path == path) return parent
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val found = findProjectNode(child, path)
            if (found != null) return found
        }
        return null
    }

    private fun findProjectEntry(parent: DefaultMutableTreeNode, path: String): ProjectTreeEntry.Project? =
        findProjectNode(parent, path)?.userObject as? ProjectTreeEntry.Project

    private fun treePath(node: DefaultMutableTreeNode) = TreePath(node.path)

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun buildTree(): List<ProjectTreeEntry> =
        (0 until rootNode.childCount).mapNotNull { buildEntry(rootNode.getChildAt(it) as? DefaultMutableTreeNode) }

    private fun buildEntry(node: DefaultMutableTreeNode?): ProjectTreeEntry? {
        node ?: return null
        return when (val entry = node.userObject) {
            is ProjectTreeEntry.Folder -> entry.copy(
                children = (0 until node.childCount).mapNotNull { buildEntry(node.getChildAt(it) as? DefaultMutableTreeNode) }
            )
            is ProjectTreeEntry.Project -> entry
            else -> null
        }
    }

    private fun persist() {
        ctx.updateConfig(ctx.config.copy(projectTree = buildTree()))
    }

    // ── Filter ───────────────────────────────────────────────────────────────

    private fun applyFilter(text: String) {
        val filter = text.trim().lowercase()
        rootNode.removeAllChildren()
        if (filter.isEmpty()) {
            migrateOrLoad().forEach { addEntryNode(rootNode, it) }
        } else {
            ctx.config.projectTree.forEach { addFilteredEntry(rootNode, it, filter) }
        }
        treeModel.reload()
        expandAll()
    }

    private fun addFilteredEntry(parent: DefaultMutableTreeNode, entry: ProjectTreeEntry, filter: String): Boolean {
        return when (entry) {
            is ProjectTreeEntry.Project -> {
                val matches = entry.directory.label().lowercase().contains(filter) ||
                    entry.tags.any { it.lowercase().contains(filter) }
                if (matches) parent.add(DefaultMutableTreeNode(entry))
                matches
            }
            is ProjectTreeEntry.Folder -> {
                val folderNode = DefaultMutableTreeNode(entry)
                var anyMatch = false
                entry.children.forEach { child -> if (addFilteredEntry(folderNode, child, filter)) anyMatch = true }
                if (anyMatch) parent.add(folderNode)
                anyMatch
            }
        }
    }

    // ── Rescan ───────────────────────────────────────────────────────────────

    private fun rescanAll() {
        buildFileWatcher.stop()
        buildFileWatcher = BuildFileWatcher { path -> rescheduleProjectScan(path) }
        ctx.register(buildFileWatcher)
        scanResults.clear()
        gitStatusCache.clear()
        activePaths = emptySet()
        onProjectSelected(null)
        forEachProject(rootNode) { scanProject(it) }
        tree.repaint()
    }

    private fun forEachProject(node: DefaultMutableTreeNode, action: (ProjectDirectory) -> Unit) {
        (node.userObject as? ProjectTreeEntry.Project)?.let { action(it.directory) }
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            forEachProject(child, action)
        }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    private fun addFolder(parentNode: DefaultMutableTreeNode?) {
        val name = JOptionPane.showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE)
            ?.trim() ?: return
        if (name.isBlank()) return
        val node = DefaultMutableTreeNode(ProjectTreeEntry.Folder(name = name))
        val parent = parentNode ?: rootNode
        treeModel.insertNodeInto(node, parent, parent.childCount)
        tree.expandPath(treePath(parent))
        tree.selectionPath = treePath(node)
        persist()
    }

    private fun addProject(parentNode: DefaultMutableTreeNode?) {
        val startDir = selectedProjectEntry()?.directory?.path?.let { File(it).parentFile }
            ?: File(System.getProperty("user.home"))
        val chooser = JFileChooser(startDir).apply {
            dialogTitle = "Select Project Directory"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val dir = ProjectDirectory(path = chooser.selectedFile.absolutePath)
        val entry = ProjectTreeEntry.Project(directory = dir)
        val node = DefaultMutableTreeNode(entry)
        val parent = parentNode ?: rootNode
        treeModel.insertNodeInto(node, parent, parent.childCount)
        tree.expandPath(treePath(parent))
        tree.selectionPath = treePath(node)
        persist()
        scanProject(dir)
    }

    private fun renameFolder(node: DefaultMutableTreeNode, folder: ProjectTreeEntry.Folder) {
        val name = JOptionPane.showInputDialog(this, "Folder name:", "Rename", JOptionPane.PLAIN_MESSAGE, null, null, folder.name)
            ?.toString()?.trim() ?: return
        if (name.isBlank()) return
        node.userObject = folder.copy(name = name)
        treeModel.nodeChanged(node)
        persist()
    }

    private fun removeNode(node: DefaultMutableTreeNode) {
        val label = when (val e = node.userObject) {
            is ProjectTreeEntry.Folder  -> "folder '${e.name}'"
            is ProjectTreeEntry.Project -> "project '${e.directory.label()}'"
            else -> "item"
        }
        if (JOptionPane.showConfirmDialog(this, "Remove $label from the project list?\n(The directory on disk is not affected.)",
                "Remove", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return
        treeModel.removeNodeFromParent(node)
        onProjectSelected(null)
        persist()
    }

    private fun deleteProjectFromDisk(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project) {
        val dir = File(entry.directory.path)
        val name = entry.directory.label()
        val fileCount = dir.walkTopDown().count()
        val confirm = JOptionPane.showConfirmDialog(this,
            "Permanently delete '$name' from disk?\n\n" +
            "Path: ${dir.absolutePath}\n" +
            "Contains: $fileCount files/directories\n\n" +
            "This cannot be undone.",
            "Delete from Disk", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
        if (confirm != JOptionPane.YES_OPTION) return

        Thread {
            val deleted = dir.deleteRecursively()
            SwingUtilities.invokeLater {
                if (deleted) {
                    treeModel.removeNodeFromParent(node)
                    onProjectSelected(null)
                    persist()
                } else {
                    JOptionPane.showMessageDialog(this@ProjectTreePanel,
                        "Could not delete '$name'. Some files may be locked or protected.",
                        "Delete Failed", JOptionPane.ERROR_MESSAGE)
                }
            }
        }.start()
    }

    private fun deleteFolderFromDisk(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Folder) {
        // Collect all project directories inside this folder
        val projects = mutableListOf<String>()
        fun collect(n: TreeNode) {
            val obj = (n as? DefaultMutableTreeNode)?.userObject
            if (obj is ProjectTreeEntry.Project) projects.add(obj.directory.path)
            for (i in 0 until n.childCount) collect(n.getChildAt(i))
        }
        collect(node)

        if (projects.isEmpty()) {
            // Virtual folder with no projects — just remove from list
            removeNode(node)
            return
        }

        val dirList = projects.joinToString("\n") { "  - $it" }
        val confirm = JOptionPane.showConfirmDialog(this,
            "Permanently delete folder '${entry.name}' and all its projects from disk?\n\n" +
            "Directories that will be deleted:\n$dirList\n\n" +
            "This cannot be undone.",
            "Delete from Disk", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
        if (confirm != JOptionPane.YES_OPTION) return

        Thread {
            val failures = projects.filter { !File(it).deleteRecursively() }
            SwingUtilities.invokeLater {
                if (failures.isEmpty()) {
                    treeModel.removeNodeFromParent(node)
                    onProjectSelected(null)
                    persist()
                } else {
                    JOptionPane.showMessageDialog(this@ProjectTreePanel,
                        "Could not delete some directories:\n${failures.joinToString("\n") { "  - $it" }}",
                        "Delete Failed", JOptionPane.ERROR_MESSAGE)
                    treeModel.removeNodeFromParent(node)
                    onProjectSelected(null)
                    persist()
                }
            }
        }.start()
    }

    private fun editTags(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project) {
        val current = entry.tags.joinToString(", ")
        val input = JOptionPane.showInputDialog(this, "Tags (comma-separated):", "Edit Tags", JOptionPane.PLAIN_MESSAGE, null, null, current)
            ?.toString()?.trim() ?: return
        val tags = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        node.userObject = entry.copy(tags = tags)
        treeModel.nodeChanged(node)
        persist()
        tree.repaint()
    }

    private fun editShellSettings(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        val dir = entry.directory
        val shellField = JTextField(dir.shellExecutable ?: "", 30)
        val startupField = JTextField(dir.startupCommand ?: "", 30)
        val defaultShell = if (IS_WINDOWS) "cmd.exe" else "/bin/bash"
        val form = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST }
            gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; add(JLabel("Shell:"), gc)
            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(shellField, gc)
            gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; add(JLabel("Startup:"), gc)
            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(startupField, gc)
            gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; gc.fill = GridBagConstraints.HORIZONTAL
            add(JLabel("<html><small>Shell: e.g. <tt>zsh</tt>, <tt>pwsh</tt> — blank = <tt>$defaultShell</tt><br>" +
                "Startup: sent on open, e.g. <tt>conda activate ml</tt></small></html>"), gc)
        }
        if (JOptionPane.showConfirmDialog(owner, form, "Shell Settings \u2014 ${dir.label()}",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return
        node.userObject = entry.copy(directory = dir.copy(
            shellExecutable = shellField.text.trim().takeIf { it.isNotEmpty() },
            startupCommand  = startupField.text.trim().takeIf { it.isNotEmpty() },
        ))
        treeModel.nodeChanged(node)
        persist()
    }

    private fun editEnv(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        EnvEditorDialog(owner, entry.directory.label(), entry.directory.env) { newEnv ->
            node.userObject = entry.copy(directory = entry.directory.copy(env = newEnv))
            treeModel.nodeChanged(node)
            persist()
        }.isVisible = true
    }

    private fun setProjectColor(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project, hex: String?) {
        node.userObject = entry.copy(directory = entry.directory.copy(color = hex))
        treeModel.nodeChanged(node); persist(); tree.repaint()
    }

    private fun setFolderColor(node: DefaultMutableTreeNode, folder: ProjectTreeEntry.Folder, hex: String?) {
        node.userObject = folder.copy(color = hex)
        treeModel.nodeChanged(node); persist(); tree.repaint()
    }

    // ── Context menus ────────────────────────────────────────────────────────

    private fun showContextMenu(node: DefaultMutableTreeNode, x: Int, y: Int) {
        val menu = JPopupMenu()
        when (val entry = node.userObject) {
            is ProjectTreeEntry.Folder -> {
                menu.add(JMenuItem("New Subfolder\u2026").apply { addActionListener { addFolder(node) } })
                menu.add(JMenuItem("Add Project\u2026").apply { addActionListener { addProject(node) } })
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameFolder(node, entry) } })
                menu.add(JMenuItem("Set Color\u2026").apply {
                    addActionListener {
                        val init = entry.color?.let { try { Color.decode(it) } catch (_: Exception) { null } }
                        val c = JColorChooser.showDialog(this@ProjectTreePanel, "Folder Color", init) ?: return@addActionListener
                        setFolderColor(node, entry, "#%02X%02X%02X".format(c.red, c.green, c.blue))
                    }
                })
                if (entry.color != null) {
                    menu.add(JMenuItem("Clear Color").apply { addActionListener { setFolderColor(node, entry, null) } })
                }
                menu.addSeparator()
                menu.add(JMenuItem("Remove from list\u2026").apply { addActionListener { removeNode(node) } })
                menu.add(JMenuItem("Delete from disk\u2026").apply { addActionListener { deleteFolderFromDisk(node, entry) } })
            }
            is ProjectTreeEntry.Project -> {
                val detected = scanResults[entry.directory.path]
                val isActive = entry.directory.path in activePaths
                if (detected != null && !isActive) {
                    menu.add(JMenuItem("\u25B6 Activate Terminal").apply {
                        addActionListener {
                            onActivate(detected)
                            activePaths = activePaths + entry.directory.path
                            tree.repaint()
                        }
                    })
                }
                if (isActive) {
                    menu.add(JMenuItem("\u23F9 Deactivate Terminal").apply {
                        addActionListener {
                            if (detected != null) onDeactivate(detected)
                            activePaths = activePaths - entry.directory.path
                            tree.repaint()
                        }
                    })
                }
                if (menu.componentCount > 0) menu.addSeparator()
                menu.add(JMenuItem("Tags\u2026").apply { addActionListener { editTags(node, entry) } })
                menu.add(JMenuItem("Shell Settings\u2026").apply { addActionListener { editShellSettings(node, entry) } })
                menu.add(JMenuItem("Environment\u2026").apply { addActionListener { editEnv(node, entry) } })
                menu.addSeparator()
                menu.add(JMenuItem("Set Color\u2026").apply {
                    addActionListener {
                        val init = entry.directory.color?.let { try { Color.decode(it) } catch (_: Exception) { null } }
                        val c = JColorChooser.showDialog(this@ProjectTreePanel, "Project Color", init) ?: return@addActionListener
                        setProjectColor(node, entry, "#%02X%02X%02X".format(c.red, c.green, c.blue))
                    }
                })
                if (entry.directory.color != null) {
                    menu.add(JMenuItem("Clear Color").apply { addActionListener { setProjectColor(node, entry, null) } })
                }
                menu.addSeparator()
                menu.add(JMenuItem("Remove from list\u2026").apply { addActionListener { removeNode(node) } })
                val dir = File(entry.directory.path)
                if (dir.exists()) {
                    menu.add(JMenuItem("Delete from disk\u2026").apply { addActionListener { deleteProjectFromDisk(node, entry) } })
                }
            }
        }
        if (menu.componentCount > 0) menu.show(tree, x, y)
    }

    private fun showRootContextMenu(x: Int, y: Int) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { addFolder(null) } })
        menu.add(JMenuItem("Add Project\u2026").apply { addActionListener { addProject(null) } })
        menu.show(tree, x, y)
    }

    // ── Cell renderer ────────────────────────────────────────────────────────

    private inner class ProjectTreeCellRenderer : TreeCellRenderer {

        private val colorStripe = JPanel().apply { preferredSize = Dimension(4, 0); isOpaque = true }
        private val nameLabel   = JLabel().apply { font = font.deriveFont(Font.BOLD, 12f) }
        private val activeDot   = JLabel("\u25CF").apply {
            font = font.deriveFont(Font.PLAIN, 10f); foreground = Color(0x4CAF50)
            border = BorderFactory.createEmptyBorder(0, 0, 0, 2)
        }
        private val agentLed = LedIndicator()
        private val dotsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(activeDot)
            add(agentLed)
        }
        private val branchLabel = JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10); foreground = Color(0x888888)
        }
        private val tagsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = false
            // Prevent tags from widening the row beyond the tree's visible width
            maximumSize = Dimension(Int.MAX_VALUE, 20)
        }
        private val nameRow = JPanel(BorderLayout(2, 0)).apply {
            isOpaque = false
            add(dotsPanel,  BorderLayout.WEST)
            add(nameLabel,  BorderLayout.CENTER)
            add(tagsPanel,  BorderLayout.EAST)
        }
        private val cellPanel = JPanel(BorderLayout(0, 1)).apply {
            isOpaque = false
            add(nameRow,     BorderLayout.NORTH)
            add(branchLabel, BorderLayout.CENTER)
        }
        private val innerPanel = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
        }
        private val projectPanel = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val base = super.getPreferredSize()
                val vp = tree.parent as? javax.swing.JViewport
                val w = vp?.width ?: tree.width
                // Always match the viewport width — never wider (prevents horizontal scroll)
                return Dimension(w.coerceAtLeast(100), base.height)
            }
        }.apply { isOpaque = true }

        private val folderLabel = JLabel().apply {
            border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
        }

        init {
            innerPanel.add(cellPanel, BorderLayout.CENTER)
            projectPanel.add(colorStripe, BorderLayout.WEST)
            projectPanel.add(innerPanel, BorderLayout.CENTER)
        }

        override fun getTreeCellRendererComponent(
            t: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean,
        ): Component {
            val node = value as? DefaultMutableTreeNode
            val bg = if (selected) (UIManager.getColor("Tree.selectionBackground") ?: t.background) else t.background
            val fg = if (selected) (UIManager.getColor("Tree.selectionForeground") ?: t.foreground) else t.foreground

            return when (val entry = node?.userObject) {
                is ProjectTreeEntry.Folder -> {
                    folderLabel.text = entry.name
                    folderLabel.icon = UIManager.getIcon(if (expanded) "Tree.openIcon" else "Tree.closedIcon")
                    folderLabel.foreground = fg
                    folderLabel.background = bg
                    folderLabel.isOpaque = true
                    val c = entry.color?.let { try { Color.decode(it) } catch (_: Exception) { null } }
                    folderLabel.border = if (c != null)
                        BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 4, 0, 0, c),
                            BorderFactory.createEmptyBorder(3, 4, 3, 6),
                        )
                    else BorderFactory.createEmptyBorder(3, 6, 3, 6)
                    folderLabel
                }
                is ProjectTreeEntry.Project -> {
                    val isActive = entry.directory.path in activePaths
                    activeDot.isVisible = isActive
                    val ledStatus = agentStatuses[entry.directory.path] ?: AgentStatus.NONE
                    agentLed.isVisible = ledStatus != AgentStatus.NONE
                    agentLed.status    = ledStatus
                    agentLed.blinkOn   = blinkOn
                    nameLabel.text = entry.directory.label()
                    nameLabel.foreground = fg

                    val gs = gitStatusCache[entry.directory.path]
                    if (gs?.branch != null) {
                        branchLabel.text = "\uE0A0 ${gs.branch}${if (gs.isDirty) "*" else ""}"
                        branchLabel.toolTipText = gs.branch
                        branchLabel.foreground = if (gs.isDirty) Color(0xE6A817) else Color(0x888888)
                    } else {
                        branchLabel.text = " "
                        branchLabel.toolTipText = null
                    }

                    tagsPanel.removeAll()
                    val scanned = scanResults[entry.directory.path]
                    when {
                        scanned == null    -> {}
                        scanned.scanFailed -> tagsPanel.add(badge("⚠", "#B71C1C"))
                        else -> {
                            scanned.buildTools.forEach { tool -> tagsPanel.add(badge(tool.tagLabel, tool.tagColor)) }
                            entry.tags.forEach { tag -> tagsPanel.add(badge(tag, "#546E7A")) }
                        }
                    }

                    val colorHex = entry.directory.color
                    colorStripe.isVisible = colorHex != null
                    if (colorHex != null) colorStripe.background = try { Color.decode(colorHex) } catch (_: Exception) { Color.GRAY }

                    projectPanel.background = bg
                    innerPanel.background = bg
                    projectPanel
                }
                else -> JLabel(value?.toString() ?: "").apply { foreground = fg; background = bg; isOpaque = true }
            }
        }

        private fun badge(text: String, colorHex: String) = JLabel(text).apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 9)
            foreground = Color.WHITE
            background = try { Color.decode(colorHex) } catch (_: Exception) { Color.GRAY }
            isOpaque = true
            border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
            preferredSize = Dimension(preferredSize.width, 14)
        }
    }

    // ── Drag and drop ────────────────────────────────────────────────────────

    private inner class TreeTransferHandler : TransferHandler() {

        private val flavor: DataFlavor = run {
            val mime = DataFlavor.javaJVMLocalObjectMimeType + ";class=" + DefaultMutableTreeNode::class.java.name
            try {
                DataFlavor(mime)
            } catch (_: ClassNotFoundException) {
                DataFlavor(DefaultMutableTreeNode::class.java, "TreeNode")
            }
        }

        override fun getSourceActions(c: JComponent) = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val path = dragPressedPath ?: (c as? JTree)?.selectionPath ?: return null
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
            return object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(flavor)
                override fun isDataFlavorSupported(f: DataFlavor) = f == flavor
                override fun getTransferData(f: DataFlavor): Any = node
            }
        }

        /** Retrieves the dragged node from the transferable without relying on a mutable field. */
        private fun nodeFrom(support: TransferSupport): DefaultMutableTreeNode? =
            try { support.transferable.getTransferData(flavor) as? DefaultMutableTreeNode }
            catch (_: Exception) { null }

        /**
         * JTree sometimes reports an INSERT drop even when the cursor is centered on a folder row.
         * If the cursor is in the middle of a folder row, treat it as an ON drop.
         */
        private fun centeredFolderDrop(dl: JTree.DropLocation): DefaultMutableTreeNode? {
            val p = dl.dropPoint ?: return null
            val rowPath = tree.getClosestPathForLocation(p.x, p.y) ?: return null
            val rowNode = rowPath.lastPathComponent as? DefaultMutableTreeNode ?: return null
            if (rowNode.userObject !is ProjectTreeEntry.Folder) return null
            val bounds = tree.getPathBounds(rowPath) ?: return null
            val top = bounds.y + (bounds.height * 0.25).toInt()
            val bottom = bounds.y + (bounds.height * 0.75).toInt()
            return if (p.y in top..bottom) rowNode else null
        }

        override fun canImport(support: TransferSupport): Boolean {
            if (!support.isDrop || !support.isDataFlavorSupported(flavor)) return false
            try {
                support.dropAction = MOVE
                support.setShowDropLocation(true)
            } catch (_: Exception) {
                // Ignore: some DnD implementations may reject explicit dropAction changes
            }
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val overrideTarget = centeredFolderDrop(dl)
            val targetPath = overrideTarget?.let { TreePath(it.path) } ?: dl.path ?: return true   // dropping at root level → OK
            val targetNode = targetPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
            val src = nodeFrom(support) ?: return false

            // Reject dropping onto itself or any of its descendants
            var n: TreeNode? = targetNode
            while (n != null) {
                if (n === src) return false
                n = n.parent
            }
            return true
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            val node = nodeFrom(support) ?: return false
            val dl   = support.dropLocation as? JTree.DropLocation ?: return false

            // Determine new parent and insertion index
            val overrideFolder = centeredFolderDrop(dl)
            val (newParent, rawIndex) = if (overrideFolder != null) {
                Pair(overrideFolder, overrideFolder.childCount)
            } else if (dl.path == null) {
                // Dropped below all rows → append to root
                Pair(rootNode, rootNode.childCount)
            } else {
                val targetNode = dl.path.lastPathComponent as? DefaultMutableTreeNode ?: return false
                if (dl.childIndex == -1) {
                    // Dropped ON a node
                    when (targetNode.userObject) {
                        is ProjectTreeEntry.Folder ->
                            Pair(targetNode, targetNode.childCount)
                        else -> {
                            // Insert after the target (as sibling)
                            val parent = targetNode.parent as? DefaultMutableTreeNode ?: rootNode
                            Pair(parent, parent.getIndex(targetNode) + 1)
                        }
                    }
                } else {
                    // Dropped BETWEEN children of targetNode
                    val parent = when (targetNode.userObject) {
                        is ProjectTreeEntry.Folder -> targetNode
                        else -> targetNode.parent as? DefaultMutableTreeNode ?: rootNode
                    }
                    val idx = if (parent === targetNode) dl.childIndex else parent.getIndex(targetNode).coerceAtLeast(0)
                    Pair(parent, idx)
                }
            }

            // Remove from old location
            val oldParent = node.parent as? DefaultMutableTreeNode ?: return false
            val oldIndex  = oldParent.getIndex(node)
            treeModel.removeNodeFromParent(node)

            // Adjust index when dragging forward within the same parent
            val insertIndex = if (newParent === oldParent && rawIndex > oldIndex)
                (rawIndex - 1).coerceAtMost(newParent.childCount)
            else
                rawIndex.coerceAtMost(newParent.childCount)

            treeModel.insertNodeInto(node, newParent, insertIndex)

            // Expand the target folder so the dropped node is immediately visible
            if (newParent !== rootNode) tree.expandPath(TreePath(newParent.path))

            val tp = treePath(node)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
            persist()
            return true
        }
    }
}
