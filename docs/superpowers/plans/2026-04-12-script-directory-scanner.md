# Script Directory Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-detect `scripts/` and `bin/` in each project and let users add extra directories, turning their shell and language scripts into first-class Needlecast commands.

**Architecture:** A new `ScriptDirectoryScanner` implements the existing `ProjectScanner` interface and slots into `CompositeProjectScanner`'s list. The per-project extra directories are stored as `extraScanDirs: List<String>` on the existing `ProjectDirectory` data class. A new "Script Directories…" context menu item in `ProjectTreePanel` lets users manage those extras.

**Tech Stack:** Kotlin, Swing (`JList`, `JFileChooser`, `DefaultListModel`), JUnit 5 (`@TempDir`), existing `ProjectScanner` / `BuildTool` / `CommandDescriptor` / `DetectedProject` types.

---

## File map

| Action | Path |
|--------|------|
| Modify | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/CommandDescriptor.kt` |
| Modify | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt` |
| **Create** | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScanner.kt` |
| Modify | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt` |
| Modify | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt` |
| **Create** | `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScannerTest.kt` |

---

### Task 1: `BuildTool.SCRIPT` + `extraScanDirs` on `ProjectDirectory`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/CommandDescriptor.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`

These two data-model changes have no logic — tests for them come in Task 2 implicitly (the scanner test uses both). Make the changes, verify the project compiles, commit.

- [ ] **Step 1: Add `BuildTool.SCRIPT` to the enum**

Open `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/CommandDescriptor.kt`.

The file currently ends with:

```kotlin
    ZIG("Zig",          "zig",      "#F7A41D"),
}
```

Add the new entry after `ZIG`:

```kotlin
    ZIG("Zig",          "zig",      "#F7A41D"),
    SCRIPT("Script",    "script",   "#5D7B6F"),
}
```

- [ ] **Step 2: Add `extraScanDirs` to `ProjectDirectory`**

Open `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`.

Find `ProjectDirectory` (around line 638). The class currently ends with:

```kotlin
    val startupCommand: String? = null,
) {
    fun label(): String = displayName ?: path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}
```

Add the new field before the closing `}`:

```kotlin
    val startupCommand: String? = null,
    /**
     * Additional directories to scan for shell and language scripts,
     * stored relative to [path] when possible.
     */
    val extraScanDirs: List<String> = emptyList(),
) {
    fun label(): String = displayName ?: path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}
```

- [ ] **Step 3: Verify the project compiles**

```bash
cd needlecast-desktop && mvn compile -q -T 4
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/CommandDescriptor.kt \
        needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt
git commit -m "feat: add BuildTool.SCRIPT and ProjectDirectory.extraScanDirs"
```

---

### Task 2: `ScriptDirectoryScanner` (TDD)

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScannerTest.kt`
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScanner.kt`

- [ ] **Step 1: Write the failing test file**

Create `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScannerTest.kt` with all 8 tests:

```kotlin
package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ScriptDirectoryScannerTest {

    private val scanner = ScriptDirectoryScanner()

    @Test
    fun `returns null when no scripts bin or extraScanDirs`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `auto-detects scripts dir`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        val script = File(scriptsDir, "deploy.sh").also { it.writeText("#!/bin/bash") }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.SCRIPT), result.buildTools)
        assertEquals(1, result.commands.size)
        val cmd = result.commands[0]
        assertEquals("scripts/deploy.sh", cmd.label)
        assertEquals(listOf("bash", script.canonicalPath), cmd.argv)
        assertEquals(dir.toString(), cmd.workingDirectory)
    }

    @Test
    fun `auto-detects bin dir`(@TempDir dir: Path) {
        val binDir = File(dir.toFile(), "bin").also { it.mkdir() }
        File(binDir, "run").writeText("#!/bin/sh")                   // no recognised extension — skipped
        val script = File(binDir, "start.py").also { it.writeText("# python") }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(1, result.commands.size)
        assertEquals("bin/start.py", result.commands[0].label)
        assertEquals(listOf("python3", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `unrecognised extension is skipped`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        File(scriptsDir, "README.md").writeText("# docs")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `ts extension produces npx ts-node argv`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        val script = File(scriptsDir, "build.ts").also { it.writeText("// ts") }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(listOf("npx", "ts-node", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `extraScanDirs relative path resolves against project root`(@TempDir dir: Path) {
        val toolsDir = File(dir.toFile(), "tools").also { it.mkdir() }
        val script = File(toolsDir, "build.sh").also { it.writeText("#!/bin/bash") }

        val result = scanner.scan(
            ProjectDirectory(dir.toString(), extraScanDirs = listOf("tools"))
        )!!

        assertEquals(1, result.commands.size)
        assertEquals("tools/build.sh", result.commands[0].label)
        assertEquals(listOf("bash", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `extraScanDirs absolute path outside project root is accepted`(@TempDir root: Path) {
        val projectDir = Files.createDirectories(root.resolve("myproject"))
        val externalDir = Files.createDirectories(root.resolve("external"))
        val script = File(externalDir.toFile(), "deploy.rb").also { it.writeText("# ruby") }

        val result = scanner.scan(
            ProjectDirectory(
                path = projectDir.toString(),
                extraScanDirs = listOf(externalDir.toString()),
            )
        )!!

        assertEquals(1, result.commands.size)
        assertEquals(listOf("ruby", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `deduplicates dirs when extraScanDirs contains auto-detected dir`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        File(scriptsDir, "deploy.sh").writeText("#!/bin/bash")

        // "scripts" is also auto-detected — command must appear only once
        val result = scanner.scan(
            ProjectDirectory(dir.toString(), extraScanDirs = listOf("scripts"))
        )!!

        assertEquals(1, result.commands.size)
    }
}
```

- [ ] **Step 2: Run tests to confirm they all fail**

```bash
cd needlecast-desktop && mvn test -pl . -Dtest=ScriptDirectoryScannerTest -T 4 2>&1 | tail -15
```

Expected: compilation error (`ScriptDirectoryScanner` not found). That is the correct red state.

- [ ] **Step 3: Implement `ScriptDirectoryScanner`**

Create `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScanner.kt`:

```kotlin
package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.io.File

class ScriptDirectoryScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val root = File(directory.path)

        val candidates = buildList {
            add(File(root, "scripts"))
            add(File(root, "bin"))
            for (extra in directory.extraScanDirs) {
                val f = File(extra)
                add(if (f.isAbsolute) f else File(root, extra))
            }
        }.distinctBy { it.canonicalPath }.filter { it.isDirectory }

        val commands = candidates.flatMap { dir ->
            dir.listFiles()
                ?.filter { it.isFile }
                ?.mapNotNull { file ->
                    val interpreter = interpreterFor(file.name) ?: return@mapNotNull null
                    val label = root.toPath().relativize(file.toPath()).toString()
                        .replace(File.separatorChar, '/')
                    CommandDescriptor(
                        label            = label,
                        buildTool        = BuildTool.SCRIPT,
                        argv             = interpreter + listOf(file.canonicalPath),
                        workingDirectory = directory.path,
                    )
                }
                ?: emptyList()
        }

        if (commands.isEmpty()) return null

        return DetectedProject(
            directory  = directory,
            buildTools = setOf(BuildTool.SCRIPT),
            commands   = commands,
        )
    }

    private fun interpreterFor(filename: String): List<String>? {
        val ext = filename.substringAfterLast('.', "")
        return when (ext) {
            "sh", "bash" -> listOf("bash")
            "zsh"        -> listOf("zsh")
            "fish"       -> listOf("fish")
            "py"         -> listOf("python3")
            "rb"         -> listOf("ruby")
            "js"         -> listOf("node")
            "ts"         -> listOf("npx", "ts-node")
            "pl"         -> listOf("perl")
            "php"        -> listOf("php")
            "ps1"        -> listOf("pwsh")
            else         -> null
        }
    }
}
```

- [ ] **Step 4: Run the tests and verify they all pass**

```bash
cd needlecast-desktop && mvn test -pl . -Dtest=ScriptDirectoryScannerTest -T 4 2>&1 | tail -10
```

Expected: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScannerTest.kt \
        needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScanner.kt
git commit -m "feat: implement ScriptDirectoryScanner with interpreter mapping"
```

---

### Task 3: Register `ScriptDirectoryScanner` in `CompositeProjectScanner`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt`

- [ ] **Step 1: Add `ScriptDirectoryScanner` to the scanner list**

Open `CompositeProjectScanner.kt`. The constructor's `scanners` list currently ends with:

```kotlin
        ElixirProjectScanner(),
        ZigProjectScanner(),
    ),
```

Append the new scanner:

```kotlin
        ElixirProjectScanner(),
        ZigProjectScanner(),
        ScriptDirectoryScanner(),
    ),
```

No other changes. The existing per-scanner try-catch already isolates failures.

- [ ] **Step 2: Run the full scanner test suite to check nothing regressed**

```bash
cd needlecast-desktop && mvn test -pl . -Dtest="CompositeProjectScannerIntegrationTest,ScriptDirectoryScannerTest" -T 4 2>&1 | tail -10
```

Expected: all tests pass, no failures.

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt
git commit -m "feat: register ScriptDirectoryScanner in CompositeProjectScanner"
```

---

### Task 4: UI — "Script Directories…" context menu item

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt`

No unit test — AWT dialog interaction, consistent with `editShellSettings` and `editEnv` which also have no dedicated unit tests.

- [ ] **Step 1: Add missing imports**

`ProjectTreePanel.kt` already imports `JFileChooser`, `BorderLayout`, `FlowLayout`, `JButton`, `JPanel`, `JScrollPane`, `JLabel`, and `SwingUtilities`. Three new ones are needed. Add them with the other `javax.swing` imports:

```kotlin
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel
```

- [ ] **Step 2: Add the context menu item**

Find the block in `ProjectTreePanel.kt` at around line 992:

```kotlin
                menu.add(JMenuItem("Shell Settings\u2026").apply { addActionListener { editShellSettings(node, entry) } })
                menu.add(JMenuItem("Environment\u2026").apply { addActionListener { editEnv(node, entry) } })
```

Add the new item immediately after "Environment…":

```kotlin
                menu.add(JMenuItem("Shell Settings\u2026").apply { addActionListener { editShellSettings(node, entry) } })
                menu.add(JMenuItem("Environment\u2026").apply { addActionListener { editEnv(node, entry) } })
                menu.add(JMenuItem("Script Directories\u2026").apply { addActionListener { editScriptDirs(node, entry) } })
```

- [ ] **Step 3: Add `editScriptDirs()` and `makeRelativeIfPossible()` helper methods**

Place these two methods directly after the `editEnv()` method (around line 834):

```kotlin
    private fun editScriptDirs(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        val dir   = entry.directory
        val listModel = DefaultListModel<String>().apply { dir.extraScanDirs.forEach { addElement(it) } }
        val list      = JList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 6
        }
        val addBtn    = JButton("Add\u2026")
        val removeBtn = JButton("Remove").apply { isEnabled = false }

        list.addListSelectionListener { removeBtn.isEnabled = list.selectedIndex >= 0 }

        addBtn.addActionListener {
            val chooser = JFileChooser(dir.path).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return@addActionListener
            val stored = makeRelativeIfPossible(chooser.selectedFile.canonicalPath, dir.path)
            if ((0 until listModel.size).none { listModel.getElementAt(it) == stored }) listModel.addElement(stored)
        }

        removeBtn.addActionListener {
            val i = list.selectedIndex
            if (i >= 0) listModel.remove(i)
        }

        val form = JPanel(BorderLayout(4, 4)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(JLabel("<html><small>\"scripts/\" and \"bin/\" in the project root are always scanned automatically.</small></html>"),
                BorderLayout.NORTH)
            add(JScrollPane(list), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(addBtn); add(removeBtn)
            }, BorderLayout.SOUTH)
        }

        if (JOptionPane.showConfirmDialog(owner, form, "Script Directories \u2014 ${dir.label()}",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return

        val newDirs = (0 until listModel.size).map { listModel.getElementAt(it) }
        node.userObject = entry.copy(directory = dir.copy(extraScanDirs = newDirs))
        treeModel.nodeChanged(node)
        persist()
        scanProject(dir.copy(extraScanDirs = newDirs))
    }

    /** Returns [absolute] as a path relative to [base] when it is a subdirectory, otherwise returns [absolute]. */
    private fun makeRelativeIfPossible(absolute: String, base: String): String {
        val rel = File(base).toPath().relativize(File(absolute).toPath()).toString()
        return if (rel.startsWith("..")) absolute else rel
    }
```

- [ ] **Step 4: Compile to verify no errors**

```bash
cd needlecast-desktop && mvn compile -q -T 4
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Run the full non-UI test suite**

```bash
cd needlecast-desktop && mvn test -T 4 -Dexclude="**/*UiTest*,**/*ScreenshotTour*" 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git commit -m "feat: add Script Directories context menu item to ProjectTreePanel"
```
