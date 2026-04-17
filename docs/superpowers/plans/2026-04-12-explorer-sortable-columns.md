# Explorer Sortable Columns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Name, Size, and Modified column headers in the Explorer panel clickable to sort entries ascending/descending, with directories always grouped above files and sort state remembered per project.

**Architecture:** All changes are in `ExplorerPanel.kt`. A private `ExplorerSortState` data class and three `internal` top-level helpers (`sortGroup`, `fileOf`, constants) are added at the bottom of the file. `setRootDirectory` restores per-project state; `loadDirectory` delegates sorting to `sortGroup`; a header `MouseListener` updates state on click; a custom header renderer shows the active arrow.

**Tech Stack:** Kotlin, Swing (`JTable`, `JTableHeader`, `MouseAdapter`, `DefaultTableCellRenderer`), JUnit 5, AssertJ (unit tests only — no display required for `sortGroup` tests)

---

## File Map

| File | Change |
|------|--------|
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt` | Add sort state fields, update `setRootDirectory`, refactor `loadDirectory`, add `sortGroup`/`fileOf` helpers and constants, add header click listener and renderer |
| `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerSortTest.kt` | **New** — unit tests for `sortGroup` and `fileOf` (no display needed) |

---

### Task 1: `sortGroup` helper and unit tests

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt` (bottom of file, after the `FileEntry` sealed class)
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerSortTest.kt`

- [ ] **Step 1: Create the test file with all failing tests**

Create `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerSortTest.kt`:

```kotlin
package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ExplorerSortTest {

    @TempDir lateinit var tempDir: Path

    private fun dir(name: String): FileEntry.Dir {
        val f = File(tempDir.toFile(), name).also { it.mkdir() }
        return FileEntry.Dir(f)
    }

    private fun file(name: String, bytes: Int = 0, lastModified: Long = 0L): FileEntry.RegularFile {
        val f = File(tempDir.toFile(), name).also {
            it.createNewFile()
            if (bytes > 0) it.writeBytes(ByteArray(bytes))
            if (lastModified != 0L) it.setLastModified(lastModified)
        }
        return FileEntry.RegularFile(f)
    }

    // ── name sort ─────────────────────────────────────────────────────────────

    @Test
    fun `name ascending sorts dirs alphabetically`() {
        val entries = listOf(dir("zebra"), dir("alpha"), dir("mango"))
        val result = sortGroup(entries, ExplorerSortState(COL_NAME, true))
        assertEquals(listOf("alpha", "mango", "zebra"),
            result.map { (it as FileEntry.Dir).file.name })
    }

    @Test
    fun `name descending reverses both dirs and files`() {
        val entries = listOf(file("apple.txt"), file("cherry.txt"), file("banana.txt"))
        val result = sortGroup(entries, ExplorerSortState(COL_NAME, false))
        assertEquals(listOf("cherry.txt", "banana.txt", "apple.txt"),
            result.map { (it as FileEntry.RegularFile).file.name })
    }

    // ── size sort ─────────────────────────────────────────────────────────────

    @Test
    fun `size ascending sorts files by byte count`() {
        val entries = listOf(file("big.txt", 300), file("small.txt", 10), file("medium.txt", 100))
        val result = sortGroup(entries, ExplorerSortState(COL_SIZE, true))
        assertEquals(listOf("small.txt", "medium.txt", "big.txt"),
            result.map { (it as FileEntry.RegularFile).file.name })
    }

    @Test
    fun `size sort on dir group falls back to name sort`() {
        val entries = listOf(dir("zebra"), dir("alpha"))
        val result = sortGroup(entries, ExplorerSortState(COL_SIZE, true))
        assertEquals(listOf("alpha", "zebra"),
            result.map { (it as FileEntry.Dir).file.name })
    }

    // ── modified sort ─────────────────────────────────────────────────────────

    @Test
    fun `modified ascending sorts files by lastModified`() {
        val entries = listOf(
            file("c.txt", lastModified = 3_000_000L),
            file("a.txt", lastModified = 1_000_000L),
            file("b.txt", lastModified = 2_000_000L),
        )
        val result = sortGroup(entries, ExplorerSortState(COL_MODIFIED, true))
        assertEquals(listOf("a.txt", "b.txt", "c.txt"),
            result.map { (it as FileEntry.RegularFile).file.name })
    }

    @Test
    fun `modified ascending sorts dirs by lastModified`() {
        val entries = listOf(dir("late"), dir("early"))
        // set different timestamps
        (entries[0] as FileEntry.Dir).file.setLastModified(2_000_000L)
        (entries[1] as FileEntry.Dir).file.setLastModified(1_000_000L)
        val result = sortGroup(entries, ExplorerSortState(COL_MODIFIED, true))
        assertEquals(listOf("early", "late"),
            result.map { (it as FileEntry.Dir).file.name })
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty group returns empty list`() {
        assertEquals(emptyList<FileEntry>(),
            sortGroup(emptyList(), ExplorerSortState(COL_NAME, true)))
    }

    @Test
    fun `fileOf returns null for ParentDir`() {
        assertNull(fileOf(FileEntry.ParentDir))
    }

    @Test
    fun `fileOf returns file for Dir and RegularFile`() {
        val d = dir("x")
        val f = file("y.txt")
        assertEquals(d.file, fileOf(d))
        assertEquals(f.file, fileOf(f))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (symbols not yet defined)**

```
cd needlecast-desktop
mvn test -pl . -Dtest=ExplorerSortTest -T 4 2>&1 | tail -20
```

Expected: compilation error — `sortGroup`, `ExplorerSortState`, `COL_NAME`, etc. not found.

- [ ] **Step 3: Add `ExplorerSortState`, constants, `sortGroup`, and `fileOf` to `ExplorerPanel.kt`**

At the **bottom** of `ExplorerPanel.kt`, after the existing `FileEntry` sealed class (line 629), add:

```kotlin
// ── Explorer sort helpers ─────────────────────────────────────────────────────

internal data class ExplorerSortState(val column: Int, val ascending: Boolean)

internal const val COL_NAME     = 0
internal const val COL_SIZE     = 1
internal const val COL_MODIFIED = 2
internal val DEFAULT_EXPLORER_SORT = ExplorerSortState(COL_NAME, true)

/**
 * Sorts [entries] (a single group — all dirs OR all files, never mixed) by [state].
 * For the size column applied to directories, falls back to name sort (dirs have no meaningful size).
 */
internal fun sortGroup(entries: List<FileEntry>, state: ExplorerSortState): List<FileEntry> {
    if (entries.isEmpty()) return entries
    val isDirGroup = entries.first() is FileEntry.Dir
    val comparator: Comparator<FileEntry> = when {
        state.column == COL_SIZE && isDirGroup ->
            compareBy { fileOf(it)?.name?.lowercase() ?: "" }
        state.column == COL_NAME     -> compareBy { fileOf(it)?.name?.lowercase() ?: "" }
        state.column == COL_SIZE     -> compareBy { fileOf(it)?.length() ?: 0L }
        state.column == COL_MODIFIED -> compareBy { fileOf(it)?.lastModified() ?: 0L }
        else                         -> compareBy { fileOf(it)?.name?.lowercase() ?: "" }
    }
    return if (state.ascending) entries.sortedWith(comparator) else entries.sortedWith(comparator.reversed())
}

/** Returns the underlying [File] for [Dir] and [RegularFile] entries; `null` for [ParentDir]. */
internal fun fileOf(entry: FileEntry): File? = when (entry) {
    is FileEntry.Dir         -> entry.file
    is FileEntry.RegularFile -> entry.file
    is FileEntry.ParentDir   -> null
}
```

- [ ] **Step 4: Run tests again — all should pass**

```
cd needlecast-desktop
mvn test -pl . -Dtest=ExplorerSortTest -T 4 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, 9 tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt \
        needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerSortTest.kt
git commit -m "feat: add sortGroup helper and ExplorerSortState with unit tests"
```

---

### Task 2: Wire sort state into ExplorerPanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

This task integrates the sort helpers from Task 1 into the panel: adds state fields, updates `setRootDirectory`, refactors `loadDirectory`, adds the header click listener and the header renderer.

- [ ] **Step 1: Add sort state fields to `ExplorerPanel`**

In `ExplorerPanel` class body, after `private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")` (line 62), add:

```kotlin
/** Sort state for each project root — keyed by absolute path. Session-only. */
private val sortStateByPath = mutableMapOf<String, ExplorerSortState>()
/** Absolute path of the project root currently shown (set by setRootDirectory). */
private var projectRootPath: String? = null
/** Sort state currently in effect. */
private var currentSortState: ExplorerSortState = DEFAULT_EXPLORER_SORT
```

- [ ] **Step 2: Update `setRootDirectory` to restore per-project state**

Replace the existing `setRootDirectory` function (line 166–168):

```kotlin
// OLD:
fun setRootDirectory(dir: File) {
    if (dir.isDirectory) navigateTo(dir)
}
```

With:

```kotlin
fun setRootDirectory(dir: File) {
    if (!dir.isDirectory) return
    projectRootPath = dir.absolutePath
    currentSortState = sortStateByPath[dir.absolutePath] ?: DEFAULT_EXPLORER_SORT
    navigateTo(dir)
}
```

- [ ] **Step 3: Refactor `loadDirectory` to use `sortGroup`**

Replace the `doInBackground` block inside `loadDirectory` (lines 224–234). The current implementation:

```kotlin
override fun doInBackground(): List<FileEntry> {
    val children = (dir.listFiles() ?: emptyArray())
        .filter { showHidden || !it.isHidden }
        .sortedWith(compareBy({ it.isFile }, { it.name.lowercase() }))
    val entries = mutableListOf<FileEntry>()
    if (dir.parentFile != null) entries.add(FileEntry.ParentDir)
    children.filter { it.isDirectory }.mapTo(entries) { FileEntry.Dir(it) }
    children.filter { it.isFile }.mapTo(entries) { FileEntry.RegularFile(it) }
    return entries
}
```

Replace with:

```kotlin
override fun doInBackground(): List<FileEntry> {
    val sortState = currentSortState   // capture for background thread
    val children = (dir.listFiles() ?: emptyArray())
        .filter { showHidden || !it.isHidden }
    val entries = mutableListOf<FileEntry>()
    if (dir.parentFile != null) entries.add(FileEntry.ParentDir)
    entries.addAll(sortGroup(children.filter { it.isDirectory }.map { FileEntry.Dir(it) }, sortState))
    entries.addAll(sortGroup(children.filter { it.isFile }.map { FileEntry.RegularFile(it) }, sortState))
    return entries
}
```

- [ ] **Step 4: Add header click listener in `init`**

In the `init` block, after the line `table.transferHandler = dropHandler` (last line of `init`, around line 156), add:

```kotlin
// Column header click — toggle sort direction or switch column
table.tableHeader.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
        val col = table.columnAtPoint(e.point)
        if (col < 0) return
        currentSortState = if (currentSortState.column == col) {
            currentSortState.copy(ascending = !currentSortState.ascending)
        } else {
            ExplorerSortState(col, true)
        }
        projectRootPath?.let { sortStateByPath[it] = currentSortState }
        loadDirectory(currentDir)
    }
})
```

- [ ] **Step 5: Add header renderer in `init`**

Immediately after the header click listener added in Step 4, add:

```kotlin
// Header renderer — show ▲ / ▼ on the active sort column
table.tableHeader.defaultRenderer = object : DefaultTableCellRenderer() {
    init { horizontalAlignment = SwingConstants.LEFT }
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean,
        hasFocus: Boolean, row: Int, column: Int,
    ): Component {
        val label = super.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, column) as JLabel
        val colName = tableModel.getColumnName(column)
        label.text = if (currentSortState.column == column) {
            "$colName ${if (currentSortState.ascending) "▲" else "▼"}"
        } else {
            colName
        }
        return label
    }
}
```

- [ ] **Step 6: Run the full test suite to check for regressions**

```
cd needlecast-desktop
mvn test -pl . -T 4 -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`. No existing tests should fail. If any scanner or model tests fail, investigate before proceeding.

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt
git commit -m "feat: sortable Explorer columns with per-project state"
```

---

## Verification Checklist

After both tasks are complete:

- [ ] Clicking Name header sorts both dirs and files A→Z (ascending), then Z→A on second click
- [ ] Clicking Size header sorts files by byte count; dirs sort by name (no meaningful size)
- [ ] Clicking Modified header sorts both groups oldest→newest, then newest→oldest
- [ ] `..` (parent directory) always stays at row 0 regardless of sort state
- [ ] Switching from project A (sorted by Size desc) to project B shows project B's default, and switching back to A restores Size desc
- [ ] Column header shows `Name ▲`, `Size ▼`, etc. on the active column; inactive columns show plain text
- [ ] Navigating into a subdirectory preserves the current sort state (does not reset to default)
