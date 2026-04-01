package io.github.quicklaunch.ui

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import io.github.quicklaunch.AppContext
import io.github.quicklaunch.isOsDark
import io.github.quicklaunch.model.ProjectGroup
import io.github.quicklaunch.ui.explorer.ExplorerPanel
import io.github.quicklaunch.ui.terminal.TerminalManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JSplitPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

class MainWindow(private val ctx: AppContext) : JFrame("QuickLaunch") {

    private val statusBar = StatusBar()
    private val consolePanel = ConsolePanel()
    private val terminalPanel = TerminalManager()
    private val explorerPanel = ExplorerPanel(ctx)
    private val commandPanel = CommandPanel(ctx, consolePanel, statusBar, showTitle = false, isWindowFocused = { isFocused })
    private val gitLogPanel = GitLogPanel()
    private val directoryPanel: DirectoryPanel = DirectoryPanel(
        ctx = ctx,
        compact = true,
        onProjectSelected = { project ->
            commandPanel.loadProject(project)
            gitLogPanel.loadProject(project?.directory?.path)
            if (project != null) {
                explorerPanel.setRootDirectory(File(project.directory.path))
                terminalPanel.showProject(project.directory.path)
            } else {
                terminalPanel.deactivate()
            }
        },
        onActivate = { project ->
            terminalPanel.activateProject(project.directory.path, project.directory.env)
            directoryPanel.setActivePaths(terminalPanel.activePaths())
        },
        onDeactivate = { project ->
            terminalPanel.deactivateProject(project.directory.path)
            directoryPanel.setActivePaths(terminalPanel.activePaths())
        },
    )
    private val groupPanel = GroupPanel(
        ctx = ctx,
        onGroupSelected = { group ->
            directoryPanel.loadGroup(group)
            commandPanel.loadProject(null)
            gitLogPanel.loadProject(null)
            terminalPanel.deactivate()
        },
        onDirectoryDropped = { transfer, targetGroup -> moveDirectory(transfer, targetGroup) },
    )

    private lateinit var mainSplit: JSplitPane
    private lateinit var leftSplit: JSplitPane
    private lateinit var middleSplit: JSplitPane
    private lateinit var rightSplit: JSplitPane
    private lateinit var middleRightSplit: JSplitPane

    init {
        size = Dimension(ctx.config.windowWidth, ctx.config.windowHeight)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        minimumSize = Dimension(1000, 600)

        contentPane = buildLayout()
        jMenuBar = buildMenuBar()

        registerKeyboardShortcuts()
        centerOnScreen()

        // React to LAF changes (e.g. OS dark-mode toggle when theme == "system")
        UIManager.addPropertyChangeListener { evt ->
            if (evt.propertyName == "lookAndFeel") applyTheme(isOsDark())
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                val cfg = ctx.config
                leftSplit.dividerLocation        = cfg.dividerLeft        ?: 130
                middleSplit.dividerLocation      = cfg.dividerMiddle      ?: (height - 260)
                rightSplit.dividerLocation       = cfg.dividerRight       ?: 180
                mainSplit.dividerLocation        = cfg.dividerMain        ?: 200
                middleRightSplit.dividerLocation = cfg.dividerMiddleRight ?: ((width - 200) * 7 / 10)

                val startDark = when (cfg.theme) {
                    "dark"   -> true
                    "system" -> isOsDark()
                    else     -> false
                }
                applyTheme(startDark)

                groupPanel.restoreSelection()
            }

            override fun windowClosing(e: WindowEvent) {
                if (!explorerPanel.checkAllUnsaved()) return
                ctx.updateConfig(ctx.config.copy(
                    windowWidth = width,
                    windowHeight = height,
                    lastSelectedGroupId = groupPanel.selectedGroupId(),
                    dividerLeft        = leftSplit.dividerLocation,
                    dividerMiddle      = middleSplit.dividerLocation,
                    dividerRight       = rightSplit.dividerLocation,
                    dividerMain        = mainSplit.dividerLocation,
                    dividerMiddleRight = middleRightSplit.dividerLocation,
                ))
                terminalPanel.dispose()
                dispose()
            }
        })
    }

    private fun buildLayout(): java.awt.Container {
        // Left column: groups (top) + projects (bottom)
        leftSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, groupPanel, directoryPanel).apply {
            resizeWeight = 0.25
        }

        // Middle column: explorer (top) + terminal (bottom)
        middleSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, explorerPanel, terminalPanel).apply {
            resizeWeight = 0.65
        }

        // Right column: commands+git-log tabs (top) + console (bottom)
        val commandTabs = javax.swing.JTabbedPane().apply {
            addTab("Commands", commandPanel)
            addTab("Git Log", gitLogPanel)
        }
        rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, commandTabs, consolePanel).apply {
            resizeWeight = 0.3
        }

        // Horizontal: middle | right
        middleRightSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, middleSplit, rightSplit).apply {
            resizeWeight = 0.7
        }

        // Horizontal: left | (middle | right)
        mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, middleRightSplit).apply {
            resizeWeight = 0.0
        }

        val panel = java.awt.Panel(BorderLayout())
        panel.add(mainSplit, BorderLayout.CENTER)
        panel.add(statusBar, BorderLayout.SOUTH)

        val contentPane = javax.swing.JPanel(BorderLayout())
        contentPane.add(mainSplit, BorderLayout.CENTER)
        contentPane.add(statusBar, BorderLayout.SOUTH)
        return contentPane
    }

    private fun buildMenuBar(): JMenuBar {
        val settingsItem = JMenuItem("Settings...").apply {
            addActionListener {
                SettingsDialog(this@MainWindow, ctx,
                    sendToTerminal = { cmd -> terminalPanel.sendInput(cmd) },
                    onShortcutsChanged = { reloadShortcuts() },
                ).isVisible = true
            }
        }
        val importItem = JMenuItem("Import Config...").apply {
            addActionListener { importConfig() }
        }
        val exportItem = JMenuItem("Export Config...").apply {
            addActionListener { exportConfig() }
        }
        val exitItem = JMenuItem("Exit").apply {
            addActionListener {
                dispatchEvent(WindowEvent(this@MainWindow, WindowEvent.WINDOW_CLOSING))
            }
        }
        val fileMenu = JMenu("File").apply {
            add(settingsItem)
            addSeparator()
            add(importItem)
            add(exportItem)
            addSeparator()
            add(exitItem)
        }

        val lightItem  = JMenuItem("Light Theme").apply  { addActionListener { setTheme("light") } }
        val darkItem   = JMenuItem("Dark Theme").apply   { addActionListener { setTheme("dark")  } }
        val systemItem = JMenuItem("System (auto)").apply { addActionListener { setTheme("system") } }
        val viewMenu = JMenu("View").apply {
            add(lightItem)
            add(darkItem)
            add(systemItem)
        }

        val aiMenu = buildAiMenu()

        val aboutItem = JMenuItem("About QuickLaunch...").apply {
            addActionListener { showAbout() }
        }
        val helpMenu = JMenu("Help").apply {
            add(aboutItem)
        }

        return JMenuBar().apply {
            add(fileMenu)
            add(viewMenu)
            add(aiMenu)
            add(helpMenu)
        }
    }

    private fun showAbout() {
        val version = try {
            val props = java.util.Properties()
            props.load(javaClass.getResourceAsStream("/version.properties"))
            props.getProperty("app.version", "unknown")
        } catch (_: Exception) { "unknown" }

        JOptionPane.showMessageDialog(
            this,
            "<html><b>QuickLaunch</b> v$version<br><br>" +
            "A Swing-based project launcher for developers.<br><br>" +
            "License: Apache 2.0<br>" +
            "Source: github.com/rygel/quicklaunch</html>",
            "About QuickLaunch",
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    private fun buildAiMenu(): JMenu {
        val menu = JMenu("AI Tools")

        // Detect lazily when the menu is opened — no startup delay
        menu.addMenuListener(object : javax.swing.event.MenuListener {
            override fun menuSelected(e: javax.swing.event.MenuEvent) {
                menu.removeAll()
                val rescanItem = JMenuItem("↻ Rescan").apply {
                    addActionListener { menu.doClick() }  // re-open to refresh
                }
                menu.add(rescanItem)
                menu.addSeparator()

                val results = detectAiClis()
                val (found, missing) = results.partition { it.second }

                if (found.isEmpty()) {
                    menu.add(JMenuItem("No AI CLIs detected").apply { isEnabled = false })
                } else {
                    found.forEach { (cli, _) ->
                        menu.add(JMenuItem("▶  ${cli.name}").apply {
                            toolTipText = cli.description
                            font = font.deriveFont(Font.BOLD)
                            addActionListener { launchCliInTerminal(cli) }
                        })
                    }
                }

                if (missing.isNotEmpty()) {
                    menu.addSeparator()
                    missing.forEach { (cli, _) ->
                        menu.add(JMenuItem("${cli.name}  (not found)").apply {
                            toolTipText = "Install '${cli.command}' to use it here"
                            isEnabled = false
                        })
                    }
                }
            }
            override fun menuDeselected(e: javax.swing.event.MenuEvent) {}
            override fun menuCanceled(e: javax.swing.event.MenuEvent) {}
        })

        return menu
    }

    private fun launchCliInTerminal(cli: AiCli) {
        val cmd = "${cli.command}\n"
        terminalPanel.sendInput(cmd)
        statusBar.setStatus("Launched ${cli.name}")
    }

    private fun importConfig() {
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = "Import Config"
            fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        try {
            val imported = ctx.configStore.import(chooser.selectedFile.toPath())
            ctx.updateConfig(imported)
            JOptionPane.showMessageDialog(
                this, "Config imported. Restart to apply all changes.",
                "Import Successful", JOptionPane.INFORMATION_MESSAGE,
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this, "Failed to import: ${e.message}", "Import Error", JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun exportConfig() {
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = "Export Config"
            fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile
        val target = if (selected.extension == "json") selected else File("${selected.absolutePath}.json")
        try {
            ctx.configStore.export(ctx.config, target.toPath())
            statusBar.setStatus("Config exported to ${target.name}")
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this, "Failed to export: ${e.message}", "Export Error", JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    /** Applies the dark or light LAF-specific colours to sub-panels that manage their own theming. */
    private fun applyTheme(dark: Boolean) {
        explorerPanel.applyTheme(dark)
        terminalPanel.applyTheme(dark)
    }

    /** Switches to the requested theme ("dark", "light", or "system") and persists the choice. */
    private fun setTheme(theme: String) {
        val dark = when (theme) {
            "dark"   -> { FlatDarkLaf.setup();  true  }
            "system" -> { if (isOsDark()) FlatDarkLaf.setup() else FlatLightLaf.setup(); isOsDark() }
            else     -> { FlatLightLaf.setup(); false }
        }
        SwingUtilities.updateComponentTreeUI(this)
        applyTheme(dark)
        ctx.updateConfig(ctx.config.copy(theme = theme))
    }

    private fun moveDirectory(transfer: DirectoryTransfer, targetGroup: ProjectGroup) {
        val sourceGroup = ctx.config.groups.find { it.id == transfer.sourceGroupId } ?: return
        if (sourceGroup.id == targetGroup.id) return

        val directory = transfer.project.directory
        val updatedSource = sourceGroup.copy(
            directories = sourceGroup.directories.filter { it.path != directory.path },
        )
        val updatedTarget = targetGroup.copy(
            directories = targetGroup.directories + directory,
        )
        val updatedGroups = ctx.config.groups.map { g ->
            when (g.id) {
                updatedSource.id -> updatedSource
                updatedTarget.id -> updatedTarget
                else -> g
            }
        }
        ctx.updateConfig(ctx.config.copy(groups = updatedGroups))
        directoryPanel.removeProjectByPath(directory.path)
        groupPanel.refreshGroups(updatedGroups)
        statusBar.setStatus("Moved '${directory.label()}' \u2192 ${targetGroup.name}")
    }

    private fun registerKeyboardShortcuts() {
        val root = rootPane
        val im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val am = root.actionMap
        val overrides = ctx.config.shortcuts

        fun bind(defaultKey: String, actionId: String, block: () -> Unit) {
            val key = overrides[actionId] ?: defaultKey
            im.put(KeyStroke.getKeyStroke(key), actionId)
            am.put(actionId, action(block))
        }

        bind("F5",     "rescan")           { directoryPanel.triggerRescan() }
        bind("ctrl T", "activate-terminal"){ directoryPanel.triggerActivateTerminal() }
        bind("ctrl 1", "focus-projects")   { directoryPanel.requestFocusOnList() }
        bind("ctrl 2", "focus-explorer")   { explorerPanel.requestFocusOnTree() }
        bind("ctrl 3", "focus-terminal")   { terminalPanel.requestFocusOnActive() }
        bind("ctrl P", "project-switcher") { showProjectSwitcher() }
    }

    /** Re-registers all keyboard shortcuts (called after the user saves new bindings). */
    fun reloadShortcuts() = registerKeyboardShortcuts()

    private fun showProjectSwitcher() {
        val dialog = ProjectSwitcherDialog(this, ctx) { groupId, path ->
            switchToProject(groupId, path)
        }
        dialog.isVisible = true
    }

    private fun switchToProject(groupId: String, path: String) {
        if (groupPanel.selectedGroupId() != groupId) {
            groupPanel.selectGroup(groupId)
            // loadGroup is triggered by the GroupPanel selection listener;
            // directoryPanel will apply the pending selection once the scan completes
        }
        directoryPanel.selectByPath(path)
        directoryPanel.requestFocusOnList()
    }

    private fun action(block: () -> Unit) = object : javax.swing.AbstractAction() {
        override fun actionPerformed(e: java.awt.event.ActionEvent) = block()
    }

    private fun centerOnScreen() {
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        setLocation((screen.width - width) / 2, (screen.height - height) / 2)
    }
}
