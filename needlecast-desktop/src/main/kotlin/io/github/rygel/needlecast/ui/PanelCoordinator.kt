package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.ui.settings.SettingsCallbacks
import io.github.rygel.needlecast.ui.terminal.ClaudeHookServer
import io.github.rygel.needlecast.ui.terminal.ClaudeUsageService
import java.io.File
import java.nio.charset.Charset
import javax.swing.Timer

internal fun buildCommandsKey(project: DetectedProject): String =
    project.commands.joinToString(separator = "|") { cmd ->
        val argv = cmd.argv.joinToString(separator = "\u0000")
        "${cmd.label}\u0000$argv\u0000${cmd.workingDirectory}"
    }

class PanelCoordinator(
    private val registry: PanelRegistry,
    private val docking: DockingController,
    private val ctx: AppContext,
) {
    private var claudeHookServer: ClaudeHookServer? = null
    private var claudeUsageService: ClaudeUsageService? = null

    private var pendingProjectSelection: DetectedProject? = null
    private val projectSelectionTimer =
        Timer(75) {
            propagateProjectSelection(pendingProjectSelection)
        }.apply { isRepeats = false }

    private var lastSelectedPath: String? = null
    private var lastSelectedCommandsKey: String? = null

    fun wire() {
        val terminalPanel = registry.terminalPanel
        val explorerPanel = registry.explorerPanel

        terminalPanel.onActivateRequested = { dir ->
            val shell = dir.shellExecutable?.takeIf { it.isNotBlank() } ?: ctx.config.defaultShell
            terminalPanel.activateProject(dir.path, dir.env, shell, dir.startupCommand)
            registry.projectTreePanel.setActivePaths(terminalPanel.activePaths())
        }

        terminalPanel.onProjectStatusChanged = { path, status ->
            registry.projectTreePanel.updateProjectStatus(path, status)
        }

        registry.gitLogPanel.onCommitSelected = { result ->
            registry.diffViewerPanel.display(result)
            if (docking.isEnabled() &&
                !io.github.andrewauclair.moderndocking.app.Docking
                    .isDocked(registry.diffDockable)
            ) {
                docking.toggleDiff(true)
            }
        }

        val initFg = ctx.config.terminalForeground?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
        val initBg = ctx.config.terminalBackground?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
        if (initFg != null || initBg != null) terminalPanel.applyTerminalColors(initFg, initBg)
        terminalPanel.applyFontSize(ctx.config.terminalFontSize)
        terminalPanel.applyFontFamily(ctx.config.terminalFontFamily)
        terminalPanel.applyCharset(Charset.forName(ctx.config.terminalEncoding))
        explorerPanel.applyEditorFont(ctx.config.editorFontFamily, ctx.config.editorFontSize)

        terminalPanel.onFontSizeChanged = { size ->
            ctx.updateConfig(ctx.config.copy(terminalFontSize = size))
        }

        terminalPanel.onCharsetChanged = { charset ->
            ctx.updateConfig(ctx.config.copy(terminalEncoding = charset.name()))
        }

        if (ctx.config.claudeHooksEnabled) {
            val server =
                ClaudeHookServer { cwd, status ->
                    terminalPanel.onHookEvent(cwd, status)
                }
            claudeHookServer = server
            server.start()
            Thread({ ClaudeHookServer.installHooks(server.port) }, "claude-hooks-installer")
                .apply {
                    isDaemon = true
                    start()
                }
            terminalPanel.setUseHooksForStatus(true)
        } else {
            Thread({ ClaudeHookServer.uninstallHooks() }, "claude-hooks-cleanup")
                .apply {
                    isDaemon = true
                    start()
                }
        }

        if (ctx.config.claudeQuotaEnabled) {
            startUsageService()
        }
    }

    fun propagateProjectSelection(project: DetectedProject?) {
        val path = project?.directory?.path
        val pathChanged = path != lastSelectedPath
        val commandsKey = project?.let { buildCommandsKey(it) }
        val commandsChanged = commandsKey != lastSelectedCommandsKey

        if (pathChanged) {
            registry.gitLogPanel.loadProject(path)
            registry.logViewerPanel.loadProject(path)
            registry.searchPanel.loadProject(path)
            registry.renovatePanel.loadProject(path)
            registry.docsPanel.loadProject(path)
            registry.skillsPanel.loadProject(project)
            registry.docViewerPanel.loadProject(project)
            path?.let { ctx.gitAutoSync.fetchIfNeeded(it) }
        }

        if (project != null) {
            if (pathChanged) {
                registry.explorerPanel.setRootDirectory(File(project.directory.path))
                registry.terminalPanel.showProject(project.directory.path, project.directory)
            }
        } else if (pathChanged) {
            registry.terminalPanel.deactivate()
        }

        if (pathChanged || commandsChanged) {
            registry.commandPanel.loadProject(project)
        }

        lastSelectedPath = path
        lastSelectedCommandsKey = commandsKey
    }

    fun propagateThemeChange(dark: Boolean) {
        registry.explorerPanel.applyTheme(dark)
        registry.terminalPanel.applyTheme(dark)
        registry.docsPanel.applyTheme(dark)
    }

    fun createSettingsCallbacks(
        reloadShortcuts: () -> Unit,
        applyUiFont: () -> Unit,
    ): SettingsCallbacks =
        SettingsCallbacks(
            onShortcutsChanged = { reloadShortcuts() },
            onLayoutChanged = { docking.resetLayout { registry.statusBar.setStatus(it) } },
            onTerminalColorsChanged = { fg, bg -> registry.terminalPanel.applyTerminalColors(fg, bg) },
            onFontSizeChanged = { size -> registry.terminalPanel.applyFontSize(size) },
            onUiFontChanged = { _, _ -> applyUiFont() },
            onEditorFontChanged = { family, size -> registry.explorerPanel.applyEditorFont(family, size) },
            onTerminalFontChanged = { family -> registry.terminalPanel.applyFontFamily(family) },
            onSyntaxThemeChanged = { registry.explorerPanel.applyTheme(ThemeRegistry.isDark(ctx.config.theme)) },
            onClaudeQuotaToggled = { enabled ->
                if (enabled) startUsageService() else stopUsageService()
            },
        )

    fun triggerProjectSelection(project: DetectedProject?) {
        pendingProjectSelection = project
        projectSelectionTimer.restart()
    }

    fun closeActiveProjectTerminals() {
        registry.terminalPanel.activePaths().toList().forEach { path ->
            registry.terminalPanel.deactivateProject(path)
        }
        registry.projectTreePanel.setActivePaths(registry.terminalPanel.activePaths())
    }

    fun startUsageService() {
        val svc =
            ClaudeUsageService { data ->
                registry.statusBar.updateQuota(data)
            }
        claudeUsageService = svc
        svc.start()
    }

    fun stopUsageService() {
        claudeUsageService?.stop()
        claudeUsageService = null
        registry.statusBar.hideQuota()
    }

    fun dispose() {
        claudeHookServer?.stop()
        claudeUsageService?.stop()
    }

    fun setLastSelectedPath(path: String?) {
        lastSelectedPath = path
    }

    fun getLastSelectedPath(): String? = lastSelectedPath

    fun resetLastSelectedCommandsKey() {
        lastSelectedCommandsKey = null
    }
}
