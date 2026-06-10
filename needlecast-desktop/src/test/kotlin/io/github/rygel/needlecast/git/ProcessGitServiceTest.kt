package io.github.rygel.needlecast.git

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProcessGitServiceTest {
    @Test
    fun `parses standard modified file`() {
        val result = parseChangedFiles(" M src/main/App.kt")
        assertEquals(1, result.size)
        assertEquals("src/main/App.kt", result[0].path)
        assertEquals(" M", result[0].statusCode)
    }

    @Test
    fun `parses staged file`() {
        val result = parseChangedFiles("M  build.gradle")
        assertEquals(1, result.size)
        assertEquals("build.gradle", result[0].path)
        assertEquals("M ", result[0].statusCode)
    }

    @Test
    fun `parses untracked file`() {
        val result = parseChangedFiles("?? newfile.txt")
        assertEquals(1, result.size)
        assertEquals("newfile.txt", result[0].path)
        assertEquals("??", result[0].statusCode)
    }

    @Test
    fun `parses deleted file`() {
        val result = parseChangedFiles(" D removed.kt")
        assertEquals(1, result.size)
        assertEquals("removed.kt", result[0].path)
    }

    @Test
    fun `parses renamed file`() {
        val result = parseChangedFiles("R  old.txt -> new.txt")
        assertEquals(1, result.size)
        assertEquals("old.txt -> new.txt", result[0].path)
        assertEquals("R ", result[0].statusCode)
    }

    @Test
    fun `parses multiple files`() {
        val output =
            """
             M src/A.kt
            M  src/B.kt
            ?? src/C.kt
            """.trimIndent()
        val result = parseChangedFiles(output)
        assertEquals(3, result.size)
    }

    @Test
    fun `skips blank lines`() {
        val result = parseChangedFiles("\n\n M file.kt\n\n")
        assertEquals(1, result.size)
    }

    @Test
    fun `empty input returns empty list`() {
        val result = parseChangedFiles("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parses merge conflict markers`() {
        val result = parseChangedFiles("UU conflicted.kt")
        assertEquals(1, result.size)
        assertEquals("UU", result[0].statusCode)
    }

    @Test
    fun `parses modified in both index and work tree`() {
        val result = parseChangedFiles("MM doubly-modified.kt")
        assertEquals(1, result.size)
        assertEquals("MM", result[0].statusCode)
    }
}
