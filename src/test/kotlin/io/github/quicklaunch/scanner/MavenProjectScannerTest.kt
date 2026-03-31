package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MavenProjectScannerTest {

    private val scanner = MavenProjectScanner()

    @Test
    fun `returns null when no pom_xml present`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `detects Maven project and returns six commands`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.MAVEN), result.buildTools)
        assertEquals(6, result.commands.size)
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("mvn clean"))
        assertTrue(labels.contains("mvn verify"))
        assertTrue(labels.contains("mvn install"))
        result.commands.forEach { assertEquals(dir.toString(), it.workingDirectory) }
    }

    @Test
    fun `all commands reference MAVEN build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach { assertEquals(BuildTool.MAVEN, it.buildTool) }
    }
}
