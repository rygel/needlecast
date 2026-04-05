package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CMakeProjectScannerTest {

    private val scanner = CMakeProjectScanner()

    @Test
    fun `returns null when no build files`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects cmake project`(@TempDir dir: Path) {
        File(dir.toFile(), "CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.20)\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.buildTools.contains(BuildTool.CMAKE))
        assertTrue(result.commands.any { it.label == "cmake -B build" })
        assertTrue(result.commands.any { it.label == "cmake --build build" })
    }

    @Test
    fun `detects makefile project`(@TempDir dir: Path) {
        File(dir.toFile(), "Makefile").writeText("all:\n\techo hello\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.buildTools.contains(BuildTool.MAKE))
        assertTrue(result.commands.any { it.label == "make" })
        assertTrue(result.commands.any { it.label == "make test" })
    }

    @Test
    fun `detects both cmake and makefile`(@TempDir dir: Path) {
        File(dir.toFile(), "CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.20)\n")
        File(dir.toFile(), "Makefile").writeText("all: build\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.buildTools.contains(BuildTool.CMAKE))
        assertTrue(result.buildTools.contains(BuildTool.MAKE))
    }
}
