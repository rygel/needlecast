# Privacy Mode

## Problem

Users need to take screenshots or share their screen without revealing project names and directory paths. Currently, the project tree, switcher dialog, explorer panel, and tooltips all display real project names and filesystem paths.

## Solution

Add a per-project `private` flag and a global `privacyModeEnabled` toggle. When privacy mode is active, all display surfaces replace private project names and paths with `••••••`.

## Data Model

Add two fields:

1. `ProjectDirectory.private: Boolean = false` — marks an individual project as private
2. `AppConfig.privacyModeEnabled: Boolean = false` — the global toggle

Both have Kotlin defaults so existing config.json files deserialize without migration.

## Marking Projects as Private

Right-click context menu on a project tree entry gets a "Mark as Private" toggle item. It shows a checkmark when the project is private. Toggling it updates the config immediately and refreshes the tree.

## Privacy Toggle

Two controls for the same `AppConfig.privacyModeEnabled` flag:

1. **Project tree header bar** — an eye/eye-slash toggle button next to Add Folder, Add Project, and Rescan
2. **View menu** — a "Privacy Mode" checkbox in `buildViewMenu()`, alongside existing toggles like "Show Console"

Both controls stay synchronized. Toggling either one saves to config and refreshes the tree immediately.

## Redaction Behavior

When `privacyModeEnabled` is true and a project has `directory.private == true`, the following display surfaces replace names and paths with `••••••`:

| Surface | File | What gets redacted |
|---------|------|--------------------|
| Project tree cell | `ProjectTreePanel.kt` | Project name (`directory.label()`) |
| Project tree tooltip | `ProjectTreePanel.kt` | Full directory path |
| Project switcher (Ctrl+P) | `ProjectSwitcherDialog.kt` | Name and path subtitle |
| Explorer panel path | `ExplorerPanel.kt` | Displayed directory path |
| Dialog titles | `ProjectTreePanel.kt` | Shell settings, script dirs dialog titles |

Non-private projects and folders are unaffected.

## Implementation Touch Points

| Change | File | Details |
|--------|------|---------|
| Add `private` field | `model/AppConfig.kt` `ProjectDirectory` | `val private: Boolean = false` |
| Add `privacyModeEnabled` | `model/AppConfig.kt` `AppConfig` | `val privacyModeEnabled: Boolean = false` |
| Add redaction helper | `model/AppConfig.kt` or new utility | `fun redactedIfPrivate(label: String, isPrivate: Boolean): String` |
| Context menu item | `ProjectTreePanel.kt` | "Mark as Private" toggle in project context menu |
| Tree header button | `ProjectTreePanel.kt` | Eye/eye-slash toggle in `btnPanel` |
| View menu item | `MainWindow.kt` | "Privacy Mode" checkbox in `buildViewMenu()` |
| Tree cell renderer | `ProjectTreePanel.kt` | Replace `entry.directory.label()` with redacted version |
| Tooltip | `ProjectTreePanel.kt` | Suppress path for private projects |
| Switcher dialog | `ProjectSwitcherDialog.kt` | Redact label and subtitle |
| Explorer panel | `ExplorerPanel.kt` | Redact displayed path |
| Dialog titles | `ProjectTreePanel.kt` | Redact path in shell settings, script dirs dialogs |

## Persistence

- `privacyModeEnabled` is saved in `config.json` and survives restarts
- Per-project `private` flag is saved in the project tree entries in `config.json`
- No config migration needed — Jackson uses Kotlin defaults for missing fields

## Out of Scope

- Folder-level privacy (only projects can be marked private)
- Redacting terminal content or file contents in the editor
- Per-session privacy (state persists across restarts)
- Exporting/importing privacy settings
