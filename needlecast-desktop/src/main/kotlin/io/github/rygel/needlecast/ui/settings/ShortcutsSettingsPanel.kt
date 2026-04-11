package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke

class ShortcutsSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(BorderLayout(0, 8)) {

    init {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val current = ctx.config.shortcuts.toMutableMap()
        val fields  = mutableMapOf<String, JTextField>()

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }
        val gc = GridBagConstraints().apply { insets = Insets(3, 4, 3, 4) }

        defaultShortcuts.entries.forEachIndexed { row, (id, default) ->
            gc.gridy = row

            gc.gridx = 0; gc.weightx = 0.4; gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.WEST
            grid.add(JLabel(actionLabels[id] ?: id), gc)

            val field = JTextField(current[id] ?: default, 16).apply {
                name = id
                toolTipText = "Click and press a key combination to record"
                addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        val ks  = KeyStroke.getKeyStrokeForEvent(e)
                        val txt = ks.toString()
                            .replace("pressed ", "")
                            .replace("released ", "")
                            .trim()
                        if (txt.isNotEmpty()) { text = txt; e.consume() }
                    }
                })
            }
            fields[id] = field
            gc.gridx = 1; gc.weightx = 0.6; gc.fill = GridBagConstraints.HORIZONTAL
            grid.add(field, gc)

            gc.gridx = 2; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
            grid.add(JButton("Reset").apply {
                addActionListener { field.text = default }
            }, gc)
        }

        val saveButton = JButton("Save Shortcuts").apply {
            addActionListener {
                val updated = fields.mapValues { (id, f) ->
                    val v = f.text.trim()
                    if (v == defaultShortcuts[id]) null else v
                }.filterValues { it != null }.mapValues { it.value!! }
                ctx.updateConfig(ctx.config.copy(shortcuts = updated))
                callbacks.onShortcutsChanged()
                JOptionPane.showMessageDialog(
                    this@ShortcutsSettingsPanel,
                    ctx.i18n.translate("settings.saved"),
                    ctx.i18n.translate("settings.savedTitle"),
                    JOptionPane.INFORMATION_MESSAGE,
                )
            }
        }

        add(JLabel("<html><i>Click a field and press a key combination to record it. Reset restores the default.</i></html>").apply {
            border = BorderFactory.createEmptyBorder(6, 8, 2, 8)
        }, BorderLayout.NORTH)
        add(JScrollPane(grid).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(saveButton) }, BorderLayout.SOUTH)
    }

    companion object {
        val defaultShortcuts: LinkedHashMap<String, String> = linkedMapOf(
            "rescan"            to "F5",
            "activate-terminal" to "ctrl T",
            "focus-projects"    to "ctrl 1",
            "focus-explorer"    to "ctrl 2",
            "focus-terminal"    to "ctrl 3",
            "project-switcher"  to "ctrl P",
        )
        val actionLabels: Map<String, String> = mapOf(
            "rescan"            to "Rescan projects (F5)",
            "activate-terminal" to "Activate terminal",
            "focus-projects"    to "Focus project list",
            "focus-explorer"    to "Focus file explorer",
            "focus-terminal"    to "Focus terminal",
            "project-switcher"  to "Global project switcher",
        )
    }
}
