# Cycle 21: TerminalManager Decomposition — ProjectTerminalPane Extraction

## Context

TerminalManager.kt (798 lines) contains 4 classes: `TerminalManager` (card-layout container, 334 lines), `ProjectTerminalPane` (tabbed terminal manager, 357 lines), `TerminalTabHeader` (tab header widget, 45 lines), and `tryRun` utility (7 lines). This cycle extracts `ProjectTerminalPane` + `TerminalTabHeader` into their own file, adding test coverage for the tab lifecycle.

## Scope

Single extraction:
1. **ProjectTerminalPane + TerminalTabHeader** → new file `ui/terminal/ProjectTerminalPane.kt`

## Extraction: ProjectTerminalPane

**File:** `ui/terminal/ProjectTerminalPane.kt` (~420 lines)

### What moves

| Item | Lines | Destination |
|------|-------|-------------|
| `ProjectTerminalPane` class | 383-739 | New file |
| `TerminalTabHeader` class | 742-786 | New file (private class) |
| `CARD_EMPTY` constant | (stays in TerminalManager) | — |
| `tryRun` utility | 792-798 | Move to new file (used by ProjectTerminalPane) |
| `ENCODINGS` constant | 788-790 | Move to new file (used by ProjectTerminalPane encoding combo) |

### Visibility

- `ProjectTerminalPane` stays `internal class`
- `TerminalTabHeader` stays `private class`
- `tryRun` stays top-level `internal fun`
- `ENCODINGS` stays top-level `internal val`

### Constructor

`ProjectTerminalPane` already takes these parameters (no change needed):
- `ctx: AppContext`
- `directory: ProjectDirectory`
- `onActivateRequested: () -> Unit`
- `onStatusChanged: (AgentStatus) -> Unit`
- `onFontSizeChanged: (Float) -> Unit`

These are the same callbacks it already receives from `TerminalManager.activateProject()`. No interface change required.

### What stays in TerminalManager

- Card-layout container (`JPanel(CardLayout())`)
- `terminals` map (`MutableMap<String, ProjectTerminalPane>`)
- `showProject()` / `activateProject()` / `deactivateProject()` / `deactivate()`
- `onHookEvent()` routing
- `setUseHooksForStatus()`
- Placeholder panel (`buildPlaceholder()`, `updatePlaceholderContent()`)
- Shell picker menu (`showShellMenu()`)
- Theme/font/encoding delegation methods (`applyTheme`, `applyTerminalColors`, `applyFontSize`, etc.)

## Impact

| File | Before | After |
|------|--------|-------|
| TerminalManager.kt | 798 | ~440 |
| ProjectTerminalPane.kt | — | ~420 |

## Testing

### ProjectTerminalPaneTest (~6-8 tests)

Tests for tab lifecycle and status aggregation. Since `ProjectTerminalPane` extends `JPanel` and creates `JTabbedPane`, tests need a headful environment or careful mocking.

Tests:
1. `addTerminalTab creates a new tab` — verify tab count increases
2. `closeTab removes and disposes tab` — verify tab count decreases
3. `closeActiveTab closes the selected tab` — select tab 1, close active, verify correct tab removed
4. `status aggregation returns highest priority status` — one tab THINKING, another WAITING → aggregated THINKING
5. `forceStatusOnClaudeTabs only affects claude sessions` — set status on claude tab, verify non-claude tab unchanged
6. `zoomActive delegates to active terminal panel` — verify font size change propagates
7. `nextTab cycles tab selection` — 3 tabs, at last, next wraps to first

### Headful test handling

Tests that construct `ProjectTerminalPane` need Swing/AWT. Use `@EnabledIf("isHeadful")` annotation (same pattern as ExplorerFileOpsTest, EditCommandDialogTest) to skip on headless CI.

## Files changed

| File | Action |
|------|--------|
| `ui/terminal/ProjectTerminalPane.kt` | New (extracted from TerminalManager) |
| `ui/terminal/TerminalManager.kt` | Modified (remove extracted classes) |
| `test/.../ProjectTerminalPaneTest.kt` | New |

## Constraints

- No changes to `TerminalPanel.kt` or other terminal files
- No interface changes to `ProjectTerminalPane` constructor
- No comments in code (project convention)
- Headful tests use `@EnabledIf("isHeadful")` pattern
