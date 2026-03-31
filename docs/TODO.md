# QuickLaunch — TODO

## UX / Productivity

- [x] **Keyboard shortcuts** — `Ctrl+T` activate terminal, `F5` rescan, `Ctrl+1/2/3` focus panels
- [x] **Project filter** — search/filter box above the project list
- [x] **Persistent layout** — save/restore split pane divider positions between sessions
- [x] **Command history** — remember last N commands run per project with output; allow re-run

## File Explorer / Editor

- [x] **Find & Replace** — wire up RSyntaxTextArea's built-in `SearchEngine` (Ctrl+F / Ctrl+H)
- [x] **Right-click context menu** — create file, rename, delete in the file explorer
- [x] **Hidden files toggle** — show/hide dot-files (`.env`, `.git`, etc.)
- [x] **Multiple editor tabs** — open more than one file at once

## Terminal

- [x] **Font size** — Ctrl+scroll or +/− in settings to change terminal font size
- [ ] **Multiple tabs per project** — support more than one shell per project

## Project Metadata

- [ ] **Git status** — show current branch and dirty indicator in the project list
- [ ] **Project color / label** — color-code groups or projects for quick visual scanning

## Infrastructure

- [ ] **Auto-save layout on close** — divider positions currently reset from hardcoded values, ignoring actual window state
- [ ] **Graceful config migration** — migration layer to handle schema changes without silent data loss
