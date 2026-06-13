package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupChangeBlocksTest {
    private fun line(type: DiffLineType) = DiffLine(type, null, null, "")

    @Test
    fun `empty input returns empty list`() {
        assertTrue(groupChangeBlocks(emptyList()).isEmpty())
    }

    @Test
    fun `all context returns empty list`() {
        val lines = listOf(line(DiffLineType.CONTEXT), line(DiffLineType.CONTEXT))
        assertTrue(groupChangeBlocks(lines).isEmpty())
    }

    @Test
    fun `single added block`() {
        val lines =
            listOf(
                line(DiffLineType.CONTEXT),
                line(DiffLineType.ADDED),
                line(DiffLineType.ADDED),
                line(DiffLineType.CONTEXT),
            )
        val blocks = groupChangeBlocks(lines)
        assertEquals(1, blocks.size)
        assertEquals(1, blocks[0].startLine)
        assertEquals(3, blocks[0].endLine)
        assertEquals(DiffLineType.ADDED, blocks[0].type)
    }

    @Test
    fun `separate added and removed blocks`() {
        val lines =
            listOf(
                line(DiffLineType.CONTEXT),
                line(DiffLineType.ADDED),
                line(DiffLineType.CONTEXT),
                line(DiffLineType.REMOVED),
                line(DiffLineType.REMOVED),
            )
        val blocks = groupChangeBlocks(lines)
        assertEquals(2, blocks.size)
        assertEquals(DiffLineType.ADDED, blocks[0].type)
        assertEquals(1, blocks[0].startLine)
        assertEquals(2, blocks[0].endLine)
        assertEquals(DiffLineType.REMOVED, blocks[1].type)
        assertEquals(3, blocks[1].startLine)
        assertEquals(5, blocks[1].endLine)
    }

    @Test
    fun `consecutive different types are separate blocks`() {
        val lines =
            listOf(
                line(DiffLineType.ADDED),
                line(DiffLineType.REMOVED),
            )
        val blocks = groupChangeBlocks(lines)
        assertEquals(2, blocks.size)
        assertEquals(DiffLineType.ADDED, blocks[0].type)
        assertEquals(DiffLineType.REMOVED, blocks[1].type)
    }

    @Test
    fun `all added returns single block`() {
        val lines =
            listOf(
                line(DiffLineType.ADDED),
                line(DiffLineType.ADDED),
                line(DiffLineType.ADDED),
            )
        val blocks = groupChangeBlocks(lines)
        assertEquals(1, blocks.size)
        assertEquals(0, blocks[0].startLine)
        assertEquals(3, blocks[0].endLine)
    }

    @Test
    fun `changes at start and end of file`() {
        val lines =
            listOf(
                line(DiffLineType.REMOVED),
                line(DiffLineType.CONTEXT),
                line(DiffLineType.ADDED),
            )
        val blocks = groupChangeBlocks(lines)
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].startLine)
        assertEquals(1, blocks[0].endLine)
        assertEquals(2, blocks[1].startLine)
        assertEquals(3, blocks[1].endLine)
    }
}
