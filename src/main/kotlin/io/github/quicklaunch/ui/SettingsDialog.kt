package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.ExternalEditor
import io.github.quicklaunch.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingWorker

class SettingsDialog(
    owner: JFrame,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    private val onShortcutsChanged: () -> Unit = {},
) : JDialog(owner, "Settings", true) {

    init {
        size = Dimension(600, 460)
        minimumSize = Dimension(520, 380)
        setLocationRelativeTo(owner)
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val tabs = JTabbedPane()
        tabs.addTab("External Editors", buildEditorsTab())
        tabs.addTab("Renovate", buildRenovateTab())
        tabs.addTab("Shortcuts", buildShortcutsTab())

        contentPane = tabs
    }

    // ── External Editors tab ──────────────────────────────────────────────

    private fun buildEditorsTab(): JPanel {
        val model = DefaultListModel<ExternalEditor>().apply {
            ctx.config.externalEditors.forEach { addElement(it) }
        }
        val list = JList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }

        val addButton = JButton("+")
        val removeButton = JButton("−").apply { isEnabled = false }

        list.addListSelectionListener {
            removeButton.isEnabled = list.selectedValue != null
        }

        addButton.addActionListener {
            val nameField = JTextField(12)
            val execField = JTextField(12)
            val form = JPanel(GridBagLayout()).apply {
                val c = GridBagConstraints().apply {
                    insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST
                }
                c.gridx = 0; c.gridy = 0; add(JLabel("Name:"), c)
                c.gridx = 1; add(nameField, c)
                c.gridx = 0; c.gridy = 1; add(JLabel("Executable:"), c)
                c.gridx = 1; add(execField, c)
            }
            val result = JOptionPane.showConfirmDialog(
                this, form, "Add External Editor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
            )
            if (result == JOptionPane.OK_OPTION) {
                val name = nameField.text.trim()
                val exec = execField.text.trim()
                if (name.isNotEmpty() && exec.isNotEmpty()) {
                    val editor = ExternalEditor(name, exec)
                    model.addElement(editor)
                    saveEditors(model)
                }
            }
        }

        removeButton.addActionListener {
            val selected = list.selectedValue ?: return@addActionListener
            model.removeElement(selected)
            saveEditors(model)
            removeButton.isEnabled = false
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(addButton)
            add(removeButton)
        }

        return JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("Editors shown in the 'Open with' menu:"), BorderLayout.NORTH)
            add(JScrollPane(list), BorderLayout.CENTER)
            add(toolbar, BorderLayout.SOUTH)
        }
    }

    private fun saveEditors(model: DefaultListModel<ExternalEditor>) {
        val editors = (0 until model.size).map { model.getElementAt(it) }
        ctx.updateConfig(ctx.config.copy(externalEditors = editors))
    }

    // ── Renovate tab ──────────────────────────────────────────────────────

    private fun buildRenovateTab(): JPanel {
        val statusLabel = JLabel("Checking…", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD)
        }
        val versionLabel = JLabel("", SwingConstants.CENTER)

        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
        }

        val infoLabel = JLabel(
            "<html>Renovate keeps your dependencies up to date by opening automated PRs.<br>" +
            "Install it globally so it can also be run locally via <code>renovate-local.sh</code>.</html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel, BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))

        val managers = buildInstallOptions()
        managers.forEach { (label, cmd) ->
            buttonsPanel.add(JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    sendToTerminal("$cmd\n")
                    JOptionPane.showMessageDialog(
                        this@SettingsDialog,
                        "Command sent to terminal:\n$cmd",
                        "Installing Renovate",
                        JOptionPane.INFORMATION_MESSAGE,
                    )
                }
            })
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply {
            addActionListener { checkRenovate(statusLabel, versionLabel) }
        }
        val bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(recheckButton) }

        panel.add(infoLabel, BorderLayout.NORTH)
        val centerPanel = JPanel(BorderLayout(0, 8)).apply {
            add(statusPanel, BorderLayout.NORTH)
            add(installPanel, BorderLayout.CENTER)
        }
        panel.add(centerPanel, BorderLayout.CENTER)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        checkRenovate(statusLabel, versionLabel)
        return panel
    }

    private fun checkRenovate(statusLabel: JLabel, versionLabel: JLabel) {
        statusLabel.text = "Checking…"
        statusLabel.foreground = statusLabel.foreground
        versionLabel.text = ""

        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                return try {
                    val probe = if (IS_WINDOWS) listOf("where", "renovate") else listOf("which", "renovate")
                    val found = ProcessBuilder(probe).redirectErrorStream(true).start().waitFor() == 0
                    if (found) {
                        val proc = ProcessBuilder(listOf("renovate", "--version"))
                            .redirectErrorStream(true).start()
                        val version = proc.inputStream.bufferedReader().readLine() ?: ""
                        proc.waitFor()
                        true to version.trim()
                    } else {
                        false to ""
                    }
                } catch (_: Exception) { false to "" }
            }

            override fun done() {
                val (found, version) = get()
                if (found) {
                    statusLabel.text = "✓  Renovate is installed"
                    statusLabel.foreground = java.awt.Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "version $version" else ""
                } else {
                    statusLabel.text = "✗  Renovate not found on PATH"
                    statusLabel.foreground = java.awt.Color(0xF44336)
                    versionLabel.text = "Use one of the buttons below to install it"
                }
            }
        }.execute()
    }

    private fun buildInstallOptions(): List<Pair<String, String>> = buildList {
        add("npm" to "npm install -g renovate")
        if (!IS_WINDOWS) add("Homebrew" to "brew install renovate")
        if (IS_WINDOWS) add("Scoop" to "scoop install renovate")
        if (IS_WINDOWS) add("Chocolatey" to "choco install renovate")
    }

    // ── Shortcuts tab ─────────────────────────────────────────────────────

    private val defaultShortcuts = linkedMapOf(
        "rescan"            to "F5",
        "activate-terminal" to "ctrl T",
        "focus-projects"    to "ctrl 1",
        "focus-explorer"    to "ctrl 2",
        "focus-terminal"    to "ctrl 3",
        "project-switcher"  to "ctrl P",
    )
    private val actionLabels = mapOf(
        "rescan"            to "Rescan projects (F5)",
        "activate-terminal" to "Activate terminal",
        "focus-projects"    to "Focus project list",
        "focus-explorer"    to "Focus file explorer",
        "focus-terminal"    to "Focus terminal",
        "project-switcher"  to "Global project switcher",
    )

    private fun buildShortcutsTab(): JPanel {
        val current = ctx.config.shortcuts.toMutableMap()
        // Map of actionId → recording field
        val fields = mutableMapOf<String, JTextField>()

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }
        val gc = GridBagConstraints().apply { insets = Insets(3, 4, 3, 4) }

        defaultShortcuts.entries.forEachIndexed { row, (id, default) ->
            gc.gridy = row

            gc.gridx = 0; gc.weightx = 0.4; gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.WEST
            grid.add(JLabel(actionLabels[id] ?: id), gc)

            val field = JTextField(current[id] ?: default, 16).apply {
                toolTipText = "Click and press a key combination to record"
                addKeyListener(object : java.awt.event.KeyAdapter() {
                    override fun keyPressed(e: java.awt.event.KeyEvent) {
                        val ks = javax.swing.KeyStroke.getKeyStrokeForEvent(e)
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
                onShortcutsChanged()
                JOptionPane.showMessageDialog(this@SettingsDialog,
                    "Shortcuts saved. They take effect immediately.", "Saved", JOptionPane.INFORMATION_MESSAGE)
            }
        }

        return JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(JLabel("<html><i>Click a field and press a key combination to record it. Reset restores the default.</i></html>").apply {
                border = BorderFactory.createEmptyBorder(6, 8, 2, 8)
            }, BorderLayout.NORTH)
            add(JScrollPane(grid).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(saveButton) }, BorderLayout.SOUTH)
        }
    }
}
