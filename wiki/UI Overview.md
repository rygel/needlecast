# UI Overview

## Panel Layout

Needlecast uses **ModernDocking** — every panel is resizable, repositionable, and detachable. The docking layout is persisted to `~/.needlecast/docking-layout.xml`.

### Default Layout

```
┌──────────────────────────────────────────────────────┐
│  File  View  Panels  AI Tools  Help          ⚙       │  ← Menu bar
├─────────────┬────────────────────────┬───────────────┤
│             │                        │               │
│  Projects   │                        │  Commands     │
│  ─────────  │     Terminal           │  ─────────    │
│  Explorer   │     (PTY)              │  Git Log      │
│             │                        │  Search       │
│             │                        │  Log Viewer   │
│             ├────────────────────────┤  Renovate     │
│             │  Output Console        │               │
├─────────────┴────────────────────────┴───────────────┤
│  Prompt Input  |  Command Input                      │
└──────────────────────────────────────────────────────┘
```

### Left Column

| Panel | Purpose |
|---|---|
| **Projects** | Hierarchical tree of project groups and projects |
| **Explorer** | File table browser for the active project |

### Center Column

| Panel | Purpose |
|---|---|
| **Terminal** | PTY terminal, supports multiple tabs per project |
| **Editor** | Syntax-highlighted code editor (opens when you open a file) |
| **Output Console** | Stdout/stderr captured from Commands panel runs |

### Right Column

| Panel | Purpose |
|---|---|
| **Commands** | Detected build commands, run history, command queue, README preview |
| **Git Log** | Commit history and diff viewer for the active project |
| **Search** | Find-in-files with ripgrep or built-in fallback |
| **Log Viewer** | Live-tailing of `.log` files with level filters |
| **Renovate** | Dependency update scanner and file patcher |

### Bottom Row

| Panel | Purpose |
|---|---|
| **Prompt Input** | AI prompt template picker and terminal submitter |
| **Command Input** | Shell command template picker and terminal submitter |

---

## 12 Dockable Panels

Every panel can be shown, hidden, undocked to a floating window, or stacked as a tab inside another panel. Toggle visibility via:
- **Panels** menu (each item is a checkbox)
- `Ctrl+1` through `Ctrl+3` for the most common panels (see [[Keyboard Shortcuts]])

To **reset the layout** to the default arrangement: **View → Reset Layout to Default**. This removes `docking-layout.xml` and restores the built-in arrangement.

---

## Menu Bar

### File

| Item | Action |
|---|---|
| **Settings…** | Open the [[Settings]] dialog |
| **Import Config…** | Replace the entire config from a JSON file |
| **Export Config…** | Save the entire config to a JSON file |
| **Exit** | Close the application |

### View

| Item | Action |
|---|---|
| **System (auto)** | Follow OS dark/light mode |
| **Dark Themes ▶** | Submenu with all dark themes |
| **Light Themes ▶** | Submenu with all light themes |
| **Show Console** | Toggle Output Console visibility (checkbox) |
| **Show Explorer Tab** | Toggle File Explorer visibility (checkbox) |
| **Highlight panel on hover [alpha]** | Experimental hover highlight (checkbox) |
| **Reset Layout to Default** | Restore default docking layout |

### Panels

Checkboxes for each of the 12 panels: Commands, Git Log, Search, Renovate, Explorer, Editor, Output, Prompt Input, Command Input.

### AI Tools

Dynamically rebuilt each time it opens:

| Item | Action |
|---|---|
| **Prompt Library…** | Open the prompt template manager |
| **Command Library…** | Open the command template manager |
| **↻ Rescan** | Clear AI CLI detection cache and re-detect |
| *(separator)* | |
| **▶ {CLI Name}** (bold) | Found CLI — click to launch in terminal |
| **{CLI Name}** (disabled) | Not found — tooltip shows install hint |

### Help

| Item | Action |
|---|---|
| **Check for Updates…** | Manual update check (polls GitHub appcast.xml) |
| **About Needlecast…** | Shows version, description, license, Java version |

---

## Status Bar

The thin bar at the very bottom of the window:

| Position | Content |
|---|---|
| **Left** | Status message: `Ready`, `Running: {cmd}`, `Finished successfully (exit 0)`, `Finished with exit code N` |
| **Right** | Update badge: `⬆ {version} available` (cyan, clickable) — visible only when an update is found |

Clicking the update badge opens the GitHub releases page in your browser. Update checks run every **15 minutes** automatically (first check is 30 seconds after launch) using Sparkle4j and the project's GitHub releases appcast.

---

## Docking Behavior

| Action | Result |
|---|---|
| Drag a panel title bar | Reposition to a new location |
| Drop onto another panel's title | Stack as a tab |
| Drop onto a panel edge (blue arrow) | Split pane |
| Double-click a title bar | Detach as floating window |
| Drag back onto main window | Re-dock |

---

## Window Size

The last window width and height are persisted to `config.windowWidth` and `config.windowHeight`. The window is restored to this size on the next launch.
