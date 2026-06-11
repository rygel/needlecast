package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.CommandDescriptor
import java.awt.BorderLayout
import java.awt.Component
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
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

class EditCommandDialog(
    owner: Window?,
    private val cmd: CommandDescriptor,
) : JDialog(owner, "Edit Command", ModalityType.APPLICATION_MODAL) {
    var result: CommandDescriptor? = null
        private set

    private val labelField = JTextField(cmd.label, 30)
    private val commandField = JTextField(cmd.argv.joinToString(" "), 40)

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(480, 160)
        setLocationRelativeTo(owner)

        val grid =
            JPanel(GridBagLayout()).apply {
                border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
            }
        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        fun row(
            r: Int,
            labelText: String,
            field: Component,
        ) {
            gc.gridy = r
            gc.gridx = 0
            gc.weightx = 0.0
            gc.anchor = GridBagConstraints.WEST
            gc.fill = GridBagConstraints.NONE
            grid.add(JLabel(labelText), gc)
            gc.gridx = 1
            gc.weightx = 1.0
            gc.fill = GridBagConstraints.HORIZONTAL
            grid.add(field, gc)
        }

        row(0, "Label:", labelField)
        row(1, "Command:", commandField)

        val ok = JButton("OK").apply { addActionListener { onOk() } }
        val cancel = JButton("Cancel").apply { addActionListener { dispose() } }
        val buttons =
            JPanel(FlowLayout(FlowLayout.RIGHT, 6, 4)).apply {
                add(ok)
                add(cancel)
            }

        contentPane =
            JPanel(BorderLayout()).apply {
                add(grid, BorderLayout.CENTER)
                add(buttons, BorderLayout.SOUTH)
            }
        pack()
        rootPane.defaultButton = ok
    }

    private fun onOk() {
        val label = labelField.text.trim()
        if (label.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Label must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        val argv =
            commandField.text
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        if (argv.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Command must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        result = cmd.copy(label = label, argv = argv)
        dispose()
    }
}
