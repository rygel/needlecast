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
    fun `detects csproj and returns solution-level commands`(@TempDir dir: Path) {
        File(dir.toFile(), "MyApp.csproj").writeText("""<Project Sdk="Microsoft.NET.Sdk"/>""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.DOTNET), result.buildTools)
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("dotnet build"))
        assertTrue(labels.contains("dotnet test"))
        assertTrue(labels.contains("dotnet clean"))
        assertTrue(labels.contains("dotnet restore"))
    }

    @Test
    fun `detects runnable Exe project and adds run command`(@TempDir dir: Path) {
        File(dir.toFile(), "MyApp.csproj").writeText("""
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <OutputType>Exe</OutputType>
              </PropertyGroup>
            </Project>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("dotnet run"), "Missing dotnet run, got: $labels")
    }

    @Test
    fun `does not add run for library project`(@TempDir dir: Path) {
        File(dir.toFile(), "MyLib.csproj").writeText("""<Project Sdk="Microsoft.NET.Sdk"/>""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertFalse(labels.contains("dotnet run"), "Library should not have dotnet run, got: $labels")
    }

    @Test
    fun `detects web project and adds watch run`(@TempDir dir: Path) {
        File(dir.toFile(), "MyApi.csproj").writeText("""<Project Sdk="Microsoft.NET.Sdk.Web"/>""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("dotnet run"), "Missing dotnet run for web project, got: $labels")
        assertTrue(labels.contains("dotnet watch run"), "Missing dotnet watch run for web project, got: $labels")
    }

    @Test
    fun `detects solution file`(@TempDir dir: Path) {
        File(dir.toFile(), "MySolution.sln").writeText("")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.DOTNET), result.buildTools)
        // Solution-level commands target the .sln
        val buildCmd = result.commands.first { it.label == "dotnet build" }
        assertTrue(buildCmd.argv.contains("MySolution.sln"), "Expected sln in argv: ${buildCmd.argv}")
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
        val buildCmd = result.commands.first { it.label == "dotnet build" }
        assertTrue(buildCmd.argv.contains("App.sln"), "Expected sln to be preferred: ${buildCmd.argv}")
    }

    @Test
    fun `all commands reference DOTNET build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "App.csproj").writeText("<Project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach { assertEquals(BuildTool.DOTNET, it.buildTool) }
    }

    // ── Solution with multiple projects ──────────────────────────────────────

    @Test
    fun `parses sln and generates per-project commands`(@TempDir dir: Path) {
        File(dir.toFile(), "MyApp.sln").writeText("""
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Api", "src\Api\Api.csproj", "{AAA}"
            EndProject
            Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Worker", "src\Worker\Worker.csproj", "{BBB}"
            EndProject
            Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Api.Tests", "tests\Api.Tests\Api.Tests.csproj", "{CCC}"
            EndProject
        """.trimIndent())

        // Web API project
        File(dir.toFile(), "src/Api").mkdirs()
        File(dir.toFile(), "src/Api/Api.csproj").writeText("""
            <Project Sdk="Microsoft.NET.Sdk.Web">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
              </PropertyGroup>
            </Project>
        """.trimIndent())

        // Worker service
        File(dir.toFile(), "src/Worker").mkdirs()
        File(dir.toFile(), "src/Worker/Worker.csproj").writeText("""
            <Project Sdk="Microsoft.NET.Sdk.Worker">
              <PropertyGroup>
                <OutputType>Exe</OutputType>
              </PropertyGroup>
            </Project>
        """.trimIndent())

        // Test project
        File(dir.toFile(), "tests/Api.Tests").mkdirs()
        File(dir.toFile(), "tests/Api.Tests/Api.Tests.csproj").writeText("""
            <Project Sdk="Microsoft.NET.Sdk">
              <ItemGroup>
                <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.0.0" />
                <PackageReference Include="xunit" Version="2.4.1" />
              </ItemGroup>
            </Project>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }

        // Solution-level
        assertTrue(labels.contains("dotnet build"), "Missing dotnet build")

        // Api: web project -> run + watch
        assertTrue(labels.any { it.contains("run") && it.contains("Api") && !it.contains("Tests") },
            "Missing run for Api, got: $labels")
        assertTrue(labels.any { it.contains("watch") && it.contains("Api") },
            "Missing watch for Api, got: $labels")

        // Worker: runnable (Exe + Worker SDK)
        assertTrue(labels.any { it.contains("run") && it.contains("Worker") },
            "Missing run for Worker, got: $labels")

        // Api.Tests: test project
        assertTrue(labels.any { it.contains("test") && it.contains("Api.Tests") },
            "Missing test for Api.Tests, got: $labels")

        // Api.Tests should NOT have a run command
        assertFalse(labels.any { it.contains("run") && it.contains("Api.Tests") },
            "Test project should not have run, got: $labels")
    }

    @Test
    fun `ignores missing project referenced in sln`(@TempDir dir: Path) {
        File(dir.toFile(), "MyApp.sln").writeText("""
            Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Ghost", "src\Ghost\Ghost.csproj", "{GGG}"
            EndProject
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertFalse(labels.any { it.contains("Ghost") }, "Missing project should be ignored, got: $labels")
    }

    @Test
    fun `detects test frameworks in csproj`(@TempDir dir: Path) {
        // xunit
        File(dir.toFile(), "Tests.csproj").writeText("""
            <Project Sdk="Microsoft.NET.Sdk">
              <ItemGroup>
                <PackageReference Include="xunit" Version="2.4.1" />
                <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.0.0" />
              </ItemGroup>
            </Project>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        // Has test but no run (it's a test library, not an exe)
        assertTrue(labels.contains("dotnet test"), "Missing dotnet test")
        assertFalse(labels.contains("dotnet run"), "Test lib should not have run")
    }
}
