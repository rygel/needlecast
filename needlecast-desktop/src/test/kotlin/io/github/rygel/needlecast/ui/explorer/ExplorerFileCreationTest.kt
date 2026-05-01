package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ExplorerFileCreationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `createNewFile succeeds for ENV file`() {
        val parentDir = tempDir.toFile()
        val file = File(parentDir, ".ENV")
        val result = file.createNewFile()
        assertTrue(result, "createNewFile should succeed for .ENV file")
        assertTrue(file.exists(), ".ENV file should exist after creation")
        file.delete()
    }

    @Test
    fun `createNewFile succeeds for env file`() {
        val parentDir = tempDir.toFile()
        val file = File(parentDir, "env")
        val result = file.createNewFile()
        assertTrue(result, "createNewFile should succeed for env file")
        assertTrue(file.exists(), "env file should exist after creation")
        file.delete()
    }

    @Test
    fun `createNewFile succeeds for dotfile with extension`() {
        val parentDir = tempDir.toFile()
        val file = File(parentDir, ".env.local")
        val result = file.createNewFile()
        assertTrue(result, "createNewFile should succeed for .env.local file")
        assertTrue(file.exists(), ".env.local file should exist after creation")
        file.delete()
    }

    @Test
    fun `createNewFile returns false when file already exists`() {
        val parentDir = tempDir.toFile()
        val file = File(parentDir, ".ENV")
        file.createNewFile()
        val result = file.createNewFile()
        assertFalse(result, "createNewFile should return false when file already exists")
        file.delete()
    }

    @Test
    fun `createNewFile works with various special names`() {
        val parentDir = tempDir.toFile()
        val specialNames = listOf(
            ".env",
            ".env.local",
            ".gitignore",
            ".bashrc",
            ".profile",
            "Dockerfile",
            ".env.development"
        )
        for (name in specialNames) {
            val file = File(parentDir, name)
            val result = file.createNewFile()
            assertTrue(result, "createNewFile should succeed for $name")
            assertTrue(file.exists(), "$name should exist after creation")
            file.delete()
        }
    }

    @Test
    fun `createNewFile creates file that can be written to`() {
        val parentDir = tempDir.toFile()
        val file = File(parentDir, ".ENV")
        file.createNewFile()
        file.writeText("DATABASE_URL=postgres://localhost\nAPI_KEY=secret123")
        val content = file.readText()
        assertEquals("DATABASE_URL=postgres://localhost\nAPI_KEY=secret123", content)
        file.delete()
    }
}