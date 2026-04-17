# Project Management

## Overview

Projects in Needlecast are organized into **Groups** (collapsible folders) inside the **Project Tree** on the left. Folders can be nested inside other folders. Each project maps to a single directory on disk.

Selecting a project makes it **active** — every panel (Commands, Terminal, Log Viewer, Git Log, Search, Renovate) updates to reflect it.

---

## Project Tree — Visual Anatomy

Each project row shows up to nine visual elements simultaneously:

| Element | Description |
|---|---|
| **Color stripe** | 4 px colored bar on the left edge (optional, hex color) |
| **Active dot** | Green circle (●) when a terminal session is running for this project |
| **Agent LED** | Blinking/solid indicator of AI agent state — see [[#Agent Status LED]] |
| **Missing icon** | Red ⚠ when the project directory does not exist on disk |
| **Project name** | Bold — shown in red if directory is missing and not selected |
| **Git branch badge** | Branch name in monospace gray; turns amber (⚠) if dirty; format: `⎇ branch*` |
| **Build tool badges** | Colored pill labels showing detected tools (e.g. maven, gradle, npm) |
| **Custom tags** | Gray pill badges, right-aligned |
| **Fail badge** | Red ⚠ if the project scanner threw an error |

Folder rows show their name and an optional color stripe. Git badges and build tool badges do not appear on folders.

---

## Toolbar Buttons

| Button | Tooltip | Action |
|---|---|---|
| 📁+ | "Add a folder to organize projects" | Creates a new top-level folder |
| 📄+ | "Add a project directory" | Adds a project under the selected folder |
| ↻ | "Rescan all projects (F5)" | Triggers a full rescan of all projects |
| Filter field | placeholder "Filter…" | Live search by name or tag |

---

## Filter / Search

The filter field at the top of the Project Tree filters in real time. It is **case-insensitive** and matches against:
- The project's display name (or directory name if no custom name is set)
- All custom tags attached to the project

Non-matching projects and empty folders are hidden. Projects that were not yet scanned while filtered get their scan triggered when shown.

---

## Right-Click Menus

### On a Folder

| Item | Action |
|---|---|
| **New Subfolder…** | Creates a nested folder inside this one |
| **Add Project…** | Opens a directory chooser and adds it under this folder |
| **Rename…** | Renames the folder |
| **Set Color…** | Opens the OS color picker; sets the left-edge stripe |
| **Clear Color** | Removes the color stripe (visible only when a color is set) |
| **Remove** | Removes the folder and its children from the tree (disk is untouched) |
| Advanced → **Delete from disk…** | Recursively deletes the folder from disk (shown in red, requires confirmation with file count) |

### On a Project

| Item | Action |
|---|---|
| **▶ Activate Terminal** | Opens a terminal session for this project (visible when not yet active) |
| **⏹ Deactivate Terminal** | Closes the terminal session (visible when active) |
| **Tags…** | Opens a comma-separated tag editor |
| **Shell Settings…** | Opens the per-project shell/startup-command dialog |
| **Environment…** | Opens the per-project environment variable table |
| **Set Color…** | Sets the color stripe |
| **Clear Color** | Removes the color stripe |
| **Remove** | Removes from tree (disk untouched) |
| Advanced → **Delete from disk…** | Permanently deletes the project directory (shown in red) |

### On Empty Space

| Item | Action |
|---|---|
| **New Folder…** | Creates a top-level folder |
| **Add Project…** | Adds a project to the root level |

---

## Per-Project Settings

### Tags

A comma-separated list of labels attached to the project. Tags appear as gray pill badges in the row and are searchable by the filter field.

Example: `backend, java, prod`

### Shell Settings

Opens a dialog with two fields:

| Field | Description |
|---|---|
| **Shell** | Shell executable, e.g. `zsh`, `pwsh`. Leave blank to use the OS default or the global default from [[Settings]]. |
| **Startup** | A command sent to the shell on open, e.g. `conda activate ml`. |

Platform defaults: Windows = `cmd.exe`, macOS = `/bin/zsh`, Linux = `/bin/bash`.

### Environment Variables

A table of key/value pairs injected on top of the system environment for every command and terminal session belonging to this project.

| Operation | How |
|---|---|
| Add row | Click the **+** button |
| Remove row | Select a row and click **−** |
| Edit | Double-click any cell |

Values are merged on top of the system environment — existing system variables are preserved unless overridden.

### Color Stripe

A 4 px colored bar on the left edge of the project or folder row. Set via **Set Color…** (OS color picker dialog), stored as a hex string (`#RRGGBB`).

### Display Name

If you want a project to appear under a custom label without renaming the directory, use **right-click → Rename…**.

---

## Drag and Drop

### Internal (Tree Reordering)

Drag a project or folder within the tree to reorder it. Dropping onto the center of a folder row (within 75 % of its height) places the dragged item *inside* that folder. Dropping onto the edge places it *next to* it.

### External (from OS File Manager)

Drop a folder from the OS file manager onto the Project Tree to add it as a new project. Supported transfer flavors: Java file list (Windows/macOS) and `text/uri-list` (Linux).

**Missing project repair:** If a dropped folder's name matches an existing project whose directory is marked missing, Needlecast asks whether to *repair* the path (replace the old path with the dropped one) or *add as a new* project.

---

## Fuzzy Project Switcher

Press `Ctrl+P` to open the project switcher. It is an undecorated floating dialog with:
- A large search field (live filtering)
- A results list showing project name and breadcrumb path
- Keyboard navigation: ↑ ↓ to move, `Enter` to switch, `Escape` to cancel
- Double-click also confirms selection

Works even when the Project Tree panel is hidden.

---

## Agent Status LED

A small LED in each project row shows the state of any AI agent (Claude Code, Gemini CLI, etc.) running in that project's terminal.

| LED state | Meaning |
|---|---|
| **NONE** | No terminal active or no agent detected |
| **THINKING** | Agent is processing (LED blinks at 600 ms intervals) |
| **IDLE** (WAITING) | Agent is waiting for input (solid green) |
| **ERROR** | Agent session encountered an error (solid red) |

When *any* project is in THINKING state, the blink timer runs globally for all THINKING LEDs.

---

## Git Branch Display

Needlecast runs `git status` asynchronously after each project scan and caches the result. The badge format is `⎇ branch-name` — an asterisk (`*`) is appended if there are uncommitted changes, and the badge color shifts from gray (#888888) to amber (#E6A817).

---

## Missing Project Directories

When a project's directory no longer exists on disk:
- The name is rendered in red
- A red ⚠ icon appears on the row
- Scanning and git status fetching are skipped
- The project can still be selected (useful for drag-drop repair)
- **Repair:** Drag the new folder location onto the row to update the path

---

## Build File Watcher

Needlecast watches build files in the active project (`pom.xml`, `build.gradle`, `package.json`, etc.) for changes. When a build file is modified on disk the Commands panel automatically refreshes without a manual rescan.

---

## Related

- [[Build Tool Detection]] — how command lists are derived from build files
- [[Terminal]] — per-project shell, startup command, and env vars
- [[Settings]] — global shell default, fonts, themes
