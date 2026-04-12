# Bug Fixes Batch 2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three independent bugs (scanner resilience, Explorer file-manager button, command persistence) and expand the screenshot tour to cover every panel and dialog.

**Architecture:** Four independent changes across five files. Tasks 1–3 are self-contained bug fixes with unit tests. Task 4 expands the existing `ScreenshotTour.kt` standalone program — infrastructure (Dockerfile, workflow) already exists and does not need changes.

**Tech Stack:** Kotlin, Swing/AWT, JUnit 5, Maven. Screenshot tour is a headless JVM main function run inside Xvfb Docker container.

---

## Context

Branch: `fix/bug-fixes-batch2` (off `develop`)

Run unit tests (no desktop) with:
```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress
```

UI/screenshot tests run inside Docker only — never locally.

---

### Task 1: CompositeProjectScanner — isolate per-scanner failures

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt`
- Test: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScannerIntegrationTest.kt`

**Problem:** Line 29 — `scanners.mapNotNull { it.scan(directory) }` — if any single scanner throws an exception (e.g. malformed `package.json`, permission error), the exception propagates through `mapNotNull` and is caught by the outer handler in `ProjectTreePanel`, which stores a `scanFailed = true` result with **no** build tools. A Maven+npm project shows no badges or only one badge depending on throw order.

- [ ] **Step 1: Read the file**

Read `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt`.
Confirm line 29 is:
```kotlin
val results = scanners.mapNotNull { it.scan(directory) }
```

- [ ] **Step 2: Write the failing test**

Add to `CompositeProjectScannerIntegrationTest.kt` (after the last `}` of the last test, before the final class `}`):

```kotlin
@Test
fun `one scanner throwing does not suppress other scanners results`(@TempDir dir: Path) {
    // Build a CompositeProjectScanner where the npm scanner always throws,
    // but the maven scanner succeeds.
    val bombScanner = object : ProjectScanner {
        override fun scan(directory: ProjectDirectory): io.github.rygel.needlecast.model.DetectedProject? =
            throw RuntimeException("Simulated scanner failure")
    }
    val mavenOnly = CompositeProjectScanner(
        scanners = listOf(bombScanner, MavenProjectScanner()),
    )
    File(dir.toFile(), "pom.xml").writeText("<project/>")

    // Before the fix this throws; after the fix it returns Maven results.
    val result = mavenOnly.scan(ProjectDirectory(dir.toString()))
    assertTrue(BuildTool.MAVEN in result.buildTools) { "Maven tools should survive bomb scanner: ${result.buildTools}" }
    assertFalse(result.scanFailed)
}
```

- [ ] **Step 3: Run test to verify it fails**

```
mvn test -pl needlecast-desktop -Dtest=CompositeProjectScannerIntegrationTest --no-transfer-progress -q
```
Expected: FAIL — the test throws `RuntimeException` out of `mapNotNull`.

- [ ] **Step 4: Apply the fix**

In `CompositeProjectScanner.kt`, replace line 29:
```kotlin
val results = scanners.mapNotNull { it.scan(directory) }
```
with:
```kotlin
val logger = org.slf4j.LoggerFactory.getLogger(CompositeProjectScanner::class.java)
val results = scanners.mapNotNull { scanner ->
    try {
        scanner.scan(directory)
    } catch (e: Exception) {
        logger.warn("Scanner ${scanner::class.simpleName} failed for '${directory.label()}'", e)
        null
    }
}
```

Add the slf4j import at the top of the file (after `package` line, with the existing imports):
```kotlin
import org.slf4j.LoggerFactory
```

Note: `logger` should be a class-level field, not inside the function. Refactor:

The full updated file:
```kotlin
package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import org.slf4j.LoggerFactory

class CompositeProjectScanner(
    private val scanners: List<ProjectScanner> = listOf(
        MavenProjectScanner(),
        GradleProjectScanner(),
        DotNetProjectScanner(),
        IntellijRunConfigScanner(),
        NpmProjectScanner(),
        ApmProjectScanner(),
        PythonProjectScanner(),
        RustProjectScanner(),
        GoProjectScanner(),
        PhpProjectScanner(),
        RubyProjectScanner(),
        SwiftProjectScanner(),
        DartProjectScanner(),
        CMakeProjectScanner(),
        SbtProjectScanner(),
        ElixirProjectScanner(),
        ZigProjectScanner(),
    ),
) : ProjectScanner {

    private val logger = LoggerFactory.getLogger(CompositeProjectScanner::class.java)

    override fun scan(directory: ProjectDirectory): DetectedProject {
        val results = scanners.mapNotNull { scanner ->
            try {
                scanner.scan(directory)
            } catch (e: Exception) {
                logger.warn("Scanner ${scanner::class.simpleName} failed for '${directory.label()}'", e)
                null
            }
        }

        if (results.isEmpty()) {
            return DetectedProject(
                directory = directory,
                buildTools = emptySet(),
                commands = emptyList(),
            )
        }

        return DetectedProject(
            directory = directory,
            buildTools = results.flatMap { it.buildTools }.toSet(),
            commands = results.flatMap { it.commands },
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```
mvn test -pl needlecast-desktop -Dtest=CompositeProjectScannerIntegrationTest --no-transfer-progress -q
```
Expected: all tests PASS.

- [ ] **Step 6: Run full build**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScannerIntegrationTest.kt
git commit -m "fix: isolate per-scanner failures in CompositeProjectScanner"
```

---

### Task 2: Explorer panel — "Open in File Manager" button

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

The address bar has three buttons (↑ up, ↻ refresh, ⊙ hidden-files) but no way to open the current directory in the OS file manager. `ProjectTreePanel` already has an identical helper at lines 863–879 — we replicate the same pattern here.

`IS_WINDOWS` and `IS_MAC` are in `io.github.rygel.needlecast.scanner.ProjectScanner` (file `ProjectScanner.kt`).

- [ ] **Step 1: Read the file**

Read `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt` lines 1–95.
Confirm:
- Line 5: `import io.github.rygel.needlecast.scanner.IS_WINDOWS` already present
- Lines 62–90: toolbar built in `init {}`
- Line 80: `val rightButtons = JPanel(FlowLayout...)` containing `hiddenButton` and `refreshButton`

- [ ] **Step 2: Apply the change**

**a.** After the existing `import io.github.rygel.needlecast.scanner.IS_WINDOWS` line, add:
```kotlin
import io.github.rygel.needlecast.scanner.IS_MAC
import java.awt.Desktop
```

**b.** In `init {}`, after the `hiddenButton` definition (after the closing `}` of that `apply` block, before `val rightButtons`), add:

```kotlin
val openFmButton = JButton("\u29C9").apply {
    toolTipText = if (IS_MAC) "Open in Finder" else "Open in Explorer"
    addActionListener { openInFileManager(currentDir) }
}
```

**c.** Change `rightButtons` to include `openFmButton`:
```kotlin
val rightButtons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
    add(openFmButton)
    add(hiddenButton)
    add(refreshButton)
}
```

**d.** Add the private helper function anywhere in the class body (e.g. after the `navigateUp()` function):

```kotlin
private fun openInFileManager(dir: File) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
        } else if (IS_WINDOWS) {
            ProcessBuilder("explorer.exe", dir.absolutePath).start()
        } else if (IS_MAC) {
            ProcessBuilder("open", dir.absolutePath).start()
        } else {
            ProcessBuilder("xdg-open", dir.absolutePath).start()
        }
    } catch (_: Exception) {}
}
```

- [ ] **Step 3: Verify it compiles**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt
git commit -m "fix: add 'open in file manager' button to Explorer panel toolbar"
```

---

### Task 3: Persistent command editing

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/CommandOverrideTest.kt`

`EditCommandDialog` changes a `CommandDescriptor` in-memory only. Rescanning (or restarting) regenerates commands from project files, discarding edits. Fix: store overrides in `AppConfig` keyed by working directory.

- [ ] **Step 1: Read both files**

Read:
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt` lines 540–613 (the `AppConfig` data class)
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt` lines 48–215 (class declaration through `loadProject`)

Confirm:
- `AppConfig` ends at line 613 with `val claudeHooksEnabled: Boolean = false,`
- `CommandPanel` constructor has `private val ctx: AppContext` at line 49
- `loadProject(project: DetectedProject?)` is at line 196
- `currentProjectPath: String?` field is at line 96
- `editSelectedCommand()` is at line 353

- [ ] **Step 2: Write the failing test**

Create `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/CommandOverrideTest.kt`:

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.CommandOverride
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandOverrideTest {

    @Test
    fun `CommandOverride is present in AppConfig with empty default`() {
        val config = AppConfig()
        assertTrue(config.commandOverrides.isEmpty())
    }

    @Test
    fun `commandOverrides round-trips through copy`() {
        val override = CommandOverride(
            originalArgv = listOf("mvn", "clean", "install"),
            label = "Build",
            argv = listOf("mvn", "clean", "install", "-DskipTests"),
        )
        val config = AppConfig(
            commandOverrides = mapOf("/home/user/project" to listOf(override))
        )
        val copied = config.copy()
        assertEquals(config.commandOverrides, copied.commandOverrides)
    }

    @Test
    fun `applying override replaces matching descriptor`() {
        val original = io.github.rygel.needlecast.model.CommandDescriptor(
            label = "clean install",
            buildTool = io.github.rygel.needlecast.model.BuildTool.MAVEN,
            argv = listOf("mvn", "clean", "install"),
            workingDirectory = "/home/user/project",
        )
        val override = CommandOverride(
            originalArgv = listOf("mvn", "clean", "install"),
            label = "Build (skip tests)",
            argv = listOf("mvn", "clean", "install", "-DskipTests"),
        )
        val result = applyCommandOverrides(listOf(original), listOf(override))
        assertEquals(1, result.size)
        assertEquals("Build (skip tests)", result[0].label)
        assertEquals(listOf("mvn", "clean", "install", "-DskipTests"), result[0].argv)
    }

    @Test
    fun `override with no matching command is silently ignored`() {
        val original = io.github.rygel.needlecast.model.CommandDescriptor(
            label = "clean install",
            buildTool = io.github.rygel.needlecast.model.BuildTool.MAVEN,
            argv = listOf("mvn", "clean", "install"),
            workingDirectory = "/home/user/project",
        )
        val override = CommandOverride(
            originalArgv = listOf("mvn", "verify"),   // no match
            label = "Verify",
            argv = listOf("mvn", "verify"),
        )
        val result = applyCommandOverrides(listOf(original), listOf(override))
        assertEquals(1, result.size)
        assertEquals("clean install", result[0].label)  // unchanged
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```
mvn test -pl needlecast-desktop -Dtest=CommandOverrideTest --no-transfer-progress -q
```
Expected: FAIL — `CommandOverride`, `AppConfig.commandOverrides`, and `applyCommandOverrides` are not yet defined.

- [ ] **Step 4: Add `CommandOverride` to `AppConfig.kt`**

In `AppConfig.kt`, add the data class just before `data class AiCliDefinition` (after the closing `}` of `AppConfig`, around line 614):

```kotlin
/** Persisted edit to a scanner-generated [CommandDescriptor], keyed by the original argv. */
data class CommandOverride(
    val originalArgv: List<String>,
    val label: String,
    val argv: List<String>,
)
```

In `AppConfig`, add a new field after `val claudeHooksEnabled` (line 612, before the closing `)`):

```kotlin
/** Per-project command overrides. Outer key = working directory path. */
val commandOverrides: Map<String, List<CommandOverride>> = emptyMap(),
```

- [ ] **Step 5: Add `applyCommandOverrides` top-level function to `CommandPanel.kt`**

At the bottom of `CommandPanel.kt`, before the `TrayNotifier` object (after the last class but before the `private object TrayNotifier`), add:

```kotlin
/**
 * Applies stored [overrides] on top of scanner-generated [commands].
 * Each override is matched by [CommandOverride.originalArgv]; unmatched overrides are ignored.
 */
internal fun applyCommandOverrides(
    commands: List<io.github.rygel.needlecast.model.CommandDescriptor>,
    overrides: List<io.github.rygel.needlecast.model.CommandOverride>,
): List<io.github.rygel.needlecast.model.CommandDescriptor> {
    if (overrides.isEmpty()) return commands
    val overrideMap = overrides.associateBy { it.originalArgv }
    return commands.map { cmd ->
        val ov = overrideMap[cmd.argv] ?: return@map cmd
        cmd.copy(label = ov.label, argv = ov.argv)
    }
}
```

- [ ] **Step 6: Wire overrides into `loadProject`**

In `CommandPanel.kt`, replace the body of `loadProject` (lines 196–214):

```kotlin
fun loadProject(project: DetectedProject?) {
    commandModel.clear()
    historyModel.clear()
    currentProjectPath = project?.directory?.path
    currentProjectEnv = project?.directory?.env ?: emptyMap()
    runButton.isEnabled = false
    queueButton.isEnabled = false

    loadReadme(project?.directory?.path)

    if (project == null) return

    val overrides = ctx.config.commandOverrides[project.directory.path] ?: emptyList()
    val commands = applyCommandOverrides(project.commands, overrides)
    commands.forEach { commandModel.addElement(it) }
    if (commandModel.size > 0) commandList.selectedIndex = 0

    // Load saved history for this project
    historyManager.getHistory(project.directory.path)
        .forEach { historyModel.addElement(it) }
}
```

- [ ] **Step 7: Persist override when user saves an edit**

In `CommandPanel.kt`, replace `editSelectedCommand()` (lines 353–361):

```kotlin
private fun editSelectedCommand() {
    val idx = commandList.selectedIndex.takeIf { it >= 0 } ?: return
    val original = commandModel.getElementAt(idx)
    val owner = SwingUtilities.getWindowAncestor(this)
    val dialog = EditCommandDialog(owner, original)
    dialog.isVisible = true
    val updated = dialog.result ?: return
    commandModel.set(idx, updated)

    // Persist the override so it survives rescans
    val workDir = currentProjectPath ?: return
    val newOverride = io.github.rygel.needlecast.model.CommandOverride(
        originalArgv = original.argv,
        label = updated.label,
        argv = updated.argv,
    )
    val existing = ctx.config.commandOverrides[workDir]
        ?.filterNot { it.originalArgv == original.argv }
        ?: emptyList()
    ctx.updateConfig(
        ctx.config.copy(
            commandOverrides = ctx.config.commandOverrides + (workDir to existing + newOverride)
        )
    )
}
```

- [ ] **Step 8: Run tests to verify they pass**

```
mvn test -pl needlecast-desktop -Dtest=CommandOverrideTest --no-transfer-progress -q
```
Expected: 4 tests PASS.

- [ ] **Step 9: Run full build**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/CommandOverrideTest.kt
git commit -m "fix: persist command label/argv edits across rescans"
```

---

### Task 4: Expand screenshot tour — every panel in multiple states

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ScreenshotTour.kt`

The existing `ScreenshotTour.kt` captures 9 screenshots (01–09). The Dockerfile, workflow (`screenshots.yml`), and demo-data helpers (`createDemoProjects`, `buildDemoConfig`, `buildDemoLog`, scaffold functions) are already present and correct — **do not change them**. Expand the screenshot capture section only.

Target: ~45 screenshots covering every dockable panel in at least two states (empty/populated, different tabs, context menus, all settings sub-panels, all dialogs).

The existing infrastructure:
- `dialogShot(robot, dest) { showDialog() }` — opens a dialog, captures it, closes it
- `screenshot(robot, window, dest)` — captures the full window
- Reflection pattern via `w.javaClass.getDeclaredField(...)` to access private panels
- `ModernDocking` is on the classpath — `io.github.andrewauclair.moderndocking.app.Docking`

- [ ] **Step 1: Read the existing file**

Read `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ScreenshotTour.kt` in full. Understand the structure: main function, try block with screenshots 01–09, helper functions at the bottom, demo data builders.

- [ ] **Step 2: Add helper functions**

Before the existing `// ── Helpers` section, add these two new helpers that will be used by the expanded tour:

```kotlin
/** Access a private field from the MainWindow by name and cast to T. */
private inline fun <reified T> MainWindow.field(name: String): T {
    val f = this.javaClass.getDeclaredField(name)
    f.isAccessible = true
    return f.get(this) as T
}

/** Invoke a method on any object by name with the given args, ignoring result. */
private fun Any.invoke(methodName: String, vararg args: Any?) {
    val argTypes = args.map { it?.javaClass ?: Void.TYPE }.toTypedArray()
    try {
        val m = this.javaClass.getMethod(methodName, *argTypes)
        m.invoke(this, *args)
    } catch (_: NoSuchMethodException) {
        val m = this.javaClass.getDeclaredMethod(methodName, *argTypes)
        m.isAccessible = true
        m.invoke(this, *args)
    }
}

/** Bring a dockable to front by its persistent ID, wait for repaint. */
private fun bringToFront(persistentId: String) {
    SwingUtilities.invokeAndWait {
        try {
            val docking = Class.forName("io.github.andrewauclair.moderndocking.app.Docking")
            val getDockable = docking.getMethod("getDockable", String::class.java)
            val dockable = getDockable.invoke(null, persistentId) ?: return@invokeAndWait
            val bringToFront = dockable.javaClass.getMethod("bringToFront")
            bringToFront.invoke(dockable)
        } catch (_: Exception) {}
    }
    Thread.sleep(300)
}
```

**Important notes for Task 4 implementation:**
- All reflection code inside `SwingUtilities.invokeAndWait { }` lambdas **must** be wrapped in `try { ... } catch (e: Exception) { e.printStackTrace() }` so that a `NoSuchFieldException` (e.g. field is a local var, not a class field) does not abort the entire remaining tour via the outer try-catch.
- `hiddenButton` in `ExplorerPanel` is a local variable inside `init {}`, **not** a class field. Access the `showHidden: Boolean` class field directly instead (see the fixed code in step 3).
- Before writing the expanded tour, read each target file to verify private field names exist as class-level fields. `historyToggle`, `queueToggle` in `CommandPanel` are class-level `private val`s — confirmed. `showHidden`, `currentDir` in `ExplorerPanel` are class-level `private var`s — confirmed.

- [ ] **Step 3: Expand the try block — Settings sub-panels (screenshots 10–18)**

After the existing screenshot 09 block (after the `println("  > 09-log-viewer.png")` line and before `println("Screenshots written to $outputDir")`), add:

```kotlin
// ── Settings sub-panels ───────────────────────────────────────────────────

val settingsTabs = listOf(
    "10-settings-appearance" to "Appearance",
    "11-settings-layout"     to "Layout",
    "12-settings-terminal"   to "Terminal",
    "13-settings-editors"    to "External Editors",
    "14-settings-ai-tools"   to "AI Tools",
    "15-settings-renovate"   to "Renovate",
    "16-settings-apm"        to "APM",
    "17-settings-shortcuts"  to "Shortcuts",
    "18-settings-language"   to "Language",
)

for ((filename, tabLabel) in settingsTabs) {
    var settingsDlg: javax.swing.JDialog? = null
    SwingUtilities.invokeAndWait {
        val dlg = io.github.rygel.needlecast.ui.SettingsDialog(w, ctx, sendToTerminal = {})
        // Navigate to the target tab via the sidebar list
        val allComponents = mutableListOf<java.awt.Component>()
        fun collectComponents(c: java.awt.Container) {
            for (i in 0 until c.componentCount) {
                val child = c.getComponent(i)
                allComponents.add(child)
                if (child is java.awt.Container) collectComponents(child)
            }
        }
        collectComponents(dlg.contentPane as java.awt.Container)
        val list = allComponents.filterIsInstance<javax.swing.JList<*>>().firstOrNull()
        if (list != null) {
            val model = list.model
            for (i in 0 until model.size) {
                val item = model.getElementAt(i)
                if (item.toString().contains(tabLabel)) {
                    @Suppress("UNCHECKED_CAST")
                    (list as javax.swing.JList<Any>).selectedIndex = i
                    break
                }
            }
        }
        dlg.isVisible = true
        settingsDlg = dlg
    }
    Thread.sleep(600)
    screenshotTopDialog(robot, outputDir.resolve("$filename.png"))
    println("  > $filename.png")
    SwingUtilities.invokeAndWait { settingsDlg?.dispose() }
    Thread.sleep(200)
}

// ── 19: Variable resolution dialog ───────────────────────────────────────
dialogShot(robot, outputDir.resolve("19-variable-resolution.png")) {
    io.github.rygel.needlecast.ui.VariableResolutionDialog(
        owner = w,
        template = "Deploy {environment} to {region} using {profile}",
        variables = listOf("environment", "region", "profile"),
        onSubmit = {},
    ).isVisible = true
}
println("  > 19-variable-resolution.png")

// ── Commands panel ────────────────────────────────────────────────────────
val commandPanel = w.field<io.github.rygel.needlecast.ui.CommandPanel>("commandPanel")
bringToFront("commands")

// 20: commands populated — already loaded from project selection
screenshot(robot, w, outputDir.resolve("20-commands-populated.png"))
println("  > 20-commands-populated.png")

// 21: commands with history toggle open
SwingUtilities.invokeAndWait {
    val toggleField = commandPanel.javaClass.getDeclaredField("historyToggle")
    toggleField.isAccessible = true
    val toggle = toggleField.get(commandPanel) as javax.swing.JToggleButton
    toggle.isSelected = true
    toggle.doClick()
}
Thread.sleep(300)
screenshot(robot, w, outputDir.resolve("21-commands-history.png"))
println("  > 21-commands-history.png")

// Reset history toggle
SwingUtilities.invokeAndWait {
    val toggleField = commandPanel.javaClass.getDeclaredField("historyToggle")
    toggleField.isAccessible = true
    val toggle = toggleField.get(commandPanel) as javax.swing.JToggleButton
    toggle.isSelected = false
    toggle.doClick()
}

// 22: commands with queue toggle open
SwingUtilities.invokeAndWait {
    val toggleField = commandPanel.javaClass.getDeclaredField("queueToggle")
    toggleField.isAccessible = true
    val toggle = toggleField.get(commandPanel) as javax.swing.JToggleButton
    toggle.isSelected = true
    toggle.doClick()
}
Thread.sleep(300)
screenshot(robot, w, outputDir.resolve("22-commands-queue.png"))
println("  > 22-commands-queue.png")
SwingUtilities.invokeAndWait {
    val toggleField = commandPanel.javaClass.getDeclaredField("queueToggle")
    toggleField.isAccessible = true
    val toggle = toggleField.get(commandPanel) as javax.swing.JToggleButton
    toggle.isSelected = false
    toggle.doClick()
}

// ── Explorer panel ────────────────────────────────────────────────────────
bringToFront("explorer")
Thread.sleep(500)

// 23: Explorer directory listing
screenshot(robot, w, outputDir.resolve("23-explorer-directory.png"))
println("  > 23-explorer-directory.png")

// 24: Explorer with hidden files shown
// Note: hiddenButton is a local var in init{}, not a class field — toggle showHidden directly.
val explorerPanel = w.field<io.github.rygel.needlecast.ui.explorer.ExplorerPanel>("explorerPanel")
SwingUtilities.invokeAndWait {
    try {
        val showHiddenField = explorerPanel.javaClass.getDeclaredField("showHidden")
        showHiddenField.isAccessible = true
        showHiddenField.setBoolean(explorerPanel, true)
        val currentDirField = explorerPanel.javaClass.getDeclaredField("currentDir")
        currentDirField.isAccessible = true
        val dir = currentDirField.get(explorerPanel) as File
        val loadDir = explorerPanel.javaClass.getDeclaredMethod("loadDirectory", File::class.java)
        loadDir.isAccessible = true
        loadDir.invoke(explorerPanel, dir)
    } catch (e: Exception) { e.printStackTrace() }
}
Thread.sleep(300)
screenshot(robot, w, outputDir.resolve("24-explorer-hidden-files.png"))
println("  > 24-explorer-hidden-files.png")
// Reset showHidden
SwingUtilities.invokeAndWait {
    try {
        val showHiddenField = explorerPanel.javaClass.getDeclaredField("showHidden")
        showHiddenField.isAccessible = true
        showHiddenField.setBoolean(explorerPanel, false)
        val currentDirField = explorerPanel.javaClass.getDeclaredField("currentDir")
        currentDirField.isAccessible = true
        val dir = currentDirField.get(explorerPanel) as File
        val loadDir = explorerPanel.javaClass.getDeclaredMethod("loadDirectory", File::class.java)
        loadDir.isAccessible = true
        loadDir.invoke(explorerPanel, dir)
    } catch (e: Exception) { e.printStackTrace() }
}

// ── Git Log panel ─────────────────────────────────────────────────────────
bringToFront("git-log")
Thread.sleep(500)

// 25: Git log — load the first demo project (maven project with git history)
SwingUtilities.invokeAndWait {
    try {
        val gitLogPanel = w.field<io.github.rygel.needlecast.ui.GitLogPanel>("gitLogPanel")
        gitLogPanel.invoke("loadProject", projects[0].dir.absolutePath)
    } catch (_: Exception) {}
}
Thread.sleep(2000)
screenshot(robot, w, outputDir.resolve("25-git-log.png"))
println("  > 25-git-log.png")

// ── Search panel ──────────────────────────────────────────────────────────
bringToFront("search")
Thread.sleep(300)

// 26: Search panel empty state
screenshot(robot, w, outputDir.resolve("26-search-empty.png"))
println("  > 26-search-empty.png")

// 27: Search panel with a query entered
SwingUtilities.invokeAndWait {
    try {
        val searchPanel = w.field<io.github.rygel.needlecast.ui.SearchPanel>("searchPanel")
        val queryField = searchPanel.javaClass.getDeclaredField("queryField")
        queryField.isAccessible = true
        val field = queryField.get(searchPanel) as javax.swing.JTextField
        field.text = "fun main"
    } catch (_: Exception) {}
}
Thread.sleep(300)
screenshot(robot, w, outputDir.resolve("27-search-query.png"))
println("  > 27-search-query.png")

// ── Docs panel ────────────────────────────────────────────────────────────
bringToFront("docs")
SwingUtilities.invokeAndWait {
    try {
        val docsPanel = w.field<io.github.rygel.needlecast.ui.DocsPanel>("docsPanel")
        docsPanel.invoke("loadProject", projects[0].dir.absolutePath)
    } catch (_: Exception) {}
}
Thread.sleep(500)

// 28: Docs panel
screenshot(robot, w, outputDir.resolve("28-docs-panel.png"))
println("  > 28-docs-panel.png")

// ── Doc Viewer panel ──────────────────────────────────────────────────────
bringToFront("doc-viewer")
Thread.sleep(300)

// 29: Doc Viewer panel
screenshot(robot, w, outputDir.resolve("29-doc-viewer.png"))
println("  > 29-doc-viewer.png")

// ── Prompt Input panel ────────────────────────────────────────────────────
bringToFront("prompt-input")
Thread.sleep(300)

// 30: Prompt Input default
screenshot(robot, w, outputDir.resolve("30-prompt-input.png"))
println("  > 30-prompt-input.png")

// ── Command Input panel ───────────────────────────────────────────────────
bringToFront("command-input")
Thread.sleep(300)

// 31: Command Input default
screenshot(robot, w, outputDir.resolve("31-command-input.png"))
println("  > 31-command-input.png")

// ── Console / Output panel ────────────────────────────────────────────────
bringToFront("console")
Thread.sleep(300)

// 32: Console panel
screenshot(robot, w, outputDir.resolve("32-console.png"))
println("  > 32-console.png")

// ── Terminal panel ────────────────────────────────────────────────────────
bringToFront("terminal")
Thread.sleep(500)

// 33: Terminal idle
screenshot(robot, w, outputDir.resolve("33-terminal-idle.png"))
println("  > 33-terminal-idle.png")

// ── Project tree states ───────────────────────────────────────────────────
bringToFront("project-tree")
Thread.sleep(300)

// 34: Project tree populated (should already show from main window)
screenshot(robot, w, outputDir.resolve("34-project-tree-populated.png"))
println("  > 34-project-tree-populated.png")

// 35: Project tree with filter active
SwingUtilities.invokeAndWait {
    try {
        val treePanel = w.field<io.github.rygel.needlecast.ui.ProjectTreePanel>("projectTreePanel")
        val filterField = treePanel.javaClass.getDeclaredField("filterField")
        filterField.isAccessible = true
        val field = filterField.get(treePanel) as javax.swing.JTextField
        field.text = "react"
        field.postActionEvent()
    } catch (_: Exception) {}
}
Thread.sleep(400)
screenshot(robot, w, outputDir.resolve("35-project-tree-filter.png"))
println("  > 35-project-tree-filter.png")

// Clear filter
SwingUtilities.invokeAndWait {
    try {
        val treePanel = w.field<io.github.rygel.needlecast.ui.ProjectTreePanel>("projectTreePanel")
        val filterField = treePanel.javaClass.getDeclaredField("filterField")
        filterField.isAccessible = true
        val field = filterField.get(treePanel) as javax.swing.JTextField
        field.text = ""
        field.postActionEvent()
    } catch (_: Exception) {}
}
Thread.sleep(300)

// ── Doc viewer — load project ─────────────────────────────────────────────
bringToFront("doc-viewer")
SwingUtilities.invokeAndWait {
    try {
        val docViewerPanel = w.field<io.github.rygel.needlecast.ui.DocViewerPanel>("docViewerPanel")
        docViewerPanel.invoke("loadProject", projects[0].dir.absolutePath)
    } catch (_: Exception) {}
}
Thread.sleep(500)

// 36: Doc Viewer with project loaded
screenshot(robot, w, outputDir.resolve("36-doc-viewer-with-project.png"))
println("  > 36-doc-viewer-with-project.png")

// ── Log viewer with filter ─────────────────────────────────────────────────
bringToFront("log-viewer")
Thread.sleep(500)

// 37: Log viewer filter active
SwingUtilities.invokeAndWait {
    try {
        val logViewer = w.field<io.github.rygel.needlecast.ui.logviewer.LogViewerPanel>("logViewerPanel")
        val filterField = logViewer.javaClass.getDeclaredField("filterField")
        filterField.isAccessible = true
        val field = filterField.get(logViewer) as javax.swing.JTextField
        field.text = "ERROR"
        field.postActionEvent()
    } catch (_: Exception) {}
}
Thread.sleep(400)
screenshot(robot, w, outputDir.resolve("37-log-viewer-filter.png"))
println("  > 37-log-viewer-filter.png")

// ── Renovate — installed state ─────────────────────────────────────────────
bringToFront("renovate")
Thread.sleep(1000)

// 38: Renovate panel (already captured as 08 but capture again with correct number)
screenshot(robot, w, outputDir.resolve("38-renovate.png"))
println("  > 38-renovate.png")
```

- [ ] **Step 4: Verify the file compiles**

```
mvn compile -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS (no compile errors).

- [ ] **Step 5: Run full build to confirm no regressions**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ScreenshotTour.kt
git commit -m "feat: expand screenshot tour to cover all panels and dialogs"
```
