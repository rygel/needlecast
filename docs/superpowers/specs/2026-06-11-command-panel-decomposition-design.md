# Cycle 19: CommandPanel Decomposition

**Date:** 2026-06-11
**Status:** Approved
**Target:** Reduce CommandPanel from 731 lines to ~400 lines by extracting four focused modules.

## Problem

CommandPanel.kt (731 lines) mixes command execution, override persistence, cell rendering, tray notifications, and an edit dialog. It has zero test coverage despite containing complex stateful logic (command overrides, history recording, queue management).

## Approach

Extract four files from CommandPanel, following the same decomposition pattern used in Cycles 13 (ProjectTreePanel) and 18 (ExplorerPanel). Each extracted unit is independently testable.

### 1. `CommandCellRenderers.kt` (~120 lines)

Cell renderers and text utilities for command and history lists.

**Extracted from CommandPanel:**
- `CommandCellRenderer` — promoted from private class to top-level
- `HistoryCellRenderer` — promoted from private class to top-level
- `toHtmlLabel()` — promoted from private to internal utility
- `timeFmt` — `SimpleDateFormat` constant

**Testable:** Renderers are Swing components but produce deterministic output based on input state. Can test that given a `CommandDescriptor` with a specific build tool, the renderer produces correct badge text and colors.

### 2. `CommandOverrideManager.kt` (~100 lines)

Command override lookup, editing, and reset logic.

**Extracted from CommandPanel:**
- `applyCommandOverrides()` — already `internal` pure function
- `findActiveOverride()` — looks up override for a command in current config
- `editSelectedCommand()` — applies edit to command model + persists override in config
- `resetSelectedCommand()` — restores original command + removes override from config

**Interface:**
```kotlin
class CommandOverrideManager(
    private val ctx: AppContext,
    private val currentProjectPath: () -> String?,
    private val updateModel: (Int, CommandDescriptor) -> Unit,
    private val selectedIndex: () -> Int,
)
```

**Testable:** `applyCommandOverrides` is already a pure function. Override persistence can be tested against `AppConfig` directly — apply edit, verify config contains the override; apply reset, verify override removed.

### 3. `TrayNotifier.kt` (~35 lines)

System tray notification utility.

**Extracted from CommandPanel:**
- `TrayNotifier` object — promoted from private to internal

**Testable:** Verify `notify()` no-ops gracefully when `SystemTray.isSupported()` returns false (headless CI). The lazy initialization can be verified.

### 4. `EditCommandDialog.kt` (~80 lines)

Modal dialog for editing command label and argv.

**Extracted from CommandPanel:**
- `EditCommandDialog` class — promoted from private to top-level

**Testable:** The `onOk()` validation logic (empty label, empty command) can be tested by constructing the dialog and invoking the method. The dialog's `result` property is the testable output.

### What Stays in CommandPanel (~400 lines)

- List models (command, history, queue) and JList setup
- Button bar construction and toolbar
- `loadProject()` — project loading + readme preview
- `executeCommand()` — process execution + output streaming to console
- Queue management (`enqueueSelected`, `clearQueue`, `drainQueue`)
- History recording (`recordHistory`)
- Cancel logic (`cancelRunning`)
- Mouse listeners and selection handlers
- Layout assembly

**Delegates to:**
- `CommandCellRenderers` for list cell rendering
- `CommandOverrideManager` for override persistence
- `TrayNotifier` for completion notifications
- `EditCommandDialog` for command editing

## New Tests

### `CommandOverrideManagerTest.kt` (~12 tests)
- `applyCommandOverrides` with empty overrides returns commands unchanged
- `applyCommandOverrides` applies matching override by originalArgv
- `applyCommandOverrides` ignores unmatched overrides
- `applyCommandOverrides` with multiple overrides applies all
- `applyCommandOverrides` last-write-wins for duplicate originalArgv
- Edit command creates override in config
- Edit already-overridden command updates existing override (doesn't stack)
- Reset command removes override from config
- Reset last override removes project key from overrides map
- Find active override returns matching override
- Find active override returns null when no overrides exist
- Find active override matches by originalArgv fallback

### `EditCommandDialogTest.kt` (~5 tests)
- Valid input produces non-null result with correct label and argv
- Empty label rejects with validation message
- Empty command rejects with validation message
- Whitespace-only input trimmed and rejected
- Label and argv are trimmed in result

### `TrayNotifierTest.kt` (~3 tests)
- `notify` does not throw when tray is unavailable (headless)
- `notify` does not throw with null caption
- Multiple calls to `notify` do not throw

**Total: ~20 new tests, bringing total from 586 to ~606.**

## File Layout After Decomposition

```
ui/
├── CommandCellRenderers.kt      (NEW — ~120 lines)
├── CommandOverrideManager.kt    (NEW — ~100 lines)
├── CommandPanel.kt              (reduced: 731 → ~400 lines)
├── EditCommandDialog.kt         (NEW — ~80 lines)
├── TrayNotifier.kt              (NEW — ~35 lines)
└── ... (other files unchanged)
```

## Out of Scope

- Queue management extraction (tightly coupled to `executeCommand`)
- History management extraction (tightly coupled to model + config)
- Readme preview extraction (self-contained SwingWorker, low complexity)
- Refactoring `executeCommand` itself (complex callback chain, high risk)

## Risks

- **EditCommandDialog promotion:** The dialog currently accesses `CommandDescriptor` directly. As a top-level class it needs the same import. Low risk — straightforward move.
- **CommandOverrideManager callback:** Needs `currentProjectPath` and `updateModel` callbacks to avoid coupling back to CommandPanel. Same pattern as ExplorerFileOps `ExplorerCallbacks`. Low risk.
- **Cell renderer access:** CommandPanel references `CommandCellRenderer()` and `HistoryCellRenderer()` in list setup. After extraction, these are top-level in the same package. Low risk — no import changes needed.
