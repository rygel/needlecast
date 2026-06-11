package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ExplorerTableModelTest {
    @TempDir
    lateinit var tempDir: Path

    private fun dir(name: String): FileEntry.Dir {
        val f = File(tempDir.toFile(), name).also { it.mkdir() }
        return FileEntry.Dir(f)
    }

    private fun file(
        name: String,
        bytes: Int = 0,
        lastModified: Long = 0L,
    ): FileEntry.RegularFile {
        val f =
            File(tempDir.toFile(), name).also {
                it.createNewFile()
                if (bytes > 0) it.writeBytes(ByteArray(bytes))
                if (lastModified != 0L) it.setLastModified(lastModified)
            }
        return FileEntry.RegularFile(f)
    }

    // ── formatSize ──────────────────────────────────────────────────────────

    @Test
    fun `formatSize - bytes range`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("1 B", formatSize(1))
        assertEquals("1023 B", formatSize(1023))
    }

    @Test
    fun `formatSize - kilobytes range`() {
        assertEquals("1 KB", formatSize(1024))
        assertEquals("512 KB", formatSize(512L * 1024))
        assertEquals("1023 KB", formatSize(1024L * 1024 - 1))
    }

    @Test
    fun `formatSize - megabytes range`() {
        assertEquals("1 MB", formatSize(1024L * 1024))
        assertEquals("512 MB", formatSize(512L * 1024 * 1024))
        assertEquals("1023 MB", formatSize(1024L * 1024 * 1024 - 1))
    }

    @Test
    fun `formatSize - gigabytes range`() {
        assertEquals("1 GB", formatSize(1024L * 1024 * 1024))
        assertEquals("10 GB", formatSize(10L * 1024 * 1024 * 1024))
    }

    // ── FileTableModel ──────────────────────────────────────────────────────

    @Test
    fun `empty model has zero rows and three columns`() {
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
        model.setEntries(emptyList())
        assertEquals(0, model.rowCount)
    }

    @Test
    fun `getValueAt returns correct name for each entry type`() {
        val entries = listOf(FileEntry.ParentDir, dir("mydir"), file("readme.txt"))
        val model = FileTableModel()
        model.setEntries(entries)
        assertEquals("..", model.getValueAt(0, 0))
        assertEquals("mydir", model.getValueAt(1, 0))
        assertEquals("readme.txt", model.getValueAt(2, 0))
    }

    @Test
    fun `getValueAt returns formatted size for files only`() {
        val f = file("data.bin", 2048)
        val model = FileTableModel()
        model.setEntries(listOf(FileEntry.ParentDir, dir("afolder"), f))
        assertEquals("", model.getValueAt(0, 1))
        assertEquals("", model.getValueAt(1, 1))
        assertEquals("2 KB", model.getValueAt(2, 1))
    }

    @Test
    fun `getValueAt returns date for dirs and files not parentDir`() {
        val ts = 1_700_000_000L
        val d = dir("z").also { it.file.setLastModified(ts) }
        val f = file("a.txt", lastModified = ts)
        val model = FileTableModel()
        model.setEntries(listOf(FileEntry.ParentDir, d, f))
        assertEquals("", model.getValueAt(0, 2))
        assertEquals("", model.getValueAt(0, 2))
        assertNotEquals("", model.getValueAt(1, 2))
        assertNotEquals("", model.getValueAt(2, 2))
    }

    @Test
    fun `entryAt returns correct entries and cells are not editable`() {
        val entries = listOf(FileEntry.ParentDir, dir("x"), file("y.txt"))
        val model = FileTableModel()
        model.setEntries(entries)
        assertSame(entries[0], model.entryAt(0))
        assertSame(entries[1], model.entryAt(1))
        assertSame(entries[2], model.entryAt(2))
        assertFalse(model.isCellEditable(0, 0))
        assertFalse(model.isCellEditable(1, 1))
        assertFalse(model.isCellEditable(2, 2))
    }
}
