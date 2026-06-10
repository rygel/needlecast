package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.BuildFileWatcher
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.JTree
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
    override val ctx: AppContext,
    override val onProjectSelected: (DetectedProject?) -> Unit,
    override val onActivate: (DetectedProject) -> Unit = {},
    override val onDeactivate: (DetectedProject) -> Unit = {},
    private val onExternalFilesDropped: (List<File>) -> Unit = {},
) : JPanel(BorderLayout()),
    ProjectTreePanelAccess {
    override val rootNode = DefaultMutableTreeNode("root")
    override val treeModel = DefaultTreeModel(rootNode)

    override val scanResults = mutableMapOf<String, DetectedProject>()
    private val gitStatusCache = mutableMapOf<String, GitStatus>()
    private var activePaths: Set<String> = emptySet()
    private var activeOnly = false
    private var lastActiveOnly = false
    override val missingPaths = mutableSetOf<String>()
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

    override val tree =
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

    override val component: java.awt.Component get() = this
    private val dialogs = ProjectTreeDialogs(this)
    private val contextMenu = ProjectTreeContextMenu(this, dialogs)

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

    override fun updateEmptyState() {
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

    override fun updateMissingPath(path: String): Boolean {
        val missing = !File(path).isDirectory
        if (missing) missingPaths += path else missingPaths.remove(path)
        return missing
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) tree.expandRow(i++)
    }

    // ── Scanning ─────────────────────────────────────────────────────────────

    override fun scanProject(dir: ProjectDirectory) {
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
                } catch (e: Exception) {
                    logger.warn("Project rescan failed", e)
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

    override fun isActivePath(path: String): Boolean = path in activePaths

    override fun addActivePath(path: String) {
        activePaths = activePaths + path
    }

    override fun removeActivePath(path: String) {
        activePaths = activePaths - path
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

    override fun selectedProjectEntry(): ProjectTreeEntry.Project? {
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

    override fun treePath(node: DefaultMutableTreeNode) = TreePath(node.path)

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

    override fun persist() {
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

    // ── Mutations (delegated) ──────────────────────────────────────────────────

    private fun addFolder(parentNode: DefaultMutableTreeNode?) = dialogs.addFolder(parentNode)

    private fun addProject(parentNode: DefaultMutableTreeNode?) = dialogs.addProject(parentNode)

    private fun renameFolder(
        node: DefaultMutableTreeNode,
        folder: ProjectTreeEntry.Folder,
    ) = dialogs.renameFolder(node, folder)

    private fun removeNode(node: DefaultMutableTreeNode) = dialogs.removeNode(node)

    private fun deleteProjectFromDisk(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) = dialogs.deleteProjectFromDisk(node, entry)

    private fun deleteFolderFromDisk(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Folder,
    ) = dialogs.deleteFolderFromDisk(node, entry)

    private fun editTags(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) = dialogs.editTags(node, entry)

    private fun editShellSettings(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) = dialogs.editShellSettings(node, entry)

    private fun editEnv(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) = dialogs.editEnv(node, entry)

    private fun editScriptDirs(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) = dialogs.editScriptDirs(node, entry)

    private fun setProjectColor(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
        hex: String?,
    ) = dialogs.setProjectColor(node, entry, hex)

    private fun setFolderColor(
        node: DefaultMutableTreeNode,
        folder: ProjectTreeEntry.Folder,
        hex: String?,
    ) = dialogs.setFolderColor(node, folder, hex)

    // ── Context menus (delegated) ─────────────────────────────────────────────

    private fun showContextMenu(
        node: DefaultMutableTreeNode,
        x: Int,
        y: Int,
    ) = contextMenu.showContextMenu(node, x, y)

    private fun showRootContextMenu(
        x: Int,
        y: Int,
    ) = contextMenu.showRootContextMenu(x, y)
}
