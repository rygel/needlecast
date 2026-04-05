package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ZigProjectScannerTest {

    private val scanner = ZigProjectScanner()

    @Test
    fun `returns null when no build_zig`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects zig project`(@TempDir dir: Path) {
        File(dir.toFile(), "build.zig").writeText("const std = @import(\"std\");\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.ZIG), result.buildTools)
        assertTrue(result.commands.any { it.label == "zig build" })
        assertTrue(result.commands.any { it.label == "zig build test" })
        assertTrue(result.commands.any { it.label == "zig build run" })
    }
}
