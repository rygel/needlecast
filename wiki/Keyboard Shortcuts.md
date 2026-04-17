# Keyboard Shortcuts

## Global Shortcuts

Registered on the main window and active whenever the window has OS focus.

| Default | Action ID | Action |
|---|---|---|
| `F5` | `rescan` | Rescan all projects |
| `Ctrl+T` | `activate-terminal` | Open or focus the terminal for the active project |
| `Ctrl+1` | `focus-projects` | Focus the Project Tree |
| `Ctrl+2` | `focus-explorer` | Focus the File Explorer |
| `Ctrl+3` | `focus-terminal` | Focus the active terminal tab |
| `Ctrl+P` | `project-switcher` | Open the fuzzy project switcher |
| `Ctrl+Shift+F` | `find-in-files` | Show the Search panel |

All global shortcuts are **rebindable** — see [[Settings#Tab 5 — Shortcuts|Settings → Shortcuts]].

---

## Code Editor

| Shortcut | Action |
|---|---|
| `Ctrl+S` / `Cmd+S` | Save current file |
| `Ctrl+F` / `Cmd+F` | Open find bar |
| `Ctrl+H` / `Cmd+H` | Open find & replace bar |
| `Escape` | Close find bar |
| `Enter` | Next match (when find bar is open) |
| `Shift+Enter` | Previous match (when find bar is open) |
| `Ctrl+Z` / `Cmd+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Ctrl+A` | Select all |
| `Ctrl+Scroll` | Zoom font size (6–72 pt, persisted) |

---

## Terminal

| Shortcut | Action |
|---|---|
| `Ctrl+V` | Paste clipboard into terminal |
| `Ctrl+Scroll` | Zoom terminal font size (8–36 pt, persisted) |

---

## Output Console

| Shortcut | Action |
|---|---|
| `Ctrl+F` / `Cmd+F` | Open search bar |
| `Enter` | Next match (search bar open) |
| `Shift+Enter` | Previous match |
| `Escape` | Close search bar |
| `Ctrl+C` / `Cmd+C` | Copy selection |
| `Ctrl+A` / `Cmd+A` | Select all |

---

## Log Viewer

| Shortcut | Action |
|---|---|
| `Ctrl+F` | Focus search field |
| `Enter` | Next match |
| `Shift+Enter` | Previous match |
| `Escape` | Close search |

---

## File Explorer

| Shortcut | Action |
|---|---|
| `Enter` | Open or navigate into selection |
| `Backspace` | Go up one directory |
| `F2` | Rename selected item |
| `Delete` | Delete selected item |
| `Ctrl+C` | Copy path of selected item |

---

## Project Switcher Dialog

| Key | Action |
|---|---|
| `↑` / `↓` | Navigate the results list |
| `Enter` | Switch to selected project |
| `Escape` | Dismiss without switching |

---

## Search Panel

| Key | Action |
|---|---|
| `Enter` (in query field) | Run search |
| `Enter` (on result) | Open file at match |
| `Escape` | Clear / cancel search |

---

## Rebinding Shortcuts

Open **[[Settings]] → Shortcuts**. Click the input field next to any action and press your desired key combination. Click the individual **Reset** button to restore the default for that action only.

> [!note]
> Shortcuts active inside terminal tabs may conflict with the terminal emulator. `Ctrl+C` inside the terminal sends SIGINT to the running process. Design terminal-panel shortcuts with this in mind.
