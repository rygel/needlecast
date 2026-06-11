# ProjectTreePanel Decomposition — Scanning + Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract scanning infrastructure and filter logic from ProjectTreePanel into testable, focused classes.

**Architecture:** Two extractions: (1) `ProjectTreeScanCoordinator` owns all scanning state (executor, queue, timers, git cache, build-file-watcher, blink) and communicates via callbacks, (2) enhance existing `ProjectTreeFilter` with `activeOnly` support and a `FilterState` data class, deleting the inline filter duplication from the panel.

**Tech Stack:** Kotlin, JUnit 5, Swing (Timer, SwingWorker), AssertJ

---

### Task 1: Write failing tests for ProjectTreeScanCoordinator

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreeScanCoordinatorTest.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProjectTreeScanCoordinatorTest {
    private fun createCoordinator(
        onScanResult: (ProjectDirectory, DetectedProject) -> Unit = { _, _ -> },
        onGitStatusReady: (String, GitStatus) -> Unit = { _, _ -> },
        requestRepaint: () -> Unit = {},
    ): ProjectTreeScanCoordinator {
        val ctx = TestAppContext()
        return ProjectTreeScanCoordinator(
            ctx = ctx,
            onScanResult = onScanResult,
            onGitStatusReady = onGitStatusReady,
            requestRepaint = requestRepaint,
        )
    }

    @Test
    fun `scanProject calls onScanResult with detected project`(
        @TempDir dir: Path,
    ) {
        val projectDir = dir.toFile()
        val latch = CountDownLatch(1)
        var capturedDir: ProjectDirectory? = null
        var capturedResult: DetectedProject? = null

        val coordinator = createCoordinator(
            onScanResult = { d, r ->
                capturedDir = d
                capturedResult = r
                latch.countDown()
            },
        )

        val pd = ProjectDirectory(path = projectDir.absolutePath)
        coordinator.scanProject(pd)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(projectDir.absolutePath, capturedDir!!.path)
        assertNotNull(capturedResult)
        coordinator.dispose()
    }

    @Test
    fun `scanProject handles scan failure gracefully`() {
        val latch = CountDownLatch(1)
        var capturedResult: DetectedProject? = null

        val coordinator = createCoordinator(
            onScanResult = { _, r ->
                capturedResult = r
                latch.countDown()
            },
        )

        val pd = ProjectDirectory(path = "/nonexistent/path/that/does/not/exist")
        coordinator.scanProject(pd)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(capturedResult)
        assertTrue(capturedResult!!.scanFailed)
        coordinator.dispose()
    }

    @Test
    fun `clearAll clears git status cache`() {
        val coordinator = createCoordinator()
        coordinator.gitStatusCache["/test"] = GitStatus(branch = "main", isDirty = false)
        assertEquals(1, coordinator.gitStatusCache.size)

        coordinator.clearAll()
        assertTrue(coordinator.gitStatusCache.isEmpty())
        coordinator.dispose()
    }

    @Test
    fun `updateAgentStatus starts blink timer when any agent thinking`() {
        val coordinator = createCoordinator()
        assertFalse(coordinator.blinkTimerRunning)

        coordinator.updateAgentStatus("/path1", AgentStatus.THINKING)
        assertTrue(coordinator.blinkTimerRunning)

        coordinator.updateAgentStatus("/path1", AgentStatus.IDLE)
        assertFalse(coordinator.blinkTimerRunning)
        coordinator.dispose()
    }

    @Test
    fun `updateAgentStatus keeps blink running if other agents still thinking`() {
        val coordinator = createCoordinator()

        coordinator.updateAgentStatus("/path1", AgentStatus.THINKING)
        coordinator.updateAgentStatus("/path2", AgentStatus.THINKING)

        coordinator.updateAgentStatus("/path1", AgentStatus.IDLE)
        assertTrue(coordinator.blinkTimerRunning)

        coordinator.updateAgentStatus("/path2", AgentStatus.IDLE)
        assertFalse(coordinator.blinkTimerRunning)
        coordinator.dispose()
    }

    @Test
    fun `drainScanQueue processes enqueued results`() {
        val results = mutableListOf<Pair<ProjectDirectory, DetectedProject>>()
        val coordinator = createCoordinator(
            onScanResult = { dir, proj ->
                results.add(dir to proj)
            },
        )

        val pd = ProjectDirectory(path = "/test1")
        val dp = DetectedProject(pd, emptySet(), emptyList())
        coordinator.enqueueForTest(pd, dp)
        coordinator.drainScanQueue(10)

        assertEquals(1, results.size)
        assertEquals("/test1", results[0].first.path)
        coordinator.dispose()
    }

    @Test
    fun `drainScanQueue respects maxPerTick limit`() {
        val results = mutableListOf<Pair<ProjectDirectory, DetectedProject>>()
        val coordinator = createCoordinator(
            onScanResult = { dir, proj ->
                results.add(dir to proj)
            },
        )

        for (i in 1..5) {
            val pd = ProjectDirectory(path = "/test$i")
            coordinator.enqueueForTest(pd, DetectedProject(pd, emptySet(), emptyList()))
        }
        coordinator.drainScanQueue(3)

        assertEquals(3, results.size)
        coordinator.dispose()
    }

    @Test
    fun `dispose stops timers`() {
        val coordinator = createCoordinator()
        coordinator.dispose()
        assertFalse(coordinator.blinkTimerRunning)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl needlecast-desktop -Dtest=ProjectTreeScanCoordinatorTest -DfailIfNoTests=false 2>&1 | tail -5`
Expected: COMPILATION ERROR — `ProjectTreeScanCoordinator` and `TestAppContext` not found

---

### Task 2: Implement ProjectTreeScanCoordinator

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreeScanCoordinator.kt`

- [ ] **Step 1: Create TestAppContext in test source**

Create a minimal test helper in the test file or a shared test fixture. Check how other tests in this project create AppContext instances:

```bash
grep -r "AppContext()" needlecast-desktop/src/test --include="*.kt" -l
```

If an existing test factory exists, reuse it. Otherwise, create a minimal one directly in the test class that provides `scanner`, `gitService`, and `register()`.

- [ ] **Step 2: Implement ProjectTreeScanCoordinator**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.Disposable
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.scanner.BuildFileWatcher
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer

internal class ProjectTreeScanCoordinator(
    val ctx: AppContext,
    private val onScanResult: (ProjectDirectory, DetectedProject) -> Unit,
    private val onGitStatusReady: (String, GitStatus) -> Unit,
    private val requestRepaint: () -> Unit,
) {
    val gitStatusCache = mutableMapOf<String, GitStatus>()
    private val agentStatuses = mutableMapOf<String, AgentStatus>()

    private val scanExecutor =
        Executors.newFixedThreadPool(2).also { exec ->
            ctx.register(object : Disposable {
                override fun dispose() {
                    exec.shutdownNow()
                }
            })
        }

    private val scanQueue = ConcurrentLinkedQueue<Pair<ProjectDirectory, DetectedProject>>()
    private val scanApplyPending = AtomicBoolean(false)
    private val scanApplyTimer = Timer(25) { drainScanQueue(10) }.apply { isRepeats = false }

    var blinkOn = false
        private set
    private val blinkTimer =
        Timer(600) {
            blinkOn = !blinkOn
            requestRepaint()
        }.apply { isRepeats = true }

    private val repaintTimer = Timer(50) { requestRepaint() }.apply { isRepeats = false }

    private val buildFileWatcher =
        BuildFileWatcher { path -> rescheduleProjectScan(path) }
            .also { ctx.register(it) }

    val blinkTimerRunning: Boolean get() = blinkTimer.isRunning

    fun scanProject(dir: ProjectDirectory) {
        scanExecutor.execute {
            val result =
                try {
                    ctx.scanner.scan(dir) ?: DetectedProject(dir, emptySet(), emptyList())
                } catch (e: Exception) {
                    logger.warn("Failed to scan '${dir.label()}'", e)
                    DetectedProject(dir, emptySet(), emptyList(), scanFailed = true)
                }
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    fun rescheduleProjectScan(path: String, dir: ProjectDirectory) {
        scanExecutor.execute {
            val result =
                try {
                    ctx.scanner.scan(dir)
                } catch (e: Exception) {
                    logger.warn("Project rescan failed", e)
                    null
                } ?: return@execute
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    fun drainScanQueue(maxPerTick: Int) {
        var processed = 0
        while (processed < maxPerTick) {
            val next = scanQueue.poll() ?: break
            val (dir, result) = next
            onScanResult(dir, result)
            if (!result.scanFailed) {
                fetchGitStatus(dir.path)
                Thread {
                    buildFileWatcher.watch(dir.path)
                }.apply {
                    isDaemon = true
                    name = "build-file-watch-${dir.label()}"
                }.start()
            }
            processed++
        }
        if (scanQueue.isNotEmpty()) {
            scanApplyTimer.restart()
        } else {
            scanApplyPending.set(false)
        }
    }

    fun fetchGitStatus(path: String) {
        object : SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = ctx.gitService.readStatus(path)

            override fun done() {
                val status =
                    try {
                        get()
                    } catch (_: Exception) {
                        return
                    }
                gitStatusCache[path] = status
                repaintTimer.restart()
                onGitStatusReady(path, status)
            }
        }.execute()
    }

    fun updateAgentStatus(path: String, status: AgentStatus) {
        agentStatuses[path] = status
        if (agentStatuses.values.any { it == AgentStatus.THINKING }) {
            blinkTimer.start()
        } else {
            blinkTimer.stop()
        }
        repaintTimer.restart()
    }

    fun unwatchAllBuildFiles() {
        buildFileWatcher.unwatchAll()
    }

    fun watchBuildFiles(path: String) {
        buildFileWatcher.watch(path)
    }

    fun clearAll() {
        gitStatusCache.clear()
        agentStatuses.clear()
    }

    fun dispose() {
        blinkTimer.stop()
        scanApplyTimer.stop()
        repaintTimer.stop()
    }

    private fun scheduleScanApply() {
        if (scanApplyPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater { scanApplyTimer.restart() }
        }
    }

    internal fun enqueueForTest(dir: ProjectDirectory, result: DetectedProject) {
        scanQueue.add(dir to result)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectTreeScanCoordinator::class.java)
    }
}
```

- [ ] **Step 3: Create the TestAppContext helper**

Read existing test files to find how AppContext is created in tests. Look at files like `ProjectTreePanelUiTest.kt` or any test that constructs `AppContext`. Adapt the approach. The minimum needed:
- `scanner: ProjectScanner` (can use a mock that returns `DetectedProject`)
- `gitService: GitService` (can use a no-op)
- `register(Disposable)` (can be a no-op)
- `config: AppConfig` (default)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl needlecast-desktop -Dtest=ProjectTreeScanCoordinatorTest -DfailIfNoTests=false`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreeScanCoordinator.kt needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreeScanCoordinatorTest.kt
git commit -m "feat(scan): add ProjectTreeScanCoordinator with test coverage"
```

---

### Task 3: Wire ScanCoordinator into ProjectTreePanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt`

- [ ] **Step 1: Add coordinator field and wire callbacks**

In `ProjectTreePanel`, add the coordinator as a field after the existing field declarations. Wire the callbacks:

1. Add `private val scanCoordinator = ProjectTreeScanCoordinator(...)` field
2. Replace `scanProject()` to delegate to `scanCoordinator.scanProject()`
3. Replace `rescheduleProjectScan()` to delegate to `scanCoordinator.rescheduleProjectScan()`
4. Replace `drainScanQueue()` — the coordinator handles the queue internally; the panel's `onScanResult` callback writes to `scanResults` and handles selection logic
5. Replace `fetchGitStatus()` — delegated to coordinator
6. Replace `updateProjectStatus()` — delegated to `scanCoordinator.updateAgentStatus()`
7. Replace `blinkOn` / `blinkTimer` — read `scanCoordinator.blinkOn` in the CellRenderer lambda
8. Replace `gitStatusCache` references with `scanCoordinator.gitStatusCache`
9. Replace `buildFileWatcher` references with coordinator methods
10. Update `reloadFromConfig()` to call `scanCoordinator.clearAll()`
11. Update `rescanAll()` to call `scanCoordinator.unwatchAllBuildFiles()` and `scanCoordinator.clearAll()`

Fields to **remove** from ProjectTreePanel:
- `scanQueue`
- `scanApplyTimer`
- `scanApplyPending`
- `scanExecutor`
- `buildFileWatcher`
- `blinkOn`
- `blinkTimer`
- `repaintTimer`

Fields to **keep** in ProjectTreePanel:
- `scanResults` (part of ProjectTreePanelAccess interface)
- `pendingSelectPath` (selection concern)
- `gitStatusCache` → replaced with `scanCoordinator.gitStatusCache` accessors

The `onScanResult` lambda should contain the panel-specific logic from the old `drainScanQueue()`:
```kotlin
{ dir: ProjectDirectory, result: DetectedProject ->
    scanResults[dir.path] = result
    if (!result.scanFailed) {
        val pending = pendingSelectPath
        if (pending == dir.path) {
            selectByPath(pending)
        } else {
            val selNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val selEntry = selNode?.userObject as? ProjectTreeEntry.Project
            if (selEntry?.directory?.path == dir.path) {
                onProjectSelected(result)
            }
        }
    }
    requestTreeRepaint()
}
```

- [ ] **Step 2: Update CellRenderer blinkOn lambda**

Change the `ProjectTreeCellRenderer` construction in the tree anonymous subclass:
```kotlin
blinkOn = { scanCoordinator.blinkOn },
```

- [ ] **Step 3: Update tooltip gitStatusCache references**

In the anonymous `tree.getToolTipText()`, replace `gitStatusCache` with `scanCoordinator.gitStatusCache`:
```kotlin
val gs = scanCoordinator.gitStatusCache[projectPath]
```
(Appears twice: line 146 and line 158)

- [ ] **Step 4: Run full test suite**

Run: `mvn test -pl needlecast-desktop`
Expected: 600 tests PASS (0 failures, 3 skipped)

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git commit -m "refactor(tree): wire ProjectTreeScanCoordinator into ProjectTreePanel"
```

---

### Task 4: Write failing tests for ProjectTreeFilter activeOnly support

**Files:**
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreeFilterTest.kt`

- [ ] **Step 1: Add FilterState tests and activeOnly filter tests**

Append these tests to the existing `ProjectTreeFilterTest.kt`:

```kotlin
@Test
fun `FilterState detects no-op when filter unchanged`() {
    val state = FilterState(lastFilter = "alpha", lastActiveOnly = false)
    assertFalse(state.needsReapply("alpha", false))
}

@Test
fun `FilterState detects change when filter differs`() {
    val state = FilterState(lastFilter = "alpha", lastActiveOnly = false)
    assertTrue(state.needsReapply("beta", false))
}

@Test
fun `FilterState detects change when activeOnly toggled`() {
    val state = FilterState(lastFilter = "", lastActiveOnly = false)
    assertTrue(state.needsReapply("", true))
}

@Test
fun `filterTree with activeOnly filters to active paths only`() {
    val entries = listOf(
        project("Alpha", path = "/a"),
        project("Beta", path = "/b"),
        project("Gamma", path = "/c"),
    )
    val result = ProjectTreeFilter.filterTree(entries, "", activeOnly = true, activePaths = setOf("/b"))
    assertEquals(1, result.size)
    assertEquals("Beta", (result[0] as ProjectTreeEntry.Project).directory.displayName)
}

@Test
fun `filterTree with activeOnly and text filter combines both`() {
    val entries = listOf(
        project("Alpha", path = "/a"),
        project("Beta", path = "/b"),
        project("BetaLib", path = "/c"),
    )
    val result = ProjectTreeFilter.filterTree(entries, "beta", activeOnly = true, activePaths = setOf("/b", "/c"))
    assertEquals(2, result.size)
}

@Test
fun `filterTree with activeOnly preserves folder with active children`() {
    val entries = listOf(
        folder("Work", project("Alpha", path = "/a"), project("Beta", path = "/b")),
    )
    val result = ProjectTreeFilter.filterTree(entries, "", activeOnly = true, activePaths = setOf("/b"))
    assertEquals(1, result.size)
    val f = result[0] as ProjectTreeEntry.Folder
    assertEquals(1, f.children.size)
    assertEquals("Beta", (f.children[0] as ProjectTreeEntry.Project).directory.displayName)
}
```

Update the `project()` helper to accept an optional `path` parameter:

```kotlin
private fun project(
    name: String,
    vararg tags: String,
    path: String = "/some/$name",
) = ProjectTreeEntry.Project(
    directory = ProjectDirectory(path = path, displayName = name),
    tags = tags.toList(),
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl needlecast-desktop -Dtest=ProjectTreeFilterTest -DfailIfNoTests=false`
Expected: COMPILATION ERROR — `FilterState` not found, `filterTree` overload not found

---

### Task 5: Implement ProjectTreeFilter enhancements

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreeFilter.kt`

- [ ] **Step 1: Add FilterState data class and enhance filterTree**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectTreeEntry

data class FilterState(
    val lastFilter: String = "",
    val lastActiveOnly: Boolean = false,
    val cachedEntries: List<ProjectTreeEntry>? = null,
) {
    fun needsReapply(
        filter: String,
        activeOnly: Boolean,
    ): Boolean = filter != lastFilter || activeOnly != lastActiveOnly
}

object ProjectTreeFilter {
    fun matches(
        entry: ProjectTreeEntry,
        filter: String,
    ): Boolean {
        val f = filter.lowercase()
        return when (entry) {
            is ProjectTreeEntry.Project -> {
                entry.directory
                    .label()
                    .lowercase()
                    .contains(f) ||
                    entry.tags.any { it.lowercase().contains(f) }
            }

            is ProjectTreeEntry.Folder -> {
                entry.children.any { matches(it, f) }
            }
        }
    }

    fun filterTree(
        entries: List<ProjectTreeEntry>,
        filter: String,
    ): List<ProjectTreeEntry> = filterTree(entries, filter, activeOnly = false, activePaths = emptySet())

    fun filterTree(
        entries: List<ProjectTreeEntry>,
        filter: String,
        activeOnly: Boolean,
        activePaths: Set<String>,
    ): List<ProjectTreeEntry> {
        if (filter.isBlank() && !activeOnly) return entries
        return entries.mapNotNull { entry ->
            filterEntry(entry, filter, activeOnly, activePaths)
        }
    }

    private fun filterEntry(
        entry: ProjectTreeEntry,
        textFilter: String,
        activeOnly: Boolean,
        activePaths: Set<String>,
    ): ProjectTreeEntry? =
        when (entry) {
            is ProjectTreeEntry.Project -> {
                val matchesText =
                    textFilter.isEmpty() ||
                        entry.directory
                            .label()
                            .lowercase()
                            .contains(textFilter.lowercase()) ||
                        entry.tags.any { it.lowercase().contains(textFilter.lowercase()) }
                val matchesActive = !activeOnly || entry.directory.path in activePaths
                if (matchesText && matchesActive) entry else null
            }

            is ProjectTreeEntry.Folder -> {
                val filteredChildren = entry.children.mapNotNull { filterEntry(it, textFilter, activeOnly, activePaths) }
                if (filteredChildren.isNotEmpty()) entry.copy(children = filteredChildren) else null
            }
        }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -pl needlecast-desktop -Dtest=ProjectTreeFilterTest -DfailIfNoTests=false`
Expected: All tests PASS (17 tests: 12 existing + 5 new)

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreeFilter.kt needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectTreeFilterTest.kt
git commit -m "feat(filter): add FilterState and activeOnly support to ProjectTreeFilter"
```

---

### Task 6: Wire filter consolidation into ProjectTreePanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt`

- [ ] **Step 1: Replace inline filter state with FilterState**

1. Remove fields: `lastFilter`, `lastActiveOnly`, `cachedAllEntries`
2. Add field: `private var filterState = FilterState()`
3. Replace `doApplyFilter()`:

```kotlin
private fun doApplyFilter() {
    val filter = pendingFilterText.trim()
    if (!filterState.needsReapply(filter, activeOnly)) return
    val source = filterState.cachedEntries ?: migrateOrLoad()
    filterState = filterState.copy(lastFilter = filter, lastActiveOnly = activeOnly, cachedEntries = source)
    rootNode.removeAllChildren()
    val result = ProjectTreeFilter.filterTree(source, filter, activeOnly, activePaths)
    result.forEach { addEntryNode(rootNode, it, scan = false) }
    if (filter.isEmpty() && !activeOnly) {
        ensureScans(source)
    }
    treeModel.reload()
    expandAll()
}
```

4. Delete the `filterEntry()` method (lines 838-859) — now in `ProjectTreeFilter`
5. Update `invalidateFilterCache()`:
```kotlin
fun invalidateFilterCache() {
    filterState = filterState.copy(cachedEntries = null)
}
```
6. Update `loadFromConfig()` to use `filterState`:
```kotlin
private fun loadFromConfig() {
    filterState = FilterState()
    rootNode.removeAllChildren()
    missingPaths.clear()
    migrateOrLoad().forEach { addEntryNode(rootNode, it) }
    treeModel.reload()
    expandAll()
    updateEmptyState()
}
```

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl needlecast-desktop`
Expected: 600+ tests PASS (0 failures, 3 skipped)

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git commit -m "refactor(tree): consolidate filter logic into ProjectTreeFilter"
```

---

### Task 7: Final verification and cleanup

- [ ] **Step 1: Run ktlint format**

Run: `mvn ktlint:format -pl needlecast-desktop`

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl needlecast-desktop`
Expected: 600+ tests, 0 failures, 3 skipped

- [ ] **Step 3: Verify line counts**

Run: `wc -l needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreeScanCoordinator.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreeFilter.kt`
Expected: ProjectTreePanel ~820 lines, ScanCoordinator ~140 lines, Filter ~80 lines

- [ ] **Step 4: Commit lint fixes if any**

```bash
git add -A
git commit -m "style: ktlint formatting"
```
(Only if ktlint made changes)

---

### Task 8: Design doc and commit plan

- [ ] **Step 1: Commit plan document**

```bash
git add docs/superpowers/plans/2026-06-11-project-tree-decomposition-plan.md
git commit -m "docs: add Cycle 20 implementation plan"
```

(The design spec was already committed separately.)

- [ ] **Step 2: Push branch and create PR**

Create a feature branch `cycle-20/project-tree-scan-filter`, push all commits, open PR into `develop`.
