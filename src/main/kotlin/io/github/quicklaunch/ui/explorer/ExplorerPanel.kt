package io.github.quicklaunch.ui.explorer

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.ExternalEditor
import io.github.quicklaunch.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ExplorerPanel(private val ctx: AppContext) : JPanel(BorderLayout()) {

    private var currentDir: File = File(System.getProperty("user.home"))
    private val addressField = JTextField()
    private val tableModel = FileTableModel()
    private val table = JTable(tableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        fillsViewportHeight = true
        setDefaultRenderer(Any::class.java, FileTableCellRenderer())
        tableHeader.reorderingAllowed = false
    }
    private val editorPanel = EditorPanel(ctx)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
    private var contentSplitInitialized = false
    private lateinit var contentSplit: JSplitPane

    init {
        val upButton = JButton("\u2191").apply {
            toolTipText = "Go up one level"
            addActionListener { navigateUp() }
        }
        val refreshButton = JButton("\u27F3").apply {
            toolTipText = "Refresh"
            addActionListener { loadDirectory(currentDir) }
        }

        val addressBar = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(upButton, BorderLayout.WEST)
            add(addressField, BorderLayout.CENTER)
            add(refreshButton, BorderLayout.EAST)
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
                    if (editorPanel.checkUnsaved()) editorPanel.openFile(entry.file)
                } else if (e.clickCount == 2) {
                    handleActivate(entry)
                }
            }
        })

        contentSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(table), editorPanel).apply {
            resizeWeight = 0.5
        }

        // Set divider to 50% on first resize
        contentSplit.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (!contentSplitInitialized && contentSplit.height > 0) {
                    contentSplit.setDividerLocation(0.5)
                    contentSplitInitialized = true
                }
            }
        })

        val topPanel = JPanel(BorderLayout()).apply {
            add(addressBar, BorderLayout.NORTH)
            add(contentSplit, BorderLayout.CENTER)
        }

        add(topPanel, BorderLayout.CENTER)
        navigateTo(currentDir)
    }

    fun setRootDirectory(dir: File) {
        if (dir.isDirectory) navigateTo(dir)
    }

    fun applyTheme(dark: Boolean) = editorPanel.applyTheme(dark)

    fun requestFocusOnTree() = table.requestFocusInWindow()

    private fun navigateTo(dir: File) {
        if (!dir.isDirectory) return
        currentDir = dir
        addressField.text = dir.absolutePath
        loadDirectory(dir)
    }

    private fun navigateUp() {
        val parent = currentDir.parentFile ?: return
        if (editorPanel.checkUnsaved()) navigateTo(parent)
    }

    private fun loadDirectory(dir: File) {
        val children = (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))

        val entries = mutableListOf<FileEntry>()
        if (dir.parentFile != null) entries.add(FileEntry.ParentDir)
        children.filter { it.isDirectory }.mapTo(entries) { FileEntry.Dir(it) }
        children.filter { it.isFile }.mapTo(entries) { FileEntry.RegularFile(it) }

        tableModel.setEntries(entries)
    }

    private fun handleActivate(entry: FileEntry) {
        when (entry) {
            is FileEntry.ParentDir -> navigateUp()
            is FileEntry.Dir -> if (editorPanel.checkUnsaved()) navigateTo(entry.file)
            is FileEntry.RegularFile -> if (editorPanel.checkUnsaved()) editorPanel.openFile(entry.file)
        }
    }

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
                menu.add(copyPathItem(entry.file))
            }
            is FileEntry.RegularFile -> {
                menu.add(JMenuItem("Open in Editor").apply {
                    addActionListener { if (editorPanel.checkUnsaved()) editorPanel.openFile(entry.file) }
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
                menu.add(copyPathItem(entry.file))
            }
        }
        menu.show(table, x, y)
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

sealed class FileEntry {
    object ParentDir : FileEntry()
    data class Dir(val file: File) : FileEntry()
    data class RegularFile(val file: File) : FileEntry()
}
