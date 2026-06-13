package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SplitLinesForSideBySideTest {
    private lateinit var panel: DiffContentPanel

    @BeforeEach
    fun setUp() {
        panel = DiffContentPanel()
    }

    private fun ctx(content: String) = DiffLine(DiffLineType.CONTEXT, null, null, content)

    private fun add(content: String) = DiffLine(DiffLineType.ADDED, null, null, content)

    private fun rem(content: String) = DiffLine(DiffLineType.REMOVED, null, null, content)

    @Test
    fun `empty input returns empty split`() {
        val result = panel.splitLinesForSideBySide(emptyList())
        assertEquals(emptyList<DiffLine>(), result.left)
        assertEquals(emptyList<DiffLine>(), result.right)
    }

    @Test
    fun `context lines appear on both sides`() {
        val result = panel.splitLinesForSideBySide(listOf(ctx("hello")))
        assertEquals(1, result.left.size)
        assertEquals(1, result.right.size)
        assertEquals("hello", result.left[0].content)
        assertEquals("hello", result.right[0].content)
    }

    @Test
    fun `removed lines go left only with padding on right`() {
        val result = panel.splitLinesForSideBySide(listOf(rem("old")))
        assertEquals(1, result.left.size)
        assertEquals(1, result.right.size)
        assertEquals("old", result.left[0].content)
        assertEquals(DiffLineType.REMOVED, result.left[0].type)
        assertEquals("", result.right[0].content)
        assertEquals(DiffLineType.CONTEXT, result.right[0].type)
    }

    @Test
    fun `added lines go right only with padding on left`() {
        val result = panel.splitLinesForSideBySide(listOf(add("new")))
        assertEquals(1, result.left.size)
        assertEquals(1, result.right.size)
        assertEquals("", result.left[0].content)
        assertEquals(DiffLineType.CONTEXT, result.left[0].type)
        assertEquals("new", result.right[0].content)
        assertEquals(DiffLineType.ADDED, result.right[0].type)
    }

    @Test
    fun `removed then added pairs side by side`() {
        val lines = listOf(rem("old1"), rem("old2"), add("new1"), add("new2"))
        val result = panel.splitLinesForSideBySide(lines)
        assertEquals(2, result.left.size)
        assertEquals(2, result.right.size)
        assertEquals("old1", result.left[0].content)
        assertEquals("old2", result.left[1].content)
        assertEquals("new1", result.right[0].content)
        assertEquals("new2", result.right[1].content)
    }

    @Test
    fun `unequal block sizes pad shorter side`() {
        val lines = listOf(rem("old1"), rem("old2"), rem("old3"), add("new1"))
        val result = panel.splitLinesForSideBySide(lines)
        assertEquals(3, result.left.size)
        assertEquals(3, result.right.size)
        assertEquals("old1", result.left[0].content)
        assertEquals("old2", result.left[1].content)
        assertEquals("old3", result.left[2].content)
        assertEquals("new1", result.right[0].content)
        assertEquals("", result.right[1].content)
        assertEquals("", result.right[2].content)
    }

    @Test
    fun `more added than removed pads left`() {
        val lines = listOf(rem("old"), add("new1"), add("new2"), add("new3"))
        val result = panel.splitLinesForSideBySide(lines)
        assertEquals(3, result.left.size)
        assertEquals(3, result.right.size)
        assertEquals("old", result.left[0].content)
        assertEquals("", result.left[1].content)
        assertEquals("", result.left[2].content)
        assertEquals("new1", result.right[0].content)
        assertEquals("new2", result.right[1].content)
        assertEquals("new3", result.right[2].content)
    }

    @Test
    fun `mixed context and changes`() {
        val lines =
            listOf(
                ctx("line 1"),
                rem("old line"),
                add("new line"),
                ctx("line 4"),
            )
        val result = panel.splitLinesForSideBySide(lines)
        assertEquals(3, result.left.size)
        assertEquals(3, result.right.size)
        assertEquals("line 1", result.left[0].content)
        assertEquals("old line", result.left[1].content)
        assertEquals("line 4", result.left[2].content)
        assertEquals("line 1", result.right[0].content)
        assertEquals("new line", result.right[1].content)
        assertEquals("line 4", result.right[2].content)
    }

    @Test
    fun `added without preceding removed gets left padding`() {
        val lines = listOf(ctx("before"), add("inserted"))
        val result = panel.splitLinesForSideBySide(lines)
        assertEquals(2, result.left.size)
        assertEquals(2, result.right.size)
        assertEquals(DiffLineType.CONTEXT, result.left[1].type)
        assertEquals("", result.left[1].content)
    }

    @Test
    fun `all context returns equal sides`() {
        val lines = listOf(ctx("a"), ctx("b"), ctx("c"))
        val result = panel.splitLinesForSideBySide(lines)
        assertEquals(3, result.left.size)
        assertEquals(3, result.right.size)
        result.left.forEachIndexed { i, line -> assertEquals(lines[i], line) }
        result.right.forEachIndexed { i, line -> assertEquals(lines[i], line) }
    }
}
