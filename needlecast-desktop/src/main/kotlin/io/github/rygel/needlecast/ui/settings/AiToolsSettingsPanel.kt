package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.AiCliDefinition
import io.github.rygel.needlecast.ui.AiCli
import io.github.rygel.needlecast.ui.KNOWN_AI_CLIS
import io.github.rygel.needlecast.ui.RemixIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

class AiToolsSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(BorderLayout(0, 6)) {

    init {
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)

        val quotaToggle = JCheckBox("Show Claude quota in status bar", ctx.config.claudeQuotaEnabled).apply {
            toolTipText = "Display 5-hour and 7-day usage percentages from your Claude subscription in the status bar. Requires Claude Code credentials."
            addActionListener {
                ctx.updateConfig(ctx.config.copy(claudeQuotaEnabled = isSelected))
                callbacks.onClaudeQuotaToggled(isSelected)
            }
        }

        val enabledMap = ctx.config.aiCliEnabled.toMutableMap()
        val builtIn    = KNOWN_AI_CLIS.map { it to false }
        val customDefs = ctx.config.customAiClis.map { AiCli(it.name, it.command, it.description) to true }
        val allClis    = (builtIn + customDefs).toMutableList()

        val listPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }

        fun rebuildList() {
            listPanel.removeAll()
            val gc = GridBagConstraints().apply { insets = Insets(2, 4, 2, 4); anchor = GridBagConstraints.WEST }
            allClis.forEachIndexed { i, (cli, isCustom) ->
                gc.gridy = i
                val enabled = enabledMap[cli.command] != false
                val cb = JCheckBox(cli.name, enabled).apply {
                    toolTipText = cli.description
                    addActionListener {
                        enabledMap[cli.command] = isSelected
                        ctx.updateConfig(ctx.config.copy(aiCliEnabled = enabledMap.toMap()))
                    }
                }
                gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
                listPanel.add(cb, gc)
                gc.gridx = 1; gc.weightx = 0.0
                listPanel.add(JLabel("<html><tt>${cli.command}</tt></html>").apply {
                    foreground = Color(0x888888)
                }, gc)
                gc.gridx = 2; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
                listPanel.add(JLabel(cli.description).apply {
                    font = font.deriveFont(Font.PLAIN, 11f)
                    foreground = Color(0x888888)
                }, gc)
            }
            gc.gridy = allClis.size; gc.gridx = 0; gc.gridwidth = 3
            gc.weighty = 1.0; gc.fill = GridBagConstraints.BOTH
            listPanel.add(JPanel(), gc)
            listPanel.revalidate(); listPanel.repaint()
        }

        rebuildList()

        val addBtn = JButton("+ Add Custom CLI").apply {
            addActionListener {
                val nameField = JTextField(14)
                val cmdField  = JTextField(14)
                val descField = JTextField(20)
                val form = JPanel(GridBagLayout()).apply {
                    val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST }
                    gc.gridx = 0; gc.gridy = 0; add(JLabel("Name:"), gc)
                    gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(nameField, gc)
                    gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; add(JLabel("Command:"), gc)
                    gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(cmdField, gc)
                    gc.gridx = 0; gc.gridy = 2; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; add(JLabel("Description:"), gc)
                    gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(descField, gc)
                }
                if (JOptionPane.showConfirmDialog(this@AiToolsSettingsPanel, form, "Add Custom CLI",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return@addActionListener
                val name = nameField.text.trim()
                val cmd  = cmdField.text.trim()
                if (name.isEmpty() || cmd.isEmpty()) return@addActionListener
                val def = AiCliDefinition(name, cmd, descField.text.trim())
                ctx.updateConfig(ctx.config.copy(customAiClis = ctx.config.customAiClis + def))
                allClis.add(AiCli(name, cmd, descField.text.trim()) to true)
                rebuildList()
            }
        }

        val removeBtn = JButton(RemixIcons.icon("ri-subtract-line", 16)).apply {
            addActionListener {
                val customOnly = allClis.filter { it.second }.map { it.first }
                if (customOnly.isEmpty()) {
                    JOptionPane.showMessageDialog(this@AiToolsSettingsPanel, "No custom CLIs to remove.", "Remove", JOptionPane.INFORMATION_MESSAGE)
                    return@addActionListener
                }
                val names  = customOnly.map { it.name }.toTypedArray()
                val choice = JOptionPane.showInputDialog(this@AiToolsSettingsPanel, "Select CLI to remove:",
                    "Remove Custom CLI", JOptionPane.PLAIN_MESSAGE, null, names, names[0]) as? String ?: return@addActionListener
                val toRemove = customOnly.first { it.name == choice }
                ctx.updateConfig(ctx.config.copy(customAiClis = ctx.config.customAiClis.filter { it.command != toRemove.command }))
                allClis.removeAll { it.second && it.first.command == toRemove.command }
                rebuildList()
            }
        }

        add(JPanel(BorderLayout()).apply {
            add(JLabel("<html>Check the AI tools shown in the project tree and AI Tools menu.<br>" +
                "Built-in tools are detected automatically; custom tools use PATH lookup.</html>").apply {
                border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            }, BorderLayout.NORTH)
            add(quotaToggle, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        add(JScrollPane(listPanel).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(addBtn); add(removeBtn) }, BorderLayout.SOUTH)
    }
}
