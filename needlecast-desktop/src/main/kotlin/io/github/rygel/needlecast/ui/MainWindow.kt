package io.github.rygel.needlecast.ui

import io.github.andrewauclair.moderndocking.app.AppState
import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.isOsDark
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.ui.components.BannerNotification
import io.github.rygel.needlecast.ui.components.TourOverlay
import io.github.rygel.needlecast.ui.components.TourStep
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.net.URI
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.plaf.FontUIResource

private const val APPCAST_URL = "https://github.com/rygel/needlecast/releases/latest/download/appcast.xml"

internal fun buildSparkle4jInstance(
    version: String,
    intervalHours: Int,
    parentComponent: Component? = null,
): io.github.rygel.sparkle4j.Sparkle4jInstance {
    val builder =
        io.github.rygel.sparkle4j.Sparkle4j
            .builder()
            .appcastUrl(APPCAST_URL)
            .currentVersion(version)
            .allowUnsignedUpdates()
            .appName("Needlecast")
            .checkIntervalHours(intervalHours)
    if (parentComponent != null) builder.parentComponent(parentComponent)
    return builder.build()
}

class MainWindow(
    private val ctx: AppContext,
) : JFrame(buildTitle()) {
    private val pendingProjectSelection =
        java.util.concurrent.atomic.AtomicReference<io.github.rygel.needlecast.model.DetectedProject?>(
            null,
        )

    private val registry = PanelRegistry(ctx) { isFocused }
    internal val docking = DockingController(registry, ctx)
    private val coordinator = PanelCoordinator(registry, docking, ctx)

    private val projectSelectionTimer =
        javax.swing
            .Timer(75) {
                val selected = pendingProjectSelection.getAndSet(null)
                coordinator.propagateProjectSelection(selected)
                title = buildTitle(selected?.directory?.label())
            }.apply { isRepeats = false }

    private val statusBar = registry.statusBar
    private val terminalPanel = registry.terminalPanel
    private val explorerPanel = registry.explorerPanel
    private val logViewerPanel = registry.logViewerPanel
    private val searchPanel = registry.searchPanel
    private lateinit var projectTreePanel: ProjectTreePanel
    private val projectTreePanelAccessor get() = projectTreePanel

    private val edtTraceForced =
        System.getProperty("needlecast.edt.trace")?.equals("true", ignoreCase = true) == true ||
            (System.getenv("NEEDLECAST_EDT_TRACE")?.equals("true", ignoreCase = true) == true) ||
            (System.getenv("NEEDLECAST_EDT_TRACE") == "1")

    @Volatile private var edtMonitorRunning = false
    private var edtMonitorThread: Thread? = null

    private val baseUiFont: Font =
        UIManager.getFont("defaultFont")
            ?: UIManager.getFont("Label.font")
            ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)

    init {
        projectTreePanel =
            ProjectTreePanel(
                ctx = ctx,
                onProjectSelected = { project ->
                    pendingProjectSelection.set(project)
                    projectSelectionTimer.restart()
                },
                onActivate = { project ->
                    val dir = project.directory
                    val shell = dir.shellExecutable?.takeIf { it.isNotBlank() } ?: ctx.config.defaultShell
                    terminalPanel.activateProject(dir.path, dir.env, shell, dir.startupCommand)
                    projectTreePanel.setActivePaths(terminalPanel.activePaths())
                    explorerPanel.setRootDirectory(File(dir.path))
                },
                onDeactivate = { project ->
                    terminalPanel.deactivateProject(project.directory.path)
                    projectTreePanel.setActivePaths(terminalPanel.activePaths())
                },
                onExternalFilesDropped = { files ->
                    files.forEach { explorerPanel.openFile(it) }
                },
            )
        registry.projectTreePanel = projectTreePanel

        coordinator.wire()

        ctx.addConfigListener { cfg ->
            SwingUtilities.invokeLater { updateDiagnosticSettings(cfg) }
        }

        val iconUrl = MainWindow::class.java.getResource("/icons/needlecast.png")
        if (iconUrl != null) {
            iconImage = javax.imageio.ImageIO.read(iconUrl)
        }

        size = Dimension(ctx.config.windowWidth, ctx.config.windowHeight)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        minimumSize = Dimension(800, 500)

        if (docking.isEnabled()) {
            docking.initialize(this)
            docking.installHoverHighlighter()
            contentPane = docking.buildRootPanel(this)
        } else {
            contentPane = docking.buildSimplePanel()
        }
        val menuBuilder =
            MenuBarBuilder(
                registry,
                coordinator,
                docking,
                ctx,
                this,
                MenuBarBuilder.MenuBarCallbacks(
                    reloadShortcuts = { reloadShortcuts() },
                    applyUiFont = { applyUiFontFromConfig() },
                    checkForUpdatesManual = { checkForUpdatesManual() },
                    importConfig = { importConfig() },
                    exportConfig = { exportConfig() },
                    importWorkspace = { importWorkspace() },
                    exportWorkspace = { exportWorkspace() },
                ),
            )
        jMenuBar = menuBuilder.build()
        applyUiFontFromConfig()

        registerKeyboardShortcuts()
        centerOnScreen()
        detectCwdProject()
        restoreActiveProjects()
        maybeStartTour()

        UIManager.addPropertyChangeListener { evt ->
            if (evt.propertyName == "lookAndFeel") applyTheme(isOsDark())
        }

        addWindowListener(
            object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    if (docking.isEnabled()) {
                        docking.restoreLayout()
                        SwingUtilities.invokeLater { projectTreePanel.invalidateTreeLayout() }
                    }
                    applyTheme(ThemeRegistry.isDark(ctx.config.theme))
                    updateTimer.start()
                    updateDiagnosticSettings(ctx.config)
                }

                override fun windowClosing(e: WindowEvent) {
                    if (!explorerPanel.checkAllUnsaved()) return
                    try {
                        ctx.updateConfig(
                            ctx.config.copy(
                                windowWidth = width,
                                windowHeight = height,
                                activeProjectPaths = terminalPanel.activePaths().toList(),
                            ),
                        )
                        AppState.setAutoPersist(false)
                        AppState.setPaused(true)
                        ctx.disposeAll()
                        updateTimer.stop()
                        logViewerPanel.dispose()
                        terminalPanel.dispose()
                        coordinator.dispose()
                        edtMonitorRunning = false
                        dispose()
                    } finally {
                        System.exit(0)
                    }
                }
            },
        )

        addWindowFocusListener(
            object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent?) {
                    val activePath = coordinator.getLastSelectedPath() ?: return
                    ctx.gitAutoSync.fetchIfNeeded(activePath)
                }
            },
        )
    }

    override fun dispose() {
        docking.dispose()
        super.dispose()
    }

    fun resetLayout() {
        docking.resetLayout { statusBar.setStatus(it) }
    }

    // ── Import / Export ──────────────────────────────────────────────────────

    private fun importConfig() {
        val chooser =
            JFileChooser(File(System.getProperty("user.home"))).apply {
                dialogTitle = "Import Config"
                fileFilter = FileNameExtensionFilter("JSON files (*.json)", "json")
            }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        try {
            val imported = ctx.configStore.import(chooser.selectedFile.toPath())
            ctx.updateConfig(imported)
            JOptionPane.showMessageDialog(
                this,
                "Config imported. Restart to apply all changes.",
                "Import Successful",
                JOptionPane.INFORMATION_MESSAGE,
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to import: ${e.message}", "Import Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun exportConfig() {
        val chooser =
            JFileChooser(File(System.getProperty("user.home"))).apply {
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

    private fun importWorkspace() {
        val chooser =
            JFileChooser(File(System.getProperty("user.home"))).apply {
                dialogTitle = "Import Workspace"
                fileFilter =
                    FileNameExtensionFilter(
                        "Needlecast workspace files (*.$WORKSPACE_FILE_EXTENSION)",
                        WORKSPACE_FILE_EXTENSION,
                    )
            }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        try {
            val imported = ctx.configStore.importWorkspace(chooser.selectedFile.toPath(), ctx.config)
            if (!confirmWorkspaceImportTerminalClosure()) return
            applyImportedWorkspace(imported)
            JOptionPane.showMessageDialog(
                this,
                "Workspace imported.",
                "Import Successful",
                JOptionPane.INFORMATION_MESSAGE,
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to import workspace: ${e.message}", "Import Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun exportWorkspace() {
        val chooser =
            JFileChooser(File(System.getProperty("user.home"))).apply {
                dialogTitle = "Export Workspace"
                fileFilter =
                    FileNameExtensionFilter(
                        "Needlecast workspace files (*.$WORKSPACE_FILE_EXTENSION)",
                        WORKSPACE_FILE_EXTENSION,
                    )
            }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile
        val target =
            if (selected.name.endsWith(".$WORKSPACE_FILE_EXTENSION", ignoreCase = true)) {
                selected
            } else {
                File("${selected.absolutePath}.$WORKSPACE_FILE_EXTENSION")
            }
        try {
            ctx.configStore.exportWorkspace(ctx.config.copy(lastSelectedProjectPath = coordinator.getLastSelectedPath()), target.toPath())
            statusBar.setStatus("Workspace exported to ${target.name}")
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to export workspace: ${e.message}", "Export Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun applyImportedWorkspace(imported: io.github.rygel.needlecast.model.AppConfig) {
        coordinator.closeActiveProjectTerminals()
        coordinator.propagateProjectSelection(null)
        coordinator.setLastSelectedPath(imported.lastSelectedProjectPath)
        coordinator.resetLastSelectedCommandsKey()
        ctx.updateConfig(imported)
        projectTreePanel.reloadFromConfig()
        projectTreePanel.setActivePaths(terminalPanel.activePaths())
        SwingUtilities.invokeLater { projectTreePanel.invalidateTreeLayout() }
    }

    private fun confirmWorkspaceImportTerminalClosure(): Boolean {
        val activePaths = registry.terminalPanel.activePaths().sorted()
        if (activePaths.isEmpty()) return true
        val lines = activePaths.joinToString("<br>") { "&nbsp;&nbsp;&bull; ${escapeHtml(it)}" }
        val message = "<html>Importing a workspace will close these active terminals:<br><br>$lines<br><br>Continue?</html>"
        return JOptionPane.showConfirmDialog(
            this,
            message,
            "Close Active Terminals?",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        ) == JOptionPane.YES_OPTION
    }

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun applyTheme(dark: Boolean) {
        applyUiFontFromConfig()
        coordinator.propagateThemeChange(dark)
    }

    private fun applyUiFontFromConfig() {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        val family =
            ctx.config.uiFontFamily?.takeIf { it.isNotBlank() && it in available }
                ?: baseUiFont.family
        val size = ctx.config.uiFontSize?.takeIf { it in 8..72 } ?: baseUiFont.size
        val font = FontUIResource(Font(family, Font.PLAIN, size))
        UIManager.put("defaultFont", font)
        SwingUtilities.updateComponentTreeUI(this)
        repaint()
    }

    // ── Keyboard shortcuts ────────────────────────────────────────────────────

    private fun registerKeyboardShortcuts() {
        val root = rootPane
        val im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val am = root.actionMap
        val overrides = ctx.config.shortcuts

        fun bind(
            defaultKey: String,
            actionId: String,
            block: () -> Unit,
        ) {
            val key = overrides[actionId] ?: defaultKey
            im.put(KeyStroke.getKeyStroke(key), actionId)
            am.put(actionId, action(block))
        }

        bind("F5", "rescan") { projectTreePanel.triggerRescan() }
        bind("ctrl T", "activate-terminal") { projectTreePanel.triggerActivateTerminal() }
        bind("ctrl 1", "focus-projects") { projectTreePanel.requestFocusOnTree() }
        bind("ctrl 2", "focus-explorer") { explorerPanel.requestFocusOnTree() }
        bind("ctrl 3", "focus-terminal") { terminalPanel.requestFocusOnActive() }
        bind("ctrl P", "project-switcher") { showProjectSwitcher() }
        bind("ctrl shift F", "find-in-files") { showSearchPanel() }
    }

    fun reloadShortcuts() = registerKeyboardShortcuts()

    private fun showProjectSwitcher() {
        val dialog =
            ProjectSwitcherDialog(this, ctx) { _, path ->
                projectTreePanel.selectByPath(path)
                projectTreePanel.requestFocusOnTree()
            }
        dialog.isVisible = true
    }

    private fun showSearchPanel() {
        if (!docking.isEnabled()) return
        if (!docking.isDocked("search")) docking.toggleSearch(true)
        docking.selectTab("search")
        searchPanel.requestFocusOnSearch()
    }

    private fun action(block: () -> Unit) =
        object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) = block()
        }

    private fun centerOnScreen() {
        val screen =
            java.awt.Toolkit
                .getDefaultToolkit()
                .screenSize
        setLocation((screen.width - width) / 2, (screen.height - height) / 2)
    }

    // ── First-run tour ─────────────────────────────────────────────────────────

    private val tourSteps =
        listOf(
            TourStep("Project Tree", "Your projects appear here. Double-click to open a terminal and file explorer.", "project-tree"),
            TourStep("Project Switcher", "Quickly switch between projects with Ctrl+P.", "project-tree"),
            TourStep("Explorer", "Browse and edit files. Syntax highlighting works for 20+ languages.", "explorer"),
            TourStep("Terminal", "Each project gets its own terminal. Agent status is shown with a pulsing dot.", "terminal"),
            TourStep("Git", "View commit history, diffs, and sync with remote. Fetches happen automatically.", "git-log"),
            TourStep("Commands", "Build commands are auto-detected. Click to run.", "commands"),
        )

    private val tourPanelMap: Map<String, java.awt.Component> by lazy {
        mapOf(
            "project-tree" to registry.projectTreeDockable,
            "terminal" to registry.terminalDockable,
            "explorer" to registry.explorerDockable,
            "git-log" to registry.gitLogDockable,
            "commands" to registry.commandsDockable,
        )
    }

    private fun findDockablePanel(id: String): java.awt.Component? = tourPanelMap[id]

    private fun maybeStartTour() {
        if (ctx.config.tourCompleted) return
        javax.swing
            .Timer(1500) { e ->
                (e.source as? javax.swing.Timer)?.stop()
                val overlay =
                    TourOverlay(
                        rootPane = rootPane,
                        steps = tourSteps,
                        findPanel = { id -> findDockablePanel(id) },
                        onComplete = { ctx.updateConfig(ctx.config.copy(tourCompleted = true)) },
                        onSkip = { ctx.updateConfig(ctx.config.copy(tourCompleted = true)) },
                    )
                overlay.start()
            }.apply {
                isRepeats = false
                start()
            }
    }

    // ── CWD auto-detect ──────────────────────────────────────────────────────

    private fun detectCwdProject() {
        val cwd = System.getProperty("user.dir")
        if (File(cwd, ".git").isDirectory) {
            val alreadyConfigured =
                ctx.config.projectTree
                    .filterIsInstance<ProjectTreeEntry.Project>()
                    .any { it.directory.path == cwd }
            if (!alreadyConfigured) {
                val dir = ProjectDirectory(cwd)
                val entry = ProjectTreeEntry.Project(directory = dir)
                val newTree = ctx.config.projectTree + entry
                ctx.updateConfig(ctx.config.copy(projectTree = newTree))
                projectTreePanel.reloadFromConfig()
            }
            showCwdBanner(cwd)
        }
    }

    private fun showCwdBanner(cwd: String) {
        if ("cwd-detect" in ctx.config.dismissedHints) return
        val banner =
            BannerNotification(
                text = "Detected project at $cwd",
                actionLabel = "Select it",
                onAction = {
                    val dir = ProjectDirectory(cwd)
                    val detected =
                        ctx.scanner.scan(dir)
                            ?: io.github.rygel.needlecast.model
                                .DetectedProject(dir, emptySet(), emptyList())
                    pendingProjectSelection.set(detected)
                    projectSelectionTimer.restart()
                },
                onDismiss = {
                    ctx.updateConfig(ctx.config.copy(dismissedHints = ctx.config.dismissedHints + "cwd-detect"))
                },
            )
        contentPane.add(banner, BorderLayout.NORTH)
        revalidate()
        repaint()
    }

    private fun restoreActiveProjects() {
        val paths = ctx.config.activeProjectPaths
        if (paths.isEmpty()) return
        val dirMap =
            ctx.config.projectTree
                .filterIsInstance<ProjectTreeEntry.Project>()
                .associate { it.directory.path to it.directory }
        for (path in paths) {
            val dir = dirMap[path] ?: continue
            if (!File(path).isDirectory) continue
            val shell = dir.shellExecutable?.takeIf { it.isNotBlank() } ?: ctx.config.defaultShell
            terminalPanel.activateProject(path, dir.env, shell, dir.startupCommand)
        }
        if (terminalPanel.activePaths().isNotEmpty()) {
            projectTreePanel.setActivePaths(terminalPanel.activePaths())
        }
    }

    // ── Diagnostics ──────────────────────────────────────────────────────────

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
        edtMonitorThread =
            Thread({
                var lastReportAt = 0L
                while (edtMonitorRunning) {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    val scheduledAt = System.nanoTime()
                    SwingUtilities.invokeLater { latch.countDown() }
                    val ok =
                        try {
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
                                val stack =
                                    Thread
                                        .getAllStackTraces()[edt]
                                        ?.joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                                        ?: "(stack unavailable)"
                                uiLogger.warn("EDT stall detected: {} ms\n{}", delayMs, stack)
                            } else {
                                uiLogger.warn("EDT stall detected: {} ms (EDT thread not found)", delayMs)
                            }
                        }
                    }
                    try {
                        Thread.sleep(periodMs)
                    } catch (_: InterruptedException) {
                    }
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

    // ── Update checker ──────────────────────────────────────────────────────

    private fun buildSparkle4j(intervalHours: Int = 24): io.github.rygel.sparkle4j.Sparkle4jInstance? {
        val version =
            currentVersion() ?: run {
                updateLogger.warn("Cannot determine app version — update check skipped")
                return null
            }
        updateLogger.info("Building sparkle4j instance: version={}, interval={}h", version, intervalHours)
        return try {
            buildSparkle4jInstance(
                version = version,
                intervalHours = intervalHours,
                parentComponent = this@MainWindow,
            )
        } catch (e: Exception) {
            updateLogger.error("Failed to configure update checker", e)
            null
        }
    }

    private var updateCheckFailures = 0
    private val updateCheckFailureThreshold = 3

    private val updateTimer =
        javax.swing.Timer(15 * 60 * 1000) { checkForUpdates() }.apply {
            isRepeats = true
            initialDelay = 30_000
        }

    private fun checkForUpdates() {
        Thread {
            try {
                updateLogger.info("Periodic update check")
                val item = buildSparkle4j(0)?.checkNow()?.orElse(null)
                updateCheckFailures = 0
                SwingUtilities.invokeLater { statusBar.hideUpdateCheckWarning() }
                if (item != null) {
                    updateLogger.info("Update available: {}", item.version())
                    SwingUtilities.invokeLater {
                        statusBar.showUpdateAvailable(item.version()) { openReleasesPage() }
                    }
                }
            } catch (e: Exception) {
                logUpdateCheckFailure("Periodic update check", e)
                updateCheckFailures++
                if (updateCheckFailures >= updateCheckFailureThreshold) {
                    updateLogger.warn("Update checks have failed {} consecutive times", updateCheckFailures)
                    SwingUtilities.invokeLater { statusBar.showUpdateCheckWarning() }
                }
            }
        }.also {
            it.isDaemon = true
            it.name = "update-check"
        }.start()
    }

    private fun openReleasesPage() {
        try {
            java.awt.Desktop
                .getDesktop()
                .browse(java.net.URI("https://github.com/rygel/needlecast/releases/latest"))
        } catch (e: Exception) {
            updateLogger.warn("Could not open releases page", e)
        }
    }

    private fun checkForUpdatesManual() {
        val instance = buildSparkle4j(0)
        if (instance == null) {
            JOptionPane.showMessageDialog(
                this,
                "Update checking is not available (version unknown).",
                "Check for Updates",
                JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        statusBar.setStatus("Checking for updates\u2026")
        Thread({
            try {
                updateLogger.info("Manual update check")
                val item = instance.checkNow().orElse(null)
                SwingUtilities.invokeLater {
                    if (item == null) {
                        updateLogger.info("No update found — already on latest version")
                        statusBar.setStatus("You are running the latest version.")
                        JOptionPane.showMessageDialog(
                            this@MainWindow,
                            "You are running the latest version of Needlecast.",
                            "Check for Updates",
                            JOptionPane.INFORMATION_MESSAGE,
                        )
                    } else {
                        updateLogger.info("Update found: {}", item.version())
                        statusBar.showUpdateAvailable(item.version()) { openReleasesPage() }
                        openReleasesPage()
                    }
                }
            } catch (e: Exception) {
                logUpdateCheckFailure("Manual update check", e)
                val details = UpdateCheckErrors.details(e)
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this@MainWindow,
                        details.userMessage,
                        "Check for Updates",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }, "update-check-manual").apply {
            isDaemon = true
            start()
        }
    }

    private fun logUpdateCheckFailure(
        context: String,
        error: Throwable,
    ) {
        val details = UpdateCheckErrors.details(error)
        val root = details.root
        val category = details.category
        val appcastHost = runCatching { URI(APPCAST_URL).host }.getOrNull() ?: "unknown"
        updateLogger.warn(
            "{} failed: category={}, appcastHost={}, exceptionType={}, message={}, rootType={}, rootMessage={}",
            context,
            category,
            appcastHost,
            error::class.java.name,
            UpdateCheckErrors.sanitizeLogField(error.message),
            root::class.java.name,
            UpdateCheckErrors.sanitizeLogField(root.message),
        )
        if (category.startsWith("tls")) {
            updateLogger.warn(
                "{} TLS hint: verify corporate proxy/SSL interception trust chain and JVM trust store",
                context,
            )
        }
        updateLogger.debug("{} stacktrace", context, error)
    }

    companion object {
        private const val WORKSPACE_FILE_EXTENSION = "needlecast-workspace"

        internal fun currentVersion(): String? =
            try {
                val props = java.util.Properties()
                props.load(MainWindow::class.java.getResourceAsStream("/version.properties"))
                props.getProperty("app.version")?.takeIf { it.isNotEmpty() && !it.contains("\${") }
            } catch (_: Exception) {
                null
            }

        private fun buildTitle(projectName: String? = null): String {
            val version = currentVersion() ?: ""
            val base = if (version.isNotEmpty()) "Needlecast $version" else "Needlecast"
            return if (!projectName.isNullOrBlank()) "$base [$projectName]" else base
        }
    }
}
