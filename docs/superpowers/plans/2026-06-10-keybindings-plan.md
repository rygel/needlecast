# Keybindings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 10 new configurable keyboard shortcuts (terminal tabs, zoom, panel navigation) and centralize all shortcut definitions into a single source of truth.

**Architecture:** Create `ShortcutIds` object with all definitions. Refactor `ShortcutsSettingsPanel` to read from it. Add public methods on `ProjectTerminalPane` (tab management) and `TerminalManager` (zoom). Wire everything in `MainWindow.registerKeyboardShortcuts()`.

**Tech Stack:** Kotlin, Swing InputMap/ActionMap, AppConfig.shortcuts persistence.

---

### Task 1: Create ShortcutIds object

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ShortcutIds.kt`

- [ ] **Step 1: Create the file**

```kotlin
package io.github.rygel.needlecast.ui.settings

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

- [ ] **Step 2: Run ktlint**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ShortcutIds.kt
git commit -m "feat(shortcuts): add centralized ShortcutIds definitions"
```

---

### Task 2: Refactor ShortcutsSettingsPanel to use ShortcutIds + conflict detection

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ShortcutsSettingsPanel.kt`

- [ ] **Step 1: Replace companion object with ShortcutIds references**

Replace the entire `companion object` at the bottom of `ShortcutsSettingsPanel`:

```kotlin
    companion object {
        val defaultShortcuts: LinkedHashMap<String, String> = ShortcutIds.defaults
        val actionLabels: Map<String, String> = ShortcutIds.labels
    }
```

- [ ] **Step 2: Add conflict detection and Reset All button**

In the `init` block, after the save button definition and before the `add()` calls, add a warning label and a Reset All button. Replace the bottom panel section:

Find the `saveButton` definition. After `JOptionPane.showMessageDialog(...)`, add a conflict check. Then replace the bottom panel:

Replace:
```kotlin
        add(
            JLabel("<html><i>Click a field and press a key combination to record it. Reset restores the default.</i></html>").apply {
                border = BorderFactory.createEmptyBorder(6, 8, 2, 8)
            },
            BorderLayout.NORTH,
        )
        add(JScrollPane(grid).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(saveButton) }, BorderLayout.SOUTH)
```

With:
```kotlin
        val warningLabel =
            JLabel("").apply {
                foreground = java.awt.Color(0xCC8800)
                border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
            }

        fun checkConflicts() {
            val byKey = fields.entries.groupBy { it.value.text.trim() }
            val conflicts =
                byKey.entries
                    .filter { it.key.isNotEmpty() && it.value.size > 1 }
                    .map { e ->
                        val actions = e.value.joinToString(" and ") { actionLabels[it.key] ?: it.key }
                        "${e.key} → $actions"
                    }
            warningLabel.text =
                if (conflicts.isNotEmpty()) "<html>⚠ Conflict: ${conflicts.joinToString("; ")}</html>" else ""
        }

        add(
            JLabel("<html><i>Click a field and press a key combination to record it. Reset restores the default.</i></html>").apply {
                border = BorderFactory.createEmptyBorder(6, 8, 2, 8)
            },
            BorderLayout.NORTH,
        )
        add(JScrollPane(grid).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout()).apply {
                add(warningLabel, BorderLayout.NORTH)
                add(
                    JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                        add(
                            JButton("Reset All").apply {
                                addActionListener {
                                    fields.forEach { (id, f) -> f.text = defaultShortcuts[id] }
                                    warningLabel.text = ""
                                }
                            },
                        )
                        add(saveButton)
                    },
                    BorderLayout.SOUTH,
                )
            },
            BorderLayout.SOUTH,
        )
```

Also update the save button's action listener to call `checkConflicts()`. Add `checkConflicts()` call right after `callbacks.onShortcutsChanged()`:

```kotlin
                    callbacks.onShortcutsChanged()
                    checkConflicts()
```

- [ ] **Step 3: Run ktlint**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: same 4 pre-existing failures

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ShortcutsSettingsPanel.kt
git commit -m "refactor(shortcuts): use centralized ShortcutIds + add conflict detection"
```

---

### Task 3: Add public methods on ProjectTerminalPane and TerminalManager

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalManager.kt`

- [ ] **Step 1: Make addTerminalTab internal and add tab navigation methods on ProjectTerminalPane**

In the `ProjectTerminalPane` inner class, find the private `addTerminalTab()` method and change its visibility to `internal`. Then add three new public methods right after the `closeTab` method:

```kotlin
    fun closeActiveTab() {
        val idx = tabs.selectedIndex
        if (idx < 0 || idx >= realTabCount) return
        val terminal = tabs.getComponentAt(idx) as? TerminalPanel ?: return
        closeTab(terminal)
    }

    fun nextTab() {
        if (realTabCount <= 1) return
        tabs.selectedIndex = (tabs.selectedIndex + 1) % realTabCount
    }

    fun prevTab() {
        if (realTabCount <= 1) return
        tabs.selectedIndex = (tabs.selectedIndex - 1 + realTabCount) % realTabCount
    }
```

- [ ] **Step 2: Add activePane property and zoom methods on TerminalManager**

In `TerminalManager` class, add after the `applyFontFamily` method:

```kotlin
    val activePane: ProjectTerminalPane? get() = terminals[shownKey]

    fun zoomIn() {
        activePane?.zoomActive(+1)
    }

    fun zoomOut() {
        activePane?.zoomActive(-1)
    }

    fun zoomReset() {
        activePane?.zoomReset()
    }
```

Then in `ProjectTerminalPane`, add:

```kotlin
    fun zoomActive(delta: Int) {
        val idx = tabs.selectedIndex
        if (idx < 0 || idx >= realTabCount) return
        val terminal = tabs.getComponentAt(idx) as? TerminalPanel ?: return
        terminal.changeFontSize(delta)
    }

    fun zoomReset() {
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyFontSize(13)
        }
        onFontSizeChanged?.invoke(13)
    }
```

- [ ] **Step 3: Run ktlint and compile**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
mvn compile -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalManager.kt
git commit -m "feat(terminal): add tab navigation, zoom, and activePane accessors"
```

---

### Task 4: Add DockingController.toggleProjectTree

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DockingController.kt`

- [ ] **Step 1: Add toggleProjectTree method**

After the existing `toggleExplorer` method, add:

```kotlin
    fun toggleProjectTree() {
        if (Docking.isDocked(registry.projectTreeDockable)) {
            Docking.undock(registry.projectTreeDockable)
        } else {
            val anchor =
                when {
                    Docking.isDocked(registry.terminalDockable) -> registry.terminalDockable
                    frame != null -> null
                    else -> return
                }
            if (anchor != null) {
                Docking.dock(registry.projectTreeDockable, anchor, DockingRegion.WEST, 0.15)
            } else {
                val f = frame ?: return
                Docking.dock(registry.projectTreeDockable, f, DockingRegion.WEST)
            }
        }
    }
```

- [ ] **Step 2: Run ktlint and compile**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
mvn compile -pl needlecast-desktop -q
```

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DockingController.kt
git commit -m "feat(docking): add toggleProjectTree for sidebar toggle shortcut"
```

---

### Task 5: Wire all shortcuts in MainWindow

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

- [ ] **Step 1: Add new bind() calls in registerKeyboardShortcuts()**

In `registerKeyboardShortcuts()`, after the existing `bind("ctrl P", ...)` line, add:

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
```

Keep the existing `bind("ctrl shift F", "find-in-files") { showSearchPanel() }` line — it's already there.

- [ ] **Step 2: Run ktlint and compile**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
mvn compile -pl needlecast-desktop -q
```

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat(shortcuts): wire 10 new configurable keyboard shortcuts"
```

---

### Task 6: Full test suite, ktlint, merge to develop

- [ ] **Step 1: Run ktlint**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 2: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: 4 pre-existing failures, all new tests pass

- [ ] **Step 3: Stage any remaining changes and push**

```bash
git add -A
git commit -m "style: ktlint formatting" # only if needed
```

- [ ] **Step 4: Create branch and merge to develop**

```bash
git checkout -b feat/cycle-17-keybindings
git push -u origin feat/cycle-17-keybindings
git checkout develop
git merge --no-ff feat/cycle-17-keybindings -m "Cycle 17: Comprehensive configurable keybindings (16 shortcuts)"
git push origin develop
```
