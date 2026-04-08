# Architecture

## Package overview

```
io.github.rygel.needlecast
├── AppContext.kt          — dependency container and config bus
├── Main.kt                — entry point
├── config/
│   ├── JsonConfigStore    — read/write config.json via Jackson
│   └── ConfigMigrator     — sequential schema version migrations
├── model/
│   ├── AppConfig          — root config data class (serialised to JSON)
│   ├── CommandDescriptor  — a runnable command with BuildTool metadata
│   ├── DetectedProject    — scanner result (commands + optional scanFailed flag)
│   └── GitStatus          — branch name + dirty flag
├── git/
│   ├── GitService         — interface: readStatus, log, show
│   └── ProcessGitService  — implementation via ProcessExecutor
├── process/
│   ├── ProcessExecutor    — centralised timeout-aware ProcessBuilder wrapper
│   ├── ProcessCommandRunner — runs a CommandDescriptor, streams output
│   ├── RunningProcess     — handle to a running process (cancel support)
│   └── ProcessOutputListener — callback interface for live output lines
├── scanner/
│   ├── ProjectScanner     — interface: scan(dir): DetectedProject?
│   ├── CompositeProjectScanner — tries all scanners, merges results
│   └── *ProjectScanner    — Maven, Gradle, npm, .NET, APM, IntelliJ Run Config
├── service/
│   ├── ProjectService     — add/remove/update project directories in config
│   ├── CommandHistoryManager — persist & retrieve last-N commands per project
│   └── CommandQueue       — FIFO queue of QueuedCommand (extracted for testability)
└── ui/
    ├── MainWindow         — top-level frame, menu bar, split layout
    ├── DirectoryPanel     — left sidebar: groups and projects
    ├── CommandPanel       — centre: command list, queue, console
    ├── ConsolePanel       — output area with Ctrl+F search
    ├── FileExplorerPanel  — file tree + syntax editor tabs
    ├── TerminalPanel      — JediTerm tab host
    ├── GitLogPanel        — read-only git log with git-show on click
    ├── SettingsDialog     — tabbed settings (general, shortcuts, APM, …)
    ├── ProjectSwitcherDialog — Ctrl+P fuzzy switcher
    ├── PromptLibraryDialog — create/edit/delete prompt templates
    ├── VariableResolutionDialog — resolves {placeholders} before paste
    └── StatusBar          — bottom bar with current project info
```

## AppContext

`AppContext` is the application's dependency container. It holds:

- `config: AppConfig` — the live config; mutated only via `updateConfig()` which persists and fires listeners
- `configStore: JsonConfigStore` — storage backend
- `scanner: CompositeProjectScanner`
- `commandRunner: ProcessCommandRunner`
- `gitService: GitService`

Components that need to react to config changes register via `addConfigListener`. Components that need cleanup on exit implement `Disposable` and call `ctx.register(this)` — `MainWindow.windowClosing` calls `ctx.disposeAll()`.

## Config storage and migration

`JsonConfigStore` deserialises `~/.needlecast/config.json` with Jackson then passes the result through `ConfigMigrator.migrate()`. Migrations are sequential: version 0 → 1 → … → `CURRENT_VERSION`. Adding a migration means incrementing `CURRENT_VERSION` and adding a step to `migrations`.

## Process execution

All `ProcessBuilder` usage goes through `ProcessExecutor.run()`:

```
ProcessExecutor.run(argv, workingDir, timeoutMs)
  → spawns process
  → reads stdout+stderr on a daemon thread (avoids blocking waitFor)
  → waitFor(timeoutMs) — destroyForcibly on timeout
  → joins reader thread briefly
  → returns Result(output, exitCode) or null on timeout
```

`ProcessCommandRunner` streams live output to a `ProcessOutputListener` and stores the reader thread in `RunningProcess` so `cancel()` can interrupt it.

## Scanner pipeline

`CompositeProjectScanner` iterates registered scanners in order. Each scanner inspects a directory and returns a `DetectedProject` with zero or more `CommandDescriptor` entries, or `null` if it does not recognise the directory. Scanners are additive — a directory can be detected by more than one (e.g. a Gradle project that also has an `apm.yml`).

If a scanner throws, `DirectoryPanel.scanAndAdd` catches the exception and sets `scanFailed = true` on a placeholder `DetectedProject`, showing a red ⚠ badge in the sidebar instead of silently dropping the project.

## Git integration

`GitService` is an interface so the implementation can be swapped or mocked in tests. `ProcessGitService` delegates to `ProcessExecutor` with a 5-second timeout. `GitLogPanel` and `DirectoryPanel` both depend on `GitService` via `AppContext`.

## BuildFileWatcher

`BuildFileWatcher` implements `Disposable` and is registered with `AppContext` at startup. It uses `java.nio.file.WatchService` to monitor project directories for changes to build files (`pom.xml`, `build.gradle`, `package.json`, `*.csproj`, `apm.yml`). Registration uses double-checked locking to avoid a check-then-act race when multiple events arrive concurrently.

## EDT safety

Long-running operations (scanning, git, process execution) run on `SwingWorker` background threads. `AppContext.addConfigListener` callbacks are always invoked on the EDT via `SwingUtilities.invokeLater`. AI CLI detection is pre-warmed on a daemon thread at window open; results are cached so the AI Tools menu never blocks the EDT.
