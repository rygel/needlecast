package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class CompositeProjectScannerIntegrationTest {

    private val scanner = CompositeProjectScanner()

    @Test
    fun `returns empty project for unrecognised directory`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertTrue(result.buildTools.isEmpty())
        assertTrue(result.commands.isEmpty())
    }

    @Test
    fun `detects Maven project`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertTrue(BuildTool.MAVEN in result.buildTools)
        assertTrue(result.commands.any { it.label.startsWith("mvn") })
    }

    @Test
    fun `detects Gradle project`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("")
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertTrue(BuildTool.GRADLE in result.buildTools)
    }

    @Test
    fun `detects npm project`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"dev":"vite"}}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertTrue(BuildTool.NPM in result.buildTools)
    }

    @Test
    fun `merges build tools and commands for Maven and npm monorepo`(@TempDir dir: Path) {
        // Root has both pom.xml and package.json (e.g. a full-stack monorepo)
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"build":"webpack"}}""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.MAVEN in result.buildTools)
        assertTrue(BuildTool.NPM in result.buildTools)
        assertTrue(result.commands.any { it.buildTool == BuildTool.MAVEN })
        assertTrue(result.commands.any { it.buildTool == BuildTool.NPM })
    }

    @Test
    fun `detects DotNet project`(@TempDir dir: Path) {
        File(dir.toFile(), "App.csproj").writeText("<Project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertTrue(BuildTool.DOTNET in result.buildTools)
    }

    @Test
    fun `all commands have correct working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"start":"node ."}}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        result.commands.forEach { assertEquals(dir.toString(), it.workingDirectory) }
    }

    @Test
    fun `one scanner throwing does not suppress other scanners results`(@TempDir dir: Path) {
        val bombScanner = object : ProjectScanner {
            override fun scan(directory: ProjectDirectory): io.github.rygel.needlecast.model.DetectedProject? =
                throw RuntimeException("Simulated scanner failure")
        }
        val mavenOnly = CompositeProjectScanner(
            scanners = listOf(bombScanner, MavenProjectScanner()),
        )
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = mavenOnly.scan(ProjectDirectory(dir.toString()))
        assertTrue(BuildTool.MAVEN in result.buildTools) { "Maven tools should survive bomb scanner: ${result.buildTools}" }
        assertFalse(result.scanFailed)
    }
}
