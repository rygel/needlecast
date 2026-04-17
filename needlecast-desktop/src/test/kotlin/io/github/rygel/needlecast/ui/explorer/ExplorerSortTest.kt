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
