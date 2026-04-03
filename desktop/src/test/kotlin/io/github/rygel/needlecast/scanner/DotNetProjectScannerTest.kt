package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DotNetProjectScannerTest {

    private val scanner = DotNetProjectScanner()

    @Test
    fun `returns null when no dotnet files present`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects csproj and returns six commands`(@TempDir dir: Path) {
        File(dir.toFile(), "MyApp.csproj").writeText("<Project Sdk=\"Microsoft.NET.Sdk\"/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.DOTNET), result.buildTools)
        assertEquals(6, result.commands.size)
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("dotnet build"))
        assertTrue(labels.contains("dotnet test"))
        assertTrue(labels.contains("dotnet run"))
        assertTrue(labels.contains("dotnet clean"))
        assertTrue(labels.contains("dotnet publish"))
        assertTrue(labels.contains("dotnet restore"))
    }

    @Test
    fun `detects solution file`(@TempDir dir: Path) {
        File(dir.toFile(), "MySolution.sln").writeText("")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.DOTNET), result.buildTools)
        // Solution file is passed as target argument
        result.commands.forEach { cmd ->
            assertTrue(cmd.argv.contains("MySolution.sln"))
        }
    }

    @Test
    fun `detects fsproj and vbproj`(@TempDir dir: Path) {
        File(dir.toFile(), "App.fsproj").writeText("<Project/>")
        assertNotNull(scanner.scan(ProjectDirectory(dir.toString())))

        val dir2 = createTempDir()
        File(dir2, "App.vbproj").writeText("<Project/>")
        assertNotNull(scanner.scan(ProjectDirectory(dir2.absolutePath)))
        dir2.deleteRecursively()
    }

    @Test
    fun `prefers sln over csproj when both present`(@TempDir dir: Path) {
        File(dir.toFile(), "App.csproj").writeText("<Project/>")
        File(dir.toFile(), "App.sln").writeText("")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach { cmd ->
            assertTrue(cmd.argv.contains("App.sln"), "Expected sln to be preferred: ${cmd.argv}")
        }
    }

    @Test
    fun `all commands reference DOTNET build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "App.csproj").writeText("<Project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach { assertEquals(BuildTool.DOTNET, it.buildTool) }
    }
}
