package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SwiftProjectScannerTest {

    private val scanner = SwiftProjectScanner()

    @Test
    fun `returns null when no Package_swift`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects swift project`(@TempDir dir: Path) {
        File(dir.toFile(), "Package.swift").writeText("// swift-tools-version:5.9\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.SPM), result.buildTools)
        assertTrue(result.commands.any { it.label == "swift build" })
        assertTrue(result.commands.any { it.label == "swift test" })
        assertTrue(result.commands.any { it.label == "swift run" })
    }
}
