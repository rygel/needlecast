package io.github.rygel.needlecast.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

/**
 * Shown when a prompt body contains {varName} placeholders.
 * Presents one labelled text field per distinct variable name.
 *
 * [onConfirm] is called with the filled-in map only if the user clicks "Insert".
 * If the user cancels, [onConfirm] is never called.
 */
class VariableResolutionDialog(
    owner: Window,
    variables: List<String>,
    private val onConfirm: (Map<String, String>) -> Unit,
) : JDialog(owner, "Fill in Placeholders", ModalityType.APPLICATION_MODAL) {

    private val fields: Map<String, JTextField> = variables.associateWith { JTextField(24) }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }
        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        fields.entries.forEachIndexed { row, (name, field) ->
            gc.gridy = row

            gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
            gc.anchor = GridBagConstraints.WEST
            grid.add(JLabel("$name:"), gc)

            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
            grid.add(field, gc)
        }

        val insertButton = JButton("Insert").apply {
            addActionListener { confirm() }
        }
        val cancelButton = JButton("Cancel").apply {
            addActionListener { dispose() }
        }

        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6)).apply {
            add(insertButton)
            add(cancelButton)
        }

        val hint = JLabel(
            "<html><i>These placeholders were found in the prompt body. Fill them in to continue.</i></html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 4, 0) }

        val center = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 4, 12)
            add(hint, BorderLayout.NORTH)
            add(JScrollPane(grid).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        }

        add(center, BorderLayout.CENTER)
        add(buttonBar, BorderLayout.SOUTH)

        rootPane.defaultButton = insertButton

        pack()
        minimumSize = Dimension(360, 160)
        setLocationRelativeTo(owner)

        fields.values.firstOrNull()?.requestFocusInWindow()
    }

    private fun confirm() {
        val result = fields.mapValues { (_, f) -> f.text }
        dispose()
        onConfirm(result)
    }
}
