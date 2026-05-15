# Diff Viewer Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the DiffViewerPanel from GitLogPanel into its own bottom dockable panel.

**Architecture:** DiffViewerPanel becomes a standalone DockablePanel registered with ModernDocking and docked SOUTH of the terminal. GitLogPanel keeps the commit list, staging card, and output card, communicating diff results to the external viewer via an `onCommitSelected` callback wired in MainWindow.

**Tech Stack:** Kotlin/Swing, ModernDocking, AssertJ Swing for tests

---

### Task 1: Add `onCommitSelected` callback to GitLogPanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt:43-56` (class header and fields)

- [ ] **Step 1: Add the callback property and modify `showCommit()` to use it**

In `GitLogPanel.kt`, add a public callback property after the `fileOpener` constructor parameter:

```kotlin
class GitLogPanel(
    private val gitService: GitService = ProcessGitService(),
    private val fileOpener: ((String) -> Unit)? = null,
) : JPanel(BorderLayout()) {
```

Add a new property after `diffViewer`:

```kotlin
    var onCommitSelected: ((DiffResult) -> Unit)? = null
```

Then modify `showCommit()` (currently around line 312). Remove all debug `println` statements. Change the `done()` block to also invoke the callback:

```kotlin
    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker = object : SwingWorker<DiffResult, Void>() {
            override fun doInBackground(): DiffResult {
                val raw = gitService.show(path, hash) ?: return DiffResult(emptyList(), DiffStats(0, 0))
                val truncated = if (raw.length > maxDiffChars) raw.take(maxDiffChars) else raw
                return DiffParser.parse(truncated)
            }
            override fun done() {
                if (isCancelled) return
                val result = try { get() } catch (_: Exception) { return }
                onCommitSelected?.invoke(result)
            }
        }.also { it.execute() }
    }
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt
git commit -m "refactor(git): add onCommitSelected callback to GitLogPanel"
```

---

### Task 2: Remove DiffViewerPanel from GitLogPanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt` (remove diffViewer field, simplify log card, remove display calls)

- [ ] **Step 1: Remove the `diffViewer` field and its imports**

Remove the `diffViewer` field:
```kotlin
// DELETE THIS LINE:
    private val diffViewer = DiffViewerPanel(fileOpener)
```

Remove these unused imports:
```kotlin
// DELETE these imports:
import io.github.rygel.needlecast.ui.diff.DiffParser
import io.github.rygel.needlecast.ui.diff.DiffResult
import io.github.rygel.needlecast.ui.diff.DiffStats
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
```

Keep the `DiffResult` and `DiffParser` imports — they are still needed in `showCommit()`.

Actually wait — `DiffResult` and `DiffParser` ARE needed in `showCommit()`. So only remove `DiffViewerPanel` and `DiffStats` imports. Check if `DiffStats` is used — it's used in the `DiffResult(emptyList(), DiffStats(0, 0))` fallback, so keep it.

Only remove:
```kotlin
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
```

- [ ] **Step 2: Replace the log card's JSplitPane with just the commit list**

In the `init` block, replace the `split` variable and its usage. Currently:

```kotlin
        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(logList).apply { minimumSize = Dimension(0, 0) },
            diffViewer,
        ).apply { resizeWeight = 0.4 }
```

Replace with:

```kotlin
        val logCard = JScrollPane(logList).apply { minimumSize = Dimension(0, 0) }
```

Then update the `cardPanel.add` line. Currently:
```kotlin
        cardPanel.add(split,              "log")
```

Replace with:
```kotlin
        cardPanel.add(logCard,            "log")
```

- [ ] **Step 3: Remove `diffViewer.display()` and `diffViewer.displayEmpty()` calls**

In `loadProject()`, replace all `diffViewer.displayEmpty(...)` calls. The method has these usages:
- `diffViewer.displayEmpty("")` — when path is null
- `diffViewer.displayEmpty("Loading commits…")` — at start of load
- In `done()`: `diffViewer.displayEmpty("Select a commit to view details.")` and `diffViewer.displayEmpty("No commits found.")`

Remove all `diffViewer.displayEmpty(...)` calls. The commit list itself is sufficient feedback.

In `showCommit()` `done()`, the `diffViewer.display(result)` call should already be removed from Task 1 (replaced by `onCommitSelected?.invoke(result)`).

- [ ] **Step 4: Remove the `pendingDiffWorker` field (already done) and `maxDiffChars` if not used**

`pendingDiffWorker` is still needed. `maxDiffChars` is still used in `showCommit()`. Keep both.

- [ ] **Step 5: Compile to verify**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt
git commit -m "refactor(git): remove DiffViewerPanel from GitLogPanel"
```

---

### Task 3: Create DiffViewerPanel as a standalone dockable in MainWindow

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt` (multiple locations)

- [ ] **Step 1: Add DiffViewerPanel import and instance**

Add import at the top of MainWindow.kt:
```kotlin
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
```

After the `gitLogPanel` field (line 97), add:
```kotlin
    private val diffViewerPanel = DiffViewerPanel { path ->
        explorerPanel.openFile(java.io.File(path))
    }
```

Change the `gitLogPanel` constructor to remove the `fileOpener` lambda (it no longer owns the diff viewer):
```kotlin
    private val gitLogPanel = GitLogPanel(ctx.gitService)
```

- [ ] **Step 2: Create the diff dockable and register it**

After `gitLogDockable` (line 149), add:
```kotlin
    private val diffDockable        = DockablePanel(diffViewerPanel, "diff-viewer", "Diff")
```

In the `init` block, after the `Docking.registerDockable(gitLogDockable)` line (line 234), add:
```kotlin
            Docking.registerDockable(diffDockable)
```

- [ ] **Step 3: Wire `onCommitSelected` callback**

After the `gitLogPanel` is created and before `init { ... }`, add the callback wiring. Actually, add it inside `init` after the gitLogPanel construction. In the `init` block, add after the terminal callbacks:

```kotlin
        gitLogPanel.onCommitSelected = { result ->
            diffViewerPanel.display(result)
            if (dockingEnabled && !Docking.isDocked(diffDockable)) {
                toggleDiff(true)
            }
        }
```

- [ ] **Step 4: Add `toggleDiff()` method**

Add near the other toggle methods (around line 555):
```kotlin
    private fun toggleDiff(show: Boolean) {
        if (show && !Docking.isDocked(diffDockable)) {
            Docking.dock(diffDockable, terminalDockable, DockingRegion.SOUTH, 0.25)
        } else if (!show && Docking.isDocked(diffDockable)) {
            Docking.undock(diffDockable)
        }
    }
```

- [ ] **Step 5: Update `setupDefaultDockingLayout()`**

After the prompt input docking (around line 466), add:
```kotlin
        // 10. Diff viewer at the bottom
        Docking.dock(diffDockable, terminalDockable, DockingRegion.SOUTH, 0.25)
```

- [ ] **Step 6: Add to `allDockables`**

In the `allDockables` property (line 1152), add `diffDockable` to the list:
```kotlin
    private val allDockables get() = listOf(
        projectTreeDockable, terminalDockable, commandsDockable, gitLogDockable,
        logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable,
        promptInputDockable, commandInputDockable, docsDockable, docViewerDockable, skillsDockable, diffDockable,
    )
```

- [ ] **Step 7: Add to `applyDockingLayout()` reset list**

In `applyDockingLayout()` (line 411), add `diffDockable` to the undock list:
```kotlin
            listOf(projectTreeDockable, terminalDockable, commandsDockable,
                   gitLogDockable, logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable, promptInputDockable, commandInputDockable, docsDockable, docViewerDockable, skillsDockable, diffDockable)
                .forEach { if (Docking.isDocked(it)) Docking.undock(it) }
```

Also add `diffDockable` to the `requiredPanels` list if desired, but it's closable so it's not strictly required.

- [ ] **Step 8: Add to `resetLayout()` undock list**

In `resetLayout()` (line 476), add `diffDockable`:
```kotlin
        listOf(projectTreeDockable, terminalDockable, commandsDockable,
               gitLogDockable, logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable, promptInputDockable, docsDockable, docViewerDockable, skillsDockable, diffDockable)
            .forEach { if (Docking.isDocked(it)) Docking.undock(it) }
```

- [ ] **Step 9: Add "Diff" checkbox to Windows > Panels menu**

In `buildWindowsMenu()` (line 1035), add a checkbox after `gitLogCb`:
```kotlin
        val diffCb = JCheckBoxMenuItem("Diff").apply {
            addActionListener { toggleDiff(isSelected) }
        }
```

In `syncState()`, add:
```kotlin
            diffCb.isSelected = Docking.isDocked(diffDockable)
```

In the menu `add()` calls, add after `gitLogCb`:
```kotlin
            add(diffCb)
```

- [ ] **Step 10: Compile to verify**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat(diff): add DiffViewerPanel as standalone bottom dockable"
```

---

### Task 4: Update GitLogPanelUiTest

**Files:**
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt`

- [ ] **Step 1: Remove DiffViewerPanel assertions from commit display test**

The test `"clicking a commit displays the parsed diff in the viewer"` (line 94) currently looks up `diffViewer` inside GitLogPanel and asserts on its document content. Since GitLogPanel no longer contains a DiffViewerPanel, this test needs to change.

The new test should verify that `onCommitSelected` was called with a valid result. Update the test to:

1. Remove `diffViewer` field and its lookup
2. Capture the callback invocation
3. Assert on the DiffResult content

Replace the test with:

```kotlin
    @Test
    fun `clicking a commit invokes onCommitSelected with parsed diff result`() {
        val diffOutput = buildString {
            appendLine("commit abc123")
            appendLine("Author: Test")
            appendLine("Date:   Now")
            appendLine()
            appendLine("    test commit")
            appendLine()
            appendLine(" 1 file changed, 1 insertion(+), 1 deletion(-)")
            appendLine()
            appendLine("diff --git a/src/Main.kt b/src/Main.kt")
            appendLine("--- a/src/Main.kt")
            appendLine("+++ b/src/Main.kt")
            appendLine("@@ -1 +1 @@")
            appendLine("-old line")
            appendLine("+new line")
        }
        val fake = FakeGitService(logLines = "abc123 Commit one\n", showOutput = diffOutput)
        panel = GuiActionRunner.execute(object : GuiQuery<GitLogPanel>() {
            override fun executeInEDT(): GitLogPanel = GitLogPanel(fake)
        })

        var capturedResult: DiffResult? = null
        panel.onCommitSelected = { result -> capturedResult = result }

        fixture = showInFrame(panel)
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { panel.loadProject(tempDir.toString()) }
        })
        waitForListSize(1, 2_000)

        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { list.selectedIndex = 0 }
        })

        waitUntil(5_000) { capturedResult != null }

        val result = capturedResult!!
        assertEquals(1, result.files.size, "Expected 1 file in diff result")
        assertEquals("src/Main.kt", result.files[0].filePath)
    }
```

- [ ] **Step 2: Remove `diffViewer` field from test class**

Remove:
```kotlin
    private lateinit var diffViewer: DiffViewerPanel
```

Remove the `diffViewer` lookup in `showInFrame()`:
```kotlin
        diffViewer = robot.finder().findByName(panel, "diff-viewer", DiffViewerPanel::class.java, true)
```

- [ ] **Step 3: Update imports**

Add:
```kotlin
import io.github.rygel.needlecast.ui.diff.DiffResult
```

Remove if no longer used:
```kotlin
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl needlecast-desktop -Dtest="GitLogPanelUiTest" -T 4`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt
git commit -m "test(git): update GitLogPanelUiTest for callback-based diff"
```

---

### Task 5: Update DiffViewerE2ETest

**Files:**
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffViewerE2ETest.kt`

- [ ] **Step 1: Rewrite tests to use the callback-based approach**

Since GitLogPanel no longer contains a DiffViewerPanel, the E2E tests need to manually wire the callback and test DiffViewerPanel directly alongside GitLogPanel.

Replace the entire test file with a version that:
1. Creates GitLogPanel and DiffViewerPanel separately
2. Wires `gitLogPanel.onCommitSelected = { diffViewerPanel.display(it) }`
3. Places both in a JFrame
4. Verifies DiffViewerPanel content after selecting a commit

```kotlin
package io.github.rygel.needlecast.ui.diff

import io.github.rygel.needlecast.git.ChangedFile
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.model.GitStatus
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import io.github.rygel.needlecast.ui.GitLogPanel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

private class E2EFakeGitService(
    private val logLines: String? = "",
    private val showOutput: String? = "",
) : GitService {
    override fun readStatus(dir: String): GitStatus = GitStatus.NotARepo
    override fun log(dir: String, maxEntries: Int): String? = logLines
    override fun show(dir: String, hash: String): String? = showOutput
    override fun changedFiles(dir: String): List<ChangedFile> = emptyList()
    override fun stage(dir: String, files: List<String>) {}
    override fun commit(dir: String, message: String) {}
    override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int = 0
    override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int = 0
    override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int = 0
}

class DiffViewerE2ETest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var gitLogPanel: GitLogPanel
    private lateinit var diffViewerPanel: DiffViewerPanel

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        robot.settings().delayBetweenEvents(1)
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanUp()
        robot.cleanUp()
    }

    private val sampleDiff = """
commit abc123
Author: Test
Date:   Now

    test commit

 2 files changed, 3 insertions(+), 2 deletions(-)

diff --git a/src/Main.kt b/src/Main.kt
--- a/src/Main.kt
+++ b/src/Main.kt
@@ -10,7 +10,8 @@ class Main {
     fun old() {
-        println("old")
+        println("new")
+        println("extra")
     }
 }
diff --git a/README.md b/README.md
--- a/README.md
+++ b/README.md
@@ -1 +1 @@
-Old readme
+New readme
    """.trimIndent()

    private fun setupFrame(fake: E2EFakeGitService): FrameFixture {
        gitLogPanel = GuiActionRunner.execute(object : GuiQuery<GitLogPanel>() {
            override fun executeInEDT(): GitLogPanel = GitLogPanel(fake)
        })
        diffViewerPanel = GuiActionRunner.execute(object : GuiQuery<DiffViewerPanel>() {
            override fun executeInEDT(): DiffViewerPanel = DiffViewerPanel()
        })
        gitLogPanel.onCommitSelected = { result -> diffViewerPanel.display(result) }

        val frame = GuiActionRunner.execute(object : GuiQuery<JFrame>() {
            override fun executeInEDT(): JFrame = JFrame("E2E Test").apply {
                contentPane.add(gitLogPanel, BorderLayout.NORTH)
                contentPane.add(diffViewerPanel, BorderLayout.CENTER)
                setSize(900, 600)
            }
        })
        val fix = FrameFixture(robot, frame)
        fix.show()
        robot.waitForIdle()
        return fix
    }

    private fun selectFirstCommit() {
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { gitLogPanel.loadProject(tempDir.toString()) }
        })
        waitForCondition(2_000) {
            robot.finder().findByName(gitLogPanel, "log-list", JList::class.java, true).model.size == 1
        }

        val logList = robot.finder().findByName(gitLogPanel, "log-list", JList::class.java, true)
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { logList.selectedIndex = 0 }
        })
    }

    @Test
    fun `DiffViewerPanel renders left and right panes with diff content`() {
        val fake = E2EFakeGitService(
            logLines = "abc123 Test commit\n",
            showOutput = sampleDiff,
        )
        fixture = setupFrame(fake)
        selectFirstCommit()

        waitForCondition(5_000) {
            GuiActionRunner.execute(object : GuiQuery<Boolean>() {
                override fun executeInEDT(): Boolean = diffViewerPanel.contentPanel.leftPane.styledDocument.length > 0
            })
        }

        val leftText = GuiActionRunner.execute(object : GuiQuery<String>() {
            override fun executeInEDT(): String {
                val doc = diffViewerPanel.contentPanel.leftPane.styledDocument
                return doc.getText(0, doc.length)
            }
        })
        val rightText = GuiActionRunner.execute(object : GuiQuery<String>() {
            override fun executeInEDT(): String {
                val doc = diffViewerPanel.contentPanel.rightPane.styledDocument
                return doc.getText(0, doc.length)
            }
        })

        assertTrue(leftText.contains("println"), "Left pane should contain removed code. Got: [$leftText]")
        assertTrue(rightText.contains("println"), "Right pane should contain added code. Got: [$rightText]")
        assertTrue(leftText.contains("old"), "Left pane should show 'old'. Got: [$leftText]")
        assertTrue(rightText.contains("new"), "Right pane should show 'new'. Got: [$rightText]")
        assertTrue(rightText.contains("extra"), "Right pane should show 'extra'. Got: [$rightText]")
    }

    @Test
    fun `file tree shows all changed files from diff`() {
        val fake = E2EFakeGitService(
            logLines = "abc123 Test commit\n",
            showOutput = sampleDiff,
        )
        fixture = setupFrame(fake)
        selectFirstCommit()

        waitForCondition(5_000) {
            val tree = GuiActionRunner.execute(object : GuiQuery<JTree?>() {
                override fun executeInEDT(): JTree? = findDescendant(diffViewerPanel, JTree::class.java)
            })
            tree != null && (tree.model as? DefaultTreeModel)?.root?.let { (it as javax.swing.tree.TreeNode).childCount } == 2
        }

        val fileTree = GuiActionRunner.execute(object : GuiQuery<JTree?>() {
            override fun executeInEDT(): JTree? = findDescendant(diffViewerPanel, JTree::class.java)
        })!!
        val root = fileTree.model.root as javax.swing.tree.TreeNode
        assertEquals(2, root.childCount, "File tree should show 2 files")
    }

    @Test
    fun `side-by-side split panes exist in content panel`() {
        val fake = E2EFakeGitService(
            logLines = "abc123 Test commit\n",
            showOutput = sampleDiff,
        )
        fixture = setupFrame(fake)
        selectFirstCommit()

        waitForCondition(5_000) {
            val splitFound = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
                override fun executeInEDT(): Boolean {
                    val hasSplit = findDescendant(diffViewerPanel.contentPanel as Container, JSplitPane::class.java) != null
                    val leftHasText = diffViewerPanel.contentPanel.leftPane.styledDocument.length > 0
                    return hasSplit && leftHasText
                }
            })
            splitFound
        }

        val hasSplit = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
            override fun executeInEDT(): Boolean {
                return findDescendant(diffViewerPanel.contentPanel as Container, JSplitPane::class.java) != null
            }
        })
        assertTrue(hasSplit, "Content panel should contain a JSplitPane for side-by-side view")
    }

    private fun waitForCondition(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000L)
        while (System.nanoTime() < deadline) {
            val met = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
                override fun executeInEDT(): Boolean = condition()
            })
            if (met) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for condition")
    }

    private fun <T : java.awt.Component> findDescendant(parent: Container, type: Class<T>): T? {
        for (comp in parent.components) {
            if (type.isInstance(comp)) return type.cast(comp)
            if (comp is Container) {
                val found = findDescendant(comp, type)
                if (found != null) return found
            }
        }
        return null
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl needlecast-desktop -Dtest="DiffViewerE2ETest" -T 4`
Expected: All 3 tests pass

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffViewerE2ETest.kt
git commit -m "test(diff): update E2E tests for standalone DiffViewerPanel"
```

---

### Task 6: Run full test suite and verify

**Files:** None

- [ ] **Step 1: Run all diff-related tests**

Run: `mvn test -pl needlecast-desktop -Dtest="DiffParserTest,WordDiffCalculatorTest,DiffEditorPaneTest,GitLogPanelUiTest,DiffViewerE2ETest" -T 4`
Expected: All tests pass

- [ ] **Step 2: Build the fat jar**

Run: `mvn package -pl needlecast-desktop -DskipTests -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 3: Delete persisted docking layout**

The old docking layout will be stale. Delete it so the new default layout is used:

Run: `Remove-Item "$env:USERPROFILE\.needlecast\docking-layout.xml" -ErrorAction SilentlyContinue`

- [ ] **Step 4: Run the app and verify visually**

Run: `java -jar needlecast-desktop\target\needlecast.jar`

Expected: App launches. Select a project, go to Git Log, select a commit. The Diff panel should appear at the bottom of the window showing the side-by-side diff view.
