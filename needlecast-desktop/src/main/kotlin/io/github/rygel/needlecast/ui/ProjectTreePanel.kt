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
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.SwingWorker
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.SwingUtilities
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
        override fun updateUI() {
            super.updateUI()
            if (ui !is FullWidthTreeUI) {
                setUI(FullWidthTreeUI())
            }
            // Keep variable-height rows even after LAF changes.
            rowHeight = 0
        }
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
    private val repaintTimer = Timer(50) { tree.repaint() }.apply { isRepeats = false }
    private val scanQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<ProjectDirectory, DetectedProject>>()
    private val scanApplyTimer = Timer(25) { drainScanQueue() }.apply { isRepeats = false }
    private val scanApplyPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val clickTraceForced = System.getProperty("needlecast.tree.clickTrace")?.equals("true", ignoreCase = true) == true ||
        (System.getenv("NEEDLECAST_TREE_CLICK_TRACE")?.equals("true", ignoreCase = true) == true) ||
        (System.getenv("NEEDLECAST_TREE_CLICK_TRACE") == "1")
    private fun isClickTraceEnabled(): Boolean = clickTraceForced || ctx.config.treeClickTraceEnabled
    private var clickSeq: Long = 0L
    private var lastClickTimeNs: Long = 0L
    private var lastClickKey: String? = null
    private var lastClickRow: Int = -1
    private val scanExecutor = java.util.concurrent.Executors.newFixedThreadPool(2).also { exec ->
        ctx.register(object : io.github.rygel.needlecast.Disposable {
            override fun dispose() {
                exec.shutdownNow()
            }
        })
    }

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
    private var dragPressPoint: java.awt.Point? = null

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectTreePanel::class.java)

        /** Draws [base] icon with a small green "+" badge in the bottom-right corner. */
        private fun plusOverlayIcon(base: javax.swing.Icon?): javax.swing.Icon? {
            if (base == null) return null
            return object : javax.swing.Icon {
                override fun getIconWidth() = base.iconWidth
                override fun getIconHeight() = base.iconHeight
                override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
                    base.paintIcon(c, g, x, y)
                    val g2 = g.create() as java.awt.Graphics2D
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                    val size = 9
                    val px = x + base.iconWidth - size
                    val py = y + base.iconHeight - size
                    // Green circle background
                    g2.color = java.awt.Color(0x4CAF50)
                    g2.fillOval(px, py, size, size)
                    // White "+" sign
                    g2.color = java.awt.Color.WHITE
                    g2.stroke = java.awt.BasicStroke(1.5f)
                    val cx = px + size / 2
                    val cy = py + size / 2
                    g2.drawLine(cx - 2, cy, cx + 2, cy)
                    g2.drawLine(cx, cy - 2, cx, cy + 2)
                    g2.dispose()
                }
            }
        }
    }

    init {
        tree.ui = FullWidthTreeUI()
        tree.cellRenderer = ProjectTreeCellRenderer()
        tree.dropMode = DropMode.ON_OR_INSERT
        tree.transferHandler = TreeTransferHandler()
        tree.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    tree.transferHandler?.exportAsDrag(tree, e, TransferHandler.MOVE)
                }
            }
        })
        tree.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                invalidateTreeLayout()
            }
        })

        fun iconBtn(icon: javax.swing.Icon?, text: String, tip: String) = JButton(icon).apply {
            if (icon == null) this.text = text
            toolTipText = tip
            isFocusPainted = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
        }
        val addFolderBtn = iconBtn(plusOverlayIcon(UIManager.getIcon("FileView.directoryIcon")), "\uD83D\uDCC1+", "Add a folder to organize projects").apply {
            addActionListener { addFolder(selectedFolderNode()) }
        }
        val addProjectBtn = iconBtn(plusOverlayIcon(UIManager.getIcon("FileView.fileIcon")), "\uD83D\uDCC4+", "Add a project directory").apply {
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
            val project = when (val entry = node.userObject) {
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
            // Defer to allow the selection highlight repaint to fire first,
            // so the click feels instant even if downstream work (dir listing, etc.) is slow.
            SwingUtilities.invokeLater {
                if (isClickTraceEnabled()) {
                    val delayMs = (System.nanoTime() - selectionTimeNs) / 1_000_000
                    logger.info("tree-select-callback seq={} delayMs={}", clickSeq, delayMs)
                }
                onProjectSelected(project)
            }
        }

        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // getPathForLocation misses clicks in the right-side empty row area;
                    // use the closest path within the row bounds instead.
                    val closest = tree.getClosestPathForLocation(e.x, e.y)
                    val bounds  = if (closest != null) tree.getPathBounds(closest) else null
                    val inRow = if (bounds != null && closest != null) {
                        val row = tree.getRowForPath(closest)
                        val node = closest.lastPathComponent
                        val rendererHeight = if (row >= 0) {
                            val renderer = tree.cellRenderer.getTreeCellRendererComponent(
                                tree,
                                node,
                                tree.isRowSelected(row),
                                tree.isExpanded(row),
                                tree.model.isLeaf(node),
                                row,
                                tree.leadSelectionRow == row,
                            )
                            renderer.preferredSize.height
                        } else bounds.height
                        val effectiveHeight = maxOf(bounds.height, rendererHeight)
                        e.y >= bounds.y && e.y < bounds.y + effectiveHeight
                    } else false
                    dragPressedPath = if (inRow) closest else null
                    dragPressPoint  = if (dragPressedPath != null) java.awt.Point(e.x, e.y) else null
                    // Ensure a single click anywhere on the row selects immediately
                    // (including the empty area to the right of the renderer).
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

    private fun addEntryNode(parent: DefaultMutableTreeNode, entry: ProjectTreeEntry, scan: Boolean = true) {
        val node = DefaultMutableTreeNode(entry)
        parent.add(node)
        when (entry) {
            is ProjectTreeEntry.Folder  -> entry.children.forEach { addEntryNode(node, it) }
            is ProjectTreeEntry.Project -> if (scan) scanProject(entry.directory)
        }
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) tree.expandRow(i++)
    }

    // ── Scanning ─────────────────────────────────────────────────────────────

    private fun scanProject(dir: ProjectDirectory) {
        scanExecutor.execute {
            val result = try {
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
            val result = try { ctx.scanner.scan(dir) } catch (_: Exception) { null } ?: return@execute
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    private fun fetchGitStatus(path: String) {
        object : javax.swing.SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = ctx.gitService.readStatus(path)
            override fun done() {
                val status = try { get() } catch (_: Exception) { return }
                gitStatusCache[path] = status
                requestTreeRepaint()
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

    /** Force the tree to recalculate all cell widths (call after initial layout). */
    fun invalidateTreeLayout() {
        val ui = tree.ui as? javax.swing.plaf.basic.BasicTreeUI ?: return
        try {
            val field = javax.swing.plaf.basic.BasicTreeUI::class.java.getDeclaredField("treeState")
            field.isAccessible = true
            (field.get(ui) as? javax.swing.tree.AbstractLayoutCache)?.invalidateSizes()
        } catch (_: Exception) {}
        tree.revalidate()
        tree.repaint()
    }

    fun setActivePaths(paths: Set<String>) {
        activePaths = paths
        requestTreeRepaint()
    }

    fun updateProjectStatus(path: String, status: AgentStatus) {
        agentStatuses[path] = status
        if (agentStatuses.values.any { it == AgentStatus.THINKING }) blinkTimer.start()
        else blinkTimer.stop()
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
                }.apply { isDaemon = true; name = "build-file-watch-${dir.label()}" }.start()
            }
            val pending = pendingSelectPath
            if (pending == dir.path) {
                selectByPath(pending)
            } else {
                // If this project is already selected, push the fresh scan result
                // to listeners (file explorer, commands panel, etc.) without
                // changing the selection path.
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

    private fun entryKey(entry: Any?): String? = when (entry) {
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
            val entries = migrateOrLoad()
            entries.forEach { addEntryNode(rootNode, it, scan = false) }
            ensureScans(entries)
        } else {
            ctx.config.projectTree.forEach { addFilteredEntry(rootNode, it, filter) }
        }
        treeModel.reload()
        expandAll()
    }

    private fun ensureScans(entries: List<ProjectTreeEntry>) {
        fun walk(entry: ProjectTreeEntry) {
            when (entry) {
                is ProjectTreeEntry.Project -> if (scanResults[entry.directory.path] == null) scanProject(entry.directory)
                is ProjectTreeEntry.Folder -> entry.children.forEach { walk(it) }
            }
        }
        entries.forEach { walk(it) }
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
        buildFileWatcher.unwatchAll()
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
        object : SwingWorker<Long, Void>() {
            override fun doInBackground(): Long = runCatching { dir.walkTopDown().count().toLong() }.getOrDefault(-1L)
            override fun done() {
                val fileCount = try { get() } catch (_: Exception) { -1 }
                val countLine = if (fileCount >= 0) "Contains: $fileCount files/directories\n\n" else ""
                val confirm = JOptionPane.showConfirmDialog(this@ProjectTreePanel,
                    "Permanently delete '$name' from disk?\n\n" +
                        "Path: ${dir.absolutePath}\n" +
                        countLine +
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
        }.execute()
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
                menu.add(JMenuItem("Remove").apply { addActionListener { removeNode(node) } })
                menu.add(JMenu("Advanced").apply {
                    add(JMenuItem("Delete from disk\u2026").apply {
                        foreground = Color(0xE53935)
                        addActionListener { deleteFolderFromDisk(node, entry) }
                    })
                })
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
                menu.add(JMenuItem("Remove").apply { addActionListener { removeNode(node) } })
                val dir = File(entry.directory.path)
                if (dir.exists()) {
                    menu.add(JMenu("Advanced").apply {
                        add(JMenuItem("Delete from disk\u2026").apply {
                            foreground = Color(0xE53935)
                            addActionListener { deleteProjectFromDisk(node, entry) }
                        })
                    })
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
        private val nameLabel   = JLabel().apply {
            font = font.deriveFont(Font.BOLD, 12f)
            // Allow truncation with ellipsis — don't force the parent wider
            minimumSize = Dimension(0, 0)
        }
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
            // Allow truncation — don't force the parent wider than available space
            minimumSize = Dimension(0, 0)
        }
        private val tagsPanel = object : JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)) {
            /** Single-row preferred size — never wrap to a second line. */
            override fun getPreferredSize(): Dimension {
                var w = 0
                val h = if (componentCount > 0) components.maxOf { it.preferredSize.height } else 0
                val gap = (layout as FlowLayout).hgap
                for (i in 0 until componentCount) {
                    if (i > 0) w += gap
                    w += components[i].preferredSize.width
                }
                val forced = projectPanel.forcedWidth
                val width = if (forced > 0) forced else w
                return Dimension(width, h)
            }
            /** Constrain max size to prevent overflow beyond allocated width. */
            override fun getMaximumSize(): Dimension = Dimension(Short.MAX_VALUE.toInt(), 16)
            /** Lay out children right-aligned; hide any that don't fit. */
            override fun doLayout() {
                val gap = (layout as FlowLayout).hgap
                var x = width
                for (i in componentCount - 1 downTo 0) {
                    val c = components[i]
                    val pref = c.preferredSize
                    val nextX = x - pref.width
                    if (nextX < 0 && i < componentCount - 1) {
                        // Hide tags that don't fit on the left
                        c.setBounds(0, 0, 0, 0)
                        c.isVisible = false
                    } else {
                        c.isVisible = true
                        c.setBounds(nextX, 0, pref.width, height.coerceAtLeast(pref.height))
                        x = nextX - gap
                    }
                }
            }
        }.apply {
            isOpaque = false
        }
        private val nameRow = JPanel(BorderLayout(2, 0)).apply {
            isOpaque = false
            add(dotsPanel,  BorderLayout.WEST)
            add(nameLabel,  BorderLayout.CENTER)
        }
        private val bottomRow = object : JPanel(BorderLayout(4, 0)) {
            override fun getPreferredSize(): Dimension {
                val base = super.getPreferredSize()
                val vp = tree.parent as? javax.swing.JViewport
                val vpWidth = vp?.width ?: tree.width
                val width = if (vpWidth > 0) vpWidth else base.width
                return Dimension(width, base.height)
            }
        }.apply {
            isOpaque = false
            add(branchLabel, BorderLayout.WEST)
            add(tagsPanel,   BorderLayout.CENTER)
        }
        private val cellPanel = JPanel(BorderLayout(0, 1)).apply {
            isOpaque = false
            add(nameRow,   BorderLayout.NORTH)
            add(bottomRow, BorderLayout.CENTER)
        }
        private val innerPanel = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
        }
        private val projectPanel = object : JPanel(BorderLayout()) {
            var forcedWidth: Int = 0
            override fun getPreferredSize(): Dimension {
                val base = super.getPreferredSize()
                val w = if (forcedWidth > 0) forcedWidth else base.width
                return Dimension(w, base.height)
            }
            override fun getMinimumSize(): Dimension = getPreferredSize()
            override fun getMaximumSize(): Dimension = getPreferredSize()
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

                    // Calculate available width for this cell.
                    // IMPORTANT: Do NOT call tree.getPathBounds() here — it re-enters the
                    // layout cache's size calculation and causes a StackOverflowError.
                    // Instead, compute the indentation offset from node depth + UI indent settings.
                    val vp = tree.parent as? javax.swing.JViewport
                    val vpWidth = vp?.width ?: tree.width
                    if (vpWidth > 0) {
                        val insets = tree.insets
                        val leftInset = insets?.left ?: 0
                        val rightInset = insets?.right ?: 0
                        val treeUI = tree.ui as? javax.swing.plaf.basic.BasicTreeUI
                        val totalIndent = treeUI?.let {
                            val left = it.leftChildIndent
                            val right = it.rightChildIndent
                            val depth = node?.level ?: 0
                            depth * (left + right)
                        } ?: 0
                        val startX = leftInset + totalIndent
                        val cellWidth = (vpWidth - startX - rightInset).coerceAtLeast(50)
                        projectPanel.forcedWidth = cellWidth
                        val cellHeight = projectPanel.preferredSize.height
                        projectPanel.setSize(cellWidth, cellHeight)
                        projectPanel.invalidate()
                        projectPanel.validate()
                        innerPanel.doLayout()
                        cellPanel.doLayout()
                        nameRow.doLayout()
                        bottomRow.doLayout()
                        tagsPanel.doLayout()
                    }

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

    private inner class FullWidthTreeUI : javax.swing.plaf.basic.BasicTreeUI() {

        override fun createLayoutCache(): javax.swing.tree.AbstractLayoutCache =
            javax.swing.tree.VariableHeightLayoutCache()

        /** Force variable-height rows so hit-detection matches the two-line cell renderer height.
         *  FlatLaf sets Tree.rowHeight to a fixed value (e.g. 24 px); our renderer returns ~40 px.
         *  Without this, clicks on the lower half of each row fall outside BasicTreeUI's hit bounds
         *  and are silently dropped. rowHeight = 0 tells VariableHeightLayoutCache to ask the
         *  renderer for the actual height of each row. */
        override fun installDefaults() {
            super.installDefaults()
            tree.rowHeight = 0
        }
        override fun paintRow(
            g: java.awt.Graphics,
            clipBounds: Rectangle?,
            insets: Insets?,
            bounds: Rectangle?,
            path: TreePath?,
            row: Int,
            isExpanded: Boolean,
            hasBeenExpanded: Boolean,
            isLeaf: Boolean,
        ) {
            val t = tree ?: return
            if (bounds == null || path == null) return
            if (t.isEditing && editingRow == row) return
            val vp = t.parent as? javax.swing.JViewport
            val vpWidth = vp?.width ?: t.width
            val rightInset = t.insets?.right ?: 0
            val fullWidth = (vpWidth - bounds.x - rightInset).coerceAtLeast(1)
            val fullBounds = Rectangle(bounds.x, bounds.y, fullWidth, bounds.height)
            val leadIndex = t.leadSelectionRow
            val selected = t.isRowSelected(row)
            val renderer = currentCellRenderer?.getTreeCellRendererComponent(
                t,
                path.lastPathComponent,
                selected,
                isExpanded,
                isLeaf,
                row,
                leadIndex == row,
            )
            if (renderer != null) {
                rendererPane.paintComponent(g, renderer, t, fullBounds.x, fullBounds.y, fullBounds.width, fullBounds.height, true)
            }
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

        /** Fallback flavor for Linux file managers that advertise folders via text/uri-list. */
        private val uriListFlavor: DataFlavor? = try {
            DataFlavor("text/uri-list;class=java.lang.String")
        } catch (_: Exception) { null }

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

        private fun nodeFrom(support: TransferSupport): DefaultMutableTreeNode? =
            try { support.transferable.getTransferData(flavor) as? DefaultMutableTreeNode }
            catch (_: Exception) { null }

        private fun isExternalDrop(support: TransferSupport): Boolean =
            support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                    (uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor))

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

        /**
         * Resolves the drop target (parent node + insertion index) from a [JTree.DropLocation].
         * Shared by internal reorder and external folder drop.
         */
        private fun resolveDropTarget(
            dl: JTree.DropLocation,
            overrideFolder: DefaultMutableTreeNode?,
        ): Pair<DefaultMutableTreeNode, Int>? {
            if (overrideFolder != null) return Pair(overrideFolder, overrideFolder.childCount)
            if (dl.path == null) return Pair(rootNode, rootNode.childCount)
            val targetNode = dl.path.lastPathComponent as? DefaultMutableTreeNode ?: return null
            return if (dl.childIndex == -1) {
                when (targetNode.userObject) {
                    is ProjectTreeEntry.Folder -> Pair(targetNode, targetNode.childCount)
                    else -> {
                        val parent = targetNode.parent as? DefaultMutableTreeNode ?: rootNode
                        Pair(parent, parent.getIndex(targetNode) + 1)
                    }
                }
            } else {
                val parent = when (targetNode.userObject) {
                    is ProjectTreeEntry.Folder -> targetNode
                    else -> targetNode.parent as? DefaultMutableTreeNode ?: rootNode
                }
                val idx = if (parent === targetNode) dl.childIndex else parent.getIndex(targetNode).coerceAtLeast(0)
                Pair(parent, idx)
            }
        }

        override fun canImport(support: TransferSupport): Boolean {
            if (!support.isDrop) return false
            return when {
                support.isDataFlavorSupported(flavor) -> {
                    try {
                        support.dropAction = MOVE
                        support.setShowDropLocation(true)
                    } catch (_: Exception) {}
                    val dl = support.dropLocation as? JTree.DropLocation ?: return false
                    val overrideTarget = centeredFolderDrop(dl)
                    val targetPath = overrideTarget?.let { TreePath(it.path) } ?: dl.path
                        ?: return true  // dropping at root level → OK
                    val targetNode = targetPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                    val src = nodeFrom(support) ?: return false
                    // Reject dropping onto itself or any of its descendants
                    var n: TreeNode? = targetNode
                    while (n != null) {
                        if (n === src) return false
                        n = n.parent
                    }
                    true
                }
                isExternalDrop(support) -> {
                    support.setShowDropLocation(true)
                    true
                }
                else -> false
            }
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            return if (isExternalDrop(support)) importExternal(support) else importInternal(support)
        }

        private fun importInternal(support: TransferSupport): Boolean {
            val node = nodeFrom(support) ?: return false
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val (newParent, rawIndex) = resolveDropTarget(dl, centeredFolderDrop(dl)) ?: return false

            val oldParent = node.parent as? DefaultMutableTreeNode ?: return false
            val oldIndex  = oldParent.getIndex(node)
            treeModel.removeNodeFromParent(node)

            // Adjust index when dragging forward within the same parent
            val insertIndex = if (newParent === oldParent && rawIndex > oldIndex)
                (rawIndex - 1).coerceAtMost(newParent.childCount)
            else
                rawIndex.coerceAtMost(newParent.childCount)

            treeModel.insertNodeInto(node, newParent, insertIndex)
            if (newParent !== rootNode) tree.expandPath(TreePath(newParent.path))
            val tp = treePath(node)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
            persist()
            return true
        }

        /** Handles a drop of one or more folders dragged from the OS file manager. */
        private fun importExternal(support: TransferSupport): Boolean {
            val dirs = dirsFromExternal(support)
            if (dirs.isEmpty()) return false
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val (newParent, startIndex) = resolveDropTarget(dl, centeredFolderDrop(dl)) ?: return false

            val existingPaths = collectAllPaths(rootNode)
            var insertIdx = startIndex.coerceAtMost(newParent.childCount)
            var lastNode: DefaultMutableTreeNode? = null

            for (dir in dirs) {
                val absPath = dir.absolutePath
                if (absPath in existingPaths) continue
                val directory = ProjectDirectory(path = absPath)
                val node = DefaultMutableTreeNode(ProjectTreeEntry.Project(directory = directory))
                treeModel.insertNodeInto(node, newParent, insertIdx)
                existingPaths += absPath
                insertIdx++
                lastNode = node
                scanProject(directory)
            }

            if (lastNode == null) return false  // all dropped dirs were duplicates
            if (newParent !== rootNode) tree.expandPath(TreePath(newParent.path))
            val tp = treePath(lastNode)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
            persist()
            return true
        }

        /**
         * Extracts directories from an OS drag.
         * - Windows / macOS: [DataFlavor.javaFileListFlavor]
         * - Linux (GTK file managers): same flavor via AWT, with [uriListFlavor] as fallback
         */
        @Suppress("UNCHECKED_CAST")
        private fun dirsFromExternal(support: TransferSupport): List<File> {
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return try {
                    (support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        ?.filterIsInstance<File>()
                        ?.filter { it.isDirectory }
                        ?: emptyList()
                } catch (_: Exception) { emptyList() }
            }
            val uriF = uriListFlavor ?: return emptyList()
            return try {
                val text = support.transferable.getTransferData(uriF) as? String ?: return emptyList()
                text.lines()
                    .map { it.trim() }
                    .filter { it.startsWith("file://") && !it.startsWith("#") }
                    .mapNotNull { runCatching { File(URI(it)) }.getOrNull() }
                    .filter { it.isDirectory }
            } catch (_: Exception) { emptyList() }
        }

        /** Walks the tree and returns all project paths currently registered. */
        private fun collectAllPaths(root: DefaultMutableTreeNode): MutableSet<String> {
            val set = mutableSetOf<String>()
            fun walk(n: DefaultMutableTreeNode) {
                val e = n.userObject
                if (e is ProjectTreeEntry.Project) set += e.directory.path
                for (i in 0 until n.childCount) walk(n.getChildAt(i) as DefaultMutableTreeNode)
            }
            walk(root)
            return set
        }
    }
}
