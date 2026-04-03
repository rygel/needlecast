package io.github.rygel.needlecast.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

/**
 * Simple dialog for editing per-project environment variable overrides.
 * Rows can be added, removed, and edited in-place. [onSave] is called with
 * the resulting map when the user clicks Save.
 */
class EnvEditorDialog(
    owner: Window,
    projectLabel: String,
    initial: Map<String, String>,
    private val onSave: (Map<String, String>) -> Unit,
) : JDialog(owner, "Environment Variables — $projectLabel", ModalityType.APPLICATION_MODAL) {

    private val tableModel = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int) = true
    }
    private val table = JTable(tableModel).apply {
        preferredScrollableViewportSize = Dimension(480, 200)
    }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(520, 300)

        // Populate from initial map
        initial.forEach { (k, v) -> tableModel.addRow(arrayOf(k, v)) }

        val addButton = JButton("+ Add Row").apply {
            addActionListener {
                tableModel.addRow(arrayOf("", ""))
                val row = tableModel.rowCount - 1
                table.editCellAt(row, 0)
                table.changeSelection(row, 0, false, false)
            }
        }
        val removeButton = JButton("- Remove Row").apply {
            addActionListener {
                val row = table.selectedRow
                if (row >= 0) {
                    if (table.isEditing) table.cellEditor.stopCellEditing()
                    tableModel.removeRow(row)
                }
            }
        }

        val saveButton = JButton("Save").apply {
            addActionListener { save() }
        }
        val cancelButton = JButton("Cancel").apply {
            addActionListener { dispose() }
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(addButton)
            add(removeButton)
        }
        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            add(saveButton)
            add(cancelButton)
        }

        val hint = JLabel("<html><i>Variables are merged on top of the system environment when running commands and terminals for this project.</i></html>").apply {
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        }

        val center = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(hint, BorderLayout.NORTH)
            add(JScrollPane(table), BorderLayout.CENTER)
            add(toolbar, BorderLayout.SOUTH)
        }

        add(center, BorderLayout.CENTER)
        add(buttonBar, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(owner)
    }

    private fun save() {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        val result = mutableMapOf<String, String>()
        for (row in 0 until tableModel.rowCount) {
            val key   = (tableModel.getValueAt(row, 0) as? String)?.trim() ?: continue
            val value = (tableModel.getValueAt(row, 1) as? String) ?: ""
            if (key.isNotEmpty()) result[key] = value
        }
        dispose()
        onSave(result)
    }
}
