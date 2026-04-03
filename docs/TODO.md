# QuickLaunch — TODO

## UX / Productivity

- [x] **Keyboard shortcuts** — `Ctrl+T` activate terminal, `F5` rescan, `Ctrl+1/2/3` focus panels
- [x] **Project filter** — search/filter box above the project list
- [x] **Persistent layout** — save/restore split pane divider positions between sessions
- [x] **Command history** — remember last N commands run per project with output; allow re-run
- [x] **Global project switcher** — fuzzy-search popup (Ctrl+P) to jump to any project across all groups
- [x] **Command output search** — Ctrl+F in the console panel to search/highlight build output
- [x] **Desktop notifications** — system tray notification when a command finishes while window is in background
- [x] **Environment variables per project** — key=value pairs stored in `ProjectDirectory`, injected into commands and terminals
- [x] **Auto OS theme** — follow OS dark/light preference automatically via FlatLaf
- [x] **README preview** — show first lines of `README.md` below command list when a project is selected
- [x] **Group color coding** — same left-stripe color mechanic applied to groups in the sidebar
- [x] **File watcher / auto-rescan** — use `WatchService` to auto-refresh command list when build files change
- [x] **Command queuing** — queue commands to run sequentially (e.g. clean → build → run)
- [x] **Git log viewer** — read-only `git log` tab with clickable commits showing `git show` in the editor

## File Explorer / Editor

- [x] **Find & Replace** — wire up RSyntaxTextArea's built-in `SearchEngine` (Ctrl+F / Ctrl+H)
- [x] **Right-click context menu** — create file, rename, delete in the file explorer
- [x] **Hidden files toggle** — show/hide dot-files (`.env`, `.git`, etc.)
- [x] **Multiple editor tabs** — open more than one file at once

## Terminal

- [x] **Font size** — Ctrl+scroll or +/− in settings to change terminal font size
- [x] **Multiple tabs per project** — support more than one shell per project

## Project Metadata

- [x] **Git status** — show current branch and dirty indicator in the project list
- [x] **Project color / label** — color-code groups or projects for quick visual scanning

## Infrastructure

- [x] **Auto-save layout on close** — divider positions currently reset from hardcoded values, ignoring actual window state
- [x] **Graceful config migration** — migration layer to handle schema changes without silent data loss
- [x] **Scanner unit tests** — fixture-based tests for Maven, Gradle, npm detection to prevent regressions
- [x] **Structured logging** — SLF4J + Logback to `~/.quicklaunch/quicklaunch.log` with rotation; replace all `printStackTrace` calls
- [x] **Keyboard shortcut editor** — settings tab to rebind F5, Ctrl+T, Ctrl+1/2/3 and persist them

## AI / Prompt Library

- [x] **Prompt Library** — create, edit, delete reusable prompt templates with `{variable}` substitution; paste resolved text into the active terminal
- [x] **APM integration** — detect `apm.yml`, surface `apm install/audit/update/bundle` commands; APM tab in Settings; `apm` in AI Tools menu CLI detector

## Architecture Improvements

Issues identified in architecture review (priority order):

### High Impact
- [x] `ProcessExecutor` utility — centralise all `ProcessBuilder` calls behind a single timeout-aware helper; eliminate 4 divergent patterns (`GitStatus`, `GitLogPanel`, `AiCliDetector`, `SettingsDialog`)
- [x] Fix EDT blocking in AI Tools menu — pre-warm CLI detection on a background thread at window open; show cached results on menu open instead of blocking the UI
- [x] `ProjectService` — extract group/directory config mutations out of `DirectoryPanel` into a dedicated service class; `DirectoryPanel` should not know the shape of `AppConfig`
- [x] `GitService` interface — decouple git operations from `ProcessBuilder` in the model layer; enables mocking in future UI tests
- [x] Surface scan failures — `scanAndAdd` now catches exceptions; failed projects appear in the list with a red ⚠ badge and tooltip instead of silently disappearing
- [x] Config change observation — `AppContext.addConfigListener` lets components react to config changes without polling

### Medium Impact
- [x] Fix `BuildFileWatcher` race condition — `watch()` has a check-then-act race on registration; fix with double-checked locking
- [x] Add `apm.yml` to `BuildFileWatcher` watched file names
- [x] `CommandHistoryManager` — extracted history persistence out of `CommandPanel`; config is now the source of truth
- [x] Graceful shutdown — `Disposable` interface added to `AppContext`; `BuildFileWatcher` implements it and is registered automatically; `windowClosing` calls `ctx.disposeAll()`
- [x] `BuildTool` self-describing — `tagLabel` and `tagColor` moved onto the enum; exhaustive `when` in renderer eliminated

### Lower Impact
- [x] Config migration runner — `ConfigMigrator` applies sequential version migrations on load; pattern in place for future schema changes
- [x] `CommandPanel` queue tests — add unit tests for the queue drain logic (highest-value untested UI component)
- [x] `ProjectSwitcherDialog` filter tests — `filterEntries` extracted as a testable pure function; 6 tests added
- [x] Process timeout enforcement in `ProcessCommandRunner` — `cancel()` now interrupts the reader thread; `RunningProcess` carries the thread reference
