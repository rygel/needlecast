package io.github.rygel.needlecast.ui

import io.github.andrewauclair.moderndocking.DockableTabPreference
import io.github.andrewauclair.moderndocking.DockingRegion
import io.github.andrewauclair.moderndocking.app.AppState
import io.github.andrewauclair.moderndocking.app.Docking
import io.github.andrewauclair.moderndocking.app.RootDockingPanel
import io.github.andrewauclair.moderndocking.ext.ui.DockingUI
import io.github.andrewauclair.moderndocking.settings.Settings
import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.isOsDark
import io.github.rygel.needlecast.ui.explorer.ExplorerPanel
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import io.github.rygel.needlecast.ui.terminal.ClaudeHookServer
import io.github.rygel.needlecast.ui.terminal.TerminalManager
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.plaf.FontUIResource

class MainWindow(private val ctx: AppContext) : JFrame(buildTitle()) {

    private val dockingEnabled = System.getProperty("needlecast.skipDocking")
        ?.equals("true", ignoreCase = true) != true

    private val statusBar      = StatusBar()
    private val consolePanel   = ConsolePanel()
    private val claudeHookServer: ClaudeHookServer? =
        if (ctx.config.claudeHooksEnabled) ClaudeHookServer { cwd, status -> terminalPanel.onHookEvent(cwd, status) }
        else null
    private val terminalPanel  = TerminalManager()
    private val explorerPanel  = ExplorerPanel(ctx)
    private val promptInputPanel  = PromptInputPanel(ctx, sendToTerminal = { terminalPanel.sendInput(it) })
    private val commandInputPanel = PromptInputPanel(
        ctx,
        sendToTerminal  = { terminalPanel.sendInput(it) },
        sendButtonLabel = "Run in Terminal",
        itemLabel       = "Command",
        loadLibrary     = { it.commandLibrary },
        updateLibrary   = { cfg, lib -> cfg.copy(commandLibrary = lib) },
    )
    private val commandPanel  = CommandPanel(ctx, consolePanel, statusBar, showTitle = false, isWindowFocused = { isFocused })
    private val gitLogPanel   = GitLogPanel(ctx.gitService)
    private val logViewerPanel = io.github.rygel.needlecast.ui.logviewer.LogViewerPanel()
    private val searchPanel   = SearchPanel { file, line, column -> explorerPanel.openFileAt(file, line, column) }
    private val renovatePanel = RenovatePanel()
    private val docsPanel     = DocsPanel()

    private var pendingProjectSelection: io.github.rygel.needlecast.model.DetectedProject? = null
    private val projectSelectionTimer = javax.swing.Timer(75) {
        applyProjectSelection(pendingProjectSelection)
    }.apply { isRepeats = false }
    private var lastSelectedPath: String? = null
    private var lastSelectedCommandsKey: String? = null
    private val edtTraceForced = System.getProperty("needlecast.edt.trace")?.equals("true", ignoreCase = true) == true ||
        (System.getenv("NEEDLECAST_EDT_TRACE")?.equals("true", ignoreCase = true) == true) ||
        (System.getenv("NEEDLECAST_EDT_TRACE") == "1")
    @Volatile private var edtMonitorRunning = false
    private var edtMonitorThread: Thread? = null

    private val projectTreePanel: ProjectTreePanel = ProjectTreePanel(
        ctx = ctx,
        onProjectSelected = { project ->
            pendingProjectSelection = project
            projectSelectionTimer.restart()
        },
        onActivate = { project ->
            // Per-project shell takes priority; fall back to the global default shell.
            val shell = project.directory.shellExecutable
                ?.takeIf { it.isNotBlank() }
                ?: ctx.config.defaultShell
            terminalPanel.activateProject(
                project.directory.path,
                project.directory.env,
                shell,
                project.directory.startupCommand,
            )
            projectTreePanel.setActivePaths(terminalPanel.activePaths())
            explorerPanel.setRootDirectory(File(project.directory.path))
        },
        onDeactivate = { project ->
            terminalPanel.deactivateProject(project.directory.path)
            projectTreePanel.setActivePaths(terminalPanel.activePaths())
        },
        onExternalFilesDropped = { files ->
            files.forEach { explorerPanel.openFile(it) }
        },
    )

    // ModernDocking dockable wrappers — one per logical panel
    private val projectTreeDockable = DockablePanel(projectTreePanel,              "project-tree", "Projects", closable = false)
    private val terminalDockable    = DockablePanel(terminalPanel,                 "terminal",     "Terminal", closable = false)
    private val commandsDockable    = DockablePanel(commandPanel,                  "commands",     "Commands")
    private val gitLogDockable      = DockablePanel(gitLogPanel,                   "git-log",      "Git Log")
    private val explorerDockable    = DockablePanel(explorerPanel,                 "explorer",     "Explorer")
    private val editorDockable      = DockablePanel(explorerPanel.editorComponent, "editor",       "Editor")
    private val renovateDockable     = DockablePanel(renovatePanel,                 "renovate",     "Renovate")
    private val consoleDockable      = DockablePanel(consolePanel,                  "console",      "Output")
    private val logViewerDockable    = DockablePanel(logViewerPanel,               "log-viewer",   "Log Viewer")
    private val searchDockable       = DockablePanel(searchPanel,                   "search",       "Search")
    private val docsDockable         = DockablePanel(docsPanel,                     "docs",         "Docs")
    private val promptInputDockable   = DockablePanel(promptInputPanel,               "prompt-input",   "Prompt Input")
    private val commandInputDockable  = DockablePanel(commandInputPanel,              "command-input",  "Command Input")

    private val dockingLayoutFile: File = Path.of(
        System.getProperty("user.home"), ".needlecast", "docking-layout.xml"
    ).toFile()
    private val baseUiFont: Font = UIManager.getFont("defaultFont")
        ?: UIManager.getFont("Label.font")
        ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)

    init {
        terminalPanel.onActivateRequested = { dir ->
            val shell = dir.shellExecutable?.takeIf { it.isNotBlank() } ?: ctx.config.defaultShell
            terminalPanel.activateProject(dir.path, dir.env, shell, dir.startupCommand)
            projectTreePanel.setActivePaths(terminalPanel.activePaths())
        }

        terminalPanel.onProjectStatusChanged = { path, status ->
            projectTreePanel.updateProjectStatus(path, status)
        }

        // Restore persisted terminal colors and font size
        val initFg = ctx.config.terminalForeground?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
        val initBg = ctx.config.terminalBackground?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
        if (initFg != null || initBg != null) terminalPanel.applyTerminalColors(initFg, initBg)
        terminalPanel.applyFontSize(ctx.config.terminalFontSize)
        terminalPanel.applyFontFamily(ctx.config.terminalFontFamily)
        explorerPanel.applyEditorFont(ctx.config.editorFontFamily, ctx.config.editorFontSize)
        terminalPanel.onFontSizeChanged = { size ->
            ctx.updateConfig(ctx.config.copy(terminalFontSize = size))
        }

        ctx.addConfigListener { cfg ->
            SwingUtilities.invokeLater { updateDiagnosticSettings(cfg) }
        }

        if (claudeHookServer != null) {
            claudeHookServer.start()
            Thread({ ClaudeHookServer.installHooks(claudeHookServer.port) }, "claude-hooks-installer")
                .apply { isDaemon = true; start() }
            terminalPanel.setUseHooksForStatus(true)
        } else {
            // Clean up hooks from previous runs when hooks are disabled
            Thread({ ClaudeHookServer.uninstallHooks() }, "claude-hooks-cleanup")
                .apply { isDaemon = true; start() }
        }

        // Application icon (taskbar, title bar, Alt+Tab)
        val iconUrl = MainWindow::class.java.getResource("/icons/needlecast.png")
        if (iconUrl != null) {
            iconImage = javax.imageio.ImageIO.read(iconUrl)
        }

        size = Dimension(ctx.config.windowWidth, ctx.config.windowHeight)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        minimumSize = Dimension(800, 500)

        if (dockingEnabled) {
            // Initialize ModernDocking before the content pane is built (RootDockingPanel registers itself)
            Docking.initialize(this)
            DockingUI.initialize()
            Settings.setActiveHighlighterEnabled(ctx.config.dockingActiveHighlight)
            // Suppress the default border/gap FlatLaf draws around tabbed-pane content areas
            UIManager.getDefaults()["TabbedPane.contentBorderInsets"] = Insets(0, 0, 0, 0)
            UIManager.getDefaults()["TabbedPane.tabsOverlapBorder"]   = true

            // Register all dockables before any dock() calls
            Docking.registerDockable(projectTreeDockable)
            Docking.registerDockable(terminalDockable)
            Docking.registerDockable(commandsDockable)
            Docking.registerDockable(gitLogDockable)
            Docking.registerDockable(logViewerDockable)
            Docking.registerDockable(searchDockable)
            Docking.registerDockable(explorerDockable)
            Docking.registerDockable(editorDockable)
            Docking.registerDockable(consoleDockable)
            Docking.registerDockable(renovateDockable)
            Docking.registerDockable(promptInputDockable)
            Docking.registerDockable(commandInputDockable)
            Docking.registerDockable(docsDockable)
            installPanelHoverHighlighter()

            contentPane = buildLayout()
        } else {
            contentPane = buildSimpleLayout()
        }
        jMenuBar = buildMenuBar()
        applyUiFontFromConfig()

        registerKeyboardShortcuts()
        centerOnScreen()

        UIManager.addPropertyChangeListener { evt ->
            if (evt.propertyName == "lookAndFeel") applyTheme(isOsDark())
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent) {
                if (dockingEnabled) {
                    applyDockingLayout()
                    // After the window is laid out, force the project tree to
                    // recalculate cell widths (tree.width is 0 during initial render)
                    SwingUtilities.invokeLater { projectTreePanel.invalidateTreeLayout() }
                }
                applyTheme(ThemeRegistry.isDark(ctx.config.theme))
                updateTimer.start()
                updateDiagnosticSettings(ctx.config)
            }

            override fun windowClosing(e: WindowEvent) {
                if (!explorerPanel.checkAllUnsaved()) return
                try {
                    ctx.updateConfig(ctx.config.copy(
                        windowWidth  = width,
                        windowHeight = height,
                    ))
                    // Stop ModernDocking's auto-persist timer before disposal.
                    // AppStatePersister fires componentResized during dispose(), which starts
                    // a 500ms Swing timer; if it fires against a partially-disposed frame it
                    // throws. Pausing also stops any already-running timer from executing.
                    AppState.setAutoPersist(false)
                    AppState.setPaused(true)
                    ctx.disposeAll()
                    updateTimer.stop()
                    logViewerPanel.dispose()
                    terminalPanel.dispose()
                    claudeHookServer?.stop()
                    edtMonitorRunning = false
                    dispose()
                } finally {
                    System.exit(0)
                }
            }
        })
    }

    override fun dispose() {
        if (dockingEnabled) {
            try {
                allDockables.forEach { dockable ->
                    if (Docking.isDockableRegistered(dockable.dockableId)) {
                        Docking.deregisterDockable(dockable)
                    }
                }
                Docking.deregisterDockingPanel(this)
                Docking.uninitialize()
            } catch (_: Exception) {
                // Defensive: avoid disposal failures when the docking registry is already cleared.
            }
        }
        super.dispose()
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private fun buildLayout(): java.awt.Container {
        // RootDockingPanel does not auto-register; we must register it for Docking API lookups.
        val rootPanel = RootDockingPanel(this)
        if (!Docking.getRootPanels().containsKey(this)) {
            Docking.registerDockingPanel(rootPanel, this)
        }

        val content = JPanel(BorderLayout())
        content.add(rootPanel, BorderLayout.CENTER)
        content.add(statusBar, BorderLayout.SOUTH)
        return content
    }

    private fun buildSimpleLayout(): java.awt.Container {
        val content = JPanel(BorderLayout())
        content.add(statusBar, BorderLayout.SOUTH)
        return content
    }

    private fun applyProjectSelection(project: io.github.rygel.needlecast.model.DetectedProject?) {
        val path = project?.directory?.path
        val pathChanged = path != lastSelectedPath
        val commandsKey = project?.let { buildCommandsKey(it) }
        val commandsChanged = commandsKey != lastSelectedCommandsKey

        if (pathChanged) {
            gitLogPanel.loadProject(path)
            logViewerPanel.loadProject(path)
            searchPanel.loadProject(path)
            renovatePanel.loadProject(path)
            docsPanel.loadProject(path)
        }

        if (project != null) {
            if (pathChanged) {
                explorerPanel.setRootDirectory(File(project.directory.path))
                terminalPanel.showProject(project.directory.path, project.directory)
            }
        } else if (pathChanged) {
            terminalPanel.deactivate()
        }

        if (pathChanged || commandsChanged) {
            commandPanel.loadProject(project)
        }

        lastSelectedPath = path
        lastSelectedCommandsKey = commandsKey
    }

    private fun buildCommandsKey(project: io.github.rygel.needlecast.model.DetectedProject): String =
        project.commands.joinToString(separator = "|") { cmd ->
            val argv = cmd.argv.joinToString(separator = "\u0000")
            "${cmd.label}\u0000$argv\u0000${cmd.workingDirectory}"
        }

    /**
     * Called in windowOpened — restores the saved docking layout from disk, or
     * falls back to the built-in default arrangement on first run or when the
     * saved layout is stale (e.g. a new panel was added since the file was written).
     * Auto-persist is enabled afterwards so every user change is saved immediately.
     */
    private fun applyDockingLayout() {
        AppState.setPersistFile(dockingLayoutFile)
        applyTabPreference()   // must run before restore so tab orientation is correct when panels appear
        val restored = try { AppState.restore() } catch (_: Exception) { false }

        // If any required panel is missing from the restored layout, reset everything.
        // This happens when a new dockable is introduced and the old XML doesn't reference it.
        val requiredPanels = listOf(terminalDockable, editorDockable, commandsDockable, projectTreeDockable, promptInputDockable, commandInputDockable)
        val allPresent = requiredPanels.all { Docking.isDocked(it) }

        if (!restored || !allPresent) {
            listOf(projectTreeDockable, terminalDockable, commandsDockable,
                   gitLogDockable, logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable, promptInputDockable, commandInputDockable)
                .forEach { if (Docking.isDocked(it)) Docking.undock(it) }
            dockingLayoutFile.delete()
            setupDefaultDockingLayout()
        }

        AppState.setAutoPersist(true)
    }

    /**
     * Default panel arrangement — used on first run or when the saved layout is missing/corrupt.
     *
     * Terminal is always the first panel docked to the window root (the stable anchor).
     * All other panels are docked relative to already-docked panels.
     *
     * ┌──────────────────┬──────────────────────┬────────────────┐
     * │ Projects/Explorer│  Terminal | Editor   │Commands/GitLog │
     * │   (tabbed)        │   (tabbed together)  ├────────────────┤
     * │                  │                      │    Console     │
     * └──────────────────┴──────────────────────┴────────────────┘
     */
    private fun applyTabPreference() {
        Settings.setDefaultTabPreference(
            if (ctx.config.tabsOnTop) DockableTabPreference.TOP_ALWAYS
            else DockableTabPreference.NONE
        )
    }

    private fun setupDefaultDockingLayout() {
        applyTabPreference()
        // 1. Terminal docked to window root — the central, dominant panel
        Docking.dock(terminalDockable,    this,                DockingRegion.CENTER)
        // 2. Project tree to the top-left (always visible)
        Docking.dock(projectTreeDockable, terminalDockable,    DockingRegion.WEST,   0.20)
        // 3. File explorer below the project tree in the left column
        Docking.dock(explorerDockable,    projectTreeDockable, DockingRegion.SOUTH,  0.50)
        // 4. Commands panel to the right of the terminal
        Docking.dock(commandsDockable,    terminalDockable,    DockingRegion.EAST,   0.24)
        // 5. Git Log tabbed alongside Commands
        Docking.dock(gitLogDockable,      commandsDockable,    DockingRegion.CENTER)
        // 5b. Log Viewer tabbed alongside Git Log
        Docking.dock(logViewerDockable,   gitLogDockable,      DockingRegion.CENTER)
        // 5c. Search tabbed alongside Log Viewer
        Docking.dock(searchDockable,      logViewerDockable,   DockingRegion.CENTER)
        // 5d. Docs tabbed alongside Search
        Docking.dock(docsDockable,        searchDockable,      DockingRegion.CENTER)
        // 6. Editor tabbed with the terminal in the centre column
        Docking.dock(editorDockable,      terminalDockable,    DockingRegion.CENTER)
        // 7. Console below Commands
        if (ctx.config.showConsole) {
            Docking.dock(consoleDockable, commandsDockable,    DockingRegion.SOUTH,  0.65)
        }
        // 8. Prompt input below the terminal/editor column
        Docking.dock(promptInputDockable,  terminalDockable,   DockingRegion.SOUTH,  0.85)
        // 9. Command input tabbed with prompt input
        Docking.dock(commandInputDockable, promptInputDockable, DockingRegion.CENTER)

        SwingUtilities.invokeLater { selectPrimaryTabs() }
    }

    /** Undock every panel, delete the saved layout file, re-apply the built-in default. */
    fun resetLayout() {
        AppState.setAutoPersist(false)
        listOf(projectTreeDockable, terminalDockable, commandsDockable,
               gitLogDockable, logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable, promptInputDockable, docsDockable)
            .forEach { if (Docking.isDocked(it)) Docking.undock(it) }
        dockingLayoutFile.delete()
        setupDefaultDockingLayout()
        AppState.setAutoPersist(true)
        statusBar.setStatus("Layout reset to default")
    }

    private fun selectPrimaryTabs() {
        selectDockableTab(projectTreeDockable)
        selectDockableTab(terminalDockable)
        selectDockableTab(commandsDockable)
        selectDockableTab(promptInputDockable)
    }

    private fun selectDockableTab(dockable: DockablePanel) {
        val tabbed = SwingUtilities.getAncestorOfClass(javax.swing.JTabbedPane::class.java, dockable) as? javax.swing.JTabbedPane
            ?: return
        for (i in 0 until tabbed.tabCount) {
            val comp = tabbed.getComponentAt(i)
            if (SwingUtilities.isDescendingFrom(dockable, comp)) {
                tabbed.selectedIndex = i
                return
            }
        }
    }

    // ── View toggles ─────────────────────────────────────────────────────────

    private fun toggleConsole(show: Boolean) {
        if (show && !Docking.isDocked(consoleDockable)) {
            val anchor = when {
                Docking.isDocked(commandsDockable) -> commandsDockable
                Docking.isDocked(explorerDockable) -> explorerDockable
                else                               -> terminalDockable
            }
            Docking.dock(consoleDockable, anchor, DockingRegion.SOUTH, 0.65)
        } else if (!show && Docking.isDocked(consoleDockable)) {
            Docking.undock(consoleDockable)
        }
        ctx.updateConfig(ctx.config.copy(showConsole = show))
    }

    private fun toggleExplorer(show: Boolean) {
        if (show && !Docking.isDocked(explorerDockable)) {
            if (Docking.isDocked(terminalDockable))
                Docking.dock(explorerDockable, terminalDockable, DockingRegion.EAST, 0.35)
            else
                Docking.dock(explorerDockable, this, DockingRegion.EAST, 0.35)
        } else if (!show && Docking.isDocked(explorerDockable)) {
            Docking.undock(explorerDockable)
        }
        ctx.updateConfig(ctx.config.copy(showExplorer = show))
    }

    private fun dockTo(
        dockable: DockablePanel,
        anchor: DockablePanel?,
        region: DockingRegion,
        proportion: Double? = null,
    ) {
        if (Docking.isDocked(dockable)) return
        if (anchor != null && Docking.isDocked(anchor)) {
            if (proportion != null) Docking.dock(dockable, anchor, region, proportion)
            else Docking.dock(dockable, anchor, region)
        } else {
            Docking.dock(dockable, this, region)
        }
    }

    private fun toggleCommands(show: Boolean) {
        if (show && !Docking.isDocked(commandsDockable)) {
            dockTo(commandsDockable, terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(commandsDockable)) {
            Docking.undock(commandsDockable)
        }
    }

    private fun toggleGitLog(show: Boolean) {
        if (show && !Docking.isDocked(gitLogDockable)) {
            if (Docking.isDocked(commandsDockable)) Docking.dock(gitLogDockable, commandsDockable, DockingRegion.CENTER)
            else dockTo(gitLogDockable, terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(gitLogDockable)) {
            Docking.undock(gitLogDockable)
        }
    }

    private fun toggleSearch(show: Boolean) {
        if (show && !Docking.isDocked(searchDockable)) {
            if (Docking.isDocked(commandsDockable)) Docking.dock(searchDockable, commandsDockable, DockingRegion.CENTER)
            else dockTo(searchDockable, terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(searchDockable)) {
            Docking.undock(searchDockable)
        }
    }

    private fun toggleEditor(show: Boolean) {
        if (show && !Docking.isDocked(editorDockable)) {
            dockTo(editorDockable, terminalDockable, DockingRegion.CENTER)
        } else if (!show && Docking.isDocked(editorDockable)) {
            Docking.undock(editorDockable)
        }
    }

    private fun togglePromptInput(show: Boolean) {
        if (show && !Docking.isDocked(promptInputDockable)) {
            dockTo(promptInputDockable, terminalDockable, DockingRegion.SOUTH, 0.78)
        } else if (!show && Docking.isDocked(promptInputDockable)) {
            Docking.undock(promptInputDockable)
        }
    }

    private fun toggleCommandInput(show: Boolean) {
        if (show && !Docking.isDocked(commandInputDockable)) {
            val anchor = if (Docking.isDocked(promptInputDockable)) promptInputDockable else terminalDockable
            dockTo(commandInputDockable, anchor, DockingRegion.SOUTH, 0.78)
        } else if (!show && Docking.isDocked(commandInputDockable)) {
            Docking.undock(commandInputDockable)
        }
    }

    private fun toggleRenovate(show: Boolean) {
        if (show && !Docking.isDocked(renovateDockable)) {
            if (Docking.isDocked(commandsDockable)) Docking.dock(renovateDockable, commandsDockable, DockingRegion.CENTER)
            else dockTo(renovateDockable, terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(renovateDockable)) {
            Docking.undock(renovateDockable)
        }
    }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    private fun buildMenuBar(): JMenuBar {
        val i18n = ctx.i18n
        val settingsItem = JMenuItem(i18n.translate("menu.file.settings")).apply {
            addActionListener {
                SettingsDialog(
                    owner = this@MainWindow,
                    ctx   = ctx,
                    sendToTerminal = { cmd -> terminalPanel.sendInput(cmd) },
                    callbacks = io.github.rygel.needlecast.ui.settings.SettingsCallbacks(
                        onShortcutsChanged      = { reloadShortcuts() },
                        onLayoutChanged         = { resetLayout() },
                        onTerminalColorsChanged = { fg, bg -> terminalPanel.applyTerminalColors(fg, bg) },
                        onFontSizeChanged       = { size -> terminalPanel.applyFontSize(size) },
                        onUiFontChanged         = { _, _ -> applyUiFontFromConfig() },
                        onEditorFontChanged     = { family, size -> explorerPanel.applyEditorFont(family, size) },
                        onTerminalFontChanged   = { family -> terminalPanel.applyFontFamily(family) },
                        onSyntaxThemeChanged    = { explorerPanel.applyTheme(ThemeRegistry.isDark(ctx.config.theme)) },
                    ),
                ).isVisible = true
            }
        }
        val importItem = JMenuItem(i18n.translate("menu.file.import")).apply {
            addActionListener { importConfig() }
        }
        val exportItem = JMenuItem(i18n.translate("menu.file.export")).apply {
            addActionListener { exportConfig() }
        }
        val exitItem = JMenuItem(i18n.translate("menu.file.exit")).apply {
            addActionListener { dispatchEvent(WindowEvent(this@MainWindow, WindowEvent.WINDOW_CLOSING)) }
        }
        val fileMenu = JMenu(i18n.translate("menu.file")).apply {
            add(settingsItem); addSeparator()
            add(importItem); add(exportItem); addSeparator()
            add(exitItem)
        }

        val viewMenu = buildViewMenu(i18n.translate("menu.view"))
        val windowsMenu = buildWindowsMenu()
        val aiMenu   = buildAiMenu()

        val checkUpdateItem = JMenuItem("Check for Updates...").apply {
            addActionListener { checkForUpdatesManual() }
        }
        val aboutItem = JMenuItem(i18n.translate("menu.help.about")).apply {
            addActionListener { showAbout() }
        }
        val helpMenu = JMenu(i18n.translate("menu.help")).apply {
            add(checkUpdateItem)
            addSeparator()
            add(aboutItem)
        }

        return JMenuBar().apply {
            add(fileMenu); add(viewMenu); add(windowsMenu); add(aiMenu); add(helpMenu)
        }
    }

    private fun showAbout() {
        val version = currentVersion() ?: "dev"
        val repoUrl = "https://github.com/rygel/needlecast"

        val icon = javaClass.getResource("/icons/needlecast.png")?.let {
            javax.swing.ImageIcon(javax.imageio.ImageIO.read(it).getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH))
        }

        val linkLabel = javax.swing.JLabel("<html><a href=''>$repoUrl</a></html>").apply {
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    try { java.awt.Desktop.getDesktop().browse(java.net.URI(repoUrl)) } catch (_: Exception) {}
                }
            })
        }

        val content = JPanel(java.awt.GridBagLayout()).apply {
            val gbc = java.awt.GridBagConstraints().apply {
                gridx = 0; gridy = 0; insets = java.awt.Insets(0, 0, 12, 0)
                anchor = java.awt.GridBagConstraints.CENTER
            }
            add(javax.swing.JLabel("Needlecast $version", javax.swing.SwingConstants.CENTER).apply {
                font = font.deriveFont(java.awt.Font.BOLD, 16f)
            }, gbc)
            gbc.gridy++; gbc.insets = java.awt.Insets(0, 0, 4, 0)
            add(javax.swing.JLabel("A project launcher for developers"), gbc)
            gbc.gridy++; gbc.insets = java.awt.Insets(0, 0, 12, 0)
            add(javax.swing.JLabel("by Alexander Brandt"), gbc)
            gbc.gridy++; gbc.insets = java.awt.Insets(0, 0, 4, 0)
            add(linkLabel, gbc)
            gbc.gridy++; gbc.insets = java.awt.Insets(8, 0, 0, 0)
            add(javax.swing.JLabel("<html><center>MIT License<br>Java ${System.getProperty("java.version")}</center></html>",
                javax.swing.SwingConstants.CENTER).apply {
                foreground = java.awt.Color.GRAY
            }, gbc)
        }

        JOptionPane.showMessageDialog(this, content, "About Needlecast",
            JOptionPane.PLAIN_MESSAGE, icon)
    }

    // Both fields are only read/written on the EDT (background thread posts via invokeLater)
    private var cliCache: List<Pair<AiCli, Boolean>> = emptyList()
    private var cliCacheReady = false

    private fun buildAiMenu(): JMenu {
        val menu = JMenu("AI Tools")

        val promptLibraryItem = JMenuItem("Prompt Library...").apply {
            addActionListener {
                PromptLibraryDialog(owner = this@MainWindow, ctx = ctx,
                    sendToTerminal = { text -> terminalPanel.sendInput(text) },
                ).isVisible = true
            }
        }
        val commandLibraryItem = JMenuItem("Command Library...").apply {
            addActionListener {
                PromptLibraryDialog(
                    owner           = this@MainWindow,
                    ctx             = ctx,
                    sendToTerminal  = { cmd -> terminalPanel.sendInput(cmd) },
                    title           = "Command Library",
                    sendButtonLabel = "Run in Terminal",
                    loadLibrary     = { ctx.config.commandLibrary },
                    saveLibrary     = { ctx.updateConfig(ctx.config.copy(commandLibrary = it)) },
                ).isVisible = true
            }
        }

        refreshCliCache()

        menu.addMenuListener(object : javax.swing.event.MenuListener {
            override fun menuSelected(e: javax.swing.event.MenuEvent) {
                menu.removeAll()
                menu.add(promptLibraryItem)
                menu.add(commandLibraryItem)
                menu.addSeparator()

                menu.add(JMenuItem("↻ Rescan").apply {
                    addActionListener { cliCacheReady = false; refreshCliCache(); menu.doClick() }
                })
                menu.addSeparator()

                if (!cliCacheReady) {
                    menu.add(JMenuItem("Detecting AI tools\u2026").apply { isEnabled = false })
                    return
                }

                val (found, missing) = cliCache.partition { it.second }
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

    private fun refreshCliCache() {
        Thread({
            val cfg = ctx.config
            // Detect built-in CLIs, then append user-defined custom CLIs
            val builtIn = detectAiClis()
            val custom = cfg.customAiClis.map { d ->
                AiCli(d.name, d.command, d.description) to
                    io.github.rygel.needlecast.process.ProcessExecutor.isOnPath(d.command)
            }
            val all = (builtIn + custom).filter { (cli, _) ->
                cfg.aiCliEnabled[cli.command] != false   // absent key → enabled by default
            }
            SwingUtilities.invokeLater {
                cliCache = all
                cliCacheReady = true
                terminalPanel.availableCliTools = all.filter { it.second }.map { it.first }
            }
        }, "cli-detector").apply { isDaemon = true; start() }
    }

    private fun launchCliInTerminal(cli: AiCli) {
        terminalPanel.sendInput("${cli.command}\n")
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
            JOptionPane.showMessageDialog(this, "Config imported. Restart to apply all changes.",
                "Import Successful", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to import: ${e.message}", "Import Error", JOptionPane.ERROR_MESSAGE)
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
            JOptionPane.showMessageDialog(this, "Failed to export: ${e.message}", "Export Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun applyTheme(dark: Boolean) {
        applyUiFontFromConfig()
        explorerPanel.applyTheme(dark)
        terminalPanel.applyTheme(dark)
    }

    private fun setTheme(themeId: String) {
        val dark = ThemeRegistry.apply(themeId)
        applyTheme(dark)
        ctx.updateConfig(ctx.config.copy(theme = themeId))
        ctx.reloadTheme()
    }

    private fun applyUiFontFromConfig() {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        val family = ctx.config.uiFontFamily?.takeIf { it.isNotBlank() && it in available }
            ?: baseUiFont.family
        val size = ctx.config.uiFontSize?.takeIf { it in 8..72 } ?: baseUiFont.size
        val font = FontUIResource(Font(family, Font.PLAIN, size))
        UIManager.put("defaultFont", font)
        SwingUtilities.updateComponentTreeUI(this)
        repaint()
    }

    private fun buildViewMenu(title: String): JMenu {
        val themeItems = mutableListOf<JCheckBoxMenuItem>()
        fun themeItem(id: String, name: String) = JCheckBoxMenuItem(name, id == ctx.config.theme).apply {
            addActionListener {
                setTheme(id)
                // Update all checkmarks
                themeItems.forEach { it.isSelected = false }
                isSelected = true
            }
            themeItems.add(this)
        }
        fun groupSubmenu(label: String, baseId: String, baseName: String, group: String): JMenu {
            return JMenu(label).apply {
                add(themeItem(baseId, baseName))
                addSeparator()
                ThemeRegistry.themes.entries
                    .filter { it.value.group == group }
                    .forEach { (id, entry) -> add(themeItem(id, entry.displayName)) }
            }
        }
        fun groupSubmenu(label: String, group: String): JMenu {
            return JMenu(label).apply {
                ThemeRegistry.themes.entries
                    .filter { it.value.group == group }
                    .forEach { (id, entry) -> add(themeItem(id, entry.displayName)) }
            }
        }

        // Panel visibility toggles
        val showConsoleCb = JCheckBoxMenuItem("Show Console", ctx.config.showConsole).apply {
            addActionListener { toggleConsole(isSelected) }
        }
        val showExplorerCb = JCheckBoxMenuItem("Show Explorer Tab", ctx.config.showExplorer).apply {
            addActionListener { toggleExplorer(isSelected) }
        }
        val resetLayoutItem = JMenuItem("Reset Layout to Default").apply {
            addActionListener { resetLayout() }
        }

        return JMenu(title).apply {
            add(themeItem("system", ctx.i18n.translate("menu.view.systemTheme")))
            addSeparator()
            add(groupSubmenu("Dark Themes",  "dark",  ctx.i18n.translate("menu.view.darkTheme"),  ThemeRegistry.GROUP_DARK))
            add(groupSubmenu("Light Themes", "light", ctx.i18n.translate("menu.view.lightTheme"), ThemeRegistry.GROUP_LIGHT))
            addSeparator()
            add(showConsoleCb)
            add(showExplorerCb)
            addSeparator()
            add(JCheckBoxMenuItem("Highlight panel on hover  [alpha]", ctx.config.panelHoverHighlight).apply {
                toolTipText = "Draws a colored border around the panel under the mouse cursor. Experimental."
                addActionListener {
                    ctx.updateConfig(ctx.config.copy(panelHoverHighlight = isSelected))
                    if (!isSelected) clearPanelHighlight()
                }
            })
            addSeparator()
            add(resetLayoutItem)
        }
    }

    private fun buildWindowsMenu(): JMenu {
        val commandsCb = JCheckBoxMenuItem("Commands").apply {
            addActionListener { toggleCommands(isSelected) }
        }
        val gitLogCb = JCheckBoxMenuItem("Git Log").apply {
            addActionListener { toggleGitLog(isSelected) }
        }
        val searchCb = JCheckBoxMenuItem("Search").apply {
            addActionListener { toggleSearch(isSelected) }
        }
        val explorerCb = JCheckBoxMenuItem("Explorer").apply {
            addActionListener { toggleExplorer(isSelected) }
        }
        val editorCb = JCheckBoxMenuItem("Editor").apply {
            addActionListener { toggleEditor(isSelected) }
        }
        val consoleCb = JCheckBoxMenuItem("Output").apply {
            addActionListener { toggleConsole(isSelected) }
        }
        val promptInputCb = JCheckBoxMenuItem("Prompt Input").apply {
            addActionListener { togglePromptInput(isSelected) }
        }
        val commandInputCb = JCheckBoxMenuItem("Command Input").apply {
            addActionListener { toggleCommandInput(isSelected) }
        }
        val renovateCb = JCheckBoxMenuItem("Renovate").apply {
            addActionListener { toggleRenovate(isSelected) }
        }

        fun syncState() {
            commandsCb.isSelected = Docking.isDocked(commandsDockable)
            gitLogCb.isSelected = Docking.isDocked(gitLogDockable)
            searchCb.isSelected = Docking.isDocked(searchDockable)
            explorerCb.isSelected = Docking.isDocked(explorerDockable)
            editorCb.isSelected = Docking.isDocked(editorDockable)
            consoleCb.isSelected = Docking.isDocked(consoleDockable)
            promptInputCb.isSelected = Docking.isDocked(promptInputDockable)
            commandInputCb.isSelected = Docking.isDocked(commandInputDockable)
            renovateCb.isSelected = Docking.isDocked(renovateDockable)
        }

        return JMenu("Panels").apply {
            addMenuListener(object : javax.swing.event.MenuListener {
                override fun menuSelected(e: javax.swing.event.MenuEvent) = syncState()
                override fun menuDeselected(e: javax.swing.event.MenuEvent) {}
                override fun menuCanceled(e: javax.swing.event.MenuEvent) {}
            })
            add(commandsCb)
            add(gitLogCb)
            add(searchCb)
            add(renovateCb)
            addSeparator()
            add(explorerCb)
            add(editorCb)
            addSeparator()
            add(consoleCb)
            add(promptInputCb)
            add(commandInputCb)
        }
    }

    // ── Keyboard shortcuts ────────────────────────────────────────────────────

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

        bind("F5",     "rescan")             { projectTreePanel.triggerRescan() }
        bind("ctrl T", "activate-terminal")  { projectTreePanel.triggerActivateTerminal() }
        bind("ctrl 1", "focus-projects")     { projectTreePanel.requestFocusOnTree() }
        bind("ctrl 2", "focus-explorer")     { explorerPanel.requestFocusOnTree() }
        bind("ctrl 3", "focus-terminal")     { terminalPanel.requestFocusOnActive() }
        bind("ctrl P", "project-switcher")   { showProjectSwitcher() }
        bind("ctrl shift F", "find-in-files") { showSearchPanel() }
    }

    fun reloadShortcuts() = registerKeyboardShortcuts()

    private fun showProjectSwitcher() {
        val dialog = ProjectSwitcherDialog(this, ctx) { _, path ->
            projectTreePanel.selectByPath(path)
            projectTreePanel.requestFocusOnTree()
        }
        dialog.isVisible = true
    }

    private fun showSearchPanel() {
        if (!dockingEnabled) return
        if (!Docking.isDocked(searchDockable)) toggleSearch(true)
        selectDockableTab(searchDockable)
        searchPanel.requestFocusOnSearch()
    }

    private fun action(block: () -> Unit) = object : javax.swing.AbstractAction() {
        override fun actionPerformed(e: java.awt.event.ActionEvent) = block()
    }

    private fun centerOnScreen() {
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        setLocation((screen.width - width) / 2, (screen.height - height) / 2)
    }

    // ── Panel hover highlight ────────────────────────────────────────────────

    private val allDockables get() = listOf(
        projectTreeDockable, terminalDockable, commandsDockable, gitLogDockable,
        logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable,
        promptInputDockable, commandInputDockable,
    )
    private var highlightedDockable: DockablePanel? = null

    private val panelHoverListener = AWTEventListener { event ->
        if (!ctx.config.panelHoverHighlight) return@AWTEventListener
        if (event !is MouseEvent) return@AWTEventListener
        if (event.id != MouseEvent.MOUSE_MOVED && event.id != MouseEvent.MOUSE_ENTERED) return@AWTEventListener
        val source = event.source as? Component ?: return@AWTEventListener
        val hovered = SwingUtilities.getAncestorOfClass(DockablePanel::class.java, source) as? DockablePanel
        if (hovered !== highlightedDockable) {
            highlightedDockable?.setHoverHighlight(false)
            hovered?.setHoverHighlight(true)
            highlightedDockable = hovered
        }
    }

    private fun installPanelHoverHighlighter() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            panelHoverListener,
            AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK,
        )
    }

    private fun clearPanelHighlight() {
        highlightedDockable?.setHoverHighlight(false)
        highlightedDockable = null
    }

    private val updateLogger = org.slf4j.LoggerFactory.getLogger("needlecast.update")
    private val uiLogger = org.slf4j.LoggerFactory.getLogger("needlecast.ui")

    private fun updateDiagnosticSettings(cfg: io.github.rygel.needlecast.model.AppConfig) {
        val shouldRun = edtTraceForced || cfg.edtStallTraceEnabled
        if (shouldRun && !edtMonitorRunning) {
            startEdtStallMonitor()
        } else if (!shouldRun && edtMonitorRunning && !edtTraceForced) {
            stopEdtStallMonitor()
        }
    }

    private fun startEdtStallMonitor() {
        if (edtMonitorRunning) return
        edtMonitorRunning = true
        val periodMs = 50L
        val thresholdMs = 200L
        val throttleMs = 2_000L
        edtMonitorThread = Thread({
            var lastReportAt = 0L
            while (edtMonitorRunning) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val scheduledAt = System.nanoTime()
                SwingUtilities.invokeLater { latch.countDown() }
                val ok = try {
                    latch.await(thresholdMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    true
                }
                if (!ok) {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastReportAt >= throttleMs) {
                        lastReportAt = nowMs
                        val delayMs = (System.nanoTime() - scheduledAt) / 1_000_000
                        val edt = Thread.getAllStackTraces().keys.firstOrNull { it.name.startsWith("AWT-EventQueue") }
                        if (edt != null) {
                            val stack = Thread.getAllStackTraces()[edt]
                                ?.joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                                ?: "(stack unavailable)"
                            uiLogger.warn("EDT stall detected: {} ms\n{}", delayMs, stack)
                        } else {
                            uiLogger.warn("EDT stall detected: {} ms (EDT thread not found)", delayMs)
                        }
                    }
                }
                try { Thread.sleep(periodMs) } catch (_: InterruptedException) {}
            }
        }, "edt-stall-monitor").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopEdtStallMonitor() {
        edtMonitorRunning = false
        edtMonitorThread?.interrupt()
        edtMonitorThread = null
    }

    private fun buildSparkle4j(intervalHours: Int = 24): io.github.rygel.sparkle4j.Sparkle4jInstance? {
        val version = currentVersion() ?: run {
            updateLogger.warn("Cannot determine app version — update check skipped")
            return null
        }
        updateLogger.info("Building sparkle4j instance: version={}, interval={}h", version, intervalHours)
        return try {
            io.github.rygel.sparkle4j.Sparkle4j.builder()
                .appcastUrl("https://github.com/rygel/needlecast/releases/latest/download/appcast.xml")
                .currentVersion(version)
                .appName("Needlecast")
                .parentComponent(this@MainWindow)
                .checkIntervalHours(intervalHours)
                .build()
        } catch (e: Exception) {
            updateLogger.error("Failed to configure update checker", e)
            null
        }
    }

    private val updateTimer = javax.swing.Timer(15 * 60 * 1000) { checkForUpdates() }.apply {
        isRepeats = true
        initialDelay = 30_000 // first check 30s after launch
    }

    private fun checkForUpdates() {
        Thread {
            try {
                updateLogger.info("Periodic update check")
                val item = buildSparkle4j(0)?.checkNow()?.orElse(null)
                if (item != null) {
                    updateLogger.info("Update available: {}", item.version())
                    SwingUtilities.invokeLater {
                        statusBar.showUpdateAvailable(item.version()) { openReleasesPage() }
                    }
                }
            } catch (e: Exception) {
                updateLogger.warn("Update check failed", e)
            }
        }.also { it.isDaemon = true; it.name = "update-check" }.start()
    }

    private fun openReleasesPage() {
        try {
            java.awt.Desktop.getDesktop()
                .browse(java.net.URI("https://github.com/rygel/needlecast/releases/latest"))
        } catch (e: Exception) {
            updateLogger.warn("Could not open releases page", e)
        }
    }

    private fun checkForUpdatesManual() {
        try {
            // interval=0 bypasses the "already checked recently" cache
            val instance = buildSparkle4j(0)
            if (instance == null) {
                JOptionPane.showMessageDialog(this,
                    "Update checking is not available (version unknown).",
                    "Check for Updates", JOptionPane.WARNING_MESSAGE)
                return
            }
            updateLogger.info("Manual update check")
            val item = instance.checkNow().orElse(null)
            if (item == null) {
                updateLogger.info("No update found — already on latest version")
                JOptionPane.showMessageDialog(this,
                    "You are running the latest version of Needlecast.",
                    "Check for Updates", JOptionPane.INFORMATION_MESSAGE)
            } else {
                updateLogger.info("Update found: {}", item.version())
                statusBar.showUpdateAvailable(item.version()) { openReleasesPage() }
                openReleasesPage()
            }
        } catch (e: Exception) {
            updateLogger.error("Manual update check failed", e)
            JOptionPane.showMessageDialog(this,
                "Could not check for updates: ${e.message}",
                "Check for Updates", JOptionPane.ERROR_MESSAGE)
        }
    }

    companion object {
        private fun currentVersion(): String? = try {
            val props = java.util.Properties()
            props.load(MainWindow::class.java.getResourceAsStream("/version.properties"))
            props.getProperty("app.version")?.takeIf { it.isNotEmpty() && !it.contains("\${") }
        } catch (_: Exception) { null }

        private fun buildTitle(): String {
            val version = currentVersion() ?: ""
            return if (version.isNotEmpty()) "Needlecast $version" else "Needlecast"
        }
    }
}
