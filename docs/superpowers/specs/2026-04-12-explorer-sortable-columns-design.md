# Explorer Sortable Columns — Design Spec

## Goal

Allow users to click the Name, Size, and Modified column headers in the Explorer panel
to sort entries in ascending or descending order. Directories always stay grouped above
files (each group sorted independently). Sort state is remembered per project — switching
projects restores each project's last sort selection.

---

## Architecture

One file changes: `ui/explorer/ExplorerPanel.kt`.

Two new private helpers are added:

| Addition | Purpose |
|----------|---------|
| `data class ExplorerSortState` | Captures active column index + direction |
| `private fun sortGroup(...)` | Sorts one group (dirs or files) by the active state |

No persistence to `AppConfig` — sort state is session-only (like scroll position).

---

## Data Model

```kotlin
private data class ExplorerSortState(val column: Int, val ascending: Boolean)

companion object {
    private const val COL_NAME     = 0
    private const val COL_SIZE     = 1
    private const val COL_MODIFIED = 2
    private val DEFAULT_SORT       = ExplorerSortState(COL_NAME, true)
}
```

Per-project state is held in two fields on `ExplorerPanel`:

```kotlin
/** Sort state for each project root, keyed by absolute path. Session-only. */
private val sortStateByPath = mutableMapOf<String, ExplorerSortState>()
/** Absolute path of the project root currently shown (set by setRootDirectory). */
private var projectRootPath: String? = null
/** Sort state currently in effect (matches sortStateByPath[projectRootPath] or DEFAULT_SORT). */
private var currentSortState = DEFAULT_SORT
```

---

## Project-Switch Integration

`setRootDirectory(dir: File)` is the only call site that signals a project change
(called from `MainWindow` when the user selects a different project in the tree). It
is updated to restore per-project state:

```kotlin
fun setRootDirectory(dir: File) {
    if (!dir.isDirectory) return
    projectRootPath = dir.absolutePath
    currentSortState = sortStateByPath[dir.absolutePath] ?: DEFAULT_SORT
    navigateTo(dir)
}
```

Internal navigation (`navigateTo`, `navigateUp`, keyboard/mouse activate) does **not**
reset `currentSortState` — the sort persists while the user browses subdirectories
within the same project.

---

## Sort Logic

`loadDirectory` currently sorts inside `doInBackground`. The sort is refactored to use
`sortGroup`, which keeps the `..` (ParentDir) entry always at position 0:

```kotlin
private fun loadDirectory(dir: File) {
    val sortState = currentSortState          // capture for background thread
    object : SwingWorker<List<FileEntry>, Void>() {
        override fun doInBackground(): List<FileEntry> {
            val children = (dir.listFiles() ?: emptyArray())
                .filter { showHidden || !it.isHidden }
            val entries = mutableListOf<FileEntry>()
            if (dir.parentFile != null) entries.add(FileEntry.ParentDir)
            entries.addAll(sortGroup(children.filter { it.isDirectory }.map { FileEntry.Dir(it) }, sortState))
            entries.addAll(sortGroup(children.filter { it.isFile    }.map { FileEntry.RegularFile(it) }, sortState))
            return entries
        }
        ...
    }.execute()
}
```

`sortGroup` produces a comparator for the active column and reverses it when
`ascending == false`:

```kotlin
private fun sortGroup(entries: List<FileEntry>, state: ExplorerSortState): List<FileEntry> {
    if (entries.isEmpty()) return entries
    val isDirGroup = entries.first() is FileEntry.Dir
    val base: Comparator<FileEntry> = when {
        state.column == COL_SIZE && isDirGroup ->
            // Dirs have no meaningful size: fall back to name sort
            compareBy { fileOf(it)?.name?.lowercase() ?: "" }
        state.column == COL_NAME     -> compareBy { fileOf(it)?.name?.lowercase() ?: "" }
        state.column == COL_SIZE     -> compareBy { fileOf(it)?.length() ?: 0L }
        state.column == COL_MODIFIED -> compareBy { fileOf(it)?.lastModified() ?: 0L }
        else                         -> compareBy { fileOf(it)?.name?.lowercase() ?: "" }
    }
    return if (state.ascending) entries.sortedWith(base) else entries.sortedWith(base.reversed())
}

private fun fileOf(entry: FileEntry): File? = when (entry) {
    is FileEntry.Dir         -> entry.file
    is FileEntry.RegularFile -> entry.file
    is FileEntry.ParentDir   -> null
}
```

---

## Header Click Listener

Added during `init`, after the table is configured:

```kotlin
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

Clicking an already-active column toggles direction; clicking a different column
resets to ascending.

---

## Header Renderer

A custom renderer appends `▲` or `▼` to the active column name:

```kotlin
table.tableHeader.defaultRenderer = object : DefaultTableCellRenderer() {
    init { horizontalAlignment = SwingConstants.LEFT }
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean,
        hasFocus: Boolean, row: Int, column: Int,
    ): Component {
        val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
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

---

## Tests

New file: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerSortTest.kt`

Since `ExplorerPanel` is a Swing component (requires a display), tests exercise `sortGroup`
through a package-level helper function extracted for testability. Alternatively, the tests
instantiate the panel in a headless Swing environment via `GuiActionRunner`.

| Test | Verifies |
|------|----------|
| `default sort is name ascending` | Fresh panel: dirs then files, both A→Z |
| `name descending sorts both groups Z to A` | After header click, both groups reverse |
| `size sort: files by byte count, dirs by name` | Files sorted by length; dir group falls back to name |
| `modified sort: both groups by lastModified` | Newer files/dirs appear last (ascending) |
| `parent dir always first` | `..` stays at row 0 regardless of sort state |
| `per-project state: project A and B independent` | Call setRootDirectory(A), sort by size, setRootDirectory(B), sort by modified, back to A → size sort restored |

---

## Changes Summary

| File | Change |
|------|--------|
| `ui/explorer/ExplorerPanel.kt` | Add `ExplorerSortState`, `sortStateByPath`, `currentSortState`, `projectRootPath`; update `setRootDirectory`; refactor `loadDirectory`; add `sortGroup` and `fileOf` helpers; add header click listener; add header renderer |

One file, no schema or config changes.
