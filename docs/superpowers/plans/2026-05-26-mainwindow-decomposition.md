# MainWindow Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose the 1,566-line `MainWindow` god class into four focused modules: `PanelRegistry`, `DockingController`, `PanelCoordinator`, and `MenuBarBuilder`.

**Architecture:** Extract four classes behind clean seams. `PanelRegistry` owns panel construction and lifecycle. `DockingController` owns all ModernDocking operations. `PanelCoordinator` owns inter-panel event wiring and project selection fan-out. `MenuBarBuilder` owns menu construction. MainWindow shrinks to ~300 lines of lifecycle glue (window sizing, startup, shutdown, update checker, EDT monitor).

**Tech Stack:** Kotlin 2.2, Swing (JFrame), ModernDocking, AssertJ Swing (UI tests)

**Key constraint:** `MainWindow(ctx: AppContext)` constructor signature must be preserved — two UI test files (`MainWindowUiTest`, `DockingLayoutUiTest`) construct MainWindow directly.

---

## File Structure

### New files to create:

| File | Responsibility | ~Lines |
|------|---------------|--------|
| `ui/PanelRegistry.kt` | Construct all 17 panels in dependency order; wrap in DockablePanels; provide typed accessors | ~130 |
| `ui/DockingController.kt` | ModernDocking init, register, default layout, toggle, reset, import/export | ~300 |
| `ui/PanelCoordinator.kt` | Project selection fan-out, inter-panel callbacks, SettingsCallbacks factory, theme propagation | ~200 |
| `ui/MenuBarBuilder.kt` | Build File, View, Panels, AI Tools, Help menus | ~250 |

### Existing files to modify:

| File | Change |
|------|--------|
| `ui/MainWindow.kt` | Replace inline code with delegation to registry/coordinator/docking/menuBuilder. Shrinks from ~1,566 to ~350 lines. |

All new files are in package `io.github.rygel.needlecast.ui`.

---

### Task 1: Create PanelRegistry

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PanelRegistry.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

PanelRegistry constructs all panels in dependency order. Panels that need lambdas from other panels get them via Kotlin's property initialization order (earlier properties are available to later ones).

`ProjectTreePanel` is constructed with empty callback defaults — the coordinator wires them later.

- [ ] **Step 1: Create PanelRegistry.kt**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
import io.github.rygel.needlecast.ui.explorer.ExplorerPanel
import io.github.rygel.needlecast.ui.logviewer.LogViewerPanel
import io.github.rygel.needlecast.ui.terminal.TerminalManager
import java.io.File

class PanelRegistry(
    val ctx: AppContext,
    private val isWindowFocused: () -> Boolean = { true },
) {

    val statusBar = StatusBar()
    val consolePanel = ConsolePanel(ctx)
    val terminalPanel = TerminalManager(ctx)
    val explorerPanel = ExplorerPanel(ctx)

    val searchPanel = SearchPanel { file, line, column ->
        explorerPanel.openFileAt(file, line, column)
    }

    val diffViewerPanel = DiffViewerPanel(
        fileOpener = { path -> explorerPanel.openFile(File(path)) },
        ctx = ctx,
    )

    val promptInputPanel = PromptInputPanel(
        ctx,
        sendToTerminal = { terminalPanel.sendInput(it) },
    )

    val commandInputPanel = PromptInputPanel(
        ctx,
        sendToTerminal = { terminalPanel.sendInput(it) },
        sendButtonLabel = "Run in Terminal",
        itemLabel = "Command",
        isCommand = true,
    )

    val commandPanel = CommandPanel(
        ctx,
        consolePanel,
        statusBar,
        showTitle = false,
        isWindowFocused = isWindowFocused,
    )

    val gitLogPanel = GitLogPanel(ctx.gitService, ctx)
    val logViewerPanel = LogViewerPanel()
    val renovatePanel = RenovatePanel(ctx)
    val docsPanel = DocsPanel(ctx)
    val skillsPanel = SkillsPanel(ctx)
    val docViewerPanel = DocViewerPanel(ctx)

    val projectTreePanel = ProjectTreePanel(ctx)

    val projectTreeDockable = DockablePanel(projectTreePanel, "project-tree", "Projects", closable = false)
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

    val allDockables: List<DockablePanel> get() = listOf(
        projectTreeDockable, terminalDockable, commandsDockable, gitLogDockable,
        logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable,
        consoleDockable, promptInputDockable, commandInputDockable, docsDockable,
        docViewerDockable, skillsDockable, diffDockable,
    )
}
```

- [ ] **Step 2: Update MainWindow to use PanelRegistry**

In `MainWindow.kt`:

1. Add a `private val registry = PanelRegistry(ctx) { isFocused }` field right after the class declaration (before `dockingEnabled`).

2. Replace lines 84–169 (all panel field declarations and DockablePanel wrappers) with delegations to the registry. For example:
   - `private val statusBar = registry.statusBar`
   - `private val consolePanel = registry.consolePanel`
   - `private val terminalPanel = registry.terminalPanel`
   - ... and so on for every panel and dockable field.

   Each `private val xxx = registry.xxx` replaces the corresponding inline construction. This is a mechanical replacement — the field names and types stay identical, so all downstream code in MainWindow continues to compile unchanged.

3. Remove the `claudeHookServer` field (line 86–88) and `claudeUsageService` field (line 89) — these will move to `PanelCoordinator` in Task 3. For now, keep them as local fields in MainWindow since the coordinator doesn't exist yet. **Actually, keep them in MainWindow for now** — they'll move in Task 3. The goal of this step is ONLY to extract panel construction.

4. Remove the now-redundant `allDockables` property (lines 1249–1253) — it's now `registry.allDockables`. Update the one usage in `dispose()` (line 331) to `registry.allDockables`.

- [ ] **Step 3: Compile and verify**

Run: `mvn -pl needlecast-desktop compile -T 4`
Expected: SUCCESS — all field names unchanged, just construction delegated.

- [ ] **Step 4: Run existing non-UI tests**

Run: `mvn -pl needlecast-desktop test -T 4 -Dexcludes="**/*UiTest.java,**/*UiTest.kt"`
Expected: All tests pass (none of these tests touch MainWindow construction).

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PanelRegistry.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "refactor: extract PanelRegistry from MainWindow"
```

---

### Task 2: Create DockingController

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DockingController.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

DockingController owns everything related to ModernDocking: initialization, registration, default layout, panel toggling, layout reset, import/export.

- [ ] **Step 1: Create DockingController.kt**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.andrewauclair.moderndocking.DockableTabPreference
import io.github.andrewauclair.moderndocking.DockingRegion
import io.github.andrewauclair.moderndocking.app.AppState
import io.github.andrewauclair.moderndocking.app.Docking
import io.github.andrewauclair.moderndocking.app.RootDockingPanel
import io.github.andrewauclair.moderndocking.ext.ui.DockingUI
import io.github.andrewauclair.moderndocking.settings.Settings
import io.github.rygel.needlecast.AppContext
import java.awt.Insets
import java.io.File
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager

class DockingController(
    private val registry: PanelRegistry,
    private val ctx: AppContext,
) {

    private val dockingEnabled = System.getProperty("needlecast.skipDocking")
        ?.equals("true", ignoreCase = true) != true

    private val layoutFile: File = Path.of(
        System.getProperty("user.home"), ".needlecast", "docking-layout.xml"
    ).toFile()

    fun isEnabled(): Boolean = dockingEnabled

    fun initialize(frame: JFrame) {
        if (!dockingEnabled) return
        Docking.initialize(frame)
        DockingUI.initialize()
        Settings.setActiveHighlighterEnabled(ctx.config.dockingActiveHighlight)
        UIManager.getDefaults()["TabbedPane.contentBorderInsets"] = Insets(0, 0, 0, 0)
        UIManager.getDefaults()["TabbedPane.tabsOverlapBorder"] = true
        registry.allDockables.forEach { Docking.registerDockable(it) }
    }

    fun buildRootPanel(frame: JFrame): java.awt.Container {
        val rootPanel = RootDockingPanel(frame)
        if (!Docking.getRootPanels().containsKey(frame)) {
            Docking.registerDockingPanel(rootPanel, frame)
        }
        val content = javax.swing.JPanel(java.awt.BorderLayout())
        content.add(rootPanel, java.awt.BorderLayout.CENTER)
        return content
    }

    fun applyTabPreference() {
        Settings.setDefaultTabPreference(
            if (ctx.config.tabsOnTop) DockableTabPreference.TOP_ALWAYS
            else DockableTabPreference.NONE
        )
    }

    fun restoreLayout() {
        AppState.setPersistFile(layoutFile)
        applyTabPreference()
        val restored = try { AppState.restore() } catch (_: Exception) { false }

        val requiredPanels = listOf(
            registry.terminalDockable, registry.editorDockable,
            registry.commandsDockable, registry.projectTreeDockable,
            registry.promptInputDockable, registry.commandInputDockable,
            registry.skillsDockable,
        )
        val allPresent = requiredPanels.all { Docking.isDocked(it) }

        if (!restored || !allPresent) {
            registry.allDockables.forEach { if (Docking.isDocked(it)) Docking.undock(it) }
            layoutFile.delete()
            applyDefaultLayout()
        }

        AppState.setAutoPersist(true)
    }

    fun applyDefaultLayout() {
        applyTabPreference()
        Docking.dock(registry.terminalDockable,    registry.projectTreeDockable, DockingRegion.CENTER)
        Docking.dock(registry.projectTreeDockable, registry.terminalDockable,    DockingRegion.WEST,   0.15)
        Docking.dock(registry.explorerDockable,    registry.projectTreeDockable, DockingRegion.CENTER)
        Docking.dock(registry.commandsDockable,    registry.terminalDockable,    DockingRegion.EAST,   0.20)
        Docking.dock(registry.gitLogDockable,      registry.commandsDockable,    DockingRegion.CENTER)
        Docking.dock(registry.logViewerDockable,   registry.gitLogDockable,      DockingRegion.CENTER)
        Docking.dock(registry.searchDockable,      registry.logViewerDockable,   DockingRegion.CENTER)
        Docking.dock(registry.docsDockable,        registry.searchDockable,      DockingRegion.CENTER)
        Docking.dock(registry.skillsDockable,      registry.docsDockable,        DockingRegion.CENTER)
        Docking.dock(registry.editorDockable,      registry.terminalDockable,    DockingRegion.CENTER)
        Docking.dock(registry.diffDockable,        registry.commandsDockable,    DockingRegion.SOUTH,  0.55)
        if (ctx.config.showConsole) {
            Docking.dock(registry.consoleDockable,  registry.diffDockable,       DockingRegion.CENTER)
        }
        Docking.dock(registry.promptInputDockable,  registry.terminalDockable,   DockingRegion.SOUTH,  0.90)
        Docking.dock(registry.commandInputDockable, registry.promptInputDockable, DockingRegion.CENTER)

        SwingUtilities.invokeLater { selectPrimaryTabs() }
    }

    // ... rest of methods move from MainWindow verbatim
    // See the exact method mapping below.
```

The following methods move from MainWindow into DockingController **verbatim** (just add `registry.` prefix when referencing panels/dockables):

| MainWindow method | Lines | DockingController method name |
|---|---|---|
| `buildLayout()` | 361–372 | `buildRootPanel(frame)` — takes JFrame param |
| `buildSimpleLayout()` | 374–378 | Remove — MainWindow handles this inline |
| `applyDockingLayout()` | 426–445 | `restoreLayout()` |
| `applyTabPreference()` | 459–464 | `applyTabPreference()` |
| `setupDefaultDockingLayout()` | 466–499 | `applyDefaultLayout()` |
| `resetLayout()` | 502–511 | `resetLayout(statusReporter: (String) -> Unit)` |
| `selectPrimaryTabs()` + `selectDockableTab()` | 513–531 | `selectPrimaryTabs()` + `selectDockableTab()` (private) |
| All 16 `toggleXxx()` methods | 535–656 | One generic `toggle(dockable, show, anchorProvider)` + keep individual methods for menu callbacks |
| `dockTo()` | 561–574 | `dockTo()` (private helper) |
| `importLayout()` | 959–978 | `importLayout(statusReporter, errorReporter)` |
| `exportLayout()` | 980–999 | `exportLayout(statusReporter, errorReporter)` |
| `installPanelHoverHighlighter()` + listener | 1269–1274 | `installHoverHighlighter()` |
| Panel hover listener + `clearPanelHighlight()` | 1256–1279 | Private fields + methods |
| `allDockables` property | 1249–1253 | Use `registry.allDockables` |

**Toggle method refactoring:** The 16 toggle methods all follow the same pattern. Replace with a single generic toggle + per-panel anchor config:

```kotlin
private data class DockConfig(
    val anchor: () -> DockablePanel?,
    val region: DockingRegion,
    val proportion: Double? = null,
)

private val dockConfigs = mapOf<String, DockConfig>(
    "console"      to DockConfig(anchor = { if (Docking.isDocked(registry.commandsDockable)) registry.commandsDockable else if (Docking.isDocked(registry.explorerDockable)) registry.explorerDockable else registry.terminalDockable }, region = DockingRegion.SOUTH, proportion = 0.65),
    "explorer"     to DockConfig(anchor = { registry.terminalDockable }, region = DockingRegion.EAST, proportion = 0.35),
    "commands"     to DockConfig(anchor = { registry.terminalDockable }, region = DockingRegion.EAST, proportion = 0.28),
    "git-log"      to DockConfig(anchor = { if (Docking.isDocked(registry.commandsDockable)) registry.commandsDockable else registry.terminalDockable }, region = DockingRegion.EAST, proportion = 0.28),
    "diff-viewer"  to DockConfig(anchor = { if (Docking.isDocked(registry.consoleDockable)) registry.consoleDockable else if (Docking.isDocked(registry.commandsDockable)) registry.commandsDockable else registry.terminalDockable }, region = DockingRegion.SOUTH, proportion = 0.55),
    "search"       to DockConfig(anchor = { if (Docking.isDocked(registry.commandsDockable)) registry.commandsDockable else registry.terminalDockable }, region = DockingRegion.EAST, proportion = 0.28),
    "editor"       to DockConfig(anchor = { registry.terminalDockable }, region = DockingRegion.CENTER),
    "prompt-input" to DockConfig(anchor = { registry.terminalDockable }, region = DockingRegion.SOUTH, proportion = 0.78),
    "command-input" to DockConfig(anchor = { if (Docking.isDocked(registry.promptInputDockable)) registry.promptInputDockable else registry.terminalDockable }, region = DockingRegion.SOUTH, proportion = 0.78),
    "renovate"     to DockConfig(anchor = { if (Docking.isDocked(registry.commandsDockable)) registry.commandsDockable else registry.terminalDockable }, region = DockingRegion.EAST, proportion = 0.28),
    "doc-viewer"   to DockConfig(anchor = { if (Docking.isDocked(registry.docsDockable)) registry.docsDockable else registry.terminalDockable }, region = DockingRegion.EAST, proportion = 0.28),
)

fun toggle(dockableId: String, show: Boolean) {
    val dockable = registry.allDockables.find { it.dockableId == dockableId } ?: return
    if (show && !Docking.isDocked(dockable)) {
        val config = dockConfigs[dockableId]
        val anchor = config?.anchor?.invoke()
        if (anchor != null && Docking.isDocked(anchor)) {
            val proportion = config.proportion
            if (proportion != null) Docking.dock(dockable, anchor, config.region, proportion)
            else Docking.dock(dockable, anchor, config.region)
        } else {
            Docking.dock(dockable, anchor, config?.region ?: DockingRegion.EAST)
        }
    } else if (!show && Docking.isDocked(dockable)) {
        Docking.undock(dockable)
    }
}

fun isDocked(dockableId: String): Boolean {
    val dockable = registry.allDockables.find { it.dockableId == dockableId } ?: return false
    return Docking.isDocked(dockable)
}
```

Note: For `git-log`, `search`, `renovate`, `doc-viewer` — when the commands panel IS docked, these tab with it (`DockingRegion.CENTER`). The config above handles the "else" case (dock east of terminal). The CENTER-tab case needs special handling:

Actually, review the original code carefully. `toggleGitLog` does:
```kotlin
if (Docking.isDocked(commandsDockable)) Docking.dock(gitLogDockable, commandsDockable, DockingRegion.CENTER)
else dockTo(gitLogDockable, terminalDockable, DockingRegion.EAST, 0.28)
```

So the config needs to encode "if commands docked → CENTER, else → EAST". This is not cleanly expressible in a single DockConfig. Keep the individual `toggle` methods for panels that have conditional CENTER behavior, and use the generic toggle for simple cases. Or encode the secondary region:

```kotlin
private data class DockConfig(
    val preferCenter: Boolean = false,
    val anchor: () -> DockablePanel?,
    val fallbackAnchor: () -> DockablePanel = { registry.terminalDockable },
    val region: DockingRegion,
    val fallbackRegion: DockingRegion = region,
    val proportion: Double? = null,
)
```

This is getting complex. **Simpler approach:** Keep individual toggle methods that delegate to a `dockTo` helper, matching the existing code structure. Don't over-abstract. The individual toggle methods are simple 4-line methods — the duplication is acceptable and readable.

Move all 16 toggle methods from MainWindow into DockingController, changing `xxxDockable` references to `registry.xxxDockable`. The method signatures stay the same: `fun toggleConsole(show: Boolean)`, etc.

Also move:
- `resetLayout()` — change `statusBar.setStatus(...)` to take a callback parameter: `fun resetLayout(onStatus: (String) -> Unit)`
- `importLayout()` / `exportLayout()` — take error/status callbacks
- `dispose()` docking logic — move the docking cleanup from MainWindow.dispose() into `fun dispose()`

- [ ] **Step 2: Update MainWindow to use DockingController**

In `MainWindow.kt`:

1. Add `private val docking = DockingController(registry, ctx)` field.

2. Replace `dockingEnabled` field with `docking.isEnabled()`.

3. In `init` block, replace docking initialization (lines 236–267) with:
   ```kotlin
   if (docking.isEnabled()) {
       docking.initialize(this)
       docking.installHoverHighlighter()
       contentPane = docking.buildRootPanel(this)
   } else {
       contentPane = buildSimpleLayout()
   }
   ```

4. In `windowOpened` (line 283), replace `applyDockingLayout()` with `docking.restoreLayout()`.

5. Replace all `Docking.isDocked(xxxDockable)` calls with `docking.isDocked("xxx")` or just delegate to the toggle methods on the docking controller.

6. Replace all `toggleXxx()` calls with `docking.toggleXxx()`.

7. Replace `resetLayout()` with `docking.resetLayout { statusBar.setStatus(it) }`.

8. Replace import/export layout with delegated calls.

9. In `dispose()`, replace the docking cleanup block with `docking.dispose()`.

10. Remove all the moved methods from MainWindow.

- [ ] **Step 3: Compile and verify**

Run: `mvn -pl needlecast-desktop compile -T 4`
Expected: SUCCESS

- [ ] **Step 4: Run existing non-UI tests**

Run: `mvn -pl needlecast-desktop test -T 4 -Dexcludes="**/*UiTest.java,**/*UiTest.kt"`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DockingController.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "refactor: extract DockingController from MainWindow"
```

---

### Task 3: Create PanelCoordinator

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PanelCoordinator.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsCallbacks.kt` (no changes needed — coordinator produces it)

PanelCoordinator owns:
- Project selection fan-out (`applyProjectSelection`)
- Inter-panel callback wiring (gitLog→diff, terminal→tree, claude hooks, usage service)
- Theme propagation
- `SettingsCallbacks` factory
- Claude hook server + usage service lifecycle
- The pending-project-selection debounce timer

- [ ] **Step 1: Create PanelCoordinator.kt**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.ui.settings.SettingsCallbacks
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import io.github.rygel.needlecast.ui.terminal.ClaudeHookServer
import io.github.rygel.needlecast.ui.terminal.ClaudeUsageService
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.Timer

class PanelCoordinator(
    private val registry: PanelRegistry,
    private val docking: DockingController,
    private val ctx: AppContext,
) {

    private val claudeHookServer: ClaudeHookServer? =
        if (ctx.config.claudeHooksEnabled) ClaudeHookServer { cwd, status ->
            registry.terminalPanel.onHookEvent(cwd, status)
        } else null

    private var claudeUsageService: ClaudeUsageService? = null

    private var pendingProjectSelection: DetectedProject? = null
    private val projectSelectionTimer = Timer(75) {
        propagateProjectSelection(pendingProjectSelection)
    }.apply { isRepeats = false }

    private var lastSelectedPath: String? = null
    private var lastSelectedCommandsKey: String? = null

    fun wire() {
        // Terminal → Tree status propagation
        registry.terminalPanel.onProjectStatusChanged = { path, status ->
            registry.projectTreePanel.updateProjectStatus(path, status)
        }

        // Terminal self-activation (when user types in an inactive terminal)
        registry.terminalPanel.onActivateRequested = { dir ->
            val shell = dir.shellExecutable?.takeIf { it.isNotBlank() } ?: ctx.config.defaultShell
            registry.terminalPanel.activateProject(dir.path, dir.env, shell, dir.startupCommand)
            registry.projectTreePanel.setActivePaths(registry.terminalPanel.activePaths())
        }

        // Terminal font size → config
        registry.terminalPanel.onFontSizeChanged = { size ->
            ctx.updateConfig(ctx.config.copy(terminalFontSize = size))
        }

        // Git log → Diff viewer
        registry.gitLogPanel.onCommitSelected = { result ->
            registry.diffViewerPanel.display(result)
            if (docking.isEnabled() && !docking.isDocked("diff-viewer")) {
                docking.toggleDiff(true)
            }
        }

        // Project tree selection → debounce → propagateProjectSelection
        registry.projectTreePanel.onProjectSelected = { project ->
            pendingProjectSelection = project
            projectSelectionTimer.restart()
        }

        // Project tree activate → terminal + explorer
        registry.projectTreePanel.onActivate = { project ->
            val shell = project.directory.shellExecutable
                ?.takeIf { it.isNotBlank() }
                ?: ctx.config.defaultShell
            registry.terminalPanel.activateProject(
                project.directory.path,
                project.directory.env,
                shell,
                project.directory.startupCommand,
            )
            registry.projectTreePanel.setActivePaths(registry.terminalPanel.activePaths())
            registry.explorerPanel.setRootDirectory(File(project.directory.path))
        }

        // Project tree deactivate → terminal
        registry.projectTreePanel.onDeactivate = { project ->
            registry.terminalPanel.deactivateProject(project.directory.path)
            registry.projectTreePanel.setActivePaths(registry.terminalPanel.activePaths())
        }

        // External file drops → explorer
        registry.projectTreePanel.onExternalFilesDropped = { files ->
            files.forEach { registry.explorerPanel.openFile(it) }
        }

        // Start Claude hooks if enabled
        if (claudeHookServer != null) {
            claudeHookServer.start()
            Thread({ ClaudeHookServer.installHooks(claudeHookServer.port) }, "claude-hooks-installer")
                .apply { isDaemon = true; start() }
            registry.terminalPanel.setUseHooksForStatus(true)
        } else {
            Thread({ ClaudeHookServer.uninstallHooks() }, "claude-hooks-cleanup")
                .apply { isDaemon = true; start() }
        }

        // Claude usage service
        if (ctx.config.claudeQuotaEnabled) {
            startUsageService()
        }

        // Restore terminal colors and font from config
        val initFg = ctx.config.terminalForeground?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
        val initBg = ctx.config.terminalBackground?.let { runCatching { java.awt.Color.decode(it) }.getOrNull() }
        if (initFg != null || initBg != null) registry.terminalPanel.applyTerminalColors(initFg, initBg)
        registry.terminalPanel.applyFontSize(ctx.config.terminalFontSize)
        registry.terminalPanel.applyFontFamily(ctx.config.terminalFontFamily)
        registry.explorerPanel.applyEditorFont(ctx.config.editorFontFamily, ctx.config.editorFontSize)
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

    fun setLastSelectedPath(path: String?) {
        lastSelectedPath = path
    }

    fun getLastSelectedPath(): String? = lastSelectedPath

    fun createSettingsCallbacks(reloadShortcuts: () -> Unit, applyUiFont: () -> Unit): SettingsCallbacks {
        return SettingsCallbacks(
            onShortcutsChanged = reloadShortcuts,
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
    }

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
        val svc = ClaudeUsageService { data ->
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

    private fun buildCommandsKey(project: DetectedProject): String =
        project.commands.joinToString(separator = "|") { cmd ->
            val argv = cmd.argv.joinToString(separator = "\u0000")
            "${cmd.label}\u0000$argv\u0000${cmd.workingDirectory}"
        }
}
```

- [ ] **Step 2: Update MainWindow to use PanelCoordinator**

In `MainWindow.kt`:

1. Add `private val coordinator = PanelCoordinator(registry, docking, ctx)` field.

2. In `init` block, replace the manual callback wiring (lines 179–224) with `coordinator.wire()`.

3. Remove the `pendingProjectSelection`, `projectSelectionTimer`, `lastSelectedPath`, `lastSelectedCommandsKey` fields — they're now in the coordinator.

4. Replace `applyProjectSelection(...)` calls with `coordinator.propagateProjectSelection(...)`.

5. Replace the `claudeHookServer` and `claudeUsageService` fields and their start/stop logic — now handled by `coordinator.wire()` and `coordinator.dispose()`.

6. Update `windowClosing` to call `coordinator.dispose()` instead of manually stopping hook server and usage service.

7. Replace `applyTheme(dark)` body with `coordinator.propagateThemeChange(dark)`.

8. Update `confirmWorkspaceImportTerminalClosure` to use `registry.terminalPanel.activePaths()`.

9. Update `applyImportedWorkspace` to use coordinator methods.

10. Update `detectCwdProject` to use `coordinator.triggerProjectSelection(...)` instead of the local timer.

11. Remove all moved methods from MainWindow.

- [ ] **Step 3: Compile and verify**

Run: `mvn -pl needlecast-desktop compile -T 4`
Expected: SUCCESS

- [ ] **Step 4: Run existing non-UI tests**

Run: `mvn -pl needlecast-desktop test -T 4 -Dexcludes="**/*UiTest.java,**/*UiTest.kt"`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PanelCoordinator.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "refactor: extract PanelCoordinator from MainWindow"
```

---

### Task 4: Create MenuBarBuilder

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MenuBarBuilder.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

MenuBarBuilder constructs all menus. It takes the registry, coordinator, docking controller, and context. It also owns the AI CLI cache and detection logic.

- [ ] **Step 1: Create MenuBarBuilder.kt**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.isOsDark
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.ui.settings.SettingsCallbacks
import java.awt.Component
import java.awt.Desktop
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

class MenuBarBuilder(
    private val registry: PanelRegistry,
    private val coordinator: PanelCoordinator,
    private val docking: DockingController,
    private val ctx: AppContext,
    private val owner: JFrame,
    private val callbacks: MenuBarCallbacks,
) {

    data class MenuBarCallbacks(
        val reloadShortcuts: () -> Unit,
        val applyUiFont: () -> Unit,
        val showAbout: () -> Unit,
        val checkForUpdatesManual: () -> Unit,
        val importConfig: () -> Unit,
        val exportConfig: () -> Unit,
        val importWorkspace: () -> Unit,
        val exportWorkspace: () -> Unit,
    )

    private var cliCache: List<Pair<AiCli, Boolean>> = emptyList()
    private var cliCacheReady = false

    fun build(): JMenuBar {
        val i18n = ctx.i18n

        val fileMenu = buildFileMenu(i18n)
        val viewMenu = buildViewMenu(i18n.translate("menu.view"))
        val windowsMenu = buildWindowsMenu()
        val aiMenu = buildAiMenu()
        val helpMenu = buildHelpMenu(i18n)

        return JMenuBar().apply {
            add(fileMenu); add(viewMenu); add(windowsMenu); add(aiMenu); add(helpMenu)
        }
    }

    // Move buildFileMenu, buildViewMenu, buildWindowsMenu, buildAiMenu,
    // buildHelpMenu, showAbout, refreshCliCache, launchCliInTerminal,
    // and all related helper methods from MainWindow verbatim.
    //
    // Changes needed:
    // - registry.xxxPanel instead of local fields
    // - docking.toggleXxx() instead of local toggle methods
    // - docking.isDocked("xxx") instead of Docking.isDocked(xxxDockable)
    // - coordinator.createSettingsCallbacks(callbacks.reloadShortcuts, callbacks.applyUiFont)
    //   instead of inline SettingsCallbacks construction
    // - ctx.updateConfig(...) stays the same
    // - callbacks.xxx() for owner-specific actions (about, import, export, etc.)
    //
    // The methods are mechanically moved with these substitution patterns:
    //   terminalPanel          → registry.terminalPanel
    //   explorerPanel          → registry.explorerPanel
    //   projectTreePanel       → registry.projectTreePanel
    //   statusBar              → registry.statusBar
    //   consolePanel           → registry.consolePanel
    //   toggleConsole(...)     → docking.toggleConsole(...)
    //   toggleExplorer(...)    → docking.toggleExplorer(...)
    //   Docking.isDocked(...)  → docking.isDocked(...)
    //   resetLayout()          → docking.resetLayout { registry.statusBar.setStatus(it) }
    //   this@MainWindow        → owner
}
```

The full method bodies for `buildFileMenu`, `buildViewMenu`, `buildWindowsMenu`, `buildAiMenu`, `buildHelpMenu` are moved from MainWindow lines 660–853, 1061–1196. Apply the substitution patterns listed above.

`buildFileMenu` creates Settings, Import/Export Config, Import/Export Workspace, Import/Export Layout, and Exit items. The Settings item constructs `SettingsDialog` using `coordinator.createSettingsCallbacks(...)`.

`buildViewMenu` creates theme items, Console/Explorer checkboxes, Privacy Mode, Panel Hover Highlight, and Reset Layout. Theme switching calls are unchanged (they call `ctx.updateConfig` and `applyTheme` via callbacks).

`buildWindowsMenu` creates panel visibility checkboxes. Each checkbox calls `docking.toggleXxx(isSelected)`.

`buildAiMenu` creates Prompt Library, Command Library, and dynamic AI CLI entries. Uses `registry.terminalPanel.sendInput(...)`.

`buildHelpMenu` creates Check for Updates and About items, delegating to callbacks.

- [ ] **Step 2: Update MainWindow to use MenuBarBuilder**

In `MainWindow.kt`:

1. Create `MenuBarCallbacks` with lambdas that reference MainWindow methods (about, import, export, etc.).

2. Replace `jMenuBar = buildMenuBar()` with:
   ```kotlin
   val menuBuilder = MenuBarBuilder(registry, coordinator, docking, ctx, this, MenuBarCallbacks(
       reloadShortcuts = { reloadShortcuts() },
       applyUiFont = { applyUiFontFromConfig() },
       showAbout = { showAbout() },
       checkForUpdatesManual = { checkForUpdatesManual() },
       importConfig = { importConfig() },
       exportConfig = { exportConfig() },
       importWorkspace = { importWorkspace() },
       exportWorkspace = { exportWorkspace() },
   ))
   jMenuBar = menuBuilder.build()
   ```

3. Remove `buildMenuBar()`, `buildViewMenu()`, `buildWindowsMenu()`, `buildAiMenu()`, `showAbout()`, `refreshCliCache()`, `launchCliInTerminal()` from MainWindow.

4. Remove `cliCache`, `cliCacheReady` fields from MainWindow.

- [ ] **Step 3: Compile and verify**

Run: `mvn -pl needlecast-desktop compile -T 4`
Expected: SUCCESS

- [ ] **Step 4: Run existing non-UI tests**

Run: `mvn -pl needlecast-desktop test -T 4 -Dexcludes="**/*UiTest.java,**/*UiTest.kt"`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MenuBarBuilder.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "refactor: extract MenuBarBuilder from MainWindow"
```

---

### Task 5: Final MainWindow Cleanup and Verification

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

Remove all dead code. MainWindow should now be ~350 lines containing only:
- Constructor with field declarations (registry, docking, coordinator, menuBuilder)
- `init` block: window sizing, icon, content pane, lifecycle listeners
- Keyboard shortcut registration
- Theme application helpers (`applyTheme`, `setTheme`, `applyUiFontFromConfig`)
- Update checker logic (`updateTimer`, `checkForUpdates`, `checkForUpdatesManual`, `buildSparkle4j`)
- EDT stall monitor
- Tour overlay setup
- CWD project detection
- Import/export config/workspace helpers
- `dispose()` override
- Companion object (`buildTitle`, `currentVersion`)

- [ ] **Step 1: Remove all dead imports and fields**

After the four extractions, MainWindow should have no references to:
- Individual panel construction (all in PanelRegistry)
- Docking API calls (all in DockingController)
- Inter-panel wiring (all in PanelCoordinator)
- Menu construction (all in MenuBarBuilder)

Remove unused imports. Remove fields that are now accessed via `registry.xxx` instead of local fields. If a local `val statusBar = registry.statusBar` is still used in MainWindow (for update checker status updates), keep it as a convenience alias — but verify it's actually needed.

- [ ] **Step 2: Verify MainWindow line count**

MainWindow should be under 400 lines. The remaining code is:
- Field declarations: ~20 lines
- `init` block: ~100 lines (window setup, listeners, update timer)
- Keyboard shortcuts: ~30 lines
- Theme helpers: ~30 lines
- Update checker: ~100 lines
- EDT monitor: ~50 lines
- Tour: ~30 lines
- CWD detection: ~30 lines
- Import/export helpers: ~60 lines
- `dispose()`: ~15 lines
- Companion: ~15 lines

- [ ] **Step 3: Full compilation and test run**

Run: `mvn -pl needlecast-desktop compile -T 4`
Expected: SUCCESS

Run: `mvn -pl needlecast-desktop test -T 4 -Dexcludes="**/*UiTest.java,**/*UiTest.kt"`
Expected: All tests pass.

- [ ] **Step 4: Verify UI tests compile (don't run locally)**

Run: `mvn -pl needlecast-desktop test-compile -T 4`
Expected: SUCCESS — `MainWindowUiTest` and `DockingLayoutUiTest` compile against the unchanged `MainWindow(ctx)` constructor signature.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "refactor: clean up MainWindow after decomposition"
```

---

## Self-Review

### Spec coverage
- **PanelRegistry**: All 17 panels + 16 DockablePanel wrappers + allDockables ✓
- **DockingController**: init, register, default layout, restore, toggle methods, reset, import/export, hover highlight ✓
- **PanelCoordinator**: project selection fan-out, inter-panel wiring, SettingsCallbacks factory, Claude hooks, usage service, theme propagation ✓
- **MenuBarBuilder**: File, View, Panels, AI Tools, Help menus ✓
- **MainWindow**: lifecycle glue, update checker, EDT monitor, tour, shortcuts, import/export config ✓

### Placeholder scan
- "Move verbatim" instructions specify exact line ranges and substitution patterns
- All class skeletons include constructor signatures, field declarations, and method signatures
- Callback wiring in PanelCoordinator.wire() is fully specified with source→sink mapping

### Type consistency
- `PanelRegistry` takes `AppContext` + `isWindowFocused: () -> Boolean`
- `DockingController` takes `PanelRegistry` + `AppContext`
- `PanelCoordinator` takes `PanelRegistry` + `DockingController` + `AppContext`
- `MenuBarBuilder` takes all three + `JFrame` + `MenuBarCallbacks`
- MainWindow constructor remains `MainWindow(ctx: AppContext)` — unchanged signature

### Risks
- **ProjectTreePanel callbacks**: The original code passes callbacks in the constructor (lines 123–150). The registry constructs it with empty defaults; the coordinator wires them in `wire()`. This works because `ProjectTreePanel` has default values for all callback params. Verify that no callbacks fire between construction and `wire()`.
- **Construction order**: The registry constructs panels in property declaration order. Kotlin guarantees this. If a panel's init block runs and tries to use a panel declared after it, it will NPE. The current dependency order (statusBar → consolePanel → terminalPanel → explorerPanel → searchPanel → ...) matches the original MainWindow order.
- **`isWindowFocused`**: CommandPanel needs `{ isFocused }` which requires the JFrame. The registry takes it as a constructor parameter. MainWindow passes `{ isFocused }` after `this` is initialized.
