package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WordDiffCalculatorTest {
    @Test
    fun `detects single word change`() {
        val removed = """        println("old")"""
        val added = """        println("new")"""
        val result = WordDiffCalculator.compute(removed, added)

        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "old")), result.removed)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "new")), result.added)
    }

    @Test
    fun `detects multiple word changes`() {
        val removed = "foo bar baz"
        val added = "foo qux baz"
        val result = WordDiffCalculator.compute(removed, added)

        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "bar")), result.removed)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "qux")), result.added)
    }

    @Test
    fun `returns empty for identical lines`() {
        val line = "same content"
        val result = WordDiffCalculator.compute(line, line)

        assertEquals(emptyList<WordDiff>(), result.removed)
        assertEquals(emptyList<WordDiff>(), result.added)
    }

    @Test
    fun `handles entirely different lines`() {
        val result = WordDiffCalculator.compute("aaa bbb", "ccc ddd")

        assertEquals(
            listOf(
                WordDiff(WordDiffType.REMOVED, "aaa"),
                WordDiff(WordDiffType.REMOVED, "bbb"),
            ),
            result.removed,
        )
        assertEquals(
            listOf(
                WordDiff(WordDiffType.ADDED, "ccc"),
                WordDiff(WordDiffType.ADDED, "ddd"),
            ),
            result.added,
        )
    }

    @Test
    fun `handles empty lines`() {
        val result = WordDiffCalculator.compute("", "")
        assertEquals(emptyList<WordDiff>(), result.removed)
        assertEquals(emptyList<WordDiff>(), result.added)
    }

    @Test
    fun `handles added line vs empty`() {
        val result = WordDiffCalculator.compute("", "added")
        assertEquals(emptyList<WordDiff>(), result.removed)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "added")), result.added)
    }

    @Test
    fun `handles removed line vs empty`() {
        val result = WordDiffCalculator.compute("removed", "")
        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "removed")), result.removed)
        assertEquals(emptyList<WordDiff>(), result.added)
    }
}
