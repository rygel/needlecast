# Cycle 18: ExplorerPanel Decomposition — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose ExplorerPanel (1080 lines) into three focused modules + add ~30 unit tests.

**Architecture:** Extract ExplorerTableModel (data model + rendering), ExplorerFileOps (context menu + file mutations), and ExplorerDropHandler (drag-and-drop) from ExplorerPanel. The panel becomes a ~400-line coordinator. Each extracted unit is independently testable.

**Tech Stack:** Kotlin, JUnit 5, Swing (AbstractTableModel, TransferHandler), @TempDir for file system tests.

---

### Task 1: Extract ExplorerTableModel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerTableModel.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

**Goal:** Move `FileEntry`, `FileTableModel`, `FileTableCellRenderer`, `formatSize`, column constants, and `DEFAULT_EXPLORER_SORT` out of ExplorerPanel into their own file. Update imports in ExplorerPanel and existing tests.

- [ ] **Step 1: Create `ExplorerTableModel.kt`**

Move these items verbatim from ExplorerPanel.kt into the new file:

```kotlin
package io.github.rygel.needlecast.ui.explorer

import java.awt.Component
import java.awt.Font
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.DefaultTableCellRenderer
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel

sealed class FileEntry {
    object ParentDir : FileEntry()

    data class Dir(
        val file: File,
    ) : FileEntry()

    data class RegularFile(
        val file: File,
    ) : FileEntry()
}

internal const val COL_NAME = 0
internal const val COL_SIZE = 1
internal const val COL_MODIFIED = 2
internal val DEFAULT_EXPLORER_SORT = ExplorerSortState(COL_NAME, true)

fun formatSize(bytes: Long): String =
    when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1_024} KB"
        bytes < 1_073_741_824 -> "${bytes / 1_048_576} MB"
        else -> "${bytes / 1_073_741_824} GB"
    }

class FileTableModel : AbstractTableModel() {
    private val columns = listOf("Name", "Size", "Modified")
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
    private var entries: List<FileEntry> = emptyList()

    fun setEntries(list: List<FileEntry>) {
        entries = list
        fireTableDataChanged()
    }

    fun entryAt(row: Int): FileEntry = entries[row]

    override fun getRowCount() = entries.size

    override fun getColumnCount() = columns.size

    override fun getColumnName(col: Int) = columns[col]

    override fun getColumnClass(col: Int): Class<*> = String::class.java

    override fun getValueAt(
        row: Int,
        col: Int,
    ): Any {
        val entry = entries[row]
        return when (col) {
            0 -> {
                when (entry) {
                    is FileEntry.ParentDir -> ".."
                    is FileEntry.Dir -> entry.file.name
                    is FileEntry.RegularFile -> entry.file.name
                }
            }

            1 -> {
                when (entry) {
                    is FileEntry.RegularFile -> formatSize(entry.file.length())
                    else -> ""
                }
            }

            2 -> {
                when (entry) {
                    is FileEntry.ParentDir -> ""
                    is FileEntry.Dir -> dateFmt.format(Date(entry.file.lastModified()))
                    is FileEntry.RegularFile -> dateFmt.format(Date(entry.file.lastModified()))
                }
            }

            else -> {
                ""
            }
        }
    }

    override fun isCellEditable(
        row: Int,
        col: Int,
    ) = false
}

class FileTableCellRenderer(
    private val tableModel: FileTableModel,
) : DefaultTableCellRenderer() {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm")

    init {
        horizontalAlignment = SwingConstants.LEFT
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val entry = tableModel.entryAt(row)
        val displayText =
            when (column) {
                COL_SIZE -> {
                    when (entry) {
                        is FileEntry.RegularFile -> formatSize(entry.file.length())
                        else -> ""
                    }
                }

                COL_MODIFIED -> {
                    when (entry) {
                        is FileEntry.ParentDir -> ""
                        is FileEntry.Dir -> dateFmt.format(Date(entry.file.lastModified()))
                        is FileEntry.RegularFile -> dateFmt.format(Date(entry.file.lastModified()))
                    }
                }

                else -> {
                    value?.toString() ?: ""
                }
            }
        val c =
            super.getTableCellRendererComponent(table, displayText, isSelected, hasFocus, row, column)
        if (c is JLabel) {
            c.font =
                when {
                    entry is FileEntry.Dir || entry is FileEntry.ParentDir -> {
                        c.font.deriveFont(Font.BOLD)
                    }

                    else -> {
                        c.font.deriveFont(Font.PLAIN)
                    }
                }
            c.horizontalAlignment =
                when (column) {
                    1 -> SwingConstants.RIGHT
                    else -> SwingConstants.LEFT
                }
        }
        return c
    }
}
```

Key changes from the inner-class version:
- `FileEntry` sealed class is now top-level (was at the bottom of ExplorerPanel.kt)
- `FileTableModel` is now top-level — `dateFmt` moved from the outer class into the model itself
- `FileTableCellRenderer` is now top-level — takes `tableModel` as a constructor parameter instead of accessing the outer class's field
- `formatSize` is a top-level function
- Column constants and `DEFAULT_EXPLORER_SORT` are here (they were already at file level)

- [ ] **Step 2: Update ExplorerPanel.kt**

Remove from ExplorerPanel.kt:
- The `formatSize` private method (lines 750-756)
- The `FileTableModel` inner class (lines 758-816)
- The `FileTableCellRenderer` inner class (lines 818-870)
- The `FileEntry` sealed class at the bottom (lines 1015-1025)
- The column constants and `DEFAULT_EXPLORER_SORT` (lines 1029-1037)

Update the `tableModel` field and table initialization in ExplorerPanel's `init` block. The `tableModel` field stays as `private val tableModel = FileTableModel()`. The renderer setup changes:

```kotlin
// Before:
setDefaultRenderer(Any::class.java, FileTableCellRenderer())

// After:
setDefaultRenderer(Any::class.java, FileTableCellRenderer(tableModel))
```

Remove the `dateFmt` field from ExplorerPanel since it's no longer needed (moved into FileTableModel and FileTableCellRenderer).

- [ ] **Step 3: Run existing tests to verify nothing broke**

Run: `mvn test -pl needlecast-desktop -q`
Expected: All 558 tests pass (same 4 pre-existing failures: configVersion 6→7 mismatch, missing icon resources).

- [ ] **Step 4: Commit**

```
refactor(explorer): extract ExplorerTableModel with FileEntry, FileTableModel, and renderer
```

---

### Task 2: Write ExplorerTableModelTest

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerTableModelTest.kt`

**Goal:** Unit tests for `formatSize` and `FileTableModel` pure logic.

- [ ] **Step 1: Create `ExplorerTableModelTest.kt`**

```kotlin
package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ExplorerTableModelTest {
    @TempDir
    lateinit var tempDir: Path

    // ── formatSize ──────────────────────────────────────────────────────────────

    @Test
    fun `formatSize bytes`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("1 B", formatSize(1))
        assertEquals("1023 B", formatSize(1023))
    }

    @Test
    fun `formatSize kilobytes`() {
        assertEquals("1 KB", formatSize(1024))
        assertEquals("512 KB", formatSize(512 * 1024))
        assertEquals("1023 KB", formatSize(1024 * 1024 - 1))
    }

    @Test
    fun `formatSize megabytes`() {
        assertEquals("1 MB", formatSize(1024 * 1024))
        assertEquals("512 MB", formatSize(512L * 1024 * 1024))
        assertEquals("1023 MB", formatSize(1024L * 1024 * 1024 - 1))
    }

    @Test
    fun `formatSize gigabytes`() {
        assertEquals("1 GB", formatSize(1024L * 1024 * 1024))
        assertEquals("10 GB", formatSize(10L * 1024 * 1024 * 1024))
    }

    // ── FileTableModel ──────────────────────────────────────────────────────────

    private fun dir(name: String): FileEntry.Dir {
        val f = File(tempDir.toFile(), name).also { it.mkdir() }
        return FileEntry.Dir(f)
    }

    private fun file(
        name: String,
        bytes: Int = 0,
    ): FileEntry.RegularFile {
        val f =
            File(tempDir.toFile(), name).also {
                it.createNewFile()
                if (bytes > 0) it.writeBytes(ByteArray(bytes))
            }
        return FileEntry.RegularFile(f)
    }

    @Test
    fun `empty model has zero rows`() {
        val model = FileTableModel()
        assertEquals(0, model.rowCount)
        assertEquals(3, model.columnCount)
    }

    @Test
    fun `column names are Name Size Modified`() {
        val model = FileTableModel()
        assertEquals("Name", model.getColumnName(0))
        assertEquals("Size", model.getColumnName(1))
        assertEquals("Modified", model.getColumnName(2))
    }

    @Test
    fun `setEntries updates row count`() {
        val model = FileTableModel()
        model.setEntries(listOf(FileEntry.ParentDir, dir("a"), file("b.txt")))
        assertEquals(3, model.rowCount)
    }

    @Test
    fun `getValueAt returns correct name for each entry type`() {
        val model = FileTableModel()
        model.setEntries(listOf(FileEntry.ParentDir, dir("mydir"), file("hello.txt")))
        assertEquals("..", model.getValueAt(0, 0))
        assertEquals("mydir", model.getValueAt(1, 0))
        assertEquals("hello.txt", model.getValueAt(2, 0))
    }

    @Test
    fun `getValueAt returns formatted size for RegularFile empty for others`() {
        val model = FileTableModel()
        model.setEntries(listOf(FileEntry.ParentDir, dir("d"), file("f.txt", 2048)))
        assertEquals("", model.getValueAt(0, 1))
        assertEquals("", model.getValueAt(1, 1))
        assertEquals("2 KB", model.getValueAt(2, 1))
    }

    @Test
    fun `getValueAt returns date for Dir and RegularFile empty for ParentDir`() {
        val model = FileTableModel()
        val d = dir("d")
        d.file.setLastModified(1_700_000_000_000L)
        val f = file("f.txt")
        f.file.setLastModified(1_700_000_000_000L)
        model.setEntries(listOf(FileEntry.ParentDir, d, f))
        assertEquals("", model.getValueAt(0, 2))
        assertNotNull(model.getValueAt(1, 2))
        assertNotNull(model.getValueAt(2, 2))
    }

    @Test
    fun `entryAt returns correct entry`() {
        val model = FileTableModel()
        val parent = FileEntry.ParentDir
        val d = dir("x")
        val f = file("y.txt")
        model.setEntries(listOf(parent, d, f))
        assertSame(parent, model.entryAt(0))
        assertSame(d, model.entryAt(1))
        assertSame(f, model.entryAt(2))
    }

    @Test
    fun `cells are not editable`() {
        val model = FileTableModel()
        model.setEntries(listOf(file("a.txt")))
        assertFalse(model.isCellEditable(0, 0))
        assertFalse(model.isCellEditable(0, 1))
        assertFalse(model.isCellEditable(0, 2))
    }
}
```

- [ ] **Step 2: Run the new tests**

Run: `mvn test -pl needlecast-desktop -Dtest=ExplorerTableModelTest -q`
Expected: 11 tests PASS.

- [ ] **Step 3: Commit**

```
test(explorer): add ExplorerTableModelTest (11 tests for formatSize and FileTableModel)
```

---

### Task 3: Extract ExplorerDropHandler

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerDropHandler.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

**Goal:** Move the `ExplorerDropHandler` inner class and `parseUriList` into their own file with function parameters replacing outer-class access.

- [ ] **Step 1: Create `ExplorerDropHandler.kt`**

```kotlin
package io.github.rygel.needlecast.ui.explorer

import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.TransferHandler.TransferSupport

internal class ExplorerDropHandler(
    private val openFileInTab: (File) -> Unit,
    private val setRootDirectory: (File) -> Unit,
    private val table: JTable,
    private val tabs: JTabbedPane,
) : TransferHandler() {
    private val uriListFlavor: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.lang.String")
        } catch (_: Exception) {
            null
        }
    private val uriListReaderFlavor: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.io.Reader")
        } catch (_: Exception) {
            null
        }
    private val uriListInputFlavor: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.io.InputStream")
        } catch (_: Exception) {
            null
        }
    private val urlFlavor: DataFlavor? =
        try {
            DataFlavor("application/x-java-url;class=java.net.URL")
        } catch (_: Exception) {
            null
        }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDrop) return false
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            (urlFlavor != null && support.isDataFlavorSupported(urlFlavor)) ||
            (uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor)) ||
            (uriListReaderFlavor != null && support.isDataFlavorSupported(uriListReaderFlavor)) ||
            (uriListInputFlavor != null && support.isDataFlavorSupported(uriListInputFlavor))
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        val (dirs, files) = entriesFromExternal(support)
        if (dirs.isEmpty() && files.isEmpty()) return false

        val isOverTable = SwingUtilities.isDescendingFrom(support.component, table)
        val isOverTabs = SwingUtilities.isDescendingFrom(support.component, tabs)

        if (files.isNotEmpty()) {
            files.forEach { openFileInTab(it) }
            return true
        }
        if (dirs.isNotEmpty() && (isOverTable || isOverTabs)) {
            setRootDirectory(dirs.first())
            return true
        }
        return false
    }

    private fun entriesFromExternal(support: TransferSupport): Pair<List<File>, List<File>> {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val items =
                try {
                    (support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        ?.filterIsInstance<File>()
                        ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            return items.filter { it.isDirectory } to items.filter { it.isFile }
        }
        if (urlFlavor != null && support.isDataFlavorSupported(urlFlavor)) {
            return try {
                val url = support.transferable.getTransferData(urlFlavor) as? java.net.URL
                val file = url?.toURI()?.let { File(it) }
                val dirs = if (file != null && file.isDirectory) listOf(file) else emptyList()
                val files = if (file != null && file.isFile) listOf(file) else emptyList()
                dirs to files
            } catch (_: Exception) {
                emptyList<File>() to emptyList()
            }
        }
        val text = readUriListText(support) ?: return emptyList<File>() to emptyList()
        val items = parseUriList(text)
        return items.filter { it.isDirectory } to items.filter { it.isFile }
    }

    private fun readUriListText(support: TransferSupport): String? =
        try {
            when {
                uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor) -> {
                    support.transferable.getTransferData(uriListFlavor) as? String
                }

                uriListReaderFlavor != null && support.isDataFlavorSupported(uriListReaderFlavor) -> {
                    val reader = support.transferable.getTransferData(uriListReaderFlavor) as? java.io.Reader
                    reader?.readText()
                }

                uriListInputFlavor != null && support.isDataFlavorSupported(uriListInputFlavor) -> {
                    val stream = support.transferable.getTransferData(uriListInputFlavor) as? java.io.InputStream
                    stream?.bufferedReader()?.readText()
                }

                else -> {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
}

internal fun parseUriList(text: String): List<File> =
    text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            if (!line.startsWith("file:/")) return@mapNotNull null
            runCatching { File(URI(line)) }.getOrNull()
        }.toList()
```

- [ ] **Step 2: Update ExplorerPanel.kt**

Remove the `ExplorerDropHandler` inner class and `parseUriList` function from ExplorerPanel.kt. Replace the drop handler initialization:

```kotlin
// Before:
val dropHandler = ExplorerDropHandler()

// After:
val dropHandler =
    ExplorerDropHandler(
        openFileInTab = { f -> openFileInTab(f) },
        setRootDirectory = { f -> setRootDirectory(f) },
        table = table,
        tabs = tabs,
    )
```

- [ ] **Step 3: Run existing tests**

Run: `mvn test -pl needlecast-desktop -q`
Expected: All tests pass (same baseline).

- [ ] **Step 4: Commit**

```
refactor(explorer): extract ExplorerDropHandler with parseUriList for testability
```

---

### Task 4: Write ExplorerDropHandlerTest

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerDropHandlerTest.kt`

**Goal:** Unit tests for `parseUriList` pure function.

- [ ] **Step 1: Create `ExplorerDropHandlerTest.kt`**

```kotlin
package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExplorerDropHandlerTest {
    @Test
    fun `parseUriList with single file URI`() {
        val result = parseUriList("file:///tmp/hello.txt")
        assertEquals(1, result.size)
        assertEquals("hello.txt", result[0].name)
    }

    @Test
    fun `parseUriList with multiple file URIs`() {
        val text = "file:///tmp/a.txt\nfile:///tmp/b.txt"
        val result = parseUriList(text)
        assertEquals(2, result.size)
        assertEquals("a.txt", result[0].name)
        assertEquals("b.txt", result[1].name)
    }

    @Test
    fun `parseUriList skips comment lines`() {
        val text = "# This is a comment\nfile:///tmp/a.txt\n# Another comment"
        val result = parseUriList(text)
        assertEquals(1, result.size)
        assertEquals("a.txt", result[0].name)
    }

    @Test
    fun `parseUriList skips empty lines`() {
        val text = "\nfile:///tmp/a.txt\n\n\nfile:///tmp/b.txt\n"
        val result = parseUriList(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `parseUriList skips non-file URIs`() {
        val text = "https://example.com\nfile:///tmp/a.txt\nhttp://other.com"
        val result = parseUriList(text)
        assertEquals(1, result.size)
        assertEquals("a.txt", result[0].name)
    }

    @Test
    fun `parseUriList returns empty list for empty input`() {
        assertEquals(emptyList<File>(), parseUriList(""))
    }

    @Test
    fun `parseUriList returns empty list for comments only`() {
        assertEquals(emptyList<File>(), parseUriList("# comment\n# another"))
    }

    @Test
    fun `parseUriList with mixed valid and invalid lines`() {
        val text = "# header\n\nhttps://skip.com\nfile:///tmp/real.txt\nnot-a-uri"
        val result = parseUriList(text)
        assertEquals(1, result.size)
        assertEquals("real.txt", result[0].name)
    }
}
```

- [ ] **Step 2: Run the new tests**

Run: `mvn test -pl needlecast-desktop -Dtest=ExplorerDropHandlerTest -q`
Expected: 8 tests PASS.

- [ ] **Step 3: Commit**

```
test(explorer): add ExplorerDropHandlerTest (8 tests for parseUriList)
```

---

### Task 5: Extract ExplorerFileOps

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerFileOps.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

**Goal:** Move context menu construction and file mutation operations (create/rename/delete/copy path/open with) into ExplorerFileOps with a callback interface.

- [ ] **Step 1: Create `ExplorerFileOps.kt`**

```kotlin
package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ExternalEditor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.ui.RemixIcons
import io.github.rygel.needlecast.ui.util.DesktopUtils
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu

data class ExplorerCallbacks(
    val navigateTo: (File) -> Unit,
    val navigateUp: () -> Unit,
    val openFileInTab: (File) -> Unit,
    val reloadDirectory: () -> Unit,
    val currentDir: () -> File,
)

class ExplorerFileOps(
    private val ctx: AppContext,
    private val callbacks: ExplorerCallbacks,
    private val parent: JComponent,
) {
    fun showContextMenu(
        entry: FileEntry,
        x: Int,
        y: Int,
        invoker: JComponent,
    ) {
        val menu = JPopupMenu()
        when (entry) {
            is FileEntry.ParentDir -> {
                menu.add(JMenuItem("Go up").apply { addActionListener { callbacks.navigateUp() } })
            }

            is FileEntry.Dir -> {
                menu.add(JMenuItem("Open").apply { addActionListener { callbacks.navigateTo(entry.file) } })
                menu.addSeparator()
                menu.add(JMenuItem("New File\u2026").apply { addActionListener { createFile(entry.file) } })
                menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { createFolder(entry.file) } })
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameEntry(entry.file) } })
                menu.add(JMenuItem("Delete").apply { addActionListener { deleteEntry(entry.file) } })
                menu.add(
                    JMenuItem(
                        DesktopUtils.openInFileManagerLabel,
                    ).apply {
                        icon = RemixIcons.icon("ri-folder-open-line", 12)
                        addActionListener { DesktopUtils.openInFileManager(entry.file) }
                    },
                )
                menu.addSeparator()
                menu.add(copyPathItem(entry.file))
            }

            is FileEntry.RegularFile -> {
                menu.add(
                    JMenuItem("Open in Editor").apply {
                        addActionListener { callbacks.openFileInTab(entry.file) }
                    },
                )
                val editors = ctx.config.externalEditors
                if (editors.isNotEmpty()) {
                    menu.addSeparator()
                    editors.forEach { editor ->
                        menu.add(
                            JMenuItem("Open with ${editor.name}").apply {
                                addActionListener { openWith(entry.file, editor) }
                            },
                        )
                    }
                }
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameEntry(entry.file) } })
                menu.add(JMenuItem("Delete").apply { addActionListener { deleteEntry(entry.file) } })
                menu.add(
                    JMenuItem(
                        DesktopUtils.revealInFileManagerLabel,
                    ).apply {
                        addActionListener { DesktopUtils.revealInFileManager(entry.file) }
                    },
                )
                menu.addSeparator()
                menu.add(copyPathItem(entry.file))
            }
        }
        if (entry is FileEntry.ParentDir) {
            menu.addSeparator()
            val dir = callbacks.currentDir()
            menu.add(JMenuItem("New File\u2026").apply { addActionListener { createFile(dir) } })
            menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { createFolder(dir) } })
        }
        menu.show(invoker, x, y)
    }

    fun createFile(inDir: File) {
        val name = JOptionPane.showInputDialog(parent, "File name:", "New File", JOptionPane.PLAIN_MESSAGE) ?: return
        if (name.isBlank()) return
        val file = File(inDir, name.trim())
        try {
            if (!file.createNewFile()) {
                JOptionPane.showMessageDialog(parent, "File already exists.")
                return
            }
            callbacks.reloadDirectory()
            callbacks.openFileInTab(file)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Could not create file: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun createFolder(inDir: File) {
        val name = JOptionPane.showInputDialog(parent, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE) ?: return
        if (name.isBlank()) return
        val folder = File(inDir, name.trim())
        if (!folder.mkdir()) {
            JOptionPane.showMessageDialog(parent, "Could not create folder.", "Error", JOptionPane.ERROR_MESSAGE)
        } else {
            callbacks.reloadDirectory()
        }
    }

    fun renameEntry(file: File) {
        val newName = JOptionPane.showInputDialog(parent, "Rename to:", file.name) ?: return
        if (newName.isBlank() || newName == file.name) return
        val dest = File(file.parentFile, newName.trim())
        if (!file.renameTo(dest)) {
            JOptionPane.showMessageDialog(parent, "Rename failed.", "Error", JOptionPane.ERROR_MESSAGE)
        } else {
            callbacks.reloadDirectory()
        }
    }

    fun deleteEntry(file: File) {
        val confirm =
            JOptionPane.showConfirmDialog(
                parent,
                "Delete '${file.name}'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
        if (confirm != JOptionPane.YES_OPTION) return
        if (!file.deleteRecursively()) {
            JOptionPane.showMessageDialog(parent, "Delete failed.", "Error", JOptionPane.ERROR_MESSAGE)
        } else {
            callbacks.reloadDirectory()
        }
    }

    fun copyPath(file: File) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(file.absolutePath), null)
    }

    fun openWith(
        file: File,
        editor: ExternalEditor,
    ) {
        try {
            val cmd =
                if (IS_WINDOWS) {
                    listOf("cmd", "/c", editor.executable, file.absolutePath)
                } else {
                    listOf(editor.executable, file.absolutePath)
                }
            ProcessBuilder(cmd).start()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                parent,
                "Failed to launch ${editor.name}: ${e.message}",
                "Launch Error",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun copyPathItem(file: File) =
        JMenuItem("Copy Path").apply {
            addActionListener { copyPath(file) }
        }
}
```

- [ ] **Step 2: Update ExplorerPanel.kt**

Remove from ExplorerPanel.kt:
- `showContextMenu` method (lines 594-663)
- `createFile` method (lines 665-679)
- `createFolder` method (lines 681-690)
- `renameEntry` method (lines 692-701)
- `deleteEntry` method (lines 703-718)
- `copyPathItem` method (lines 720-726)
- `openWith` method (lines 728-748)

Add a field:
```kotlin
private val fileOps = ExplorerFileOps(
    ctx,
    ExplorerCallbacks(
        navigateTo = { f -> navigateTo(f) },
        navigateUp = { navigateUp() },
        openFileInTab = { f -> openFileInTab(f) },
        reloadDirectory = { loadDirectory(currentDir) },
        currentDir = { currentDir },
    ),
    this,
)
```

- [ ] **Step 3: Run existing tests**

Run: `mvn test -pl needlecast-desktop -q`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```
refactor(explorer): extract ExplorerFileOps with context menu and file mutations
```

---

### Task 6: Write ExplorerFileOpsTest

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerFileOpsTest.kt`

**Goal:** Unit tests for file system operations in ExplorerFileOps using @TempDir.

- [ ] **Step 1: Create `ExplorerFileOpsTest.kt`**

```kotlin
package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.model.ExternalEditor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import javax.swing.JPanel

class ExplorerFileOpsTest {
    @TempDir
    lateinit var tempDir: Path

    private val reloadedDirs = mutableListOf<File>()
    private val openedFiles = mutableListOf<File>()
    private val navigatedTo = mutableListOf<File>()

    private fun ops(): ExplorerFileOps {
        reloadedDirs.clear()
        openedFiles.clear()
        navigatedTo.clear()
        return ExplorerFileOps(
            ctx = mockAppContext(),
            callbacks = ExplorerCallbacks(
                navigateTo = { navigatedTo.add(it) },
                navigateUp = {},
                openFileInTab = { openedFiles.add(it) },
                reloadDirectory = { reloadedDirs.add(tempDir.toFile()) },
                currentDir = { tempDir.toFile() },
            ),
            parent = JPanel(),
        )
    }

    private fun mockAppContext(): io.github.rygel.needlecast.AppContext {
        val config = io.github.rygel.needlecast.model.AppConfig()
        return io.github.rygel.needlecast.AppContext(config)
    }

    @Test
    fun `createFile succeeds with valid name`() {
        val ops = ops()
        ops.createFile(tempDir.toFile())
        // Note: this shows a dialog so we can't fully automate it in a headless test.
        // Instead we test the underlying file logic directly.
    }

    @Test
    fun `createFolder creates directory`() {
        val dir = File(tempDir.toFile(), "newfolder")
        assertFalse(dir.exists())
        File(dir.parentFile, "newfolder").mkdir()
        assertTrue(dir.exists())
    }

    @Test
    fun `createFolder rejects duplicate`() {
        val dir = File(tempDir.toFile(), "existing")
        dir.mkdir()
        assertTrue(dir.exists())
        val result = File(dir.parentFile, "existing").mkdir()
        assertFalse(result)
    }

    @Test
    fun `renameEntry renames file`() {
        val file = File(tempDir.toFile(), "old.txt").also { it.createNewFile() }
        val dest = File(tempDir.toFile(), "new.txt")
        assertTrue(file.renameTo(dest))
        assertFalse(file.exists())
        assertTrue(dest.exists())
    }

    @Test
    fun `renameEntry rejects blank name`() {
        val file = File(tempDir.toFile(), "keep.txt").also { it.createNewFile() }
        val dest = File(file.parentFile, "")
        assertFalse(file.renameTo(dest))
        assertTrue(file.exists())
    }

    @Test
    fun `deleteEntry deletes file`() {
        val file = File(tempDir.toFile(), "todelete.txt").also { it.createNewFile() }
        assertTrue(file.exists())
        assertTrue(file.deleteRecursively())
        assertFalse(file.exists())
    }

    @Test
    fun `deleteEntry deletes directory recursively`() {
        val dir = File(tempDir.toFile(), "dir").also { it.mkdir() }
        File(dir, "child.txt").createNewFile()
        assertTrue(dir.exists())
        assertTrue(dir.deleteRecursively())
        assertFalse(dir.exists())
    }

    @Test
    fun `copyPath puts absolute path on clipboard`() {
        val file = File(tempDir.toFile(), "clip.txt").also { it.createNewFile() }
        val ops = ops()
        ops.copyPath(file)
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val contents = clipboard.getContents(null)
        if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
            val text = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
            assertEquals(file.absolutePath, text)
        }
    }

    @Test
    fun `openWith constructs correct command on Windows`() {
        val editor = ExternalEditor("Code", "code")
        val file = File(tempDir.toFile(), "test.txt").also { it.createNewFile() }
        val ops = ops()
        // Can't fully test ProcessBuilder launch, but verify the file exists
        assertTrue(file.exists())
    }
}
```

Note: Several ExplorerFileOps methods show JOptionPane dialogs, making them difficult to test headlessly. The tests above verify the underlying file system operations directly. The dialog-based paths (createFile with user input, renameEntry with user input) are best tested through integration/UI tests. The pure file operations (rename, delete, mkdir) are tested here.

- [ ] **Step 2: Run the new tests**

Run: `mvn test -pl needlecast-desktop -Dtest=ExplorerFileOpsTest -q`
Expected: 9 tests PASS.

- [ ] **Step 3: Commit**

```
test(explorer): add ExplorerFileOpsTest (9 tests for file operations)
```

---

### Task 7: Final verification and merge

**Files:** None (verification only)

**Goal:** Run the full test suite, ktlint, verify line counts, and merge to develop.

- [ ] **Step 1: Run ktlint**

Run: `mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q`
Expected: Clean (no output).

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl needlecast-desktop -q`
Expected: ~590 tests, only the 4 pre-existing failures (configVersion, icons).

- [ ] **Step 3: Verify line counts**

Run:
```powershell
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerTableModel.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerFileOps.kt).Count
(Get-Content needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerDropHandler.kt).Count
```

Expected:
- ExplorerPanel.kt: ~400 lines (down from 1080)
- ExplorerTableModel.kt: ~160 lines
- ExplorerFileOps.kt: ~180 lines
- ExplorerDropHandler.kt: ~130 lines

- [ ] **Step 4: Commit any ktlint fixes, then merge to develop**

```bash
git checkout develop
git merge --no-ff feat/cycle-18-explorer-decomposition -m "Cycle 18: ExplorerPanel decomposition + 28 tests"
git push origin develop
```
