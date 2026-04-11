package io.github.rygel.needlecast.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangedFileParsingTest {

    @Test
    fun `returns empty list for blank input`() {
        assertEquals(emptyList<ChangedFile>(), parseChangedFiles(""))
    }

    @Test
    fun `parses a modified file`() {
        val result = parseChangedFiles(" M src/Main.kt")
        assertEquals(1, result.size)
        assertEquals(ChangedFile("src/Main.kt", " M"), result[0])
    }

    @Test
    fun `parses an untracked file`() {
        val result = parseChangedFiles("?? new-file.txt")
        assertEquals(1, result.size)
        assertEquals(ChangedFile("new-file.txt", "??"), result[0])
    }

    @Test
    fun `parses multiple files of different status types`() {
        val result = parseChangedFiles(" M src/Main.kt\n?? new.txt\nD  deleted.kt")
        assertEquals(3, result.size)
        assertEquals(ChangedFile("src/Main.kt", " M"), result[0])
        assertEquals(ChangedFile("new.txt",     "??"), result[1])
        assertEquals(ChangedFile("deleted.kt",  "D "), result[2])
    }

    @Test
    fun `skips lines shorter than 3 characters`() {
        assertEquals(emptyList<ChangedFile>(), parseChangedFiles("M\n??"))
    }
}
