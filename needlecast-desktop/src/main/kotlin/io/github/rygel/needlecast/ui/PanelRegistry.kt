package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
import io.github.rygel.needlecast.ui.explorer.ExplorerPanel
import io.github.rygel.needlecast.ui.terminal.TerminalManager

class PanelRegistry(
    val ctx: AppContext,
    private val isWindowFocused: () -> Boolean = { true },
) {
    val statusBar = StatusBar()
    val consolePanel = ConsolePanel(ctx)
    val terminalPanel = TerminalManager(ctx)
    val explorerPanel = ExplorerPanel(ctx)

    val searchPanel =
        SearchPanel { file, line, column ->
            explorerPanel.openFileAt(file, line, column)
        }

    val promptInputPanel =
        PromptInputPanel(
            ctx,
            sendToTerminal = { terminalPanel.sendInput(it) },
        )

    val commandInputPanel =
        PromptInputPanel(
            ctx,
            sendToTerminal = { terminalPanel.sendInput(it) },
            sendButtonLabel = "Run in Terminal",
            itemLabel = "Command",
            isCommand = true,
        )

    val commandPanel =
        CommandPanel(
            ctx,
            consolePanel,
            statusBar,
            showTitle = false,
            isWindowFocused = isWindowFocused,
        )

    val gitLogPanel = GitLogPanel(ctx.gitService, ctx)
    val diffViewerPanel =
        DiffViewerPanel(
            fileOpener = { path -> explorerPanel.openFile(java.io.File(path)) },
            ctx = ctx,
        )
    val logViewerPanel =
        io.github.rygel.needlecast.ui.logviewer
            .LogViewerPanel()
    val renovatePanel = RenovatePanel(ctx)
    val docsPanel = DocsPanel(ctx)
    val skillsPanel = SkillsPanel(ctx)
    val docViewerPanel = DocViewerPanel(ctx)

    lateinit var projectTreePanel: ProjectTreePanel

    val projectTreeDockable by lazy { DockablePanel(projectTreePanel, "project-tree", "Projects", closable = false) }
    val terminalDockable = DockablePanel(terminalPanel, "terminal", "Terminal", closable = false)
    val commandsDockable = DockablePanel(commandPanel, "commands", "Commands")
    val gitLogDockable = DockablePanel(gitLogPanel, "git-log", "Git Log")
    val diffDockable = DockablePanel(diffViewerPanel, "diff-viewer", "Diff")
    val explorerDockable = DockablePanel(explorerPanel, "explorer", "Explorer")
    val editorDockable = DockablePanel(explorerPanel.editorComponent, "editor", "Editor")
    val renovateDockable = DockablePanel(renovatePanel, "renovate", "Renovate")
    val consoleDockable = DockablePanel(consolePanel, "console", "Output")
    val logViewerDockable = DockablePanel(logViewerPanel, "log-viewer", "Log Viewer")
    val searchDockable = DockablePanel(searchPanel, "search", "Search")
    val docsDockable = DockablePanel(docsPanel, "docs", "Docs")
    val promptInputDockable = DockablePanel(promptInputPanel, "prompt-input", "Prompt Input")
    val commandInputDockable = DockablePanel(commandInputPanel, "command-input", "Command Input")
    val docViewerDockable = DockablePanel(docViewerPanel, "doc-viewer", "Doc Viewer")
    val skillsDockable = DockablePanel(skillsPanel, "skills", "Skills")

    val allDockables: List<DockablePanel> get() =
        listOf(
            projectTreeDockable,
            terminalDockable,
            commandsDockable,
            gitLogDockable,
            diffDockable,
            logViewerDockable,
            searchDockable,
            renovateDockable,
            explorerDockable,
            editorDockable,
            consoleDockable,
            promptInputDockable,
            commandInputDockable,
            docsDockable,
            docViewerDockable,
            skillsDockable,
        )
}
