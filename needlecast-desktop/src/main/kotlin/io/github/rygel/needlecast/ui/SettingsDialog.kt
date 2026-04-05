package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.AiCliDefinition
import io.github.rygel.needlecast.model.ExternalEditor
import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.outerstellar.i18n.Language
import javax.swing.JComboBox
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
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
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

class SettingsDialog(
    owner: JFrame,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    private val onShortcutsChanged: () -> Unit = {},
    private val onLayoutChanged: () -> Unit = {},
    private val onTerminalColorsChanged: (fg: java.awt.Color?, bg: java.awt.Color?) -> Unit = { _, _ -> },
    private val onFontSizeChanged: (Int) -> Unit = {},
    private val onSyntaxThemeChanged: () -> Unit = {},
) : JDialog(owner, ctx.i18n.translate("settings.title"), true) {

    init {
        size = Dimension(600, 500)
        minimumSize = Dimension(520, 420)
        setLocationRelativeTo(owner)
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val i18n = ctx.i18n
        val tabs = JTabbedPane()
        tabs.addTab(i18n.translate("settings.tab.editors"),   buildEditorsTab())
        tabs.addTab("AI Tools",                               buildAiToolsTab())
        tabs.addTab(i18n.translate("settings.tab.renovate"),  buildRenovateTab())
        tabs.addTab(i18n.translate("settings.tab.apm"),       buildApmTab())
        tabs.addTab(i18n.translate("settings.tab.shortcuts"), buildShortcutsTab())
        tabs.addTab(i18n.translate("settings.tab.language"),  buildLanguageTab())
        tabs.addTab("Layout & Terminal",                       buildLayoutTab())

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

        val outputArea = buildOutputArea()

        // All action buttons — disabled while any command is running
        val allActionButtons = mutableListOf<JButton>()
        fun lockButtons() = allActionButtons.forEach { it.isEnabled = false }
        fun unlockButtons() = allActionButtons.forEach { it.isEnabled = true }

        // ── Installation status ──────────────────────────────────────────
        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel, BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        buildInstallOptions().forEach { (label, cmd) ->
            val btn = JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    lockButtons()
                    runCommandStreaming(cmd, outputArea) {
                        unlockButtons()
                        checkRenovate(statusLabel, versionLabel)
                    }
                }
            }
            allActionButtons.add(btn)
            buttonsPanel.add(btn)
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply {
            addActionListener { checkRenovate(statusLabel, versionLabel) }
        }

        // ── Run Renovate ─────────────────────────────────────────────────
        val envToken = System.getenv("RENOVATE_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: ""
        val tokenField = javax.swing.JPasswordField(envToken, 28).apply {
            toolTipText = "GitHub token with Contents R/W and Pull requests R/W"
        }
        val repoField = JTextField(detectGitRepo() ?: "", 22).apply {
            toolTipText = "GitHub repo (owner/name)"
        }
        val dryRunCheckbox = javax.swing.JCheckBox("Dry run (no PRs created)", false)

        val runLocalButton = JButton("Run (local CLI)").apply {
            toolTipText = "Run 'renovate' directly (must be installed)"
            addActionListener {
                val token = String(tokenField.password).trim()
                val repo = repoField.text.trim()
                if (token.isEmpty()) {
                    JOptionPane.showMessageDialog(this@SettingsDialog,
                        "A GitHub token is required.\nSet RENOVATE_TOKEN / GITHUB_TOKEN or enter one above.",
                        "Missing token", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                if (repo.isEmpty()) {
                    JOptionPane.showMessageDialog(this@SettingsDialog,
                        "Enter the GitHub repository (owner/name).",
                        "Missing repository", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                val dryFlag = if (dryRunCheckbox.isSelected) " --dry-run=full" else ""
                lockButtons()
                runCommandStreaming("renovate$dryFlag --platform=github $repo", outputArea,
                    env = mapOf("RENOVATE_TOKEN" to token, "LOG_LEVEL" to "info")) {
                    unlockButtons()
                }
            }
        }
        allActionButtons.add(runLocalButton)

        val runDockerButton = JButton("Run (Docker)").apply {
            toolTipText = "Run Renovate inside a Docker container"
            addActionListener {
                val token = String(tokenField.password).trim()
                val repo = repoField.text.trim()
                if (token.isEmpty()) {
                    JOptionPane.showMessageDialog(this@SettingsDialog,
                        "A GitHub token is required.\nSet RENOVATE_TOKEN / GITHUB_TOKEN or enter one above.",
                        "Missing token", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                if (repo.isEmpty()) {
                    JOptionPane.showMessageDialog(this@SettingsDialog,
                        "Enter the GitHub repository (owner/name).",
                        "Missing repository", JOptionPane.WARNING_MESSAGE)
                    return@addActionListener
                }
                val dryFlag = if (dryRunCheckbox.isSelected) " --dry-run=full" else ""
                val cmd = "docker run --rm" +
                    " -e RENOVATE_TOKEN=$token" +
                    " -e LOG_LEVEL=info" +
                    " ghcr.io/renovatebot/renovate:latest" +
                    "$dryFlag --platform=github $repo"
                lockButtons()
                runCommandStreaming(cmd, outputArea) { unlockButtons() }
            }
        }
        allActionButtons.add(runDockerButton)

        val runPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Run Renovate")
        }
        val gc = GridBagConstraints().apply {
            insets = Insets(3, 6, 3, 6); anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL
        }
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
        runPanel.add(JLabel("Token:"), gc)
        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.gridwidth = 2
        runPanel.add(tokenField, gc)

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; gc.gridwidth = 1
        runPanel.add(JLabel("Repository:"), gc)
        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.gridwidth = 2
        runPanel.add(repoField, gc)

        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 3; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
        runPanel.add(dryRunCheckbox, gc)

        gc.gridy = 3; gc.gridwidth = 3
        runPanel.add(JPanel(FlowLayout(FlowLayout.CENTER, 8, 2)).apply {
            add(runLocalButton); add(runDockerButton)
        }, gc)

        // ── Layout ───────────────────────────────────────────────────────
        val topSection = JPanel(GridBagLayout()).apply {
            val tc = GridBagConstraints().apply {
                gridx = 0; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; insets = Insets(0, 0, 4, 0)
            }
            tc.gridy = 0; add(JLabel(
                "<html>Renovate keeps your dependencies up to date by opening automated PRs.<br>" +
                "Install it globally so it can also be run locally via <code>renovate-local.sh</code>.</html>"
            ).apply { border = BorderFactory.createEmptyBorder(0, 0, 4, 0) }, tc)
            tc.gridy = 1; add(statusPanel, tc)
            tc.gridy = 2; add(installPanel, tc)
            tc.gridy = 3; add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(recheckButton) }, tc)
            tc.gridy = 4; add(runPanel, tc)
        }

        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
        }
        panel.add(JScrollPane(topSection).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)
        panel.add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
            preferredSize = Dimension(0, 160)
        }, BorderLayout.SOUTH)

        checkRenovate(statusLabel, versionLabel)
        return panel
    }

    private fun checkRenovate(statusLabel: JLabel, versionLabel: JLabel) {
        statusLabel.text = "Checking…"
        versionLabel.text = ""

        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val found = ProcessExecutor.isOnPath("renovate")
                if (!found) return false to ""
                val version = ProcessExecutor.run(listOf("renovate", "--version"), timeoutMs = 5_000L)
                    ?.output?.lines()?.firstOrNull()?.trim() ?: ""
                return true to version
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
        val nodePackageManager = if (ProcessExecutor.isOnPath("pnpm")) "pnpm" else "npm"
        add(nodePackageManager to "$nodePackageManager add -g renovate")
        if (!IS_WINDOWS) add("Homebrew" to "brew install renovate")
        if (IS_WINDOWS) add("Scoop" to "scoop install renovate")
        if (IS_WINDOWS) add("Chocolatey" to "choco install renovate")
    }

    // ── APM tab ───────────────────────────────────────────────────────────

    private fun buildApmTab(): JPanel {
        val statusLabel  = JLabel("Checking…", SwingConstants.CENTER).apply { font = font.deriveFont(Font.BOLD) }
        val versionLabel = JLabel("", SwingConstants.CENTER)

        val infoLabel = JLabel(
            "<html>APM (Agent Package Manager) by Microsoft manages AI agent dependencies:<br>" +
            "skills, prompts, instructions, plugins, and MCP servers via an <code>apm.yml</code> manifest.<br>" +
            "Projects with <code>apm.yml</code> are automatically detected and their commands surfaced.</html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel,  BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val outputArea = buildOutputArea()

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))

        val installButtons = mutableListOf<JButton>()
        buildApmInstallOptions().forEach { (label, cmd) ->
            val btn = JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    installButtons.forEach { it.isEnabled = false }
                    runCommandStreaming(cmd, outputArea) {
                        installButtons.forEach { it.isEnabled = true }
                        checkApm(statusLabel, versionLabel)
                    }
                }
            }
            installButtons.add(btn)
            buttonsPanel.add(btn)
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply {
            addActionListener { checkApm(statusLabel, versionLabel) }
        }

        val topSection = JPanel(BorderLayout(0, 8)).apply {
            add(infoLabel, BorderLayout.NORTH)
            val mid = JPanel(BorderLayout(0, 8)).apply {
                add(statusPanel, BorderLayout.NORTH)
                add(installPanel, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(recheckButton) }, BorderLayout.SOUTH)
            }
            add(mid, BorderLayout.CENTER)
        }

        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
        }
        panel.add(topSection, BorderLayout.NORTH)
        panel.add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
            preferredSize = Dimension(0, 160)
        }, BorderLayout.CENTER)

        checkApm(statusLabel, versionLabel)
        return panel
    }

    private fun checkApm(statusLabel: JLabel, versionLabel: JLabel) {
        statusLabel.text = "Checking…"
        versionLabel.text = ""

        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val found = ProcessExecutor.isOnPath("apm")
                if (!found) return false to ""
                val version = ProcessExecutor.run(listOf("apm", "--version"), timeoutMs = 5_000L)
                    ?.output?.lines()?.firstOrNull()?.trim() ?: ""
                return true to version
            }

            override fun done() {
                val (found, version) = get()
                if (found) {
                    statusLabel.text = "✓  APM is installed"
                    statusLabel.foreground = java.awt.Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "version $version" else ""
                } else {
                    statusLabel.text = "✗  APM not found on PATH"
                    statusLabel.foreground = java.awt.Color(0xF44336)
                    versionLabel.text = "Use one of the buttons below to install it"
                }
            }
        }.execute()
    }

    private fun buildApmInstallOptions(): List<Pair<String, String>> = buildList {
        if (!IS_WINDOWS) add("curl"     to "curl -sSL https://aka.ms/apm-unix | sh")
        if (IS_WINDOWS)  add("PowerShell" to "irm https://aka.ms/apm-windows | iex")
        if (!IS_WINDOWS) add("Homebrew" to "brew install microsoft/apm/apm")
        add("pip"                       to "pip install apm-cli")
        if (IS_WINDOWS)  add("Scoop"   to "scoop install apm")
    }

    // ── Shortcuts tab ─────────────────────────────────────────────────────

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
                name = id
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
                    ctx.i18n.translate("settings.saved"),
                    ctx.i18n.translate("settings.savedTitle"),
                    JOptionPane.INFORMATION_MESSAGE)
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

    // ── AI Tools tab ─────────────────────────────────────────────────────

    private fun buildAiToolsTab(): JPanel {
        val enabledMap = ctx.config.aiCliEnabled.toMutableMap()

        // Build list of all CLIs: built-in + custom
        val builtIn = KNOWN_AI_CLIS.map { it to false }  // second = isCustom
        val customDefs = ctx.config.customAiClis.map {
            AiCli(it.name, it.command, it.description) to true
        }
        val allClis = (builtIn + customDefs).toMutableList()

        // Scroll panel with one checkbox row per CLI
        val listPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }

        fun rebuildList() {
            listPanel.removeAll()
            val gc = GridBagConstraints().apply { insets = Insets(2, 4, 2, 4); anchor = GridBagConstraints.WEST }
            allClis.forEachIndexed { i, (cli, isCustom) ->
                gc.gridy = i

                val enabled = enabledMap[cli.command] != false
                val cb = javax.swing.JCheckBox(cli.name, enabled).apply {
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
                    foreground = java.awt.Color(0x888888)
                }, gc)

                gc.gridx = 2; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
                listPanel.add(JLabel(cli.description).apply {
                    font = font.deriveFont(java.awt.Font.PLAIN, 11f)
                    foreground = java.awt.Color(0x888888)
                }, gc)
            }
            // Spacer
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
                if (JOptionPane.showConfirmDialog(this@SettingsDialog, form, "Add Custom CLI",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return@addActionListener
                val name = nameField.text.trim()
                val cmd  = cmdField.text.trim()
                if (name.isEmpty() || cmd.isEmpty()) return@addActionListener
                val def = io.github.rygel.needlecast.model.AiCliDefinition(name, cmd, descField.text.trim())
                val updated = ctx.config.customAiClis + def
                ctx.updateConfig(ctx.config.copy(customAiClis = updated))
                allClis.add(AiCli(name, cmd, descField.text.trim()) to true)
                rebuildList()
            }
        }

        val removeBtn = JButton("− Remove Custom CLI").apply {
            addActionListener {
                val customOnly = allClis.filter { it.second }.map { it.first }
                if (customOnly.isEmpty()) {
                    JOptionPane.showMessageDialog(this@SettingsDialog, "No custom CLIs to remove.", "Remove", JOptionPane.INFORMATION_MESSAGE)
                    return@addActionListener
                }
                val names = customOnly.map { it.name }.toTypedArray()
                val choice = JOptionPane.showInputDialog(this@SettingsDialog, "Select CLI to remove:",
                    "Remove Custom CLI", JOptionPane.PLAIN_MESSAGE, null, names, names[0]) as? String ?: return@addActionListener
                val toRemove = customOnly.first { it.name == choice }
                val updatedDefs = ctx.config.customAiClis.filter { it.command != toRemove.command }
                ctx.updateConfig(ctx.config.copy(customAiClis = updatedDefs))
                allClis.removeAll { it.second && it.first.command == toRemove.command }
                rebuildList()
            }
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(addBtn); add(removeBtn)
        }

        return JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
            add(JLabel("<html>Check the AI tools shown in the project tree and AI Tools menu.<br>" +
                "Built-in tools are detected automatically; custom tools use PATH lookup.</html>").apply {
                border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            }, BorderLayout.NORTH)
            add(JScrollPane(listPanel).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
            add(toolbar, BorderLayout.SOUTH)
        }
    }

    // ── Language tab ──────────────────────────────────────────────────────

    private fun buildLanguageTab(): JPanel {
        val i18n = ctx.i18n
        val languages = Language.availableLanguages()
        val currentLocale = i18n.getLocale()

        val comboModel = languages.map { l -> l.nativeName }.toTypedArray()
        val combo = JComboBox(comboModel)
        val currentIdx = languages.indexOfFirst { l -> l.locale.language == currentLocale.language }
        if (currentIdx >= 0) combo.selectedIndex = currentIdx

        val applyButton = JButton(i18n.translate("settings.language.apply")).apply {
            addActionListener {
                val selected = languages[combo.selectedIndex]
                ctx.switchLocale(selected.locale)
                JOptionPane.showMessageDialog(
                    this@SettingsDialog,
                    i18n.translate("settings.language.applied", selected.displayName),
                    i18n.translate("settings.language.title"),
                    JOptionPane.INFORMATION_MESSAGE,
                )
            }
        }

        return JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
            val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

            gc.gridy = 0; gc.gridx = 0; gc.weightx = 0.0; gc.anchor = GridBagConstraints.WEST
            add(JLabel(i18n.translate("settings.language.select")), gc)

            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
            add(combo, gc)

            gc.gridy = 1; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 0.0
            gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
            add(JLabel("<html><i>${i18n.translate("settings.language.description")}</i></html>").apply {
                foreground = foreground.darker()
            }, gc)

            gc.gridy = 2; gc.gridwidth = 2; gc.fill = GridBagConstraints.NONE
            gc.anchor = GridBagConstraints.EAST
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { add(applyButton) }, gc)

            // Spacer to push content to the top
            gc.gridy = 3; gc.weighty = 1.0; gc.fill = GridBagConstraints.BOTH
            add(JPanel(), gc)
        }
    }

    // ── Layout & Terminal tab ─────────────────────────────────────────────────

    private fun buildLayoutTab(): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
            val gc = GridBagConstraints().apply {
                insets = Insets(4, 4, 4, 4)
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                gridx = 0; gridy = 0; weightx = 1.0
            }

            // ── Layout section ────────────────────────────────────────────
            add(JLabel("Layout").apply { font = font.deriveFont(Font.BOLD) }, gc)

            val tabsOnTopCb = javax.swing.JCheckBox("Show panel tabs at the top", ctx.config.tabsOnTop)
            gc.gridy = 1
            add(tabsOnTopCb, gc)

            tabsOnTopCb.addActionListener {
                ctx.updateConfig(ctx.config.copy(tabsOnTop = tabsOnTopCb.isSelected))
                onLayoutChanged()
            }

            val dockingHighlightCb = javax.swing.JCheckBox(
                "Highlight active docking panel border  [alpha]",
                ctx.config.dockingActiveHighlight,
            ).apply {
                toolTipText = "ModernDocking draws a border around the currently active panel. Experimental — may look odd with some themes."
            }
            gc.gridy = 2
            add(dockingHighlightCb, gc)

            dockingHighlightCb.addActionListener {
                ctx.updateConfig(ctx.config.copy(dockingActiveHighlight = dockingHighlightCb.isSelected))
                io.github.andrewauclair.moderndocking.settings.Settings.setActiveHighlighterEnabled(dockingHighlightCb.isSelected)
            }

            // ── Terminal section ──────────────────────────────────────────
            gc.gridy = 3; gc.insets = Insets(12, 4, 4, 4)
            add(JLabel("Terminal").apply { font = font.deriveFont(Font.BOLD) }, gc)

            gc.gridy = 4; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.WEST
            val fontSizeSpinner = javax.swing.JSpinner(
                javax.swing.SpinnerNumberModel(ctx.config.terminalFontSize, 8, 36, 1)
            ).apply { preferredSize = java.awt.Dimension(70, preferredSize.height) }
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(JLabel("Font size:"))
                add(fontSizeSpinner)
            }, gc)
            fontSizeSpinner.addChangeListener {
                val size = fontSizeSpinner.value as Int
                ctx.updateConfig(ctx.config.copy(terminalFontSize = size))
                onFontSizeChanged(size)
            }

            gc.gridy = 5; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL
            add(JLabel("Default shell (per-project shell overrides this):"), gc)

            // Detect installed shells in background, populate combo when ready
            val manualItem = ShellInfo("Manual entry…", "")
            val osDefault  = ShellInfo("OS default (cmd.exe / bash)", "")
            val shellCombo = JComboBox(arrayOf<Any>(osDefault, manualItem))
            val shellField = JTextField(ctx.config.defaultShell ?: "", 28).apply {
                isVisible = ctx.config.defaultShell?.isNotBlank() == true
            }

            // Pre-select current value if it matches a known shell
            fun refreshComboSelection(shells: List<ShellInfo>) {
                val current = ctx.config.defaultShell?.trim()
                if (current.isNullOrBlank()) {
                    shellCombo.selectedItem = osDefault
                } else {
                    val match = shells.firstOrNull { it.command == current }
                    shellCombo.selectedItem = match ?: manualItem
                    if (match == null) shellField.text = current
                }
            }

            shellCombo.addActionListener {
                when (shellCombo.selectedItem) {
                    osDefault  -> { shellField.isVisible = false; ctx.updateConfig(ctx.config.copy(defaultShell = null)) }
                    manualItem -> { shellField.isVisible = true }
                    is ShellInfo -> {
                        val s = shellCombo.selectedItem as ShellInfo
                        shellField.isVisible = false
                        ctx.updateConfig(ctx.config.copy(defaultShell = s.command))
                    }
                }
                shellField.revalidate(); shellField.repaint()
                revalidate(); repaint()
            }

            gc.gridy = 6; gc.fill = GridBagConstraints.HORIZONTAL
            add(shellCombo, gc)
            gc.gridy = 7
            add(shellField, gc)

            val applyShellBtn = JButton("Apply").apply {
                isVisible = shellField.isVisible
                addActionListener {
                    val v = shellField.text.trim().takeIf { it.isNotEmpty() }
                    ctx.updateConfig(ctx.config.copy(defaultShell = v))
                }
            }
            shellField.addPropertyChangeListener("visible") { applyShellBtn.isVisible = shellField.isVisible }

            val shellNote = JLabel("<html><i>Takes effect on next terminal activation.</i></html>").apply {
                font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            }
            gc.gridy = 8; gc.insets = Insets(0, 4, 4, 4)
            add(shellNote, gc)
            gc.gridy = 9; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.EAST
            add(applyShellBtn, gc)

            // Load shells in background; cancel if the dialog closes before detection finishes
            val shellWorker = object : SwingWorker<List<ShellInfo>, Unit>() {
                override fun doInBackground() = ShellDetector.detect()
                override fun done() {
                    if (isCancelled) return
                    val shells = try { get() } catch (_: Exception) { emptyList() }
                    val currentSelected = shellCombo.selectedItem
                    shellCombo.removeAllItems()
                    shellCombo.addItem(osDefault)
                    shells.forEach { shellCombo.addItem(it) }
                    shellCombo.addItem(manualItem)
                    shellCombo.setRenderer { list, value, index, sel, focus ->
                        javax.swing.DefaultListCellRenderer()
                            .getListCellRendererComponent(list, value, index, sel, focus)
                            .also { c -> if (value is ShellInfo) (c as? JLabel)?.text = value.displayName }
                    }
                    refreshComboSelection(shells)
                    if (currentSelected == manualItem) shellCombo.selectedItem = manualItem
                }
            }
            addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent) { shellWorker.cancel(true) }
            })
            shellWorker.execute()

            // ── Terminal colors section ───────────────────────────────────
            gc.gridy = 10; gc.insets = Insets(16, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
            add(JLabel("Terminal Colors").apply { font = font.deriveFont(Font.BOLD) }, gc)

            fun colorSwatch(hex: String?): JButton {
                val btn = JButton()
                btn.preferredSize = java.awt.Dimension(60, 22)
                btn.background = hex?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
                    ?: java.awt.Color.GRAY
                btn.isOpaque = true
                btn.isBorderPainted = true
                return btn
            }

            fun hexOrNull(c: java.awt.Color?): String? = c?.let { "#%02X%02X%02X".format(it.red, it.green, it.blue) }

            var currentFgColor: java.awt.Color? = ctx.config.terminalForeground
                ?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
            var currentBgColor: java.awt.Color? = ctx.config.terminalBackground
                ?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }

            val fgSwatch = colorSwatch(ctx.config.terminalForeground)
            val bgSwatch = colorSwatch(ctx.config.terminalBackground)

            fun saveAndApply() {
                ctx.updateConfig(ctx.config.copy(
                    terminalForeground = hexOrNull(currentFgColor),
                    terminalBackground = hexOrNull(currentBgColor),
                ))
                onTerminalColorsChanged(currentFgColor, currentBgColor)
            }

            fgSwatch.addActionListener {
                val chosen = javax.swing.JColorChooser.showDialog(this@SettingsDialog, "Terminal Foreground", currentFgColor)
                    ?: return@addActionListener
                currentFgColor = chosen
                fgSwatch.background = chosen
                saveAndApply()
            }
            bgSwatch.addActionListener {
                val chosen = javax.swing.JColorChooser.showDialog(this@SettingsDialog, "Terminal Background", currentBgColor)
                    ?: return@addActionListener
                currentBgColor = chosen
                bgSwatch.background = chosen
                saveAndApply()
            }

            val resetColorsBtn = JButton("Reset").apply {
                toolTipText = "Reset to theme defaults"
                addActionListener {
                    currentFgColor = null
                    currentBgColor = null
                    fgSwatch.background = java.awt.Color.GRAY
                    bgSwatch.background = java.awt.Color.GRAY
                    saveAndApply()
                }
            }

            val colorRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(JLabel("Foreground:"))
                add(fgSwatch)
                add(JLabel("Background:"))
                add(bgSwatch)
                add(resetColorsBtn)
            }
            gc.gridy = 11; gc.insets = Insets(4, 4, 4, 4)
            add(colorRow, gc)

            val colorNote = JLabel("<html><i>Click a swatch to pick a color. Takes full effect in new terminal tabs.</i></html>").apply {
                font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            }
            gc.gridy = 12; gc.insets = Insets(0, 4, 4, 4)
            add(colorNote, gc)

            // ── Syntax highlight theme section ────────────────────────────
            gc.gridy = 13; gc.insets = Insets(16, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
            add(JLabel("Syntax Theme").apply { font = font.deriveFont(Font.BOLD) }, gc)

            val syntaxThemes = linkedMapOf(
                "auto"        to "Auto (follows app theme)",
                "monokai"     to "Monokai (dark)",
                "dark"        to "Dark",
                "druid"       to "Druid (dark)",
                "idea"        to "IntelliJ IDEA (light)",
                "eclipse"     to "Eclipse (light)",
                "default"     to "Default (light)",
                "default-alt" to "Default Alt (light)",
                "vs"          to "Visual Studio (light)",
            )
            val syntaxThemeCombo = JComboBox(syntaxThemes.values.toTypedArray())
            val themeKeys = syntaxThemes.keys.toList()
            val currentIdx = themeKeys.indexOf(ctx.config.syntaxTheme).takeIf { it >= 0 } ?: 0
            syntaxThemeCombo.selectedIndex = currentIdx
            syntaxThemeCombo.addActionListener {
                val key = themeKeys[syntaxThemeCombo.selectedIndex]
                ctx.updateConfig(ctx.config.copy(syntaxTheme = key))
                onSyntaxThemeChanged()
            }
            gc.gridy = 14; gc.insets = Insets(4, 4, 4, 4)
            add(syntaxThemeCombo, gc)

            // Spacer
            gc.gridy = 15; gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0; gc.anchor = GridBagConstraints.WEST
            add(JPanel(), gc)
        }
    }

    // ── Embedded output helpers ─────────────────────────────────────────

    private fun buildOutputArea(): JTextArea = JTextArea().apply {
        isEditable = false
        font = Font(monoFont(), Font.PLAIN, 11)
        lineWrap = true
        wrapStyleWord = false
        rows = 8
    }

    /**
     * Runs [command] in a shell and streams stdout+stderr line-by-line into [outputArea].
     * Buttons should be disabled before calling; [onFinished] re-enables them on the EDT.
     */
    private fun runCommandStreaming(
        command: String,
        outputArea: JTextArea,
        env: Map<String, String> = emptyMap(),
        onFinished: () -> Unit,
    ) {
        outputArea.text = ""
        outputArea.append("$ $command\n")

        object : SwingWorker<Int, String>() {
            override fun doInBackground(): Int {
                val argv = if (IS_WINDOWS) listOf("cmd", "/c", command) else listOf("sh", "-c", command)
                val pb = ProcessBuilder(argv).redirectErrorStream(true)
                pb.environment()["PATH"] = System.getenv("PATH") ?: ""
                env.forEach { (k, v) -> pb.environment()[k] = v }
                val proc = pb.start()
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) publish(line)
                }
                return proc.waitFor()
            }

            override fun process(chunks: List<String>) {
                for (line in chunks) {
                    outputArea.append("$line\n")
                    outputArea.caretPosition = outputArea.document.length
                }
            }

            override fun done() {
                val exitCode = try { get() } catch (e: Exception) {
                    outputArea.append("\nError: ${e.cause?.message ?: e.message}\n")
                    -1
                }
                if (exitCode == 0) {
                    outputArea.append("\nCompleted successfully.\n")
                } else if (exitCode > 0) {
                    outputArea.append("\nCommand failed (exit code $exitCode).\n")
                }
                outputArea.caretPosition = outputArea.document.length
                onFinished()
            }
        }.execute()
    }

    /** Detect the GitHub repo (owner/name) from the git remote of the current working directory. */
    private fun detectGitRepo(): String? {
        val result = ProcessExecutor.run(listOf("git", "remote", "get-url", "origin"), timeoutMs = 3_000L)
            ?: return null
        if (result.exitCode != 0) return null
        val url = result.output.trim()
        // SSH: git@github.com:owner/repo.git  or  HTTPS: https://github.com/owner/repo.git
        val sshMatch = Regex("""git@github\.com:(.+?)(?:\.git)?$""").find(url)
        if (sshMatch != null) return sshMatch.groupValues[1]
        val httpsMatch = Regex("""https?://github\.com/(.+?)(?:\.git)?$""").find(url)
        if (httpsMatch != null) return httpsMatch.groupValues[1]
        return null
    }

    private fun monoFont(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        val preferred = when {
            os.contains("win") -> listOf("Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Consolas")
            os.contains("mac") -> listOf("SF Mono", "Menlo", "JetBrains Mono", "Monaco")
            else -> listOf("JetBrains Mono", "Fira Code", "DejaVu Sans Mono", "Liberation Mono")
        }
        return preferred.firstOrNull { it in available } ?: Font.MONOSPACED
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
