package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.BuildFileWatcher
import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JColorChooser
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer
import javax.swing.ToolTipManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class ProjectTreePanel(
    private val ctx: AppContext,
    private val onProjectSelected: (DetectedProject?) -> Unit,
    private val onActivate: (DetectedProject) -> Unit = {},
    private val onDeactivate: (DetectedProject) -> Unit = {},
    private val onExternalFilesDropped: (List<File>) -> Unit = {},
) : JPanel(BorderLayout()) {
    private val rootNode = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(rootNode)

    private val scanResults = mutableMapOf<String, DetectedProject>()
    private val gitStatusCache = mutableMapOf<String, GitStatus>()
    private var activePaths: Set<String> = emptySet()
    private var activeOnly = false
    private var lastActiveOnly = false
    private val missingPaths = mutableSetOf<String>()
    private var pendingSelectPath: String? = null
    private val agentStatuses = mutableMapOf<String, AgentStatus>()
    private val repaintTimer = Timer(50) { tree.repaint() }.apply { isRepeats = false }
    private var filterTimer: Timer? = null
    private val scanQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<ProjectDirectory, DetectedProject>>()
    private val scanApplyTimer = Timer(25) { drainScanQueue() }.apply { isRepeats = false }
    private val scanApplyPending =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    private var lastFilter = ""
    private var pendingFilterText = ""
    private var cachedAllEntries: List<ProjectTreeEntry>? = null
    private val filterDebounceTimer = Timer(150) { doApplyFilter() }.apply { isRepeats = false }
    private val clickTraceForced =
        System.getProperty("needlecast.tree.clickTrace")?.equals("true", ignoreCase = true) == true ||
            (System.getenv("NEEDLECAST_TREE_CLICK_TRACE")?.equals("true", ignoreCase = true) == true) ||
            (System.getenv("NEEDLECAST_TREE_CLICK_TRACE") == "1")

    private fun isClickTraceEnabled(): Boolean = clickTraceForced || ctx.config.treeClickTraceEnabled

    private var clickSeq: Long = 0L
    private var lastClickTimeNs: Long = 0L
    private var lastClickKey: String? = null
    private var lastClickRow: Int = -1
    private val scanExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(2).also { exec ->
            ctx.register(
                object : io.github.rygel.needlecast.Disposable {
                    override fun dispose() {
                        exec.shutdownNow()
                    }
                },
            )
        }

    private var blinkOn = false
    private val blinkTimer =
        Timer(600) {
            blinkOn = !blinkOn
            tree.repaint()
        }.apply { isRepeats = true }

    private var buildFileWatcher =
        BuildFileWatcher { path -> rescheduleProjectScan(path) }
            .also { ctx.register(it) }

    private var dragPressedPath: TreePath? = null
    private var dragPressPoint: java.awt.Point? = null

    private var dndHandler: ProjectTreeDndHandler? = null

    private val tree =
        object : JTree(treeModel) {
            override fun getScrollableTracksViewportWidth(): Boolean = true

            override fun updateUI() {
                super.updateUI()
                if (ui !is FullWidthTreeUI) {
                    setUI(FullWidthTreeUI())
                }
                rowHeight = 0
            }

            override fun getToolTipText(e: java.awt.event.MouseEvent): String? {
                val path = getPathForLocation(e.x, e.y) ?: return null
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
                val entry = node.userObject

                if (entry is ProjectTreeEntry.Project) {
                    val projectPath = entry.directory.path
                    val row = getRowForPath(path)
                    val bounds = getPathBounds(path) ?: return projectPath
                    val renderer =
                        cellRenderer.getTreeCellRendererComponent(
                            this,
                            node,
                            isRowSelected(row),
                            isExpanded(row),
                            model.isLeaf(node),
                            row,
                            leadSelectionRow == row,
                        )
                    val rowHeight = maxOf(bounds.height, renderer.preferredSize.height)
                    val relY = e.y - bounds.y
                    if (relY < 0 || relY >= rowHeight) return projectPath

                    val isUpperRow = relY < rowHeight / 2
                    if (!isUpperRow) {
                        val gs = gitStatusCache[projectPath]
                        val parts = mutableListOf<String>()
                        if (gs != null) {
                            if (gs.isDirty) parts += "Uncommitted changes"
                            if (gs.branch != null) parts += "Branch: ${gs.branch}"
                        }
                        if (parts.isNotEmpty()) return parts.joinToString("\n")
                    } else {
                        val agentStatus = agentStatuses[projectPath]
                        if (agentStatus == AgentStatus.THINKING) return "Agent processing"
                        if (agentStatus == AgentStatus.WAITING) return "Agent waiting"
                        if (projectPath in activePaths) return "Terminal active"
                        val gs = gitStatusCache[projectPath]
                        if (gs?.isDirty == true) return "Uncommitted changes"
                    }

                    return entry.directory.redactedPath(ctx.config.privacyModeEnabled)
                }

                if (entry is ProjectTreeEntry.Folder) {
                    return "Folder: ${entry.name}"
                }

                return null
            }
        }.apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            ToolTipManager.sharedInstance().registerComponent(this)
            ui = FullWidthTreeUI()
            cellRenderer =
                ProjectTreeCellRenderer(
                    tree = this,
                    activePaths = { activePaths },
                    agentStatuses = agentStatuses,
                    blinkOn = { blinkOn },
                    missingPaths = missingPaths,
                    gitStatusCache = gitStatusCache,
                    scanResults = scanResults,
                    isPrivacyModeEnabled = { ctx.config.privacyModeEnabled },
                )
            dropMode = DropMode.ON_OR_INSERT
            dragEnabled = true
            dndHandler =
                ProjectTreeDndHandler(
                    tree = this,
                    treeModel = treeModel,
                    rootNode = rootNode,
                    missingPaths = missingPaths,
                    dragPressedPath = { dragPressedPath },
                    parentComponent = this@ProjectTreePanel,
                    persist = { persist() },
                    updateMissingPath = { path -> updateMissingPath(path) },
                    scanProject = { dir -> scanProject(dir) },
                    onProjectSelected = { project -> onProjectSelected(project) },
                    onExternalFilesDropped = { files -> onExternalFilesDropped(files) },
                )
            transferHandler = dndHandler!!.transferHandler

            addComponentListener(
                object : java.awt.event.ComponentAdapter() {
                    override fun componentResized(e: java.awt.event.ComponentEvent) {
                        invalidateTreeLayout()
                    }
                },
            )
        }

    private val treeScroll =
        JScrollPane(tree).apply {
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
    private val emptyPlaceholder =
        JPanel(GridBagLayout()).apply {
            val label = JLabel("<html><div style='text-align:center;font-size:14px;color:#888;'>Add a project to get started</div></html>")
            val button = JButton("Add Project").apply { addActionListener { addProject(null) } }
            val inner =
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(label)
                    add(Box.createVerticalStrut(12))
                    add(button)
                    button.alignmentX = Component.CENTER_ALIGNMENT
                    label.alignmentX = Component.CENTER_ALIGNMENT
                }
            add(inner, GridBagConstraints())
        }
    private val centerPanel =
        JPanel(CardLayout()).apply {
            add(treeScroll, "tree")
            add(emptyPlaceholder, "empty")
        }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectTreePanel::class.java)
    }

    init {
        fun iconBtn(
            icon: javax.swing.Icon?,
            text: String,
            tip: String,
        ) = JButton(icon).apply {
            if (icon == null) this.text = text
            toolTipText = tip
            isFocusPainted = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
        }
        val addFolderBtn =
            iconBtn(RemixIcons.icon("ri-folder-add-line", 16), "", "Add a folder to organize projects").apply {
                addActionListener { addFolder(selectedFolderNode()) }
            }
        val addProjectBtn =
            iconBtn(RemixIcons.icon("ri-file-add-line", 16), "", "Add a project directory").apply {
                addActionListener { addProject(selectedFolderNode()) }
            }
        val rescanBtn =
            iconBtn(RemixIcons.icon("ri-refresh-line", 16), "", "Rescan all projects (F5)").apply {
                addActionListener { rescanAll() }
            }

        val privacyBtn =
            JButton(RemixIcons.icon("ri-eye-line", 16)).apply {
                toolTipText = "Privacy Mode \u2014 hide private project names and paths"
                isFocusPainted = false
                isContentAreaFilled = false
                border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
                isOpaque = false
                val updateState = {
                    icon = if (ctx.config.privacyModeEnabled) RemixIcons.icon("ri-eye-off-line", 16) else RemixIcons.icon("ri-eye-line", 16)
                    toolTipText =
                        if (ctx.config.privacyModeEnabled) "Privacy Mode ON \u2014 click to show names" else "Privacy Mode \u2014 hide private project names and paths"
                }
                updateState()
                addActionListener {
                    ctx.updateConfig(ctx.config.copy(privacyModeEnabled = !ctx.config.privacyModeEnabled))
                    updateState()
                    tree.repaint()
                }
            }

        val activeOnlyBtn =
            JToggleButton(RemixIcons.icon("ri-play-circle-line", 16)).apply {
                toolTipText = "Show active projects only"
                isFocusPainted = false
                isContentAreaFilled = false
                border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
                isOpaque = false
                addActionListener {
                    activeOnly = isSelected
                    icon = if (isSelected) RemixIcons.icon("ri-stop-line", 16) else RemixIcons.icon("ri-play-circle-line", 16)
                    toolTipText = if (isSelected) "Showing active projects only" else "Show active projects only"
                    doApplyFilter()
                }
            }

        val filterField =
            JTextField().apply {
                accessibleContext.accessibleName = "Filter projects"
                toolTipText = "Filter projects"
                putClientProperty("JTextField.placeholderText", "Filter\u2026")
                document.addDocumentListener(
                    object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent) {
                            pendingFilterText = text
                            filterDebounceTimer.restart()
                        }

                        override fun removeUpdate(e: DocumentEvent) {
                            pendingFilterText = text
                            filterDebounceTimer.restart()
                        }

                        override fun changedUpdate(e: DocumentEvent) {}
                    },
                )
                actionMap.put(
                    "clear-filter",
                    object : javax.swing.AbstractAction() {
                        override fun actionPerformed(e: java.awt.event.ActionEvent) {
                            text = ""
                            pendingFilterText = ""
                            doApplyFilter()
                        }
                    },
                )
                inputMap.put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "clear-filter")
            }

        val northPanel =
            JPanel(BorderLayout(4, 2)).apply {
                border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
                val btnPanel =
                    JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                        isOpaque = false
                        add(privacyBtn)
                        add(activeOnlyBtn)
                        add(addFolderBtn)
                        add(addProjectBtn)
                        add(rescanBtn)
                    }
                add(filterField, BorderLayout.CENTER)
                add(btnPanel, BorderLayout.EAST)
            }

        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val project =
                when (val entry = node.userObject) {
                    is ProjectTreeEntry.Project -> scanResults[entry.directory.path]
                    else -> null
                }
            val selectionTimeNs = System.nanoTime()
            if (isClickTraceEnabled()) {
                val key = entryKey(node.userObject)
                val row = tree.leadSelectionRow
                val dtMs = if (lastClickTimeNs > 0L) (selectionTimeNs - lastClickTimeNs) / 1_000_000 else -1
                val match = key != null && key == lastClickKey
                logger.info("tree-select seq={} row={} key={} dtFromClickMs={} match={}", clickSeq, row, key, dtMs, match)
            }
            SwingUtilities.invokeLater {
                if (isClickTraceEnabled()) {
                    val delayMs = (System.nanoTime() - selectionTimeNs) / 1_000_000
                    logger.info("tree-select-callback seq={} delayMs={}", clickSeq, delayMs)
                }
                onProjectSelected(project)
            }
        }

        tree.addMouseListener(
            object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        val closest = tree.getClosestPathForLocation(e.x, e.y)
                        val bounds = if (closest != null) tree.getPathBounds(closest) else null
                        val inRow =
                            if (bounds != null && closest != null) {
                                val row = tree.getRowForPath(closest)
                                val node = closest.lastPathComponent
                                val rendererHeight =
                                    if (row >= 0) {
                                        val renderer =
                                            tree.cellRenderer.getTreeCellRendererComponent(
                                                tree,
                                                node,
                                                tree.isRowSelected(row),
                                                tree.isExpanded(row),
                                                tree.model.isLeaf(node),
                                                row,
                                                tree.leadSelectionRow == row,
                                            )
                                        renderer.preferredSize.height
                                    } else {
                                        bounds.height
                                    }
                                val effectiveHeight = maxOf(bounds.height, rendererHeight)
                                e.y >= bounds.y && e.y < bounds.y + effectiveHeight
                            } else {
                                false
                            }
                        dragPressedPath = if (inRow) closest else null
                        dragPressPoint = if (dragPressedPath != null) java.awt.Point(e.x, e.y) else null
                        if (inRow && closest != null) {
                            if (isClickTraceEnabled()) {
                                clickSeq++
                                lastClickTimeNs = System.nanoTime()
                                lastClickRow = tree.getRowForPath(closest)
                                lastClickKey = entryKey((closest.lastPathComponent as? DefaultMutableTreeNode)?.userObject)
                                logger.info("tree-click seq={} row={} key={}", clickSeq, lastClickRow, lastClickKey)
                            }
                            tree.selectionPath = closest
                            tree.requestFocusInWindow()
                        }
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
            },
        )

        tree.addMouseMotionListener(
            object : java.awt.event.MouseMotionAdapter() {
                override fun mouseDragged(e: java.awt.event.MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return
                    val press = dragPressPoint ?: return
                    val threshold =
                        java.awt.dnd.DragSource
                            .getDragThreshold()
                    if (Math.abs(e.x - press.x) > threshold || Math.abs(e.y - press.y) > threshold) {
                        dragPressPoint = null
                        tree.transferHandler?.exportAsDrag(tree, e, javax.swing.TransferHandler.MOVE)
                    }
                }
            },
        )

        add(northPanel, BorderLayout.NORTH)
        add(
            JScrollPane(tree).apply {
                horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            },
            BorderLayout.CENTER,
        )

        loadFromConfig()
        updateEmptyState()
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private fun loadFromConfig() {
        cachedAllEntries = null
        rootNode.removeAllChildren()
        missingPaths.clear()
        migrateOrLoad().forEach { addEntryNode(rootNode, it) }
        treeModel.reload()
        expandAll()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val hasEntries = rootNode.childCount > 0
        (centerPanel.layout as CardLayout).show(centerPanel, if (hasEntries) "tree" else "empty")
    }

    private fun migrateOrLoad(): List<ProjectTreeEntry> {
        val cfg = ctx.config
        if (cfg.projectTree.isNotEmpty()) return cfg.projectTree
        if (cfg.groups.isEmpty()) return emptyList()
        val migrated =
            cfg.groups.map { group ->
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

    private fun addEntryNode(
        parent: DefaultMutableTreeNode,
        entry: ProjectTreeEntry,
        scan: Boolean = true,
    ) {
        val node = DefaultMutableTreeNode(entry)
        parent.add(node)
        when (entry) {
            is ProjectTreeEntry.Folder -> {
                entry.children.forEach { addEntryNode(node, it) }
            }

            is ProjectTreeEntry.Project -> {
                val missing = updateMissingPath(entry.directory.path)
                if (scan && !missing) scanProject(entry.directory)
            }
        }
    }

    private fun updateMissingPath(path: String): Boolean {
        val missing = !File(path).isDirectory
        if (missing) missingPaths += path else missingPaths.remove(path)
        return missing
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) tree.expandRow(i++)
    }

    // ── Scanning ─────────────────────────────────────────────────────────────

    private fun scanProject(dir: ProjectDirectory) {
        scanExecutor.execute {
            val result =
                try {
                    ctx.scanner.scan(dir) ?: DetectedProject(dir, emptySet(), emptyList())
                } catch (e: Exception) {
                    logger.warn("Failed to scan '${dir.label()}'", e)
                    DetectedProject(dir, emptySet(), emptyList(), scanFailed = true)
                }
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    private fun rescheduleProjectScan(path: String) {
        val dir = findProjectEntry(rootNode, path)?.directory ?: return
        scanExecutor.execute {
            val result =
                try {
                    ctx.scanner.scan(dir)
                } catch (_: Exception) {
                    null
                } ?: return@execute
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    private fun fetchGitStatus(path: String) {
        object : SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = ctx.gitService.readStatus(path)

            override fun done() {
                val status =
                    try {
                        get()
                    } catch (_: Exception) {
                        return
                    }
                gitStatusCache[path] = status
                requestTreeRepaint()
            }
        }.execute()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun requestFocusOnTree() = tree.requestFocusInWindow()

    fun triggerRescan() = rescanAll()

    fun reloadFromConfig() {
        scanResults.clear()
        gitStatusCache.clear()
        activePaths = emptySet()
        pendingSelectPath = null
        loadFromConfig()
    }

    fun triggerActivateTerminal() {
        val entry = selectedProjectEntry() ?: return
        val detected = scanResults[entry.directory.path] ?: return
        if (entry.directory.path !in activePaths) {
            onActivate(detected)
            activePaths = activePaths + entry.directory.path
            tree.repaint()
        }
    }

    fun invalidateTreeLayout() {
        val ui = tree.ui as? javax.swing.plaf.basic.BasicTreeUI ?: return
        try {
            val field = javax.swing.plaf.basic.BasicTreeUI::class.java.getDeclaredField("treeState")
            field.isAccessible = true
            (field.get(ui) as? javax.swing.tree.AbstractLayoutCache)?.invalidateSizes()
        } catch (_: Exception) {
        }
        tree.revalidate()
        tree.repaint()
    }

    fun setActivePaths(paths: Set<String>) {
        activePaths = paths
        requestTreeRepaint()
    }

    fun updateProjectStatus(
        path: String,
        status: AgentStatus,
    ) {
        agentStatuses[path] = status
        if (agentStatuses.values.any { it == AgentStatus.THINKING }) {
            blinkTimer.start()
        } else {
            blinkTimer.stop()
        }
        requestTreeRepaint()
    }

    private fun requestTreeRepaint() {
        repaintTimer.restart()
    }

    private fun drainScanQueue() {
        val maxPerTick = 10
        var updated = false
        var processed = 0
        while (processed < maxPerTick) {
            val next = scanQueue.poll() ?: break
            val (dir, result) = next
            scanResults[dir.path] = result
            updated = true
            if (!result.scanFailed) {
                fetchGitStatus(dir.path)
                Thread {
                    buildFileWatcher.watch(dir.path)
                }.apply {
                    isDaemon = true
                    name = "build-file-watch-${dir.label()}"
                }.start()
            }
            val pending = pendingSelectPath
            if (pending == dir.path) {
                selectByPath(pending)
            } else {
                val selNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
                val selEntry = selNode?.userObject as? ProjectTreeEntry.Project
                if (selEntry?.directory?.path == dir.path) {
                    onProjectSelected(result)
                }
            }
            processed++
        }
        if (updated) requestTreeRepaint()
        if (scanQueue.isNotEmpty()) {
            scanApplyTimer.restart()
        } else {
            scanApplyPending.set(false)
        }
    }

    private fun scheduleScanApply() {
        if (scanApplyPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater { scanApplyTimer.restart() }
        }
    }

    private fun entryKey(entry: Any?): String? =
        when (entry) {
            is ProjectTreeEntry.Folder -> "folder:${entry.name}"
            is ProjectTreeEntry.Project -> "project:${entry.directory.path}"
            else -> null
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

    internal fun simulateExternalDropForTest(
        items: List<File>,
        targetFolder: String? = null,
    ): Boolean = dndHandler!!.simulateExternalDropForTest(items, targetFolder)

    internal fun findMissingMatch(droppedName: String): DefaultMutableTreeNode? = dndHandler!!.findMissingMatch(droppedName)

    // ── Tree helpers ─────────────────────────────────────────────────────────

    private fun selectedNode(): DefaultMutableTreeNode? = tree.lastSelectedPathComponent as? DefaultMutableTreeNode

    private fun selectedFolderNode(): DefaultMutableTreeNode? {
        val node = selectedNode() ?: return null
        return when (node.userObject) {
            is ProjectTreeEntry.Folder -> node
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

    private fun findProjectNode(
        parent: DefaultMutableTreeNode,
        path: String,
    ): DefaultMutableTreeNode? {
        if ((parent.userObject as? ProjectTreeEntry.Project)?.directory?.path == path) return parent
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val found = findProjectNode(child, path)
            if (found != null) return found
        }
        return null
    }

    private fun findProjectEntry(
        parent: DefaultMutableTreeNode,
        path: String,
    ): ProjectTreeEntry.Project? = findProjectNode(parent, path)?.userObject as? ProjectTreeEntry.Project

    private fun treePath(node: DefaultMutableTreeNode) = TreePath(node.path)

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun buildTree(): List<ProjectTreeEntry> = (0 until rootNode.childCount).mapNotNull { buildEntry(rootNode.getChildAt(it) as? DefaultMutableTreeNode) }

    private fun buildEntry(node: DefaultMutableTreeNode?): ProjectTreeEntry? {
        node ?: return null
        return when (val entry = node.userObject) {
            is ProjectTreeEntry.Folder -> {
                entry.copy(
                    children = (0 until node.childCount).mapNotNull { buildEntry(node.getChildAt(it) as? DefaultMutableTreeNode) },
                )
            }

            is ProjectTreeEntry.Project -> {
                entry
            }

            else -> {
                null
            }
        }
    }

    private fun persist() {
        ctx.updateConfig(ctx.config.copy(projectTree = buildTree()))
    }

    // ── Filter ───────────────────────────────────────────────────────────────

    private fun doApplyFilter() {
        val filter = pendingFilterText.trim()
        if (filter == lastFilter && activeOnly == lastActiveOnly) return
        lastFilter = filter
        lastActiveOnly = activeOnly
        rootNode.removeAllChildren()
        if (filter.isEmpty() && !activeOnly) {
            val all = cachedAllEntries ?: migrateOrLoad().also { cachedAllEntries = it }
            all.forEach { addEntryNode(rootNode, it, scan = false) }
            ensureScans(all)
        } else {
            val source = cachedAllEntries ?: ctx.config.projectTree
            val filtered = mutableListOf<ProjectTreeEntry>()
            for (entry in source) {
                val result = filterEntry(entry, filter)
                if (result != null) filtered.add(result)
            }
            filtered.forEach { addEntryNode(rootNode, it, scan = false) }
        }
        treeModel.reload()
        if (filter.isEmpty() && !activeOnly) expandAll()
    }

    fun invalidateFilterCache() {
        cachedAllEntries = null
    }

    private fun filterEntry(
        entry: ProjectTreeEntry,
        textFilter: String,
    ): ProjectTreeEntry? =
        when (entry) {
            is ProjectTreeEntry.Project -> {
                val matchesText =
                    textFilter.isEmpty() ||
                        entry.directory
                            .label()
                            .lowercase()
                            .contains(textFilter) ||
                        entry.tags.any { it.lowercase().contains(textFilter) }
                val matchesActive = !activeOnly || entry.directory.path in activePaths
                if (matchesText && matchesActive) entry else null
            }

            is ProjectTreeEntry.Folder -> {
                val filteredChildren = entry.children.mapNotNull { filterEntry(it, textFilter) }
                if (filteredChildren.isNotEmpty()) entry.copy(children = filteredChildren) else null
            }
        }

    private fun ensureScans(entries: List<ProjectTreeEntry>) {
        fun walk(entry: ProjectTreeEntry) {
            when (entry) {
                is ProjectTreeEntry.Project -> {
                    if (scanResults[entry.directory.path] != null) return
                    val missing = updateMissingPath(entry.directory.path)
                    if (!missing) scanProject(entry.directory)
                }

                is ProjectTreeEntry.Folder -> {
                    entry.children.forEach { walk(it) }
                }
            }
        }
        entries.forEach { walk(it) }
    }

    // ── Rescan ───────────────────────────────────────────────────────────────

    private fun rescanAll() {
        buildFileWatcher.unwatchAll()
        scanResults.clear()
        gitStatusCache.clear()
        activePaths = emptySet()
        missingPaths.clear()
        onProjectSelected(null)
        forEachProject(rootNode) {
            val missing = updateMissingPath(it.path)
            if (!missing) scanProject(it)
        }
        tree.repaint()
    }

    private fun forEachProject(
        node: DefaultMutableTreeNode,
        action: (ProjectDirectory) -> Unit,
    ) {
        (node.userObject as? ProjectTreeEntry.Project)?.let { action(it.directory) }
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            forEachProject(child, action)
        }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    private fun addFolder(parentNode: DefaultMutableTreeNode?) {
        val name =
            JOptionPane
                .showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE)
                ?.trim() ?: return
        if (name.isBlank()) return
        val node = DefaultMutableTreeNode(ProjectTreeEntry.Folder(name = name))
        val parent = parentNode ?: rootNode
        treeModel.insertNodeInto(node, parent, parent.childCount)
        tree.expandPath(treePath(parent))
        tree.selectionPath = treePath(node)
        persist()
        updateEmptyState()
    }

    private fun addProject(parentNode: DefaultMutableTreeNode?) {
        val startDir =
            selectedProjectEntry()?.directory?.path?.let { File(it).parentFile }
                ?: File(System.getProperty("user.home"))
        val chooser =
            JFileChooser(startDir).apply {
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
        updateEmptyState()
        val missing = updateMissingPath(dir.path)
        if (!missing) scanProject(dir)
        tree.repaint()
    }

    private fun renameFolder(
        node: DefaultMutableTreeNode,
        folder: ProjectTreeEntry.Folder,
    ) {
        val name =
            JOptionPane
                .showInputDialog(this, "Folder name:", "Rename", JOptionPane.PLAIN_MESSAGE, null, null, folder.name)
                ?.toString()
                ?.trim() ?: return
        if (name.isBlank()) return
        node.userObject = folder.copy(name = name)
        treeModel.nodeChanged(node)
        persist()
    }

    private fun removeNode(node: DefaultMutableTreeNode) {
        val label =
            when (val e = node.userObject) {
                is ProjectTreeEntry.Folder -> "folder '${e.name}'"
                is ProjectTreeEntry.Project -> "project '${e.directory.label()}'"
                else -> "item"
            }
        if (JOptionPane.showConfirmDialog(
                this,
                "Remove $label from the project list?\n(The directory on disk is not affected.)",
                "Remove",
                JOptionPane.OK_CANCEL_OPTION,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }
        (node.userObject as? ProjectTreeEntry.Project)?.let { missingPaths.remove(it.directory.path) }
        treeModel.removeNodeFromParent(node)
        onProjectSelected(null)
        persist()
    }

    private fun deleteProjectFromDisk(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val dir = File(entry.directory.path)
        val name = entry.directory.label()
        object : SwingWorker<Long, Void>() {
            override fun doInBackground(): Long = runCatching { dir.walkTopDown().count().toLong() }.getOrDefault(-1L)

            override fun done() {
                val fileCount =
                    try {
                        get()
                    } catch (_: Exception) {
                        -1
                    }
                val countLine = if (fileCount >= 0) "Contains: $fileCount files/directories\n\n" else ""
                val confirm =
                    JOptionPane.showConfirmDialog(
                        this@ProjectTreePanel,
                        "Permanently delete '$name' from disk?\n\n" +
                            "Path: ${dir.absolutePath}\n" +
                            countLine +
                            "This cannot be undone.",
                        "Delete from Disk",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                    )
                if (confirm != JOptionPane.YES_OPTION) return

                Thread {
                    val deleted = dir.deleteRecursively()
                    SwingUtilities.invokeLater {
                        if (deleted) {
                            treeModel.removeNodeFromParent(node)
                            onProjectSelected(null)
                            persist()
                            updateEmptyState()
                        } else {
                            JOptionPane.showMessageDialog(
                                this@ProjectTreePanel,
                                "Could not delete '$name'. Some files may be locked or protected.",
                                "Delete Failed",
                                JOptionPane.ERROR_MESSAGE,
                            )
                        }
                    }
                }.start()
            }
        }.execute()
    }

    private fun deleteFolderFromDisk(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Folder,
    ) {
        val projects = mutableListOf<String>()

        fun collect(n: javax.swing.tree.TreeNode) {
            val obj = (n as? DefaultMutableTreeNode)?.userObject
            if (obj is ProjectTreeEntry.Project) projects.add(obj.directory.path)
            for (i in 0 until n.childCount) collect(n.getChildAt(i))
        }
        collect(node)

        if (projects.isEmpty()) {
            removeNode(node)
            return
        }

        val dirList = projects.joinToString("\n") { "  - $it" }
        val confirm =
            JOptionPane.showConfirmDialog(
                this,
                "Permanently delete folder '${entry.name}' and all its projects from disk?\n\n" +
                    "Directories that will be deleted:\n$dirList\n\n" +
                    "This cannot be undone.",
                "Delete from Disk",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
        if (confirm != JOptionPane.YES_OPTION) return

        Thread {
            val failures = projects.filter { !File(it).deleteRecursively() }
            SwingUtilities.invokeLater {
                if (failures.isEmpty()) {
                    treeModel.removeNodeFromParent(node)
                    onProjectSelected(null)
                    persist()
                    updateEmptyState()
                } else {
                    JOptionPane.showMessageDialog(
                        this@ProjectTreePanel,
                        "Could not delete some directories:\n${failures.joinToString("\n") { "  - $it" }}",
                        "Delete Failed",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    treeModel.removeNodeFromParent(node)
                    onProjectSelected(null)
                    persist()
                    updateEmptyState()
                }
            }
        }.start()
    }

    private fun editTags(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val current = entry.tags.joinToString(", ")
        val input =
            JOptionPane
                .showInputDialog(this, "Tags (comma-separated):", "Edit Tags", JOptionPane.PLAIN_MESSAGE, null, null, current)
                ?.toString()
                ?.trim() ?: return
        val tags = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        node.userObject = entry.copy(tags = tags)
        treeModel.nodeChanged(node)
        persist()
        tree.repaint()
    }

    private fun editShellSettings(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        val dir = entry.directory
        val shellField = JTextField(dir.shellExecutable ?: "", 30)
        val startupField = JTextField(dir.startupCommand ?: "", 30)
        val defaultShell =
            when {
                IS_WINDOWS -> "cmd.exe"
                IS_MAC -> "/bin/zsh"
                else -> "/bin/bash"
            }
        val form =
            JPanel(GridBagLayout()).apply {
                border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                val gc =
                    GridBagConstraints().apply {
                        insets = Insets(4, 4, 4, 4)
                        anchor = GridBagConstraints.WEST
                    }
                gc.gridx = 0
                gc.gridy = 0
                gc.weightx = 0.0
                gc.fill = GridBagConstraints.NONE
                add(JLabel("Shell:"), gc)
                gc.gridx = 1
                gc.weightx = 1.0
                gc.fill = GridBagConstraints.HORIZONTAL
                add(shellField, gc)
                gc.gridx = 0
                gc.gridy = 1
                gc.weightx = 0.0
                gc.fill = GridBagConstraints.NONE
                add(JLabel("Startup:"), gc)
                gc.gridx = 1
                gc.weightx = 1.0
                gc.fill = GridBagConstraints.HORIZONTAL
                add(startupField, gc)
                gc.gridx = 0
                gc.gridy = 2
                gc.gridwidth = 2
                gc.fill = GridBagConstraints.HORIZONTAL
                add(
                    JLabel(
                        "<html><small>Shell: e.g. <tt>zsh</tt>, <tt>pwsh</tt> \u2014 blank = <tt>$defaultShell</tt><br>" +
                            "Startup: sent on open, e.g. <tt>conda activate ml</tt></small></html>",
                    ),
                    gc,
                )
            }
        if (JOptionPane.showConfirmDialog(
                owner,
                form,
                "Shell Settings \u2014 ${dir.label(ctx.config.privacyModeEnabled)}",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }
        node.userObject =
            entry.copy(
                directory =
                    dir.copy(
                        shellExecutable = shellField.text.trim().takeIf { it.isNotEmpty() },
                        startupCommand = startupField.text.trim().takeIf { it.isNotEmpty() },
                    ),
            )
        treeModel.nodeChanged(node)
        persist()
    }

    private fun editEnv(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        EnvEditorDialog(owner, entry.directory.label(ctx.config.privacyModeEnabled), entry.directory.env) { newEnv ->
            node.userObject = entry.copy(directory = entry.directory.copy(env = newEnv))
            treeModel.nodeChanged(node)
            persist()
        }.isVisible = true
    }

    private fun editScriptDirs(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        val dir = entry.directory
        val listModel = DefaultListModel<String>().apply { dir.extraScanDirs.forEach { addElement(it) } }
        val list =
            JList(listModel).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                visibleRowCount = 6
            }
        val addBtn = JButton("Add\u2026")
        val removeBtn = JButton("Remove").apply { isEnabled = false }

        list.addListSelectionListener { removeBtn.isEnabled = list.selectedIndex >= 0 }

        addBtn.addActionListener {
            val chooser = JFileChooser(dir.path).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return@addActionListener
            val stored = makeRelativeIfPossible(chooser.selectedFile.canonicalPath, dir.path)
            if ((0 until listModel.size).none { listModel.getElementAt(it) == stored }) listModel.addElement(stored)
        }

        removeBtn.addActionListener {
            val i = list.selectedIndex
            if (i >= 0) {
                listModel.remove(i)
                list.clearSelection()
            }
        }

        val form =
            JPanel(BorderLayout(4, 4)).apply {
                border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                add(
                    JLabel("<html><small>\"scripts/\" and \"bin/\" in the project root are always scanned automatically.</small></html>"),
                    BorderLayout.NORTH,
                )
                add(JScrollPane(list), BorderLayout.CENTER)
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                        add(addBtn)
                        add(removeBtn)
                    },
                    BorderLayout.SOUTH,
                )
            }

        if (JOptionPane.showConfirmDialog(
                owner,
                form,
                "Script Directories \u2014 ${dir.label(ctx.config.privacyModeEnabled)}",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }

        val newDirs = (0 until listModel.size).map { listModel.getElementAt(it) }
        val updated = dir.copy(extraScanDirs = newDirs)
        node.userObject = entry.copy(directory = updated)
        treeModel.nodeChanged(node)
        persist()
        scanProject(updated)
    }

    private fun makeRelativeIfPossible(
        absolute: String,
        base: String,
    ): String {
        val rel =
            File(base)
                .toPath()
                .relativize(File(absolute).toPath())
                .toString()
                .replace(File.separatorChar, '/')
        return if (rel.startsWith("..")) absolute else rel
    }

    private fun setProjectColor(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
        hex: String?,
    ) {
        node.userObject = entry.copy(directory = entry.directory.copy(color = hex))
        treeModel.nodeChanged(node)
        persist()
        tree.repaint()
    }

    private fun setFolderColor(
        node: DefaultMutableTreeNode,
        folder: ProjectTreeEntry.Folder,
        hex: String?,
    ) {
        node.userObject = folder.copy(color = hex)
        treeModel.nodeChanged(node)
        persist()
        tree.repaint()
    }

    // ── Context menus ────────────────────────────────────────────────────────

    private fun collectTopTags(count: Int): List<String> {
        val freq = mutableMapOf<String, Int>()

        fun walk(node: DefaultMutableTreeNode) {
            val entry = node.userObject
            if (entry is ProjectTreeEntry.Project) {
                entry.tags.forEach { tag -> freq[tag] = (freq[tag] ?: 0) + 1 }
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i) as DefaultMutableTreeNode)
        }
        walk(rootNode)
        return freq.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(count)
            .map { it.key }
    }

    private fun openInFileManager(path: String) {
        val file = File(path)
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            } else if (IS_WINDOWS) {
                ProcessBuilder("explorer.exe", path).start()
            } else if (IS_MAC) {
                ProcessBuilder("open", path).start()
            } else {
                ProcessBuilder("xdg-open", path).start()
            }
        } catch (e: Exception) {
            logger.warn("Failed to open file manager for '$path'", e)
        }
    }

    private fun buildColorMenu(
        title: String,
        currentHex: String?,
        presets: List<Pair<String, String>>,
        onSet: (String?) -> Unit,
    ): JMenu =
        JMenu(title).apply {
            presets.forEach { (label, hex) ->
                add(
                    JMenuItem(label, colorSwatchIcon(hex)).apply {
                        addActionListener { onSet(hex) }
                    },
                )
            }
            addSeparator()
            add(
                JMenuItem("Custom\u2026").apply {
                    addActionListener {
                        val init =
                            currentHex?.let {
                                try {
                                    Color.decode(it)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        val c = JColorChooser.showDialog(this@ProjectTreePanel, title, init) ?: return@addActionListener
                        onSet("#%02X%02X%02X".format(c.red, c.green, c.blue))
                    }
                },
            )
            if (currentHex != null) {
                add(JMenuItem("Clear").apply { addActionListener { onSet(null) } })
            }
        }

    private fun showContextMenu(
        node: DefaultMutableTreeNode,
        x: Int,
        y: Int,
    ) {
        val menu = JPopupMenu()
        when (val entry = node.userObject) {
            is ProjectTreeEntry.Folder -> {
                menu.add(JMenuItem("New Subfolder\u2026").apply { addActionListener { addFolder(node) } })
                menu.add(JMenuItem("Add Project\u2026").apply { addActionListener { addProject(node) } })
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameFolder(node, entry) } })
                val folderColorPresets =
                    listOf(
                        "Red" to "#E53935",
                        "Orange" to "#F57C00",
                        "Blue" to "#1565C0",
                        "Green" to "#2E7D32",
                        "Purple" to "#6A1B9A",
                        "Teal" to "#00695C",
                    )
                menu.add(
                    buildColorMenu("Color", entry.color, folderColorPresets) { hex ->
                        setFolderColor(node, node.userObject as ProjectTreeEntry.Folder, hex)
                    },
                )
                menu.addSeparator()
                menu.add(JMenuItem("Remove").apply { addActionListener { removeNode(node) } })
                menu.add(
                    JMenu("Advanced").apply {
                        add(
                            JMenuItem("Delete from disk\u2026").apply {
                                foreground = Color(0xE53935)
                                addActionListener { deleteFolderFromDisk(node, entry) }
                            },
                        )
                    },
                )
            }

            is ProjectTreeEntry.Project -> {
                val detected = scanResults[entry.directory.path]
                val isActive = entry.directory.path in activePaths
                if (detected != null && !isActive) {
                    menu.add(
                        JMenuItem("Activate Terminal", RemixIcons.icon("ri-play-circle-line", 12)).apply {
                            addActionListener {
                                onActivate(detected)
                                activePaths = activePaths + entry.directory.path
                                tree.repaint()
                            }
                        },
                    )
                }
                if (isActive) {
                    menu.add(
                        JMenuItem("Deactivate Terminal", RemixIcons.icon("ri-stop-line", 12)).apply {
                            addActionListener {
                                if (detected != null) onDeactivate(detected)
                                activePaths = activePaths - entry.directory.path
                                tree.repaint()
                            }
                        },
                    )
                }
                if (menu.componentCount > 0) menu.addSeparator()
                val dir = File(entry.directory.path)
                if (dir.exists()) {
                    val label = if (IS_MAC) "Open in Finder" else "Open in Explorer"
                    menu.add(
                        JMenuItem(label).apply {
                            addActionListener { openInFileManager(entry.directory.path) }
                        },
                    )
                    menu.addSeparator()
                }
                val topTags = collectTopTags(10)
                menu.add(
                    JMenu("Tags").apply {
                        topTags.forEach { tag ->
                            add(
                                JCheckBoxMenuItem(tag, tag in entry.tags).apply {
                                    addActionListener {
                                        val cur = node.userObject as? ProjectTreeEntry.Project ?: return@addActionListener
                                        val newTags = if (isSelected) cur.tags + tag else cur.tags.filter { it != tag }
                                        node.userObject = cur.copy(tags = newTags)
                                        treeModel.nodeChanged(node)
                                        persist()
                                        tree.repaint()
                                    }
                                },
                            )
                        }
                        if (topTags.isNotEmpty()) addSeparator()
                        add(JMenuItem("Edit\u2026").apply { addActionListener { editTags(node, entry) } })
                    },
                )
                menu.add(
                    JCheckBoxMenuItem("Private", entry.directory.isPrivate).apply {
                        toolTipText = "Hide project name and path when Privacy Mode is on"
                        addActionListener {
                            val cur = node.userObject as? ProjectTreeEntry.Project ?: return@addActionListener
                            node.userObject = cur.copy(directory = cur.directory.copy(isPrivate = isSelected))
                            treeModel.nodeChanged(node)
                            persist()
                            tree.repaint()
                        }
                    },
                )
                menu.add(JMenuItem("Shell Settings\u2026").apply { addActionListener { editShellSettings(node, entry) } })
                menu.add(JMenuItem("Environment\u2026").apply { addActionListener { editEnv(node, entry) } })
                menu.add(JMenuItem("Script Directories\u2026").apply { addActionListener { editScriptDirs(node, entry) } })
                menu.addSeparator()
                val colorPresets =
                    listOf(
                        "Red" to "#E53935",
                        "Orange" to "#F57C00",
                        "Blue" to "#1565C0",
                        "Green" to "#2E7D32",
                        "Purple" to "#6A1B9A",
                        "Teal" to "#00695C",
                    )
                menu.add(
                    buildColorMenu("Color", entry.directory.color, colorPresets) { hex ->
                        setProjectColor(node, node.userObject as ProjectTreeEntry.Project, hex)
                    },
                )
                menu.addSeparator()
                menu.add(JMenuItem("Remove").apply { addActionListener { removeNode(node) } })
                if (dir.exists()) {
                    menu.add(
                        JMenu("Advanced").apply {
                            add(
                                JMenuItem("Delete from disk\u2026").apply {
                                    foreground = Color(0xE53935)
                                    addActionListener { deleteProjectFromDisk(node, entry) }
                                },
                            )
                        },
                    )
                }
            }
        }
        if (menu.componentCount > 0) menu.show(tree, x, y)
    }

    private fun showRootContextMenu(
        x: Int,
        y: Int,
    ) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { addFolder(null) } })
        menu.add(JMenuItem("Add Project\u2026").apply { addActionListener { addProject(null) } })
        menu.show(tree, x, y)
    }
}
