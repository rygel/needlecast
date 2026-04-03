package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class NpmProjectScannerTest {

    private val scanner = NpmProjectScanner()

    @Test
    fun `returns null when no package_json present`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects npm project from minimal package_json`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"name":"test"}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.NPM), result.buildTools)
        assertTrue(result.commands.any { it.label == "npm install" })
    }

    @Test
    fun `reads scripts from package_json and orders preferred scripts first`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""
            {
              "name": "my-app",
              "scripts": {
                "build": "vite build",
                "test": "vitest",
                "dev": "vite",
                "lint": "eslint ."
              }
            }
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }

        // dev and build should come before test and lint (preferred order)
        assertTrue(labels.indexOf("npm run dev") < labels.indexOf("npm run test"))
        assertTrue(labels.contains("npm run build"))
        assertTrue(labels.contains("npm run lint"))
        assertTrue(labels.contains("npm install"))
    }

    @Test
    fun `always includes npm install command`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"start":"node index.js"}}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "npm install" })
    }

    @Test
    fun `all commands have correct build tool and working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"dev":"vite"}}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach {
            assertEquals(BuildTool.NPM, it.buildTool)
            assertEquals(dir.toString(), it.workingDirectory)
        }
    }

    @Test
    fun `handles malformed package_json gracefully`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("not valid json {{{")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        // Should fall back to just npm install
        assertEquals(1, result.commands.size)
        assertEquals("npm install", result.commands[0].label)
    }
}
