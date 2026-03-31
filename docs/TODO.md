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
- [ ] **File watcher / auto-rescan** — use `WatchService` to auto-refresh command list when build files change
- [ ] **Command queuing** — queue commands to run sequentially (e.g. clean → build → run)
- [ ] **Git log viewer** — read-only `git log` tab with clickable commits showing `git show` in the editor

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
- [ ] **Scanner unit tests** — fixture-based tests for Maven, Gradle, npm detection to prevent regressions
- [ ] **Structured logging** — SLF4J + Logback to `~/.quicklaunch/quicklaunch.log` with rotation; replace all `printStackTrace` calls
- [ ] **Keyboard shortcut editor** — settings tab to rebind F5, Ctrl+T, Ctrl+1/2/3 and persist them
