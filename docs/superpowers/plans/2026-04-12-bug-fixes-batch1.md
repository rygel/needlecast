# Bug Fixes Batch 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four independent bugs: Renovate scan button enabled when renovate is absent, Command panel Unicode symbols and emoji rendering as boxes, terminal/viewer font ordering, and log viewer not showing the app's own log file.

**Architecture:** Four independent edits across five files plus one new `internal` helper function in `LogViewerPanel.kt`. No new files are created. All changes are self-contained within their respective files; nothing touches shared infrastructure.

**Tech Stack:** Kotlin, Swing/AWT, FlatLaf, assertj-swing (UI tests in Podman only), JUnit 5, Maven.

---

## Context

Branch: `fix/renovate-emoji-logviewer` (off `develop`)

Run unit tests (no desktop profile) with:
```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress
```

UI tests (assertj-swing) must ONLY run inside Podman with `-Ptest-desktop`. Never run locally.

---

### Task 1: Renovate scan button guard

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/RenovatePanel.kt`

The `runButton` is currently enabled by default. If renovate is not installed, clicking "Scan for Updates" runs `cmd /c renovate ...` which exits non-zero, showing a subtle error in `summaryLabel`. The fix: track whether renovate was found and gate the button on that state.

- [ ] **Step 1: Read the file**

Read `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/RenovatePanel.kt` (557 lines). The relevant locations are:
- Line 107: `runButton` definition — no `isEnabled = false`
- Lines 499–511: `checkRenovateStatus().done()` — sets label text but not `runButton.isEnabled`
- Lines 514–520: `setButtonsEnabled(enabled)` — sets `runButton.isEnabled = enabled` unconditionally

- [ ] **Step 2: Apply the three-part change**

**a.** Add a field after the `updates` declaration (around line 63):
```kotlin
private var renovateFound = false
```

**b.** Disable `runButton` at declaration (line 107–109):
```kotlin
private val runButton = JButton("Scan for Updates").apply {
    isEnabled = false  // enabled only after checkRenovateStatus() confirms installation
    addActionListener { runLocalScan() }
}
```

**c.** In `checkRenovateStatus().done()` (after line 503 `val (found, version) = get()`), add before the `if (found)` block:
```kotlin
renovateFound = found
runButton.isEnabled = found
```

**d.** In `setButtonsEnabled(enabled: Boolean)` (line 515), change the first line from:
```kotlin
runButton.isEnabled = enabled
```
to:
```kotlin
runButton.isEnabled = enabled && renovateFound
```

- [ ] **Step 3: Verify it compiles**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/RenovatePanel.kt
git commit -m "fix: disable Renovate scan button when renovate is not installed"
```

---

### Task 2: Command panel — fix Unicode symbols and emoji rendering

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

**Part A — symbol replacements:** U+23F9 (⏹) and U+23ED (⏭) are in the Miscellaneous Technical Unicode block, absent from most JVM bundled fonts. Replace with Geometric Shapes (U+25A0–U+25FF), which every JVM font includes.

**Part B — emoji in labels:** FlatLaf sets a physical font (e.g. Segoe UI) that blocks Java's font-substitution pipeline. Wrapping `JLabel.text` in `<html>…</html>` activates Java's HTML renderer, which re-engages the OS emoji fallback chain (Segoe UI Emoji → Windows; Apple Color Emoji → macOS).

- [ ] **Step 1: Read the file**

Read `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`. Key locations:
- Line 73: `cancelButton` — `"\u23F9  Cancel"`
- Line 74: `queueButton`  — `"\u23ED  Queue"`
- Line 76: `queueToggle`  — `"\u23ED Queue"`
- Line 341: context menu `JMenuItem` — `"\u23ED  Queue"`
- Lines 451–472: `CommandCellRenderer` — `label.text = value?.label ?: ""`
- Lines 476–501: `HistoryCellRenderer` — `nameLabel.text = value?.label ?: ""`

- [ ] **Step 2: Fix button and menu symbols**

Replace the four occurrences:

| Old string | New string |
|---|---|
| `"\u23F9  Cancel"` | `"\u25A0  Cancel"` |
| `"\u23ED  Queue"` (queueButton, line 74) | `"\u25B6\u25B6  Queue"` |
| `"\u23ED Queue"` (queueToggle, line 76) | `"\u25B6\u25B6 Queue"` |
| `"\u23ED  Queue"` (context menu, line 341) | `"\u25B6\u25B6  Queue"` |

- [ ] **Step 3: Fix emoji in CommandCellRenderer**

In `CommandCellRenderer.getListCellRendererComponent()` (around line 460), change:
```kotlin
label.text = value?.label ?: ""
```
to:
```kotlin
label.text = (value?.label ?: "").toHtmlLabel()
```

Add the extension function at the bottom of the file (after the last class, before the end of the file):
```kotlin
/** Wraps text in HTML so Java's platform font-fallback chain (incl. emoji) is active. */
private fun String.toHtmlLabel(): String =
    "<html>${replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</html>"
```

- [ ] **Step 4: Fix emoji in HistoryCellRenderer**

In `HistoryCellRenderer.getListCellRendererComponent()` (around line 489), change:
```kotlin
nameLabel.text = value?.label ?: ""
```
to:
```kotlin
nameLabel.text = (value?.label ?: "").toHtmlLabel()
```

- [ ] **Step 5: Verify it compiles**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt
git commit -m "fix: replace unsupported Unicode symbols in CommandPanel; use HTML labels for emoji"
```

---

### Task 3: Prefer Cascadia Code over Cascadia Mono in font preference lists

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/QuickLaunchTerminalSettings.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtils.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/logviewer/LogViewerPanel.kt`

Cascadia Code has broader Unicode coverage than Cascadia Mono. Once selected as the primary font, Java's AWT fallback uses Segoe UI Emoji (Windows) for any glyph absent from Cascadia Code.

- [ ] **Step 1: Fix QuickLaunchTerminalSettings.kt**

Read the file. Find `terminalFontName` lazy val (around line 33). The Windows branch is:
```kotlin
os.contains("win") -> listOf("Cascadia Mono", "Cascadia Code", "Consolas", "Lucida Console")
```
Change to:
```kotlin
os.contains("win") -> listOf("Cascadia Code", "Cascadia Mono", "Consolas", "Lucida Console")
```

- [ ] **Step 2: Fix SettingsUtils.kt**

Read the file. Find `monoFont()` (around line 12). The Windows branch is:
```kotlin
IS_WINDOWS -> listOf("Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Consolas")
```
Change to:
```kotlin
IS_WINDOWS -> listOf("Cascadia Code", "Cascadia Mono", "JetBrains Mono", "Consolas")
```

- [ ] **Step 3: Fix LogViewerPanel.kt**

Read the file. Find `monoFont()` at the bottom (around line 420). The Windows branch is:
```kotlin
os.contains("win") -> listOf("Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Fira Code", "Consolas")
```
Change to:
```kotlin
os.contains("win") -> listOf("Cascadia Code", "Cascadia Mono", "JetBrains Mono", "Fira Code", "Consolas")
```

- [ ] **Step 4: Verify it compiles**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/QuickLaunchTerminalSettings.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtils.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/logviewer/LogViewerPanel.kt
git commit -m "fix: prefer Cascadia Code over Cascadia Mono for better Unicode/emoji coverage"
```

---

### Task 4: Log viewer — surface the app log

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/logviewer/LogViewerPanel.kt`
- Test: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/AppLogFilesTest.kt`

The app writes to `${user.home}/.needlecast/needlecast.log` (logback rolling, up to 5 archives). `LogFileScanner.scan()` never finds these. Add a helper `appLogFiles(logDir)` and wire it into `loadProject()`.

- [ ] **Step 1: Write the failing test**

Create `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/AppLogFilesTest.kt`:

```kotlin
package io.github.rygel.needlecast.ui.logviewer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.name

class AppLogFilesTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `returns empty list when no log files exist`() {
        val result = appLogFiles(dir.toFile())
        assert(result.isEmpty()) { "Expected empty but got $result" }
    }

    @Test
    fun `returns base log file when it exists`() {
        dir.resolve("needlecast.log").createFile()
        val result = appLogFiles(dir.toFile())
        assert(result.size == 1) { "Expected 1 file" }
        assert(result[0].name == "needlecast.log")
    }

    @Test
    fun `returns base log and archives in order`() {
        dir.resolve("needlecast.log").createFile()
        dir.resolve("needlecast.log.1").createFile()
        dir.resolve("needlecast.log.3").createFile()   // gap — .2 missing
        val result = appLogFiles(dir.toFile())
        val names = result.map { it.name }
        assert(names == listOf("needlecast.log", "needlecast.log.1", "needlecast.log.3")) {
            "Expected ordered list but got $names"
        }
    }

    @Test
    fun `skips archives beyond index 5`() {
        dir.resolve("needlecast.log").createFile()
        dir.resolve("needlecast.log.6").createFile()   // beyond limit
        val result = appLogFiles(dir.toFile())
        val names = result.map { it.name }
        assert(names == listOf("needlecast.log")) { "Should not include .log.6: $names" }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn test -pl needlecast-desktop -Dtest=AppLogFilesTest --no-transfer-progress -q
```
Expected: FAIL — `appLogFiles` is not yet defined.

- [ ] **Step 3: Add `appLogFiles` helper and update `loadProject()`**

In `LogViewerPanel.kt`, add the top-level internal function just before the class declaration (after the package/imports):

```kotlin
/**
 * Returns the Needlecast app log and its rotation archives (`.log.1`–`.log.5`)
 * from [logDir], in order, filtering to files that actually exist.
 */
internal fun appLogFiles(
    logDir: File = File(System.getProperty("user.home"), ".needlecast"),
): List<File> = buildList {
    val base = File(logDir, "needlecast.log")
    if (base.exists()) add(base)
    for (i in 1..5) {
        val rotated = File(logDir, "needlecast.log.$i")
        if (rotated.exists()) add(rotated)
    }
}
```

Replace the entire `loadProject(path: String?)` function:

```kotlin
fun loadProject(path: String?) {
    tailTimer.stop()
    tailFile = null
    tailOffset = 0
    entries.clear()
    textPane.text = ""
    currentProjectPath = path

    if (path == null) {
        val appLogs = appLogFiles()
        fileCombo.model = DefaultComboBoxModel(appLogs.toTypedArray())
        if (appLogs.isNotEmpty()) fileCombo.selectedIndex = 0
        return
    }

    // Discover project log files in background
    object : SwingWorker<List<java.io.File>, Void>() {
        override fun doInBackground(): List<File> = LogFileScanner.scan(path)
        override fun done() {
            val projectFiles = try { get() } catch (_: Exception) { emptyList() }
            val allFiles = appLogFiles() + projectFiles
            fileCombo.model = DefaultComboBoxModel(allFiles.toTypedArray())
            if (allFiles.isNotEmpty()) {
                fileCombo.selectedIndex = 0
                // onFileSelected() fires via actionListener
            }
        }
    }.execute()
}
```

Update the file combo renderer to label app log files distinctly. Find the `fileCombo` declaration (around line 60) and change the renderer lambda from:

```kotlin
it.toRelativeString(File(currentProjectPath ?: ""))
```
to:
```kotlin
val appLogDir = java.io.File(System.getProperty("user.home"), ".needlecast")
if (it.parentFile == appLogDir) "${it.name} (app)"
else it.toRelativeString(File(currentProjectPath ?: ""))
```

The full updated renderer block inside `fileCombo`:
```kotlin
renderer = object : javax.swing.DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: javax.swing.JList<*>?, value: Any?, index: Int,
        isSelected: Boolean, cellHasFocus: Boolean,
    ) = super.getListCellRendererComponent(list, (value as? File)?.let { f ->
        val appLogDir = File(System.getProperty("user.home"), ".needlecast")
        if (f.parentFile == appLogDir) "${f.name} (app)"
        else f.toRelativeString(File(currentProjectPath ?: ""))
    } ?: "", index, isSelected, cellHasFocus)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
mvn test -pl needlecast-desktop -Dtest=AppLogFilesTest --no-transfer-progress -q
```
Expected: 4 tests PASS

- [ ] **Step 5: Run full test suite**

```
mvn verify -T 4 -pl needlecast-desktop -am --no-transfer-progress -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/logviewer/LogViewerPanel.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/AppLogFilesTest.kt
git commit -m "fix: surface app log (needlecast.log) in Log Viewer panel"
```
