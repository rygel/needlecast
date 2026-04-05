package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DartProjectScannerTest {

    private val scanner = DartProjectScanner()

    @Test
    fun `returns null when no pubspec_yaml`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects pure dart project`(@TempDir dir: Path) {
        File(dir.toFile(), "pubspec.yaml").writeText("name: my_app\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.DART), result.buildTools)
        assertTrue(result.commands.any { it.label == "dart run" })
        assertTrue(result.commands.any { it.label == "dart test" })
        assertFalse(result.commands.any { it.label == "flutter run" })
    }

    @Test
    fun `detects flutter project`(@TempDir dir: Path) {
        File(dir.toFile(), "pubspec.yaml").writeText("""
            name: my_app
            dependencies:
              flutter:
                sdk: flutter
        """.trimIndent())
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "flutter run" })
        assertTrue(result.commands.any { it.label == "flutter test" })
        assertTrue(result.commands.any { it.label == "flutter build apk" })
        assertFalse(result.commands.any { it.label == "dart run" })
    }
}
