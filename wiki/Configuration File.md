# Configuration File

## Location

All application settings, projects, shortcuts, and templates are stored in a single JSON file:

```
~/.needlecast/config.json
```

The docking panel layout is stored separately:

```
~/.needlecast/docking-layout.xml
```

The application log is at:

```
~/.needlecast/needlecast.log
```

---

## Backup and Safety

**Automatic backups:** Before each save, Needlecast rotates up to **5 numbered backups**:

```
config.json.bak.1
config.json.bak.2
config.json.bak.3
config.json.bak.4
config.json.bak.5
```

**Atomic writes:** Config is written to a temp file then atomically moved into place, preventing corruption from interrupted writes.

**Corrupt file handling:** If the JSON cannot be parsed on startup, the corrupt file is renamed to `config.json.corrupt.{timestamp}` and Needlecast starts with default settings.

> [!warning]
> Do not edit `config.json` while Needlecast is running. The application writes the file on exit and will overwrite your changes. Quit Needlecast first, edit, then relaunch.

---

## Schema Version

The config includes a `configVersion` field (currently **3**). When Needlecast loads a config from an older version, it runs `ConfigMigrator.migrate()` to upgrade the schema automatically before using the data.

---

## Complete Field Reference

| Field | Type | Default | Description |
|---|---|---|---|
| `configVersion` | Int | 3 | Schema version — used for migrations |
| `windowWidth` | Int | 1200 | Persisted window width |
| `windowHeight` | Int | 800 | Persisted window height |
| `theme` | String | `"dark-purple"` | Active theme ID |
| `language` | String | `"en"` | BCP 47 locale tag (`"en"`, `"de"`, etc.) |
| `projectTree` | List | `[]` | Nested folder/project structure |
| `commandHistory` | Map | `{}` | Per-project command history (max 20 per project) |
| `shortcuts` | Map | `{}` | Overridden keyboard shortcuts (only non-defaults stored) |
| `promptLibrary` | List | 27 built-ins | AI prompt templates |
| `commandLibrary` | List | 34 built-ins | Shell command templates |
| `externalEditors` | List | VS Code, Zed, IntelliJ | External editor definitions |
| `aiCliEnabled` | Map | `{}` | Per-CLI enable flags (absent = enabled) |
| `customAiClis` | List | `[]` | User-defined AI CLI entries |
| `showConsole` | Boolean | `true` | Output Console visible |
| `showExplorer` | Boolean | `true` | File Explorer visible |
| `tabsOnTop` | Boolean | `true` | Panel tab strip position |
| `panelHoverHighlight` | Boolean | `false` | Panel hover highlight (experimental) |
| `dockingActiveHighlight` | Boolean | `false` | Active panel border highlight (experimental) |
| `treeClickTraceEnabled` | Boolean | `false` | Debug: log project tree clicks |
| `edtStallTraceEnabled` | Boolean | `false` | Debug: log EDT stalls > 200 ms |
| `defaultShell` | String? | `null` | Global default shell (`null` = OS default) |
| `syntaxTheme` | String | `"auto"` | Editor syntax highlight theme |
| `terminalBackground` | String? | `null` | Terminal background color (hex, e.g. `"#1E1E1E"`) |
| `terminalForeground` | String? | `null` | Terminal foreground color (hex) |
| `terminalFontSize` | Int | 13 | Terminal font size (8–36 pt) |
| `terminalFontFamily` | String? | `null` | Terminal font family (`null` = auto) |
| `uiFontFamily` | String? | `null` | UI font family override |
| `uiFontSize` | Int? | `null` | UI font size override |
| `editorFontFamily` | String? | `null` | Editor font family (`null` = auto monospace) |
| `editorFontSize` | Int | 12 | Editor font size (6–72 pt) |
| `claudeHooksEnabled` | Boolean | `false` | Install Claude Code hooks in `~/.claude/settings.json` |

### Deprecated Fields (migrated automatically)

| Field | Replaced by |
|---|---|
| `groups` | `projectTree` |
| `lastSelectedGroupId` | *(removed)* |

---

## Project Tree Structure

The `projectTree` field is a list of `ProjectTreeEntry` objects (a sealed type):

**Folder:**
```json
{
  "type": "folder",
  "id": "uuid",
  "name": "Work",
  "color": "#4CAF50",
  "children": [ ... ]
}
```

**Project:**
```json
{
  "type": "project",
  "id": "uuid",
  "directory": {
    "path": "/home/user/projects/myapp",
    "displayName": "My App",
    "color": "#2196F3",
    "env": { "DATABASE_URL": "postgres://localhost/dev" },
    "shellExecutable": "zsh",
    "startupCommand": "conda activate ml"
  },
  "tags": ["backend", "java"]
}
```

---

## Import / Export

Use **File → Export Config** to save a copy of the current config as JSON. Use **File → Import Config** to replace the entire config from a JSON file.

Import runs schema migration and replaces **all** settings — there is no selective merge. See [[Settings#Import / Export Config]].

---

## Related

- [[Settings]] — the UI for editing config values
- [[Keyboard Shortcuts]] — how shortcut overrides are stored
- [[Prompt Library]] — how templates are stored
