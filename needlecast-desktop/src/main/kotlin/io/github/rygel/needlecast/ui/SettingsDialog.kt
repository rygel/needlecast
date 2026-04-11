package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.AiCliDefinition
import io.github.rygel.needlecast.model.ExternalEditor
import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.outerstellar.i18n.Language
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
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
import javax.swing.UIManager

class SettingsDialog(
    owner: JFrame,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    private val onShortcutsChanged: () -> Unit = {},
    private val onLayoutChanged: () -> Unit = {},
    private val onTerminalColorsChanged: (fg: java.awt.Color?, bg: java.awt.Color?) -> Unit = { _, _ -> },
    private val onFontSizeChanged: (Int) -> Unit = {},
    private val onUiFontChanged: (family: String?, size: Int?) -> Unit = { _, _ -> },
    private val onEditorFontChanged: (family: String?, size: Int) -> Unit = { _, _ -> },
    private val onTerminalFontChanged: (family: String?) -> Unit = { _ -> },
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

        val panel = JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
        }

        val infoLabel = JLabel(
            "<html>Renovate keeps your dependencies up to date by opening automated PRs.<br>" +
            "Install it globally, then use the <b>Renovate</b> panel (Panels menu) to run it.</html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel, BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val outputArea = buildOutputArea()

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))

        val managers = buildInstallOptions()
        val installButtons = mutableListOf<JButton>()
        managers.forEach { (label, cmd) ->
            val btn = JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    installButtons.forEach { it.isEnabled = false }
                    runCommandStreaming(cmd, outputArea) {
                        installButtons.forEach { it.isEnabled = true }
                        checkRenovate(statusLabel, versionLabel)
                    }
                }
            }
            installButtons.add(btn)
            buttonsPanel.add(btn)
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply {
            addActionListener { checkRenovate(statusLabel, versionLabel) }
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

        panel.add(topSection, BorderLayout.NORTH)
        panel.add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
            preferredSize = Dimension(0, 160)
        }, BorderLayout.CENTER)

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

            // ── Diagnostics section ───────────────────────────────────────
            gc.gridy = 3; gc.insets = Insets(12, 4, 4, 4)
            add(JLabel("Diagnostics").apply { font = font.deriveFont(Font.BOLD) }, gc)

            val clickTraceCb = javax.swing.JCheckBox(
                "Enable project tree click tracing",
                ctx.config.treeClickTraceEnabled,
            )
            gc.gridy = 4; gc.insets = Insets(4, 4, 4, 4)
            add(clickTraceCb, gc)
            clickTraceCb.addActionListener {
                ctx.updateConfig(ctx.config.copy(treeClickTraceEnabled = clickTraceCb.isSelected))
            }

            val edtTraceCb = javax.swing.JCheckBox(
                "Enable EDT stall monitor",
                ctx.config.edtStallTraceEnabled,
            )
            gc.gridy = 5
            add(edtTraceCb, gc)
            edtTraceCb.addActionListener {
                ctx.updateConfig(ctx.config.copy(edtStallTraceEnabled = edtTraceCb.isSelected))
            }

            val diagNote = JLabel("<html><i>Logs go to ~/.needlecast/needlecast.log. Enable only while diagnosing lag.</i></html>").apply {
                font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
                foreground = foreground.darker()
            }
            gc.gridy = 6; gc.insets = Insets(0, 4, 4, 4)
            add(diagNote, gc)

            // ── Fonts section ────────────────────────────────────────────
            gc.gridy = 7; gc.insets = Insets(12, 4, 4, 4)
            add(JLabel("Fonts").apply { font = font.deriveFont(Font.BOLD) }, gc)

            data class FontChoice(val label: String, val value: String?)
            fun fontRenderer() = javax.swing.DefaultListCellRenderer().also { renderer ->
                renderer.foreground = foreground
            }
            fun JComboBox<FontChoice>.installRenderer() {
                setRenderer { list, value, index, isSelected, cellHasFocus ->
                    val text = value?.label ?: ""
                    fontRenderer().getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
                }
            }

            val uiBase = uiBaseFont()
            val allFonts = availableFontFamilies()
            val monoFonts = availableMonospaceFamilies()

            val uiChoices = (listOf(FontChoice("System default", null)) + allFonts.map { FontChoice(it, it) })
            val monoChoices = (listOf(FontChoice("Auto (monospace)", null)) + monoFonts.map { FontChoice(it, it) })

            val uiCombo = JComboBox(uiChoices.toTypedArray()).apply {
                installRenderer()
                selectedItem = uiChoices.firstOrNull { it.value == ctx.config.uiFontFamily } ?: uiChoices.first()
                preferredSize = java.awt.Dimension(220, preferredSize.height)
            }
            val uiSizeSpinner = javax.swing.JSpinner(
                javax.swing.SpinnerNumberModel(ctx.config.uiFontSize ?: uiBase.size, 9, 32, 1)
            ).apply { preferredSize = java.awt.Dimension(70, preferredSize.height) }
            val uiResetBtn = JButton("Reset").apply {
                addActionListener {
                    uiCombo.selectedIndex = 0
                    uiSizeSpinner.value = uiBase.size
                    ctx.updateConfig(ctx.config.copy(uiFontFamily = null, uiFontSize = null))
                    onUiFontChanged(null, null)
                }
            }
            fun saveUiFont() {
                val choice = uiCombo.selectedItem as? FontChoice
                val size = uiSizeSpinner.value as Int
                val sizeValue = if (size == uiBase.size) null else size
                ctx.updateConfig(ctx.config.copy(uiFontFamily = choice?.value, uiFontSize = sizeValue))
                onUiFontChanged(choice?.value, sizeValue)
            }
            uiCombo.addActionListener { saveUiFont() }
            uiSizeSpinner.addChangeListener { saveUiFont() }

            val uiRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(JLabel("UI font:"))
                add(uiCombo)
                add(JLabel("Size:"))
                add(uiSizeSpinner)
                add(uiResetBtn)
            }
            gc.gridy = 8; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL
            add(uiRow, gc)

            val editorCombo = JComboBox(monoChoices.toTypedArray()).apply {
                installRenderer()
                selectedItem = monoChoices.firstOrNull { it.value == ctx.config.editorFontFamily } ?: monoChoices.first()
                preferredSize = java.awt.Dimension(220, preferredSize.height)
            }
            val editorSizeSpinner = javax.swing.JSpinner(
                javax.swing.SpinnerNumberModel(ctx.config.editorFontSize, 6, 72, 1)
            ).apply { preferredSize = java.awt.Dimension(70, preferredSize.height) }
            val editorResetBtn = JButton("Reset").apply {
                addActionListener {
                    editorCombo.selectedIndex = 0
                    editorSizeSpinner.value = 12
                    ctx.updateConfig(ctx.config.copy(editorFontFamily = null, editorFontSize = 12))
                    onEditorFontChanged(null, 12)
                }
            }
            fun saveEditorFont() {
                val choice = editorCombo.selectedItem as? FontChoice
                val size = editorSizeSpinner.value as Int
                ctx.updateConfig(ctx.config.copy(editorFontFamily = choice?.value, editorFontSize = size))
                onEditorFontChanged(choice?.value, size)
            }
            editorCombo.addActionListener { saveEditorFont() }
            editorSizeSpinner.addChangeListener { saveEditorFont() }

            val editorRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(JLabel("Editor font:"))
                add(editorCombo)
                add(JLabel("Size:"))
                add(editorSizeSpinner)
                add(editorResetBtn)
            }
            gc.gridy = 9; gc.insets = Insets(4, 4, 4, 4)
            add(editorRow, gc)

            val terminalCombo = JComboBox(monoChoices.toTypedArray()).apply {
                installRenderer()
                selectedItem = monoChoices.firstOrNull { it.value == ctx.config.terminalFontFamily } ?: monoChoices.first()
                preferredSize = java.awt.Dimension(220, preferredSize.height)
            }
            terminalCombo.addActionListener {
                val choice = terminalCombo.selectedItem as? FontChoice
                ctx.updateConfig(ctx.config.copy(terminalFontFamily = choice?.value))
                onTerminalFontChanged(choice?.value)
            }
            val terminalRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(JLabel("Terminal font:"))
                add(terminalCombo)
            }
            gc.gridy = 10; gc.insets = Insets(4, 4, 4, 4)
            add(terminalRow, gc)

            // ── Terminal section ──────────────────────────────────────────
            gc.gridy = 11; gc.insets = Insets(12, 4, 4, 4)
            add(JLabel("Terminal").apply { font = font.deriveFont(Font.BOLD) }, gc)

            gc.gridy = 12; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.WEST
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

            gc.gridy = 13; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL
            add(JLabel("Default shell (per-project shell overrides this):"), gc)

            // Detect installed shells in background, populate combo when ready
            val manualItem = ShellInfo("Manual entry…", "")
            val osDefaultLabel = when {
                IS_WINDOWS -> "OS default (cmd.exe)"
                IS_MAC     -> "OS default (zsh)"
                else       -> "OS default (bash)"
            }
            val osDefault  = ShellInfo(osDefaultLabel, "")
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

            gc.gridy = 14; gc.fill = GridBagConstraints.HORIZONTAL
            add(shellCombo, gc)
            gc.gridy = 15
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
            gc.gridy = 16; gc.insets = Insets(0, 4, 4, 4)
            add(shellNote, gc)
            gc.gridy = 17; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.EAST
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
            gc.gridy = 18; gc.insets = Insets(16, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
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
            gc.gridy = 19; gc.insets = Insets(4, 4, 4, 4)
            add(colorRow, gc)

            val colorNote = JLabel("<html><i>Click a swatch to pick a color. Takes full effect in new terminal tabs.</i></html>").apply {
                font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            }
            gc.gridy = 20; gc.insets = Insets(0, 4, 4, 4)
            add(colorNote, gc)

            // ── Syntax highlight theme section ────────────────────────────
            gc.gridy = 21; gc.insets = Insets(16, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
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
            gc.gridy = 22; gc.insets = Insets(4, 4, 4, 4)
            add(syntaxThemeCombo, gc)

            // Spacer
            gc.gridy = 23; gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0; gc.anchor = GridBagConstraints.WEST
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

    private fun uiBaseFont(): Font {
        return UIManager.getFont("defaultFont")
            ?: UIManager.getFont("Label.font")
            ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)
    }

    private fun availableFontFamilies(): List<String> =
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList().sorted()

    private fun availableMonospaceFamilies(): List<String> {
        val candidates = availableFontFamilies()
        return candidates.filter { isMonospaced(it) }
    }

    private fun isMonospaced(name: String): Boolean {
        val font = Font(name, Font.PLAIN, 12)
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val fm = g.getFontMetrics(font)
        val w1 = fm.charWidth('i')
        val w2 = fm.charWidth('W')
        val w3 = fm.charWidth('m')
        g.dispose()
        return w1 > 0 && w1 == w2 && w2 == w3
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
