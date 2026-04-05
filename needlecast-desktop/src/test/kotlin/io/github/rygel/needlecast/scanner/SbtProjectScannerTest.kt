package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SbtProjectScannerTest {

    private val scanner = SbtProjectScanner()

    @Test
    fun `returns null when no build_sbt`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects scala project`(@TempDir dir: Path) {
        File(dir.toFile(), "build.sbt").writeText("name := \"my-app\"\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.SBT), result.buildTools)
        assertTrue(result.commands.any { it.label == "sbt compile" })
        assertTrue(result.commands.any { it.label == "sbt test" })
        assertTrue(result.commands.any { it.label == "sbt run" })
    }
}
