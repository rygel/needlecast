package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.ui.RemixIcons
import io.github.rygel.needlecast.ui.util.DesktopUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.table.DefaultTableCellRenderer

class ExplorerPanel(
    private val ctx: AppContext,
) : JPanel(BorderLayout()) {
    private val logger = LoggerFactory.getLogger(ExplorerPanel::class.java)
    private var currentDir: File = File(System.getProperty("user.home"))
    private val fileOps =
        ExplorerFileOps(
            ctx,
            ExplorerCallbacks(
                navigateTo = { f -> navigateTo(f) },
                navigateUp = { navigateUp() },
                openFileInTab = { f -> openFileInTab(f) },
                reloadDirectory = { loadDirectory(currentDir) },
                currentDir = { currentDir },
            ),
            this,
        )
    private var showHidden = false
    private var fullEntries: List<FileEntry> = emptyList()
    private val addressField = JTextField()
    private val filterField =
        JTextField().apply {
            toolTipText = "Filter files"
            putClientProperty("JTextField.placeholderText", "Filter\u2026")
        }
    private val filterTimer = javax.swing.Timer(150) { applyFileFilter() }.apply { isRepeats = false }
    private val tableModel = FileTableModel()
    private val table =
        JTable(tableModel).apply {
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
            fillsViewportHeight = true
            setDefaultRenderer(Any::class.java, FileTableCellRenderer(tableModel))
            tableHeader.reorderingAllowed = false
        }

    private val tabs = JTabbedPane()

    /** Canonical file path → open tab component (EditorPanel or ImageViewerPanel) */
    private val openFiles = LinkedHashMap<String, javax.swing.JComponent>()

    private var isDark: Boolean = ctx.config.theme == "dark"

    /** Sort state for each project root — keyed by absolute path. Session-only. */
    private val sortStateByPath = mutableMapOf<String, ExplorerSortState>()

    /** Absolute path of the project root currently shown (set by setRootDirectory). */
    private var projectRootPath: String? = null

    /** Sort state currently in effect.
     *  Written only on the EDT; @Volatile ensures the SwingWorker capture in doInBackground
     *  sees the latest value without a data race. */
    @Volatile
    private var currentSortState: ExplorerSortState = DEFAULT_EXPLORER_SORT

    init {
        val upButton =
            JButton(RemixIcons.icon("ri-arrow-up-line", 16)).apply {
                toolTipText = "Go up one level"
                addActionListener { navigateUp() }
            }
        val refreshButton =
            JButton(RemixIcons.icon("ri-refresh-line", 16)).apply {
                toolTipText = "Refresh"
                addActionListener { loadDirectory(currentDir) }
            }
        val hiddenButton =
            JButton(RemixIcons.icon("ri-eye-line", 16)).apply {
                toolTipText = "Show hidden files"
                addActionListener {
                    showHidden = !showHidden
                    toolTipText = if (showHidden) "Hide hidden files" else "Show hidden files"
                    foreground = if (showHidden) java.awt.Color(0x4CAF50) else null
                    loadDirectory(currentDir)
                }
            }

        val openFmButton =
            JButton(RemixIcons.icon("ri-external-link-line", 16)).apply {
                toolTipText = DesktopUtils.openInFileManagerLabel
                addActionListener { openInFileManager(currentDir) }
            }

        val rightButtons =
            JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
                add(openFmButton)
                add(hiddenButton)
                add(refreshButton)
            }

        val addressBar =
            JPanel(BorderLayout(4, 0)).apply {
                border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                add(upButton, BorderLayout.WEST)
                add(addressField, BorderLayout.CENTER)
                add(rightButtons, BorderLayout.EAST)
            }

        addressField.addActionListener { navigateTo(File(addressField.text)) }

        // Filter field — debounced, Escape clears
        filterField.document.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterTimer.restart()

                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterTimer.restart()

                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterTimer.restart()
            },
        )
        filterField.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ESCAPE) {
                        filterField.text = ""
                        applyFileFilter()
                    }
                }
            },
        )

        // Keyboard shortcuts on the table
        table.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> {
                            val entry = selectedEntry() ?: return
                            handleActivate(entry)
                        }

                        KeyEvent.VK_BACK_SPACE -> {
                            navigateUp()
                        }
                    }
                }
            },
        )

        // Mouse clicks
        table.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = table.rowAtPoint(e.point)
                    if (row < 0) return
                    val entry = tableModel.entryAt(row)
                    if (SwingUtilities.isRightMouseButton(e)) {
                        table.selectionModel.setSelectionInterval(row, row)
                        fileOps.showContextMenu(entry, e.x, e.y, table)
                        return
                    }
                    if (e.clickCount == 1 && entry is FileEntry.RegularFile) {
                        openFileInTab(entry.file)
                    } else if (e.clickCount == 2) {
                        handleActivate(entry)
                    }
                }
            },
        )

        // Right-click context menu on editor/image tabs
        tabs.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (SwingUtilities.isRightMouseButton(e)) showTabContextMenu(e)
                }
            },
        )

        // File browser — address bar + table only.
        // The editor tabs are exposed via [editorComponent] so MainWindow can dock them separately.
        add(
            JPanel(BorderLayout()).apply {
                add(addressBar, BorderLayout.NORTH)
            },
            BorderLayout.NORTH,
        )
        add(JScrollPane(table).apply { minimumSize = java.awt.Dimension(0, 0) }, BorderLayout.CENTER)
        minimumSize = java.awt.Dimension(0, 0)
        navigateTo(currentDir)

        // Drag-and-drop from OS into explorer/editor areas.
        val dropHandler =
            ExplorerDropHandler(
                openFileInTab = { f -> openFileInTab(f) },
                setRootDirectory = { f -> setRootDirectory(f) },
                table = table,
                tabs = tabs,
            )
        table.dropMode = DropMode.ON
        table.transferHandler = dropHandler
        tabs.transferHandler = dropHandler

        // Column header click — toggle sort direction or switch column
        table.tableHeader.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val col = table.columnAtPoint(e.point)
                    if (col < 0) return
                    currentSortState =
                        if (currentSortState.column == col) {
                            currentSortState.copy(ascending = !currentSortState.ascending)
                        } else {
                            ExplorerSortState(col, true)
                        }
                    projectRootPath?.let { sortStateByPath[it] = currentSortState }
                    loadDirectory(currentDir)
                }
            },
        )

        // Header renderer — show ▲ / ▼ on the active sort column
        table.tableHeader.defaultRenderer =
            object : DefaultTableCellRenderer() {
                init {
                    horizontalAlignment = SwingConstants.LEFT
                }

                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    val label =
                        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                    val colName = tableModel.getColumnName(column)
                    label.text =
                        if (currentSortState.column == column) {
                            "$colName ${if (currentSortState.ascending) "▲" else "▼"}"
                        } else {
                            colName
                        }
                    return label
                }
            }

        ctx.addConfigListener {
            SwingUtilities.invokeLater { refreshAddressField() }
        }
    }

    /**
     * The editor tab pane — expose this as a separate dockable in [MainWindow] so the
     * code editor gets its own resizable panel rather than being cramped inside the
     * file browser split.
     */
    val editorComponent: JTabbedPane get() = tabs

    fun setRootDirectory(dir: File) {
        if (!dir.isDirectory) return
        projectRootPath = dir.absolutePath
        currentSortState = sortStateByPath[dir.absolutePath] ?: DEFAULT_EXPLORER_SORT
        navigateTo(dir)
    }

    fun applyTheme(dark: Boolean) {
        isDark = dark
        openFiles.values.filterIsInstance<EditorPanel>().forEach { it.applyTheme(dark) }
    }

    fun applyEditorFont(
        family: String?,
        size: Int,
    ) {
        openFiles.values.filterIsInstance<EditorPanel>().forEach { it.applyFont(family, size) }
    }

    fun requestFocusOnTree() = table.requestFocusInWindow()

    /** Check all open editors for unsaved changes before the app closes. */
    fun checkAllUnsaved(): Boolean = openFiles.values.filterIsInstance<EditorPanel>().all { it.checkUnsaved() }

    fun openFile(file: File) = openFileInTab(file)

    fun openFileAt(
        file: File,
        line: Int,
        column: Int? = null,
    ) {
        val key =
            try {
                file.canonicalPath
            } catch (e: Exception) {
                logger.warn("Failed to resolve canonical path", e)
                file.absolutePath
            }
        val existing = openFiles[key]
        if (existing is EditorPanel) {
            tabs.selectedComponent = existing
            existing.focusLocation(line, column)
            return
        }
        openFileInTab(file, line, column)
    }

    private fun isCurrentProjectPrivate(): Boolean {
        val root = projectRootPath ?: return false
        return findProjectEntryByPath(ctx.config.projectTree, root)?.directory?.isPrivate == true
    }

    private fun findProjectEntryByPath(
        entries: List<ProjectTreeEntry>,
        rootPath: String,
    ): ProjectTreeEntry.Project? {
        for (entry in entries) {
            when (entry) {
                is ProjectTreeEntry.Project -> {
                    if (entry.directory.path == rootPath) return entry
                }

                is ProjectTreeEntry.Folder -> {
                    val nested = findProjectEntryByPath(entry.children, rootPath)
                    if (nested != null) return nested
                }
            }
        }
        return null
    }

    private fun navigateTo(dir: File) {
        if (!dir.isDirectory) return
        currentDir = dir
        refreshAddressField()
        loadDirectory(dir)
    }

    private fun refreshAddressField() {
        addressField.text = if (ctx.config.privacyModeEnabled && isCurrentProjectPrivate()) "••••••" else currentDir.absolutePath
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile ?: return
        navigateTo(parent)
    }

    private fun openInFileManager(dir: File) {
        DesktopUtils.openInFileManager(dir)
    }

    private fun revealInFileManager(file: File) {
        DesktopUtils.revealInFileManager(file)
    }

    private fun loadDirectory(dir: File) {
        object : SwingWorker<List<FileEntry>, Void>() {
            override fun doInBackground(): List<FileEntry> {
                val sortState = currentSortState // capture for background thread
                val children =
                    (dir.listFiles() ?: emptyArray())
                        .filter { showHidden || !it.isHidden }
                val entries = mutableListOf<FileEntry>()
                if (dir.parentFile != null) entries.add(FileEntry.ParentDir)
                entries.addAll(sortGroup(children.filter { it.isDirectory }.map { FileEntry.Dir(it) }, sortState))
                entries.addAll(sortGroup(children.filter { it.isFile }.map { FileEntry.RegularFile(it) }, sortState))
                return entries
            }

            override fun done() {
                if (currentDir != dir) return
                val entries =
                    try {
                        get()
                    } catch (e: Exception) {
                        logger.warn("Failed to load directory listing", e)
                        return
                    }
                tableModel.setEntries(entries)
            }
        }.execute()
    }

    private fun applyFileFilter() {
        val query = filterField.text.trim().lowercase()
        val filtered =
            if (query.isEmpty()) {
                fullEntries
            } else {
                fullEntries.filter { entry ->
                    when (entry) {
                        is FileEntry.ParentDir -> {
                            true
                        }

                        is FileEntry.Dir -> {
                            entry.file.name
                                .lowercase()
                                .contains(query)
                        }

                        is FileEntry.RegularFile -> {
                            entry.file.name
                                .lowercase()
                                .contains(query)
                        }
                    }
                }
            }
        tableModel.setEntries(filtered)
    }

    private fun handleActivate(entry: FileEntry) {
        when (entry) {
            is FileEntry.ParentDir -> navigateUp()
            is FileEntry.Dir -> navigateTo(entry.file)
            is FileEntry.RegularFile -> openFileInTab(entry.file)
        }
    }

    private fun openFileInTab(
        file: File,
        line: Int? = null,
        column: Int? = null,
    ) {
        val key =
            try {
                file.canonicalPath
            } catch (e: Exception) {
                logger.warn("Failed to resolve canonical path for tab", e)
                file.absolutePath
            }
        val existing = openFiles[key]
        if (existing != null) {
            tabs.selectedComponent = existing
            when (existing) {
                is ImageViewerPanel -> {
                    existing.reloadIfChanged()
                }

                is SvgViewerPanel -> {
                    existing.reloadIfChanged()
                }

                is EditorPanel -> {
                    if (line != null) existing.focusLocation(line, column)
                }

                is MediaPlayerPanel -> {} // no reload; media can be restarted via controls
            }
            return
        }
        val panel: javax.swing.JComponent =
            when {
                isSvgFile(file) -> {
                    SvgViewerPanel(file)
                }

                isImageFile(file) -> {
                    ImageViewerPanel(file)
                }

                isMediaFile(file) -> {
                    MediaPlayerPanel(file, ctx)
                }

                else -> {
                    EditorPanel(ctx).also {
                        it.applyTheme(isDark)
                        it.openFile(file, line, column)
                    }
                }
            }
        openFiles[key] = panel
        val idx = tabs.tabCount
        tabs.addTab(file.name, panel)
        tabs.setTabComponentAt(idx, TabHeader(file.name) { closeTab(key) })
        if (panel is ImageViewerPanel || panel is SvgViewerPanel) {
            tabs.setToolTipTextAt(idx, "[alpha] Viewer is new and may have rough edges")
        }
        tabs.selectedIndex = idx
    }

    private fun closeTab(key: String) {
        val panel = openFiles[key] ?: return
        if (panel is EditorPanel && !panel.checkUnsaved()) return
        if (panel is MediaPlayerPanel) panel.dispose()
        val idx = tabs.indexOfComponent(panel)
        if (idx >= 0) tabs.removeTabAt(idx)
        openFiles.remove(key)
    }

    private fun keyForTabIndex(idx: Int): String? {
        val component = tabs.getComponentAt(idx)
        return openFiles.entries.firstOrNull { it.value === component }?.key
    }

    private fun showTabContextMenu(e: MouseEvent) {
        val clickedIdx = tabs.indexAtLocation(e.x, e.y).takeIf { it >= 0 } ?: return
        val menu = JPopupMenu()
        menu.add(
            JMenuItem("Close").apply {
                addActionListener { keyForTabIndex(clickedIdx)?.let { closeTab(it) } }
            },
        )
        menu.addSeparator()
        menu.add(
            JMenuItem("Close All to the Left").apply {
                isEnabled = clickedIdx > 0
                addActionListener {
                    // Close right-to-left to avoid index shifting
                    for (i in clickedIdx - 1 downTo 0) keyForTabIndex(i)?.let { closeTab(it) }
                }
            },
        )
        menu.add(
            JMenuItem("Close All to the Right").apply {
                isEnabled = clickedIdx < tabs.tabCount - 1
                addActionListener {
                    val total = tabs.tabCount
                    for (i in total - 1 downTo clickedIdx + 1) keyForTabIndex(i)?.let { closeTab(it) }
                }
            },
        )
        menu.addSeparator()
        menu.add(
            JMenuItem("Close All").apply {
                addActionListener {
                    val keys = openFiles.keys.toList()
                    keys.forEach { closeTab(it) }
                }
            },
        )
        menu.show(tabs, e.x, e.y)
    }

    private fun isSvgFile(file: File) = file.extension.lowercase() == "svg"

    private fun isImageFile(file: File) =
        file.extension.lowercase() in
            setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "ico")

    private fun isMediaFile(file: File) =
        file.extension.lowercase() in
            setOf(
                // Audio
                "mp3",
                "wav",
                "wave",
                "aiff",
                "aif",
                "flac",
                "ogg",
                "oga",
                "opus",
                "m4a",
                "aac",
                "wma",
                // Video
                "mp4",
                "m4v",
                "mov",
                "mkv",
                "avi",
                "webm",
                "mpg",
                "mpeg",
                "flv",
                "3gp",
                "ogv",
            )

    private fun selectedEntry(): FileEntry? {
        val row = table.selectedRow
        if (row < 0) return null
        return tableModel.entryAt(row)
    }
}

/** Tab header component with a label and a close (✕) button. */
private class TabHeader(
    title: String,
    onClose: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        add(JLabel(title))
        add(
            JButton(RemixIcons.icon("ri-close-line", 16)).apply {
                toolTipText = "Close tab"
                preferredSize = Dimension(20, 20)
                isFocusable = false
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { onClose() }
            },
        )
    }
}

// ── Explorer sort helpers ─────────────────────────────────────────────────

internal data class ExplorerSortState(
    val column: Int,
    val ascending: Boolean,
)

/**
 * Sorts [entries] (a single group — all dirs OR all files, never mixed) by [state].
 * For the size column applied to directories, falls back to name sort (dirs have no meaningful size).
 */
internal fun sortGroup(
    entries: List<FileEntry>,
    state: ExplorerSortState,
): List<FileEntry> {
    if (entries.isEmpty()) return entries
    val isDirGroup = entries.first() is FileEntry.Dir
    val comparator: Comparator<FileEntry> =
        when {
            state.column == COL_SIZE && isDirGroup -> {
                compareBy { fileOf(it)?.name?.lowercase() ?: "" }
            }

            state.column == COL_NAME -> {
                compareBy { fileOf(it)?.name?.lowercase() ?: "" }
            }

            state.column == COL_SIZE -> {
                compareBy { fileOf(it)?.length() ?: 0L }
            }

            state.column == COL_MODIFIED -> {
                compareBy { fileOf(it)?.lastModified() ?: 0L }
            }

            else -> {
                compareBy { fileOf(it)?.name?.lowercase() ?: "" }
            }
        }
    return if (state.ascending) entries.sortedWith(comparator) else entries.sortedWith(comparator.reversed())
}

/** Returns the underlying [File] for [Dir] and [RegularFile] entries; `null` for [ParentDir]. */
internal fun fileOf(entry: FileEntry): File? =
    when (entry) {
        is FileEntry.Dir -> entry.file
        is FileEntry.RegularFile -> entry.file
        is FileEntry.ParentDir -> null
    }
