# Cycle 18: ExplorerPanel Decomposition

**Date:** 2026-06-11
**Status:** Approved
**Target:** Reduce ExplorerPanel from 1080 lines to ~400 lines by extracting three focused modules.

## Problem

ExplorerPanel.kt (1080 lines) mixes five distinct responsibilities: table data model, cell rendering, file system operations (create/rename/delete), drag-and-drop handling, and UI coordination (navigation, tabs, filtering). This makes it the largest file in the application and the only major panel with zero test coverage.

The Cycle 13 architecture polish spec explicitly deferred ExplorerPanel decomposition. This cycle delivers it.

## Approach

Extract three files from ExplorerPanel, following the same decomposition pattern used for ProjectTreePanel in Cycle 13 (ProjectTreeContextMenu, ProjectTreeDialogs). Each extracted unit is independently testable.

### 1. `ExplorerFileOps.kt` (~180 lines)

All file system mutation operations and context menu construction.

**Extracted from ExplorerPanel:**
- Context menu builder (`showContextMenu`) for ParentDir, Dir, and RegularFile entries
- File creation (`createFile`) — "New File..." dialog
- Folder creation (`createFolder`) — "New Folder..." dialog
- Rename (`renameEntry`) — rename dialog
- Delete (`deleteEntry`) — confirm + recursive delete
- Copy path (`copyPath`) — clipboard copy
- Open with external editor (`openWith`) — process launch
- Copy path menu item factory (`copyPathItem`)

**Interface:**
```kotlin
data class ExplorerCallbacks(
    val navigateTo: (File) -> Unit,
    val openFileInTab: (File) -> Unit,
    val reloadDirectory: () -> Unit,
)

class ExplorerFileOps(
    private val ctx: AppContext,
    private val callbacks: ExplorerCallbacks,
    private val parent: JComponent,
) {
    fun showContextMenu(entry: FileEntry, x: Int, y: Int, invoker: JComponent)
    fun createFile(inDir: File)
    fun createFolder(inDir: File)
    fun renameEntry(file: File)
    fun deleteEntry(file: File)
    fun openWith(file: File, editor: ExternalEditor)
}
```

**Testable without Swing:** File operations (createFile, createFolder, renameEntry, deleteEntry) can be tested against temp directories. The `parent` JComponent is only used for JOptionPane positioning — tests can pass a mock or null. `openWith` command construction is verifiable. Context menu structure can be tested by inspecting the JPopupMenu component count and item text.

### 2. `ExplorerTableModel.kt` (~160 lines)

Pure data model and display logic for the file table.

**Extracted from ExplorerPanel:**
- `FileTableModel : AbstractTableModel` — inner class promoted to top-level
- `FileTableCellRenderer : DefaultTableCellRenderer` — inner class promoted to top-level
- `formatSize(bytes: Long): String` — pure function
- `FileEntry` sealed class (ParentDir, Dir, RegularFile)
- Column constants (`COL_NAME`, `COL_SIZE`, `COL_MODIFIED`)
- `DEFAULT_EXPLORER_SORT`

**Interface:**
```kotlin
sealed class FileEntry { ... }

class FileTableModel : AbstractTableModel() {
    fun setEntries(list: List<FileEntry>)
    fun entryAt(row: Int): FileEntry
}

class FileTableCellRenderer(
    private val tableModel: FileTableModel,
    private val dateFmt: SimpleDateFormat,
) : DefaultTableCellRenderer()

fun formatSize(bytes: Long): String
```

**Testable:** FileTableModel is a plain data model — set entries, verify getValueAt, getColumnName, rowCount. formatSize is a pure function covering B/KB/MB/GB boundaries. FileTableCellRenderer requires a JTable for full testing but its display logic (bold for dirs, right-aligned size) can be verified.

### 3. `ExplorerDropHandler.kt` (~130 lines)

Drag-and-drop handler for external file drops.

**Extracted from ExplorerPanel:**
- `ExplorerDropHandler : TransferHandler` — inner class promoted to top-level
- URI list parsing logic (`parseUriList`)
- URI list text reading (`readUriListText`)

**Interface:**
```kotlin
class ExplorerDropHandler(
    private val openFileInTab: (File) -> Unit,
    private val setRootDirectory: (File) -> Unit,
    private val table: JTable,
    private val tabs: JTabbedPane,
) : TransferHandler()

internal fun parseUriList(text: String): List<File>
```

**Testable:** `parseUriList()` is a pure function — test with valid file URIs, comment lines, empty lines, non-file URIs, mixed content. This is the highest-value pure function to test since URI list parsing has multiple edge cases (platform-specific paths, URL-encoded characters).

### What Stays in ExplorerPanel (~400 lines)

ExplorerPanel becomes a pure coordinator:

- Address bar construction (up button, refresh, hidden toggle, file manager button)
- Navigation logic (navigateTo, navigateUp, refreshAddressField)
- Filter field + debounced document listener
- Tab management (openFileInTab, closeTab, showTabContextMenu, TabHeader)
- Sort state management + column header click handler
- Mouse/keyboard event wiring
- Config listener for privacy mode
- Theme/font delegation to open editors
- Public API surface (setRootDirectory, applyTheme, applyEditorFont, openFile, openFileAt, checkAllUnsaved)

**Delegates to:**
- `ExplorerFileOps` for all context menu and file mutation operations
- `FileTableModel` for table data (same class, just moved to its own file)
- `ExplorerDropHandler` for drag-and-drop

## New Tests

### `ExplorerFileOpsTest.kt` (~12 tests)
- createFile succeeds with valid name
- createFile rejects blank name
- createFile rejects existing file
- createFolder succeeds
- createFolder rejects duplicate
- renameEntry succeeds
- renameEntry rejects blank/same name
- renameEntry handles failure
- deleteEntry succeeds with confirmation
- deleteEntry handles failure
- openWith constructs correct command (Windows vs Unix)
- copyPath puts absolute path on clipboard

### `ExplorerTableModelTest.kt` (~10 tests)
- formatSize: bytes, KB, MB, GB boundaries
- FileTableModel: setEntries updates rowCount
- FileTableModel: getValueAt returns correct name for Dir and RegularFile
- FileTableModel: getValueAt returns formatted size for RegularFile, empty for Dir
- FileTableModel: getValueAt returns formatted date for Dir and RegularFile
- FileTableModel: entryAt returns correct entry
- FileTableModel: column names are Name, Size, Modified

### `ExplorerDropHandlerTest.kt` (~8 tests)
- parseUriList: single file URI
- parseUriList: multiple file URIs
- parseUriList: skips comment lines (starting with #)
- parseUriList: skips empty lines
- parseUriList: skips non-file URIs
- parseUriList: handles URL-encoded paths
- parseUriList: empty input returns empty list
- parseUriList: mixed valid and invalid lines

**Total: ~30 new tests, bringing total from 558 to ~590.**

## File Layout After Decomposition

```
ui/explorer/
├── EditorPanel.kt           (unchanged)
├── ExplorerDropHandler.kt   (NEW — 130 lines)
├── ExplorerFileOps.kt       (NEW — 180 lines)
├── ExplorerPanel.kt         (reduced: 1080 → ~400 lines)
├── ExplorerTableModel.kt    (NEW — 160 lines)
├── FindBar.kt               (unchanged)
├── ImageViewerPanel.kt      (unchanged)
├── MediaPlayerPanel.kt      (unchanged)
├── SvgViewerPanel.kt        (unchanged)
└── ExplorerSortTest.kt      (existing, unchanged)
```

## Out of Scope

- DirectoryPanel / ProjectTreePanel overlap resolution (deferred — requires separate design decision)
- Tab management extraction (tightly coupled to panel, not worth the indirection)
- File type detection extraction (3 small functions, not enough complexity to justify a separate file)
- UI tests for ExplorerPanel (Swing UI testing remains in Podman-only per AGENTS.md)
- ExplorerPanel further decomposition below 400 lines

## Risks

- **Inner class promotion:** FileTableModel and FileTableCellRenderer reference `dateFmt` and `tableModel` from the outer class. These become constructor parameters. Low risk — straightforward refactoring.
- **Context menu callbacks:** ExplorerFileOps needs callbacks to trigger navigation and file opening. The `ExplorerCallbacks` data class encapsulates these. Low risk — same pattern as ProjectTreeContextMenu.
- **Drop handler coupling:** ExplorerDropHandler currently calls `openFileInTab` and `setRootDirectory` on the enclosing class. These become constructor function parameters. Low risk.
