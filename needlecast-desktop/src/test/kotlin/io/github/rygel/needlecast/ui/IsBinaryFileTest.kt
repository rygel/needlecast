package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class IsBinaryFileTest {
    @TempDir
    lateinit var tmpDir: Path

    @Test
    fun `text file is not binary`() {
        val file = tmpDir.resolve("hello.txt")
        file.toFile().writeText("Hello, world!")
        assertFalse(isBinaryFile(file))
    }

    @Test
    fun `file with null byte is binary`() {
        val file = tmpDir.resolve("data.bin")
        file.toFile().writeBytes(byteArrayOf(1, 2, 3, 0, 5, 6))
        assertTrue(isBinaryFile(file))
    }

    @Test
    fun `empty file is not binary`() {
        val file = tmpDir.resolve("empty.txt")
        file.toFile().writeBytes(byteArrayOf())
        assertFalse(isBinaryFile(file))
    }

    @Test
    fun `null byte at position 4095 is detected`() {
        val bytes = ByteArray(4096) { 0x41 }
        bytes[4095] = 0
        val file = tmpDir.resolve("edge.bin")
        file.toFile().writeBytes(bytes)
        assertTrue(isBinaryFile(file))
    }

    @Test
    fun `nonexistent file is treated as binary`() {
        assertTrue(isBinaryFile(tmpDir.resolve("no-such-file")))
    }

    @Test
    fun `large text file without nulls is not binary`() {
        val file = tmpDir.resolve("large.txt")
        file.toFile().writeText("a".repeat(8000))
        assertFalse(isBinaryFile(file))
    }
}
