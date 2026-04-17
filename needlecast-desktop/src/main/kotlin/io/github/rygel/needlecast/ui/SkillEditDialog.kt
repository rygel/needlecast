package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.SkillEntry
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
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
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField

class SkillEditDialog(
    owner: Window?,
    title: String = "New Skill",
    private val existing: SkillEntry? = null,
) : JDialog(owner, title, ModalityType.APPLICATION_MODAL) {

    var result: Pair<SkillEntry, String>? = null
        private set

    private val nameField = JTextField(30)
    private val descField = JTextField(30)
    private val bodyArea = JTextArea(12, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(560, 380)
        setLocationRelativeTo(owner)

        if (existing != null) {
            nameField.text = existing.name
            nameField.isEnabled = false
            descField.text = existing.description
        }

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
        }
        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        fun row(r: Int, label: String, field: java.awt.Component, fill: Boolean = false) {
            gc.gridy = r; gc.gridx = 0; gc.weightx = 0.0
            gc.anchor = GridBagConstraints.NORTHWEST; gc.fill = GridBagConstraints.NONE; gc.weighty = 0.0
            grid.add(JLabel(label), gc)
            gc.gridx = 1; gc.weightx = 1.0
            gc.fill = if (fill) GridBagConstraints.BOTH else GridBagConstraints.HORIZONTAL
            if (fill) gc.weighty = 1.0
            grid.add(field, gc)
        }

        row(0, "Name:", nameField)
        row(1, "Description:", descField)
        row(2, "Body:", JScrollPane(bodyArea), fill = true)

        val ok = JButton("OK").apply { addActionListener { onOk() } }
        val cancel = JButton("Cancel").apply { addActionListener { dispose() } }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 4)).apply {
            add(ok); add(cancel)
        }

        contentPane = JPanel(BorderLayout()).apply {
            add(grid, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        pack()
        rootPane.defaultButton = ok
    }

    private fun onOk() {
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        if (name != name.lowercase().replace(Regex("[^a-z0-9-]"), "").replace(Regex("-+"), "-").trim('-')) {
            JOptionPane.showMessageDialog(this, "Name must be lowercase with hyphens only (e.g. my-skill).", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        val description = descField.text.trim()
        val body = bodyArea.text
        val skillDir = existing?.skillDir ?: java.nio.file.Path.of(
            System.getProperty("user.home"), ".needlecast", "skills", name
        )
        val entry = SkillEntry(name = name, description = description, skillDir = skillDir)
        result = entry to body
        dispose()
    }
}
