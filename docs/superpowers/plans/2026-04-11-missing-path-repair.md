# Missing-Path Repair via Drag-and-Drop — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a project's directory is missing and the user drops a directory with the same final name onto the tree, show a confirmation dialog and, on acceptance, update the project's path in place instead of adding a new entry.

**Architecture:** `findMissingMatch` (internal, testable) does a depth-first walk of the tree model to find the first missing project whose final directory name matches the dropped name; `confirmRepairPath` (private) shows a `JOptionPane` modal on the EDT and applies the update; `importExternal` is modified to pre-scan each dropped directory before the normal insert flow.

**Tech Stack:** Kotlin, Swing (`JOptionPane`, `DefaultTreeModel`), JUnit 5, AssertJ Swing (GuiActionRunner for EDT safety)

---

## File Map

| File | Change |
|---|---|
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt` | Add `namesMatch`, `findMissingMatch`, `confirmRepairPath`; modify `importExternal` |
| `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanelExternalDropUiTest.kt` | Add 5 tests for `findMissingMatch` |

---

### Task 1: Write failing tests for `findMissingMatch`

**Files:**
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanelExternalDropUiTest.kt`

- [ ] **Step 1: Add imports needed by the new tests**

Open `ProjectTreePanelExternalDropUiTest.kt`. The existing imports already cover `AppConfig`, `ProjectTreeEntry`, `ProjectDirectory`, `GuiActionRunner`, `TempDir`. Add these two missing ones after the existing import block:

```kotlin
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import javax.swing.tree.DefaultMutableTreeNode
```

(Check what's already imported and only add what's missing — `assertNotNull`/`assertNull` may already be present.)

- [ ] **Step 2: Append the five new tests at the end of the class, before the closing `}`**

```kotlin
    // ── findMissingMatch tests ───────────────────────────────────────────────

    @Test
    fun `findMissingMatch returns node when name matches missing project`() {
        // Use a path that doesn't exist on disk so it becomes a missing project
        val missingPath = tempDir.resolve("ghost").resolve("myapp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = missingPath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNotNull(match, "should find the missing node")
        val entry = (match!!.userObject as ProjectTreeEntry.Project)
        assertEquals(missingPath, entry.directory.path)
    }

    @Test
    fun `findMissingMatch returns null when name matches a present project`() {
        val presentDir = newDir("myapp")   // exists on disk
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = presentDir.absolutePath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNull(match, "present projects should not be candidates for repair")
    }

    @Test
    fun `findMissingMatch returns null when no project name matches`() {
        val missingPath = tempDir.resolve("ghost").resolve("myapp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = missingPath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("different-name")
        }

        assertNull(match, "no match expected for unrelated name")
    }

    @Test
    fun `findMissingMatch is case-insensitive on Windows`() {
        assumeTrue(IS_WINDOWS, "Windows-only: case-insensitive name matching")
        val missingPath = tempDir.resolve("ghost").resolve("MyApp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = missingPath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNotNull(match, "Windows: 'myapp' should match 'MyApp'")
    }

    @Test
    fun `findMissingMatch returns first match in tree order when two missing projects share a name`() {
        val firstPath  = tempDir.resolve("first").resolve("myapp").toString()
        val secondPath = tempDir.resolve("second").resolve("myapp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = firstPath)),
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = secondPath)),
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNotNull(match)
        val entry = (match!!.userObject as ProjectTreeEntry.Project)
        assertEquals(firstPath, entry.directory.path, "first entry in tree order should win")
    }
```

- [ ] **Step 3: Run the tests to verify they fail with "Unresolved reference: findMissingMatch"**

```
cd needlecast-desktop
mvn test -Ptest-desktop -pl . -Dtest=ProjectTreePanelExternalDropUiTest -q 2>&1 | tail -30
```

Expected: compilation failure mentioning `findMissingMatch` is not resolved.

- [ ] **Step 4: Implement `namesMatch` and `findMissingMatch` on `ProjectTreePanel`**

In `ProjectTreePanel.kt`, find the block ending at line 1280 (`simulateExternalDropForTest` closing brace). Insert the following two functions **after** line 1280, before `findFolderNodeByName` at line 1282:

```kotlin
    /**
     * Depth-first walk: returns the first tree node whose project path is in
     * [missingPaths] and whose final directory segment matches [droppedName].
     * Returns null if no such node exists.
     */
    internal fun findMissingMatch(droppedName: String): DefaultMutableTreeNode? {
        fun walk(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            val e = node.userObject
            if (e is ProjectTreeEntry.Project && e.directory.path in missingPaths) {
                if (namesMatch(File(e.directory.path).name, droppedName)) return node
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i) as DefaultMutableTreeNode)?.let { return it }
            }
            return null
        }
        return walk(rootNode)
    }

    private fun namesMatch(a: String, b: String): Boolean =
        if (IS_WINDOWS) a.equals(b, ignoreCase = true) else a == b

```

- [ ] **Step 5: Run the tests to verify they pass**

```
cd needlecast-desktop
mvn test -Ptest-desktop -pl . -Dtest=ProjectTreePanelExternalDropUiTest -q 2>&1 | tail -30
```

Expected: all tests in `ProjectTreePanelExternalDropUiTest` pass (5 new + 6 existing).

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanelExternalDropUiTest.kt
git commit -m "feat: add findMissingMatch for missing-path repair"
```

---

### Task 2: Implement `confirmRepairPath` and wire it into `importExternal`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt`

- [ ] **Step 1: Add `confirmRepairPath` to `ProjectTreePanel`**

Insert the following function immediately after the `namesMatch` function added in Task 1 (between `namesMatch` and `findFolderNodeByName`):

```kotlin
    /**
     * Called on the EDT. Shows a modal dialog asking the user whether to repair
     * the missing project path or add the dropped directory as a new entry.
     *
     * Returns `true` if the user chose Replace (the dropped dir is consumed and
     * should NOT be inserted as a new project). Returns `false` if the user
     * chose "Add as new project" or dismissed the dialog.
     */
    private fun confirmRepairPath(missingNode: DefaultMutableTreeNode, newPath: String): Boolean {
        val entry = missingNode.userObject as ProjectTreeEntry.Project
        val oldPath = entry.directory.path
        val projectName = File(oldPath).name
        val choice = JOptionPane.showOptionDialog(
            this,
            "Project:  $projectName\nOld path: $oldPath\nNew path: $newPath",
            "Replace missing project path?",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            arrayOf("Replace", "Add as new project"),
            "Replace",
        )
        if (choice != 0) return false   // "Add as new project" or dialog closed

        val updatedDirectory = entry.directory.copy(path = newPath)
        missingNode.userObject = entry.copy(directory = updatedDirectory)
        missingPaths.remove(oldPath)
        updateMissingPath(newPath)
        treeModel.nodeChanged(missingNode)
        persist()
        scanProject(updatedDirectory)
        return true
    }

```

- [ ] **Step 2: Modify `importExternal` inside `TreeTransferHandler` to pre-scan for missing matches**

Find `importExternal` at line 1443 (inside `private inner class TreeTransferHandler`). Replace the current body:

```kotlin
        /** Handles a drop of one or more folders dragged from the OS file manager. */
        private fun importExternal(support: TransferSupport): Boolean {
            val (dirs, files) = entriesFromExternal(support)
            if (dirs.isEmpty() && files.isEmpty()) return false
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val (newParent, startIndex) = resolveDropTarget(dl, centeredFolderDrop(dl)) ?: return false
            return doImportExternal(dirs, files, newParent, startIndex)
        }
```

With the new version:

```kotlin
        /** Handles a drop of one or more folders dragged from the OS file manager. */
        private fun importExternal(support: TransferSupport): Boolean {
            val (dirs, files) = entriesFromExternal(support)
            if (dirs.isEmpty() && files.isEmpty()) return false
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val (newParent, startIndex) = resolveDropTarget(dl, centeredFolderDrop(dl)) ?: return false

            val remainingDirs = mutableListOf<File>()
            for (dir in dirs) {
                val match = findMissingMatch(dir.name)
                if (match != null) {
                    val consumed = confirmRepairPath(match, dir.absolutePath)
                    if (!consumed) remainingDirs += dir
                } else {
                    remainingDirs += dir
                }
            }
            return doImportExternal(remainingDirs, files, newParent, startIndex)
        }
```

- [ ] **Step 3: Compile-check with a full build (no tests yet)**

```
mvn compile -pl needlecast-desktop -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS, no errors.

- [ ] **Step 4: Run the full non-UI test suite to check for regressions**

```
mvn test -pl needlecast-desktop -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git commit -m "feat: repair missing project path via drag-and-drop"
```

---

### Task 3: Full build + open PR

**Files:** none — verification and PR only

- [ ] **Step 1: Run the full build including UI tests**

```
mvn verify -Ptest-desktop -T 4 2>&1 | tail -40
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Check for merge conflicts against develop**

```bash
git fetch origin develop
git merge origin/develop --no-commit --no-ff
git merge --abort
```

Expected: no conflicts. If there are conflicts, resolve them before creating the PR.

- [ ] **Step 3: Push the branch**

```bash
git push -u origin HEAD
```

- [ ] **Step 4: Open the PR targeting `develop`**

```bash
gh pr create --base develop --title "feat: repair missing project path via drag-and-drop" --body "$(cat <<'EOF'
## Summary
- When a directory is dropped onto the project tree, its final name is now pre-checked against missing projects
- If the name matches (case-insensitive on Windows, case-sensitive on macOS/Linux), a confirmation dialog is shown: **Replace** updates the path in place; **Add as new project** falls through to the normal insert flow
- Multiple dropped directories produce one dialog each, in payload order
- If two missing projects share the same directory name, the first in depth-first tree order is the candidate

Closes #[fill in issue number if applicable]

## Test plan
- [ ] `ProjectTreePanelExternalDropUiTest` — 5 new tests for `findMissingMatch` (case-sensitive, case-insensitive Windows guard, first-in-order, present-project is not a candidate, no-match returns null)
- [ ] Manual: mark a project missing (rename its dir), drop the renamed dir onto the tree, click Replace — ⚠ clears, path updates
- [ ] Manual: drop 3 missing-matching dirs in one drag — 3 sequential dialogs fire

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Check for merge conflicts and CI status on the PR**

```bash
gh pr view --json number --jq .number | xargs -I{} gh pr view {} --json mergeable --jq .mergeable
gh pr checks
```

Expected: `MERGEABLE`, all checks green (or running).
