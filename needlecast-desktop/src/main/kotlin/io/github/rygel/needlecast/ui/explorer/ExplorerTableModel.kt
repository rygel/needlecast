package io.github.rygel.needlecast.ui.explorer

import java.awt.Component
import java.awt.Font
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

sealed class FileEntry {
    object ParentDir : FileEntry()

    data class Dir(
        val file: File,
    ) : FileEntry()

    data class RegularFile(
        val file: File,
    ) : FileEntry()
}

internal const val COL_NAME = 0
internal const val COL_SIZE = 1
internal const val COL_MODIFIED = 2
internal val DEFAULT_EXPLORER_SORT = ExplorerSortState(COL_NAME, true)

internal fun formatSize(bytes: Long): String =
    when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1_024} KB"
        bytes < 1_073_741_824 -> "${bytes / 1_048_576} MB"
        else -> "${bytes / 1_073_741_824} GB"
    }

internal class FileTableModel : AbstractTableModel() {
    private val columns = listOf("Name", "Size", "Modified")
    private var entries: List<FileEntry> = emptyList()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")

    fun setEntries(list: List<FileEntry>) {
        entries = list
        fireTableDataChanged()
    }

    fun entryAt(row: Int): FileEntry = entries[row]

    override fun getRowCount() = entries.size

    override fun getColumnCount() = columns.size

    override fun getColumnName(col: Int) = columns[col]

    override fun getColumnClass(col: Int): Class<*> = String::class.java

    override fun getValueAt(
        row: Int,
        col: Int,
    ): Any {
        val entry = entries[row]
        return when (col) {
            0 -> {
                when (entry) {
                    is FileEntry.ParentDir -> ".."
                    is FileEntry.Dir -> entry.file.name
                    is FileEntry.RegularFile -> entry.file.name
                }
            }

            1 -> {
                when (entry) {
                    is FileEntry.RegularFile -> formatSize(entry.file.length())
                    else -> ""
                }
            }

            2 -> {
                when (entry) {
                    is FileEntry.ParentDir -> ""
                    is FileEntry.Dir -> dateFmt.format(Date(entry.file.lastModified()))
                    is FileEntry.RegularFile -> dateFmt.format(Date(entry.file.lastModified()))
                }
            }

            else -> {
                ""
            }
        }
    }

    override fun isCellEditable(
        row: Int,
        col: Int,
    ) = false
}

internal class FileTableCellRenderer(
    private val tableModel: FileTableModel,
) : DefaultTableCellRenderer() {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val entry = tableModel.entryAt(row)
        val displayText =
            when (column) {
                COL_SIZE -> {
                    when (entry) {
                        is FileEntry.RegularFile -> formatSize(entry.file.length())
                        else -> ""
                    }
                }

                COL_MODIFIED -> {
                    when (entry) {
                        is FileEntry.ParentDir -> ""
                        is FileEntry.Dir -> dateFmt.format(Date(entry.file.lastModified()))
                        is FileEntry.RegularFile -> dateFmt.format(Date(entry.file.lastModified()))
                    }
                }

                else -> {
                    value?.toString() ?: ""
                }
            }
        val c =
            super.getTableCellRendererComponent(table, displayText, isSelected, hasFocus, row, column)
        if (c is JLabel) {
            c.font =
                when {
                    entry is FileEntry.Dir || entry is FileEntry.ParentDir -> {
                        c.font.deriveFont(Font.BOLD)
                    }

                    else -> {
                        c.font.deriveFont(Font.PLAIN)
                    }
                }
            c.horizontalAlignment =
                when (column) {
                    1 -> SwingConstants.RIGHT
                    else -> SwingConstants.LEFT
                }
        }
        return c
    }
}
