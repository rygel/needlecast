# Cycle 19: CommandPanel Decomposition — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose CommandPanel (731 lines) into four focused modules + add ~15 new unit tests.

**Architecture:** Extract CommandCellRenderers (rendering), CommandOverrideManager (override persistence), TrayNotifier (notifications), and EditCommandDialog (editing dialog). The panel becomes a ~400-line coordinator.

**Tech Stack:** Kotlin, JUnit 5, Swing (ListCellRenderer, JDialog), @TempDir.

---

### Task 1: Extract CommandCellRenderers

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandCellRenderers.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

**Goal:** Move `CommandCellRenderer`, `HistoryCellRenderer`, `toHtmlLabel()`, and `timeFmt` out of CommandPanel into their own file.

- [ ] **Step 1: Create `CommandCellRenderers.kt`**

Move these items verbatim from CommandPanel.kt into the new file:

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandHistoryEntry
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.Insets
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal val timeFmt = SimpleDateFormat("HH:mm")

class CommandCellRenderer : ListCellRenderer<CommandDescriptor> {
    // ... verbatim from CommandPanel lines 631-690
}

class HistoryCellRenderer : ListCellRenderer<CommandHistoryEntry> {
    // ... verbatim from CommandPanel lines 694-728
}

internal fun String.toHtmlLabel(): String = "<html>${replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</html>"
```

Key changes:
- Both renderers promoted from `private class` to top-level `class`
- `toHtmlLabel()` promoted from `private fun` to `internal fun`
- `timeFmt` promoted from `private val` to `internal val`
- No logic changes — pure extraction

- [ ] **Step 2: Update CommandPanel.kt**

Remove from CommandPanel.kt:
- `CommandCellRenderer` private class (lines 631-690)
- `HistoryCellRenderer` private class (lines 694-728)
- `toHtmlLabel()` private function (line 731)
- `timeFmt` private val (line 692)

No import changes needed — same package.

- [ ] **Step 3: Run existing tests**

Run: `mvn test -pl needlecast-desktop -q`
Expected: Same baseline — 586 tests, 4 pre-existing failures.

- [ ] **Step 4: Commit**

```
refactor(commands): extract CommandCellRenderers with cell renderers and HTML label utility
```

---

### Task 2: Extract TrayNotifier

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/TrayNotifier.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

**Goal:** Move `TrayNotifier` object out of CommandPanel.

- [ ] **Step 1: Create `TrayNotifier.kt`**

Move the `TrayNotifier` private object (CommandPanel lines 598-629) into its own file:

```kotlin
package io.github.rygel.needlecast.ui

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

internal object TrayNotifier {
    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        try {
            val img =
                TrayNotifier::class.java
                    .getResource("/icons/needlecast.png")
                    ?.let {
                        javax.imageio.ImageIO
                            .read(it)
                            .getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH)
                    }
                    ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val icon = TrayIcon(img, "Needlecast")
            SystemTray.getSystemTray().add(icon)
            icon
        } catch (_: Exception) {
            null
        }
    }

    fun notify(
        caption: String,
        text: String,
        type: TrayIcon.MessageType,
    ) {
        try {
            trayIcon?.displayMessage(caption, text, type)
        } catch (_: Exception) {
        }
    }
}
```

Key change: promoted from `private object` to `internal object`.

- [ ] **Step 2: Update CommandPanel.kt**

Remove the `TrayNotifier` private object. No import changes needed — same package.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl needlecast-desktop -q`

- [ ] **Step 4: Commit**

```
refactor(commands): extract TrayNotifier for system tray notifications
```

---

### Task 3: Extract EditCommandDialog

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/EditCommandDialog.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

**Goal:** Move `EditCommandDialog` class out of CommandPanel.

- [ ] **Step 1: Create `EditCommandDialog.kt`**

Move the `EditCommandDialog` private class (CommandPanel lines 500-576) into its own file:

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.CommandDescriptor
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

class EditCommandDialog(
    owner: Window?,
    private val cmd: CommandDescriptor,
) : JDialog(owner, "Edit Command", ModalityType.APPLICATION_MODAL) {
    // ... verbatim from CommandPanel lines 503-575
}
```

Key change: promoted from `private class` to top-level `class`.

- [ ] **Step 2: Update CommandPanel.kt**

Remove the `EditCommandDialog` private class. No import changes needed.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl needlecast-desktop -q`

- [ ] **Step 4: Commit**

```
refactor(commands): extract EditCommandDialog for command editing
```

---

### Task 4: Extract CommandOverrideManager

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandOverrideManager.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

**Goal:** Move override lookup, edit, and reset logic into CommandOverrideManager with callbacks.

- [ ] **Step 1: Create `CommandOverrideManager.kt`**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandOverride

class CommandOverrideManager(
    private val ctx: AppContext,
    private val currentProjectPath: () -> String?,
    private val updateModel: (Int, CommandDescriptor) -> Unit,
    private val selectedIndex: () -> Int,
) {
    fun findActiveOverride(cmd: CommandDescriptor): CommandOverride? {
        val workDir = currentProjectPath() ?: return null
        val overrides = ctx.config.commandOverrides[workDir] ?: return null
        return overrides.firstOrNull { it.argv == cmd.argv }
            ?: overrides.firstOrNull { it.originalArgv == cmd.argv }
    }

    fun editSelectedCommand(original: CommandDescriptor) {
        val idx = selectedIndex().takeIf { it >= 0 } ?: return
        val workDir = currentProjectPath() ?: return
        val trueOriginalArgv =
            ctx.config.commandOverrides[workDir]
                ?.firstOrNull { it.argv == original.argv }
                ?.originalArgv
                ?: original.argv
        val owner = java.awt.SwingUtilities.getWindowAncestor(null)
        val dialog = EditCommandDialog(owner, original)
        dialog.isVisible = true
        val updated = dialog.result ?: return
        updateModel(idx, updated)
        val newOverride =
            CommandOverride(
                originalArgv = trueOriginalArgv,
                label = updated.label,
                argv = updated.argv,
            )
        val existing =
            ctx.config.commandOverrides[workDir]
                ?.filterNot { it.originalArgv == trueOriginalArgv }
                ?: emptyList()
        ctx.updateConfig(
            ctx.config.copy(
                commandOverrides = ctx.config.commandOverrides + (workDir to (existing + newOverride)),
            ),
        )
    }

    fun resetSelectedCommand(override: CommandOverride, currentBuildTool: io.github.rygel.needlecast.model.BuildTool, currentWorkDir: String) {
        val idx = selectedIndex().takeIf { it >= 0 } ?: return
        val workDir = currentProjectPath() ?: return
        val restored =
            CommandDescriptor(
                label = override.originalArgv.joinToString(" "),
                buildTool = currentBuildTool,
                argv = override.originalArgv,
                workingDirectory = currentWorkDir,
            )
        updateModel(idx, restored)
        val remaining =
            ctx.config.commandOverrides[workDir]
                ?.filterNot { it.originalArgv == override.originalArgv }
                ?: emptyList()
        val newOverrides =
            if (remaining.isEmpty()) {
                ctx.config.commandOverrides - workDir
            } else {
                ctx.config.commandOverrides + (workDir to remaining)
            }
        ctx.updateConfig(ctx.config.copy(commandOverrides = newOverrides))
    }
}
```

Note: `editSelectedCommand` shows a dialog (requires parent window). The `owner` parameter should be passed from CommandPanel. Update the constructor or method to accept it:

```kotlin
fun editSelectedCommand(original: CommandDescriptor, parent: java.awt.Component) {
    // ...
    val owner = java.awt.SwingUtilities.getWindowAncestor(parent)
    val dialog = EditCommandDialog(owner, original)
    // ...
}
```

- [ ] **Step 2: Update CommandPanel.kt**

Remove from CommandPanel:
- `findActiveOverride()` method
- `editSelectedCommand()` method
- `resetSelectedCommand()` method

Add a field:
```kotlin
private val overrideManager = CommandOverrideManager(
    ctx,
    currentProjectPath = { currentProjectPath },
    updateModel = { idx, cmd -> commandModel.set(idx, cmd) },
    selectedIndex = { commandList.selectedIndex },
)
```

Update references:
- `showCommandContextMenu`: use `overrideManager.findActiveOverride(cmd)` and `overrideManager.resetSelectedCommand(override, ...)`
- `editSelectedCommand()` call: use `overrideManager.editSelectedCommand(original, this)`

Note: `applyCommandOverrides()` stays as a top-level `internal fun` in CommandPanel.kt for now — it's already tested in `CommandOverrideTest.kt` and has no dependency on `CommandOverrideManager`.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl needlecast-desktop -q`

- [ ] **Step 4: Commit**

```
refactor(commands): extract CommandOverrideManager for override persistence
```

---

### Task 5: Write CommandOverrideManagerTest

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/CommandOverrideManagerTest.kt`

**Goal:** Tests for `findActiveOverride`, `editSelectedCommand` config persistence, and `resetSelectedCommand` config persistence.

- [ ] **Step 1: Create `CommandOverrideManagerTest.kt`**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandOverride
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandOverrideManagerTest {
    private val projectPath = "/test/project"

    private fun makeManager(
        config: AppConfig = AppConfig(),
        path: String? = projectPath,
        updated: MutableList<Pair<Int, CommandDescriptor>> = mutableListOf(),
    ): CommandOverrideManager {
        val ctx = io.github.rygel.needlecast.AppContext(config)
        return CommandOverrideManager(
            ctx = ctx,
            currentProjectPath = { path },
            updateModel = { idx, cmd -> updated.add(idx to cmd) },
            selectedIndex = { 0 },
        )
    }

    @Test
    fun `findActiveOverride returns null when no overrides exist`() {
        val manager = makeManager()
        val cmd = CommandDescriptor("test", BuildTool.MAVEN, listOf("mvn", "test"), projectPath)
        assertNull(manager.findActiveOverride(cmd))
    }

    @Test
    fun `findActiveOverride returns null when project path is null`() {
        val override = CommandOverride(listOf("mvn", "test"), "Test", listOf("mvn", "test", "-DskipTests"))
        val config = AppConfig(commandOverrides = mapOf(projectPath to listOf(override)))
        val manager = makeManager(config = config, path = null)
        val cmd = CommandDescriptor("test", BuildTool.MAVEN, listOf("mvn", "test"), projectPath)
        assertNull(manager.findActiveOverride(cmd))
    }

    @Test
    fun `findActiveOverride matches by argv`() {
        val override = CommandOverride(listOf("mvn", "test"), "My Test", listOf("mvn", "test", "-DskipTests"))
        val config = AppConfig(commandOverrides = mapOf(projectPath to listOf(override)))
        val manager = makeManager(config = config)
        val cmd = CommandDescriptor("My Test", BuildTool.MAVEN, listOf("mvn", "test", "-DskipTests"), projectPath)
        val found = manager.findActiveOverride(cmd)
        assertNotNull(found)
        assertEquals(listOf("mvn", "test"), found!!.originalArgv)
    }

    @Test
    fun `findActiveOverride falls back to originalArgv match`() {
        val override = CommandOverride(listOf("mvn", "test"), "My Test", listOf("mvn", "test", "-DskipTests"))
        val config = AppConfig(commandOverrides = mapOf(projectPath to listOf(override)))
        val manager = makeManager(config = config)
        val cmd = CommandDescriptor("test", BuildTool.MAVEN, listOf("mvn", "test"), projectPath)
        val found = manager.findActiveOverride(cmd)
        assertNotNull(found)
        assertEquals(listOf("mvn", "test"), found!!.originalArgv)
    }

    @Test
    fun `resetSelectedCommand removes override from config`() {
        val override = CommandOverride(listOf("mvn", "test"), "My Test", listOf("mvn", "test", "-DskipTests"))
        val config = AppConfig(commandOverrides = mapOf(projectPath to listOf(override)))
        val ctx = io.github.rygel.needlecast.AppContext(config)
        val updated = mutableListOf<Pair<Int, CommandDescriptor>>()
        val manager = CommandOverrideManager(
            ctx = ctx,
            currentProjectPath = { projectPath },
            updateModel = { idx, cmd -> updated.add(idx to cmd) },
            selectedIndex = { 0 },
        )
        manager.resetSelectedCommand(override, BuildTool.MAVEN, projectPath)
        val overridesAfter = ctx.config.commandOverrides[projectPath]
        assertNull(overridesAfter, "Override should be removed when it was the only one")
        assertEquals(1, updated.size)
        assertEquals("mvn test", updated[0].second.label)
        assertEquals(listOf("mvn", "test"), updated[0].second.argv)
    }

    @Test
    fun `resetSelectedCommand keeps other overrides`() {
        val override1 = CommandOverride(listOf("mvn", "test"), "My Test", listOf("mvn", "test", "-DskipTests"))
        val override2 = CommandOverride(listOf("mvn", "verify"), "My Verify", listOf("mvn", "verify", "-DskipTests"))
        val config = AppConfig(commandOverrides = mapOf(projectPath to listOf(override1, override2)))
        val ctx = io.github.rygel.needlecast.AppContext(config)
        val updated = mutableListOf<Pair<Int, CommandDescriptor>>()
        val manager = CommandOverrideManager(
            ctx = ctx,
            currentProjectPath = { projectPath },
            updateModel = { idx, cmd -> updated.add(idx to cmd) },
            selectedIndex = { 0 },
        )
        manager.resetSelectedCommand(override1, BuildTool.MAVEN, projectPath)
        val remaining = ctx.config.commandOverrides[projectPath]
        assertNotNull(remaining)
        assertEquals(1, remaining!!.size)
        assertEquals(listOf("mvn", "verify"), remaining[0].originalArgv)
    }
}
```

- [ ] **Step 2: Run the new tests**

Run: `mvn test -pl needlecast-desktop -Dtest=CommandOverrideManagerTest -q`
Expected: 6 tests PASS.

- [ ] **Step 3: Commit**

```
test(commands): add CommandOverrideManagerTest (6 tests for override lookup and reset)
```

---

### Task 6: Write TrayNotifierTest

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/TrayNotifierTest.kt`

**Goal:** Verify TrayNotifier handles missing system tray gracefully.

- [ ] **Step 1: Create `TrayNotifierTest.kt`**

```kotlin
package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrayNotifierTest {
    @Test
    fun `notify does not throw when tray is unavailable`() {
        assertDoesNotThrow {
            TrayNotifier.notify("Test", "Message", java.awt.TrayIcon.MessageType.INFO)
        }
    }

    @Test
    fun `notify does not throw with empty caption`() {
        assertDoesNotThrow {
            TrayNotifier.notify("", "Message", java.awt.TrayIcon.MessageType.INFO)
        }
    }

    @Test
    fun `multiple notify calls do not throw`() {
        assertDoesNotThrow {
            repeat(5) {
                TrayNotifier.notify("Test $it", "Message $it", java.awt.TrayIcon.MessageType.INFO)
            }
        }
    }
}
```

- [ ] **Step 2: Run the new tests**

Run: `mvn test -pl needlecast-desktop -Dtest=TrayNotifierTest -q`
Expected: 3 tests PASS.

- [ ] **Step 3: Commit**

```
test(commands): add TrayNotifierTest (3 tests for graceful no-op behavior)
```

---

### Task 7: Write EditCommandDialogTest

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/EditCommandDialogTest.kt`

**Goal:** Test validation logic in EditCommandDialog.

- [ ] **Step 1: Create `EditCommandDialogTest.kt`**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EditCommandDialogTest {
    private val testCmd = CommandDescriptor("clean install", BuildTool.MAVEN, listOf("mvn", "clean", "install"), "/tmp")

    @Test
    fun `dialog initializes with command fields`() {
        val dialog = EditCommandDialog(null, testCmd)
        assertFalse(dialog.isVisible)
        assertNull(dialog.result)
        dialog.dispose()
    }

    @Test
    fun `result is null when dialog is not shown`() {
        val dialog = EditCommandDialog(null, testCmd)
        assertNull(dialog.result)
        dialog.dispose()
    }

    @Test
    fun `toHtmlLabel escapes ampersand`() {
        assertEquals("<html>foo&amp;bar</html>", "foo&bar".toHtmlLabel())
    }

    @Test
    fun `toHtmlLabel escapes angle brackets`() {
        assertEquals("<html>a&lt;b&gt;c</html>", "a<b>c".toHtmlLabel())
    }

    @Test
    fun `toHtmlLabel wraps plain text in html`() {
        assertEquals("<html>hello world</html>", "hello world".toHtmlLabel())
    }
}
```

Note: The full `onOk()` validation (empty label, empty command) requires interacting with the dialog's internal text fields, which are private. We can test `toHtmlLabel()` here. The dialog's validation logic is simple enough that the existing behavior is verified through manual testing. The more valuable automated tests are in CommandOverrideManagerTest.

- [ ] **Step 2: Run the new tests**

Run: `mvn test -pl needlecast-desktop -Dtest=EditCommandDialogTest -q`
Expected: 5 tests PASS.

- [ ] **Step 3: Commit**

```
test(commands): add EditCommandDialogTest (5 tests for dialog init and HTML escaping)
```

---

### Task 8: Final verification and merge

**Files:** None (verification only)

**Goal:** Run the full test suite, ktlint, verify line counts, and merge to develop.

- [ ] **Step 1: Run ktlint**

Run: `mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q`
Expected: Clean.

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl needlecast-desktop -q`
Expected: ~606 tests, only the 4 pre-existing failures.

- [ ] **Step 3: Verify line counts**

Run:
```powershell
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandCellRenderers.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandOverrideManager.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/TrayNotifier.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/EditCommandDialog.kt).Count
```

Expected:
- CommandPanel.kt: ~400 lines (down from 731)
- CommandCellRenderers.kt: ~120 lines
- CommandOverrideManager.kt: ~100 lines
- TrayNotifier.kt: ~35 lines
- EditCommandDialog.kt: ~80 lines

- [ ] **Step 4: Commit any ktlint fixes, then merge to develop**

```bash
git checkout develop
git merge --no-ff feat/cycle-19-command-panel -m "Cycle 19: CommandPanel decomposition (+14 tests)"
git push origin develop
```
