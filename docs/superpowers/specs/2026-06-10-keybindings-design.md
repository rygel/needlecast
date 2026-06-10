# Comprehensive Configurable Keybindings

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add comprehensive configurable keyboard shortcuts for terminal tab management, zoom, panel navigation, and make all shortcuts user-configurable through the existing Settings UI.

**Architecture:** Extend the existing `MainWindow.bind()` + `ShortcutsSettingsPanel` system. Centralize shortcut definitions into a `ShortcutIds` object so both consumers share a single source of truth. Add public methods on `ProjectTerminalPane` and `DockingController` for the new actions.

**Tech Stack:** Kotlin, Swing InputMap/ActionMap, existing `AppConfig.shortcuts: Map<String, String>` persistence.

---

## Shortcut Definitions

### New Global Shortcuts

| Action ID | Default | Description | Target Method |
|-----------|---------|-------------|---------------|
| `new-terminal-tab` | `ctrl shift T` | Open new terminal tab | `ProjectTerminalPane.addTerminalTab()` |
| `close-terminal-tab` | `ctrl W` | Close active terminal tab | `ProjectTerminalPane.closeActiveTab()` |
| `next-terminal-tab` | `ctrl TAB` | Switch to next terminal tab | `ProjectTerminalPane.nextTab()` |
| `prev-terminal-tab` | `ctrl shift TAB` | Switch to previous terminal tab | `ProjectTerminalPane.prevTab()` |
| `zoom-in` | `ctrl EQUALS` | Increase terminal font | `TerminalManager.zoomIn()` |
| `zoom-out` | `ctrl MINUS` | Decrease terminal font | `TerminalManager.zoomOut()` |
| `zoom-reset` | `ctrl 0` | Reset terminal font to default (13) | `TerminalManager.zoomReset()` |
| `toggle-sidebar` | `ctrl B` | Toggle project tree panel | `DockingController.toggleProjectTree()` |
| `focus-commands` | `ctrl 4` | Focus commands panel | `DockingController.selectTab("commands")` |
| `focus-search` | `ctrl shift F` | Focus find-in-files panel | `DockingController.selectTab("search")` + focus |

### Existing Shortcuts (already configurable, keep as-is)

| Action ID | Default |
|-----------|---------|
| `rescan` | `F5` |
| `activate-terminal` | `ctrl T` |
| `focus-projects` | `ctrl 1` |
| `focus-explorer` | `ctrl 2` |
| `focus-terminal` | `ctrl 3` |
| `project-switcher` | `ctrl P` |

---

## Implementation Details

### 1. ShortcutIds object

Create `ui/settings/ShortcutIds.kt`:

```kotlin
object ShortcutIds {
    data class Definition(val id: String, val defaultKey: String, val label: String)

    val all: List<Definition> = listOf(
        Definition("rescan", "F5", "Rescan projects"),
        Definition("activate-terminal", "ctrl T", "Activate terminal"),
        Definition("focus-projects", "ctrl 1", "Focus project list"),
        Definition("focus-explorer", "ctrl 2", "Focus file explorer"),
        Definition("focus-terminal", "ctrl 3", "Focus terminal"),
        Definition("focus-commands", "ctrl 4", "Focus commands"),
        Definition("project-switcher", "ctrl P", "Global project switcher"),
        Definition("focus-search", "ctrl shift F", "Find in files"),
        Definition("new-terminal-tab", "ctrl shift T", "New terminal tab"),
        Definition("close-terminal-tab", "ctrl W", "Close terminal tab"),
        Definition("next-terminal-tab", "ctrl TAB", "Next terminal tab"),
        Definition("prev-terminal-tab", "ctrl shift TAB", "Previous terminal tab"),
        Definition("zoom-in", "ctrl EQUALS", "Zoom terminal in"),
        Definition("zoom-out", "ctrl MINUS", "Zoom terminal out"),
        Definition("zoom-reset", "ctrl 0", "Reset terminal zoom"),
        Definition("toggle-sidebar", "ctrl B", "Toggle sidebar"),
    )

    val defaults: LinkedHashMap<String, String> =
        linkedMapOf(*all.map { it.id to it.defaultKey }.toTypedArray())

    val labels: Map<String, String> = all.associate { it.id to it.label }
}
```

### 2. ShortcutsSettingsPanel

Replace the companion object's `defaultShortcuts` and `actionLabels` with references to `ShortcutIds.defaults` and `ShortcutIds.labels`. Add conflict detection: when saving, if two actions share the same key, show a warning label (yellow text below the save button). Don't block saving — same key can be valid in different focus contexts.

Also add a "Reset All" button next to "Save Shortcuts" that resets all fields to `ShortcutIds.defaults`.

### 3. ProjectTerminalPane new public methods

In `TerminalManager.kt` inner class `ProjectTerminalPane`:

- `fun addTerminalTab()` — already exists (private). Make it `internal` so `MainWindow` can call it via `TerminalManager`.
- `fun closeActiveTab()` — find active tab index, call existing `closeTab(terminal)` if `realTabCount > 1`.
- `fun nextTab()` — `tabs.selectedIndex = (tabs.selectedIndex + 1) % realTabCount`.
- `fun prevTab()` — `tabs.selectedIndex = (tabs.selectedIndex - 1 + realTabCount) % realTabCount`.

### 4. TerminalManager zoom methods

In `TerminalManager.kt`:

- `fun zoomIn()` — find active pane, call `activePane.changeFontSize(+1)`.
- `fun zoomOut()` — find active pane, call `activePane.changeFontSize(-1)`.
- `fun zoomReset()` — find active pane, call `activePane.applyFontSize(13)`, persist via callback.

To find the active pane: `terminals[shownKey]` gives the current `ProjectTerminalPane`.

### 5. DockingController.toggleProjectTree

Add `fun toggleProjectTree()` — if `isDocked("project-tree")`, undock it; otherwise dock it back to its default position (WEST of terminal, 0.15 proportion).

### 6. MainWindow.registerKeyboardShortcuts

Add `bind()` calls for all new shortcuts. Wire to the methods above:

```kotlin
bind("ctrl shift T", "new-terminal-tab") { terminalPanel.activePane?.addTerminalTab() }
bind("ctrl W", "close-terminal-tab") { terminalPanel.activePane?.closeActiveTab() }
bind("ctrl TAB", "next-terminal-tab") { terminalPanel.activePane?.nextTab() }
bind("ctrl shift TAB", "prev-terminal-tab") { terminalPanel.activePane?.prevTab() }
bind("ctrl EQUALS", "zoom-in") { terminalPanel.zoomIn() }
bind("ctrl MINUS", "zoom-out") { terminalPanel.zoomOut() }
bind("ctrl 0", "zoom-reset") { terminalPanel.zoomReset() }
bind("ctrl B", "toggle-sidebar") { docking.toggleProjectTree() }
bind("ctrl 4", "focus-commands") { docking.selectTab("commands") }
bind("ctrl shift F", "focus-search") { showSearchPanel() }
```

`TerminalManager.activePane` — add a public property: `val activePane: ProjectTerminalPane? get() = terminals[shownKey]`.

### 7. Ctrl+Tab handling

`Ctrl+TAB` on a `JTabbedPane` is normally consumed by Swing's built-in focus traversal. To override this, the `bind()` must use `WHEN_IN_FOCUSED_WINDOW` (which it already does via `root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)`). This has higher priority than focus traversal. If Swing still eats it, register `Tabs` as a custom focus traversal policy or disable focus traversal keys on the terminal tabbed pane.

---

## Conflict Detection

In `ShortcutsSettingsPanel.saveButton.addActionListener`:

1. Collect all field values into a `Map<String, String>` (actionId → key)
2. Group by key: `entries.groupBy { it.value }`
3. Find groups with size > 1
4. If conflicts exist, show a yellow warning label: "Warning: <key> is bound to <action1> and <action2>"
5. Still allow saving — the user may intentionally use the same key for different contexts

---

## Out of Scope

- Making panel-local shortcuts configurable (editor Ctrl+S/F/H, console Ctrl+F, etc.) — deferred to a future cycle. The complexity of per-component configurable shortcuts isn't justified yet.
- Command palette / search commands shortcut — deferred.
- Maximize/restore active panel — deferred.
