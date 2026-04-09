package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ExternalEditor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
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
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ExplorerPanel(private val ctx: AppContext) : JPanel(BorderLayout()) {

    private var currentDir: File = File(System.getProperty("user.home"))
    private var showHidden = false
    private val addressField = JTextField()
    private val tableModel = FileTableModel()
    private val table = JTable(tableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        fillsViewportHeight = true
        setDefaultRenderer(Any::class.java, FileTableCellRenderer())
        tableHeader.reorderingAllowed = false
    }

    private val tabs = JTabbedPane()
    /** Canonical file path → open tab component (EditorPanel or ImageViewerPanel) */
    private val openFiles = LinkedHashMap<String, javax.swing.JComponent>()

    private var isDark: Boolean = ctx.config.theme == "dark"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
    init {
        val upButton = JButton("\u2191").apply {
            toolTipText = "Go up one level"
            addActionListener { navigateUp() }
        }
        val refreshButton = JButton("\u27F3").apply {
            toolTipText = "Refresh"
            addActionListener { loadDirectory(currentDir) }
        }
        val hiddenButton = JButton("\u25CC").apply {
            toolTipText = "Show hidden files"
            addActionListener {
                showHidden = !showHidden
                toolTipText = if (showHidden) "Hide hidden files" else "Show hidden files"
                foreground = if (showHidden) java.awt.Color(0x4CAF50) else null
                loadDirectory(currentDir)
            }
        }

        val rightButtons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            add(hiddenButton)
            add(refreshButton)
        }

        val addressBar = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(upButton, BorderLayout.WEST)
            add(addressField, BorderLayout.CENTER)
            add(rightButtons, BorderLayout.EAST)
        }

        addressField.addActionListener { navigateTo(File(addressField.text)) }

        // Keyboard shortcuts on the table
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val entry = selectedEntry() ?: return
                        handleActivate(entry)
                    }
                    KeyEvent.VK_BACK_SPACE -> navigateUp()
                }
            }
        })

        // Mouse clicks
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                if (row < 0) return
                val entry = tableModel.entryAt(row)
                if (SwingUtilities.isRightMouseButton(e)) {
                    table.selectionModel.setSelectionInterval(row, row)
                    showContextMenu(entry, e.x, e.y)
                    return
                }
                if (e.clickCount == 1 && entry is FileEntry.RegularFile) {
                    openFileInTab(entry.file)
                } else if (e.clickCount == 2) {
                    handleActivate(entry)
                }
            }
        })

        // Right-click context menu on editor/image tabs
        tabs.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) showTabContextMenu(e)
            }
        })

        // File browser — address bar + table only.
        // The editor tabs are exposed via [editorComponent] so MainWindow can dock them separately.
        add(addressBar, BorderLayout.NORTH)
        add(JScrollPane(table).apply { minimumSize = java.awt.Dimension(0, 0) }, BorderLayout.CENTER)
        minimumSize = java.awt.Dimension(0, 0)
        navigateTo(currentDir)
    }

    /**
     * The editor tab pane — expose this as a separate dockable in [MainWindow] so the
     * code editor gets its own resizable panel rather than being cramped inside the
     * file browser split.
     */
    val editorComponent: JTabbedPane get() = tabs

    fun setRootDirectory(dir: File) {
        if (dir.isDirectory) navigateTo(dir)
    }

    fun applyTheme(dark: Boolean) {
        isDark = dark
        openFiles.values.filterIsInstance<EditorPanel>().forEach { it.applyTheme(dark) }
    }

    fun requestFocusOnTree() = table.requestFocusInWindow()

    /** Check all open editors for unsaved changes before the app closes. */
    fun checkAllUnsaved(): Boolean = openFiles.values.filterIsInstance<EditorPanel>().all { it.checkUnsaved() }

    private fun navigateTo(dir: File) {
        if (!dir.isDirectory) return
        currentDir = dir
        addressField.text = dir.absolutePath
        loadDirectory(dir)
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile ?: return
        navigateTo(parent)
    }

    private fun loadDirectory(dir: File) {
        object : SwingWorker<List<FileEntry>, Void>() {
            override fun doInBackground(): List<FileEntry> {
                val children = (dir.listFiles() ?: emptyArray())
                    .filter { showHidden || !it.isHidden }
                    .sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
                val entries = mutableListOf<FileEntry>()
                if (dir.parentFile != null) entries.add(FileEntry.ParentDir)
                children.filter { it.isDirectory }.mapTo(entries) { FileEntry.Dir(it) }
                children.filter { it.isFile }.mapTo(entries) { FileEntry.RegularFile(it) }
                return entries
            }
            override fun done() {
                // Only apply if the user hasn't navigated away while we were loading
                if (currentDir != dir) return
                val entries = try { get() } catch (_: Exception) { return }
                tableModel.setEntries(entries)
            }
        }.execute()
    }

    private fun handleActivate(entry: FileEntry) {
        when (entry) {
            is FileEntry.ParentDir  -> navigateUp()
            is FileEntry.Dir        -> navigateTo(entry.file)
            is FileEntry.RegularFile -> openFileInTab(entry.file)
        }
    }

    private fun openFileInTab(file: File) {
        val key = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        val existing = openFiles[key]
        if (existing != null) {
            tabs.selectedComponent = existing
            when (existing) {
                is ImageViewerPanel -> existing.reloadIfChanged()
                is SvgViewerPanel   -> existing.reloadIfChanged()
            }
            return
        }
        val panel: javax.swing.JComponent = when {
            isSvgFile(file)   -> SvgViewerPanel(file)
            isImageFile(file) -> ImageViewerPanel(file)
            else              -> EditorPanel(ctx).also { it.applyTheme(isDark); it.openFile(file) }
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
        menu.add(JMenuItem("Close").apply {
            addActionListener { keyForTabIndex(clickedIdx)?.let { closeTab(it) } }
        })
        menu.addSeparator()
        menu.add(JMenuItem("Close All to the Left").apply {
            isEnabled = clickedIdx > 0
            addActionListener {
                // Close right-to-left to avoid index shifting
                for (i in clickedIdx - 1 downTo 0) keyForTabIndex(i)?.let { closeTab(it) }
            }
        })
        menu.add(JMenuItem("Close All to the Right").apply {
            isEnabled = clickedIdx < tabs.tabCount - 1
            addActionListener {
                val total = tabs.tabCount
                for (i in total - 1 downTo clickedIdx + 1) keyForTabIndex(i)?.let { closeTab(it) }
            }
        })
        menu.addSeparator()
        menu.add(JMenuItem("Close All").apply {
            addActionListener {
                val keys = openFiles.keys.toList()
                keys.forEach { closeTab(it) }
            }
        })
        menu.show(tabs, e.x, e.y)
    }

    private fun isSvgFile(file: File)   = file.extension.lowercase() == "svg"
    private fun isImageFile(file: File) = file.extension.lowercase() in
        setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "ico")

    private fun selectedEntry(): FileEntry? {
        val row = table.selectedRow
        if (row < 0) return null
        return tableModel.entryAt(row)
    }

    private fun showContextMenu(entry: FileEntry, x: Int, y: Int) {
        val menu = JPopupMenu()
        when (entry) {
            is FileEntry.ParentDir -> {
                menu.add(JMenuItem("Go up").apply { addActionListener { navigateUp() } })
            }
            is FileEntry.Dir -> {
                menu.add(JMenuItem("Open").apply { addActionListener { navigateTo(entry.file) } })
                menu.addSeparator()
                menu.add(JMenuItem("New File\u2026").apply   { addActionListener { createFile(entry.file) } })
                menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { createFolder(entry.file) } })
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameEntry(entry.file) } })
                menu.add(JMenuItem("Delete").apply  { addActionListener { deleteEntry(entry.file) } })
                menu.addSeparator()
                menu.add(copyPathItem(entry.file))
            }
            is FileEntry.RegularFile -> {
                menu.add(JMenuItem("Open in Editor").apply {
                    addActionListener { openFileInTab(entry.file) }
                })
                val editors = ctx.config.externalEditors
                if (editors.isNotEmpty()) {
                    menu.addSeparator()
                    editors.forEach { editor ->
                        menu.add(JMenuItem("Open with ${editor.name}").apply {
                            addActionListener { openWith(entry.file, editor) }
                        })
                    }
                }
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameEntry(entry.file) } })
                menu.add(JMenuItem("Delete").apply  { addActionListener { deleteEntry(entry.file) } })
                menu.addSeparator()
                menu.add(copyPathItem(entry.file))
            }
        }
        // New File / New Folder always available for current directory
        if (entry is FileEntry.ParentDir) {
            menu.addSeparator()
            menu.add(JMenuItem("New File\u2026").apply   { addActionListener { createFile(currentDir) } })
            menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { createFolder(currentDir) } })
        }
        menu.show(table, x, y)
    }

    private fun createFile(inDir: File) {
        val name = JOptionPane.showInputDialog(this, "File name:", "New File", JOptionPane.PLAIN_MESSAGE) ?: return
        if (name.isBlank()) return
        val file = File(inDir, name.trim())
        try {
            if (!file.createNewFile()) { JOptionPane.showMessageDialog(this, "File already exists."); return }
            loadDirectory(currentDir)
            openFileInTab(file)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Could not create file: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun createFolder(inDir: File) {
        val name = JOptionPane.showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE) ?: return
        if (name.isBlank()) return
        val folder = File(inDir, name.trim())
        if (!folder.mkdir()) JOptionPane.showMessageDialog(this, "Could not create folder.", "Error", JOptionPane.ERROR_MESSAGE)
        else loadDirectory(currentDir)
    }

    private fun renameEntry(file: File) {
        val newName = JOptionPane.showInputDialog(this, "Rename to:", file.name) ?: return
        if (newName.isBlank() || newName == file.name) return
        val dest = File(file.parentFile, newName.trim())
        if (!file.renameTo(dest)) JOptionPane.showMessageDialog(this, "Rename failed.", "Error", JOptionPane.ERROR_MESSAGE)
        else loadDirectory(currentDir)
    }

    private fun deleteEntry(file: File) {
        val confirm = JOptionPane.showConfirmDialog(
            this, "Delete '${file.name}'?", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
        )
        if (confirm != JOptionPane.YES_OPTION) return
        if (!file.deleteRecursively()) JOptionPane.showMessageDialog(this, "Delete failed.", "Error", JOptionPane.ERROR_MESSAGE)
        else loadDirectory(currentDir)
    }

    private fun copyPathItem(file: File) = JMenuItem("Copy Path").apply {
        addActionListener {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(file.absolutePath), null)
        }
    }

    private fun openWith(file: File, editor: ExternalEditor) {
        try {
            val cmd = if (IS_WINDOWS) listOf("cmd", "/c", editor.executable, file.absolutePath)
                      else listOf(editor.executable, file.absolutePath)
            ProcessBuilder(cmd).start()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this, "Failed to launch ${editor.name}: ${e.message}", "Launch Error", JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun formatSize(bytes: Long) = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1_024} KB"
        bytes < 1_073_741_824 -> "${bytes / 1_048_576} MB"
        else -> "${bytes / 1_073_741_824} GB"
    }

    private inner class FileTableModel : AbstractTableModel() {
        private val columns = listOf("Name", "Size", "Modified")
        private var entries: List<FileEntry> = emptyList()

        fun setEntries(list: List<FileEntry>) {
            entries = list
            fireTableDataChanged()
        }

        fun entryAt(row: Int): FileEntry = entries[row]

        override fun getRowCount() = entries.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(col: Int) = columns[col]
        override fun getColumnClass(col: Int): Class<*> = String::class.java

        override fun getValueAt(row: Int, col: Int): Any {
            val entry = entries[row]
            return when (col) {
                0 -> when (entry) {
                    is FileEntry.ParentDir -> ".."
                    is FileEntry.Dir -> entry.file.name
                    is FileEntry.RegularFile -> entry.file.name
                }
                1 -> when (entry) {
                    is FileEntry.RegularFile -> formatSize(entry.file.length())
                    else -> ""
                }
                2 -> when (entry) {
                    is FileEntry.ParentDir -> ""
                    is FileEntry.Dir -> dateFmt.format(Date(entry.file.lastModified()))
                    is FileEntry.RegularFile -> dateFmt.format(Date(entry.file.lastModified()))
                }
                else -> ""
            }
        }

        override fun isCellEditable(row: Int, col: Int) = false
    }

    private inner class FileTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int,
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val entry = tableModel.entryAt(row)
            if (c is JLabel) {
                c.font = when {
                    entry is FileEntry.Dir || entry is FileEntry.ParentDir ->
                        c.font.deriveFont(Font.BOLD)
                    else -> c.font.deriveFont(Font.PLAIN)
                }
                c.horizontalAlignment = when (column) {
                    1 -> SwingConstants.RIGHT
                    else -> SwingConstants.LEFT
                }
            }
            return c
        }
    }
}

/** Tab header component with a label and a close (✕) button. */
private class TabHeader(title: String, onClose: () -> Unit) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {
    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        add(JLabel(title))
        add(JButton("\u2715").apply {
            toolTipText = "Close tab"
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false
            font = font.deriveFont(9f)
            addActionListener { onClose() }
        })
    }
}

sealed class FileEntry {
    object ParentDir : FileEntry()
    data class Dir(val file: File) : FileEntry()
    data class RegularFile(val file: File) : FileEntry()
}
