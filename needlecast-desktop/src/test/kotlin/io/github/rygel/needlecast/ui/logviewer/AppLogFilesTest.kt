package io.github.rygel.needlecast.ui.logviewer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.name

class AppLogFilesTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `returns empty list when no log files exist`() {
        val result = appLogFiles(dir.toFile())
        assert(result.isEmpty()) { "Expected empty but got $result" }
    }

    @Test
    fun `returns base log file when it exists`() {
        dir.resolve("needlecast.log").createFile()
        val result = appLogFiles(dir.toFile())
        assert(result.size == 1) { "Expected 1 file" }
        assert(result[0].name == "needlecast.log")
    }

    @Test
    fun `returns base log and archives in order`() {
        dir.resolve("needlecast.log").createFile()
        dir.resolve("needlecast.log.1").createFile()
        dir.resolve("needlecast.log.3").createFile()
        val result = appLogFiles(dir.toFile())
        val names = result.map { it.name }
        assert(names == listOf("needlecast.log", "needlecast.log.1", "needlecast.log.3")) {
            "Expected ordered list but got $names"
        }
    }

    @Test
    fun `skips archives beyond index 5`() {
        dir.resolve("needlecast.log").createFile()
        dir.resolve("needlecast.log.6").createFile()
        val result = appLogFiles(dir.toFile())
        val names = result.map { it.name }
        assert(names == listOf("needlecast.log")) { "Should not include .log.6: $names" }
    }

    @Test
    fun `returns archive when only archive exists without base log`() {
        dir.resolve("needlecast.log.1").createFile()
        val result = appLogFiles(dir.toFile())
        val names = result.map { it.name }
        assert(names == listOf("needlecast.log.1")) { "Expected archive only but got $names" }
    }
}
