package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FindAllCaseInsensitiveMatchesTest {
    @Test
    fun `empty query returns empty`() {
        assertTrue(findAllCaseInsensitiveMatches("hello", "").isEmpty())
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(findAllCaseInsensitiveMatches("hello world", "xyz").isEmpty())
    }

    @Test
    fun `single match`() {
        val result = findAllCaseInsensitiveMatches("hello world", "world")
        assertEquals(listOf(6 to 11), result)
    }

    @Test
    fun `multiple matches`() {
        val result = findAllCaseInsensitiveMatches("ab ab ab", "ab")
        assertEquals(listOf(0 to 2, 3 to 5, 6 to 8), result)
    }

    @Test
    fun `case insensitive matching`() {
        val result = findAllCaseInsensitiveMatches("Hello HELLO hello", "hello")
        assertEquals(3, result.size)
    }

    @Test
    fun `overlapping matches advance by one`() {
        val result = findAllCaseInsensitiveMatches("aaa", "aa")
        assertEquals(listOf(0 to 2, 1 to 3), result)
    }

    @Test
    fun `match at start`() {
        val result = findAllCaseInsensitiveMatches("test data", "test")
        assertEquals(listOf(0 to 4), result)
    }

    @Test
    fun `match at end`() {
        val result = findAllCaseInsensitiveMatches("some data", "data")
        assertEquals(listOf(5 to 9), result)
    }

    @Test
    fun `entire text is the query`() {
        val result = findAllCaseInsensitiveMatches("exact", "exact")
        assertEquals(listOf(0 to 5), result)
    }

    @Test
    fun `empty text returns empty`() {
        assertTrue(findAllCaseInsensitiveMatches("", "test").isEmpty())
    }
}
