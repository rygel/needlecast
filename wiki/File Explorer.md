# File Explorer

## Overview

The **File Explorer** is a dockable panel (left column, below the Project Tree) providing a table-based directory browser for the active project. It supports file operations via context menus and drag-and-drop.

---

## Layout

| Element | Description |
|---|---|
| **Address bar** | Shows the current path; click to type a path and press Enter to navigate |
| **↑ (Up)** button | Navigate to the parent directory |
| **⟳ (Refresh)** button | Reload the current directory listing |
| **◯ (Hidden files)** toggle | Show/hide files and folders whose names start with `.` |
| **File table** | Columns: Name, Type, Size, Modified |

The Explorer opens at the active project's root when you switch projects.

---

## Navigating

| Action | Result |
|---|---|
| **Single-click a file** | Opens in [[Code Editor]] |
| **Single-click a folder** | Navigates into it |
| **Double-click** | Same as single-click |
| `Enter` | Open / navigate into selected item |
| `Backspace` | Go up one directory |
| Type a path in address bar + `Enter` | Navigate directly to that path |

---

## File Table Columns

| Column | Content |
|---|---|
| **Name** | File or folder name |
| **Type** | Folder, or file extension |
| **Size** | Human-readable (KB, MB, etc.); directories show **—** |
| **Modified** | Last-modified timestamp (`YYYY-MM-DD HH:mm`) |

Click a column header to sort; click again to reverse.

---

## Hidden Files Toggle

The **◯** button toggles visibility of dotfiles and hidden directories (`.git`, `.venv`, `.idea`, etc.). The button turns **green** (#4CAF50) when hidden files are visible.

> [!note]
> This setting is **per-session only** — it resets to hidden when you restart Needlecast. It is not persisted in the config.

---

## Right-Click Context Menu

### On a File

| Item | Action |
|---|---|
| **Open in Editor** | Opens the file in a [[Code Editor]] tab |
| **Open with {Editor}** | Launches the file in the named external editor (one item per configured editor) |
| **Rename…** | Opens a dialog with the current name pre-filled |
| **Delete** | **Permanently deletes** the file — no trash/recycle bin (confirmation dialog shown) |
| **Copy Path** | Copies the absolute path to the clipboard |

### On a Folder

| Item | Action |
|---|---|
| **Open** | Navigate into the folder |
| **New File…** | Creates an empty file and opens it in the editor |
| **New Folder…** | Creates a subdirectory |
| **Rename…** | Opens rename dialog |
| **Delete** | Recursively deletes the folder and all contents (confirmation dialog) |
| **Copy Path** | Copies the absolute path to the clipboard |

> [!warning]
> Deletion is **permanent**. There is no undo and no recycle-bin integration.

---

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Enter` | Open / navigate into selected item |
| `Backspace` | Go up one level |
| `F2` | Rename selected item |
| `Delete` | Delete selected item |
| `Ctrl+C` | Copy path of selected item |

---

## Limitations

| Feature | Status |
|---|---|
| Multi-select | Not supported (single-selection only) |
| Inline rename | Not supported (modal dialog) |
| Show in system file manager | Not implemented |
| File properties dialog | Not implemented |
| Tab completion in address bar | Not implemented |

---

## Drag and Drop

Files dragged from the OS file manager onto the Explorer panel are opened in new editor tabs. Directories dragged onto the panel are set as the explorer root.

---

## Open Editor Tabs — Tab Context Menu

Right-click an open editor tab in the Explorer/Editor area:

| Item | Action |
|---|---|
| **Close** | Close this tab |
| **Close All to the Left** | Close all tabs left of this one |
| **Close All to the Right** | Close all tabs right of this one |
| **Close All** | Close all open tabs |

---

## External Editors

The **Open with** entries are driven by [[Settings#External Editors|Settings → Editors]]. Defaults: VS Code (`code`), Zed (`zed`), IntelliJ IDEA (`idea`). Add any editor that accepts a file path as an argument.

> [!note]
> Line number is **not** passed to the external editor — only the file path. If you need to open at a specific line, use the [[Code Editor]] and then **Open with** from there is also file-path only.

---

## Related

- [[Code Editor]] — where files open when clicked
- [[Project Management]] — switching the active project changes the Explorer root
- [[Settings#External Editors|External Editors]] — configuring "Open with" targets
