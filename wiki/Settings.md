# Settings

## Opening Settings

- Click the **gear icon (⚙)** in the top-right of the menu bar
- Go to **File → Settings…**
- Default shortcut (rebindable in the Shortcuts tab)

The Settings dialog has **7 tabs**.

---

## Tab 1 — Editors

Manage the list of external editors shown in the **Open with** context menu in the [[Code Editor]] and [[File Explorer]].

| Element | Description |
|---|---|
| **Editor list** | Shows name and executable for each configured editor |
| **Add** button | Opens a small form: Name field + Executable field |
| **Remove** button | Removes the selected editor (disabled if nothing is selected) |

**Default editors:** VS Code (`code`), Zed (`zed`), IntelliJ IDEA (`idea`).

The external editor launch passes the **file path only** — no line number is included.

---

## Tab 2 — AI Tools

Configure which AI CLI tools Needlecast recognizes and integrates with.

### Built-in CLI Toggles

A list of checkboxes for the 17 auto-detected CLIs. Each row shows the CLI name and its command in monospace gray. Unchecking hides the CLI from the **AI Tools** menu.

### Custom CLIs

| Element | Description |
|---|---|
| **Add Custom CLI** button | Opens a form: Name, Command (executable), Description |
| **Remove Custom CLI** dropdown | Select which custom CLI to delete |

Custom CLIs appear in the AI Tools menu alongside built-in ones and follow the same enable/disable toggle logic.

---

## Tab 3 — Renovate

| Element | Description |
|---|---|
| **Status** | ✓ `v{version}` (green) if installed, ✗ "Not found on PATH" (red) if not |
| **Install buttons** | OS-appropriate install methods (see [[Dependency Updates#Prerequisites]]) |
| **Recheck** button | Re-runs PATH detection |
| **Output pane** | Shows live output from install commands |

---

## Tab 4 — APM

APM (Agent Package Manager by Microsoft) manages AI skills, prompts, plugins, and MCP servers via an `apm.yml` file.

| Element | Description |
|---|---|
| **Status** | ✓ `v{version}` (green) if installed, ✗ "Not found on PATH" (red) if not |
| **Install buttons** | OS-appropriate methods: `curl` (Unix), PowerShell `irm` (Windows), Homebrew, pip, Scoop |
| **Recheck** button | Re-runs detection |
| **Output pane** | Live install output |
| **Info text** | Describes what APM does |

---

## Tab 5 — Shortcuts

A grid listing all rebindable actions.

| Column | Content |
|---|---|
| **Action label** | Human-readable name |
| **Input field** | Current binding — click and press a new key combo to record |
| **Reset** button | Restores the default binding for that action |

### All Bindable Actions

| Action | Default |
|---|---|
| Rescan projects | `F5` |
| Activate terminal | `Ctrl+T` |
| Focus project list | `Ctrl+1` |
| Focus file explorer | `Ctrl+2` |
| Focus terminal | `Ctrl+3` |
| Global project switcher | `Ctrl+P` |
| Find in files | `Ctrl+Shift+F` |

Changes are stored in `AppConfig.shortcuts` (only overridden keys are stored — defaults are omitted).

---

## Tab 6 — Language

| Element | Description |
|---|---|
| **Language dropdown** | Lists supported languages with native names |
| **Apply** button | Switches the UI locale immediately |

**Supported languages:** English, Deutsch (German). (Additional languages may be added in future releases.)

The locale is stored as a BCP 47 tag (e.g. `en`, `de`) in `AppConfig.language`.

---

## Tab 7 — Layout & Terminal

### Layout Section

| Setting | Config key | Description |
|---|---|---|
| Show panel tabs at the top | `tabsOnTop` | Tab strip position (top or bottom) |
| Highlight active docking panel border [alpha] | `dockingActiveHighlight` | Adds a colored border to the focused panel (experimental) |

### Diagnostics Section

| Setting | Config key | Description |
|---|---|---|
| Enable project tree click tracing | `treeClickTraceEnabled` | Logs click sequences to `~/.needlecast/needlecast.log` |
| Enable EDT stall monitor | `edtStallTraceEnabled` | Logs EDT stalls > 200 ms with stack traces |

> [!note]
> Diagnostic settings write to `~/.needlecast/needlecast.log`. Enable only while diagnosing lag or click issues.

### Fonts Section

**UI font:**
- Family dropdown (System default + all installed fonts)
- Size spinner (9–32 pt, default from UIManager)
- Reset button (clears to theme default)
- Saved to `uiFontFamily`, `uiFontSize`

**Editor font:**
- Family dropdown (Auto monospace + detected monospace fonts)
- Size spinner (6–72 pt, default 12)
- Saved to `editorFontFamily`, `editorFontSize`

**Terminal font:**
- Family dropdown (Auto monospace + detected monospace fonts)
- Saved to `terminalFontFamily` (size is in the Terminal section below)

### Terminal Section

| Setting | Range | Config key |
|---|---|---|
| Font size | 8–36 pt (default 13) | `terminalFontSize` |
| Default shell | OS default / detected / manual | `defaultShell` |
| Terminal foreground color | Hex color swatch | `terminalForeground` |
| Terminal background color | Hex color swatch | `terminalBackground` |

The **Default shell** dropdown auto-detects shells installed on your system. Choose **Manual entry…** to type a custom shell name. Takes effect on the next terminal activation.

Click a **color swatch** to open the OS color picker. **Reset** clears both custom colors and reverts to theme-derived defaults.

### Syntax Theme Section

| Key | Description |
|---|---|
| `auto` | Follows app dark/light mode |
| `monokai` | Monokai (dark) |
| `dark` | Generic dark |
| `druid` | Druid (dark) |
| `idea` | IntelliJ IDEA (light) |
| `eclipse` | Eclipse (light) |
| `default` | Default (light) |
| `default-alt` | Default Alt (light) |
| `vs` | Visual Studio (light) |

Saved to `syntaxTheme`.

---

## Themes

Select a theme from **View → Dark Themes** or **View → Light Themes**. There are **27 built-in themes**:

### Dark Themes
Dracula, Nord, One Dark, Gruvbox Dark, Cobalt 2, Carbon, **Dark Purple** *(default)*, Spacegray, Hiberbee Dark, Solarized Dark, Xcode Dark, Gradiant Deep Ocean, Atom One Dark, Moonlight, Night Owl, Material Deep Ocean, Material Oceanic, Material Palenight

### Light Themes
Arc, Arc Orange, Cyan Light, Solarized Light, GitHub Light, Atom One Light, Light Owl

### Catppuccin
Catppuccin Mocha *(dark)*, Catppuccin Macchiato *(dark)*, Catppuccin Frappé *(dark)*, Catppuccin Latte *(light)*

**System (auto)** — detected via **View → System (auto)**; follows OS dark/light mode.

---

## Import / Export Config

| Action | Location | Behavior |
|---|---|---|
| **Export Config** | File → Export Config… | Saves entire `AppConfig` as pretty-printed JSON to a file you choose |
| **Import Config** | File → Import Config… | Reads a JSON config file, runs schema migration, then **replaces** the entire current config |

> [!warning]
> Import is a full **replace** — there is no selective merge. All current settings, projects, shortcuts, and templates are overwritten with the imported file. A restart is recommended after import to apply all changes.

---

## Configuration File

**Location:** `~/.needlecast/config.json`

**Backup rotation:** Before each save, up to **5 numbered backups** are kept (`config.json.bak.1` through `.bak.5`).

**Corrupt file handling:** If the JSON cannot be parsed on startup, the corrupt file is moved to `config.json.corrupt.{timestamp}` and Needlecast starts with default settings.

**Docking layout:** Stored separately in `~/.needlecast/docking-layout.xml`.

**Application log:** `~/.needlecast/needlecast.log` (10 MB rotation, 5 archives).

> [!warning]
> Do not edit `config.json` while Needlecast is running. Changes will be overwritten when the app exits. Quit first, edit, then relaunch.

---

## Claude Code Hooks

When **Advanced → Claude Code hooks** is enabled, Needlecast installs hooks into `~/.claude/settings.json` on startup and removes them on exit. See [[Terminal#Claude Code sessions]] for details.

---

## Related

- [[Keyboard Shortcuts]] — full default table and rebinding instructions
- [[Terminal]] — terminal font, colors, shell configuration
- [[Code Editor]] — editor font, syntax theme
- [[Dependency Updates]] — Renovate installation
- [[Prompt Library]] — AI CLI configuration
