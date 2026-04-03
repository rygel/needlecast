package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ApmProjectScannerTest {

    private val scanner = ApmProjectScanner()

    @Test
    fun `returns null when no apm_yml present`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects apm project when apm_yml is present`(@TempDir dir: Path) {
        File(dir.toFile(), "apm.yml").writeText("name: my-agent\nversion: 1.0.0\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.APM), result.buildTools)
    }

    @Test
    fun `emits install audit update and bundle commands`(@TempDir dir: Path) {
        File(dir.toFile(), "apm.yml").writeText("name: my-agent\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("apm install"))
        assertTrue(labels.contains("apm audit"))
        assertTrue(labels.contains("apm update"))
        assertTrue(labels.contains("apm bundle"))
    }

    @Test
    fun `all commands have correct build tool and working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "apm.yml").writeText("name: my-agent\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach {
            assertEquals(BuildTool.APM, it.buildTool)
            assertEquals(dir.toString(), it.workingDirectory)
        }
    }

    @Test
    fun `install is the first command`(@TempDir dir: Path) {
        File(dir.toFile(), "apm.yml").writeText("name: my-agent\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals("apm install", result.commands.first().label)
    }
}
