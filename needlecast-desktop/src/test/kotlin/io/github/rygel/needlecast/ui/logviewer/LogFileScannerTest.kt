package io.github.rygel.needlecast.ui.logviewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LogFileScannerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `scan returns empty for non-existent directory`() {
        val result = LogFileScanner.scan(File(tempDir, "nonexistent").absolutePath)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scan finds log files in root directory`() {
        File(tempDir, "app.log").createNewFile()
        File(tempDir, "debug.log").createNewFile()
        File(tempDir, "readme.txt").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(2, result.size)
        val names = result.map { it.name }.toSet()
        assertTrue(names.contains("app.log"))
        assertTrue(names.contains("debug.log"))
    }

    @Test
    fun `scan finds log files in target subdirectory`() {
        val target = File(tempDir, "target")
        target.mkdirs()
        File(target, "build.log").createNewFile()
        File(target, "other.txt").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("build.log", result[0].name)
    }

    @Test
    fun `scan finds log files in logs subdirectory`() {
        val logs = File(tempDir, "logs")
        logs.mkdirs()
        File(logs, "app.log.1").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("app.log.1", result[0].name)
    }

    @Test
    fun `scan finds rotated log files`() {
        File(tempDir, "server.log").createNewFile()
        File(tempDir, "server.log.1").createNewFile()
        File(tempDir, "server.log.5").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(3, result.size)
        val names = result.map { it.name }.toSet()
        assertTrue(names.contains("server.log"))
        assertTrue(names.contains("server.log.1"))
        assertTrue(names.contains("server.log.5"))
    }

    @Test
    fun `scan ignores non-log extensions`() {
        File(tempDir, "app.txt").createNewFile()
        File(tempDir, "app.out").createNewFile()
        File(tempDir, "app.log").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("app.log", result[0].name)
    }

    @Test
    fun `scan results sorted by last-modified descending`() {
        val older = File(tempDir, "older.log")
        older.createNewFile()
        older.setLastModified(System.currentTimeMillis() - 5000)

        val newer = File(tempDir, "newer.log")
        newer.createNewFile()
        newer.setLastModified(System.currentTimeMillis())

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(2, result.size)
        assertEquals("newer.log", result[0].name)
        assertEquals("older.log", result[1].name)
    }

    @Test
    fun `scan searches nested log dirs two levels deep`() {
        val nested = File(tempDir, "target${File.separator}surefire-reports")
        nested.mkdirs()
        File(nested, "test.log").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("test.log", result[0].name)
    }

    @Test
    fun `scan is case-insensitive for log extension`() {
        File(tempDir, "app.LOG").createNewFile()

        val result = LogFileScanner.scan(tempDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("app.LOG", result[0].name)
    }
}
