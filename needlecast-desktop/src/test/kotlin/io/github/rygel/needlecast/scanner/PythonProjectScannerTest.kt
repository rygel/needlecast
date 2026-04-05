package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class PythonProjectScannerTest {

    private val scanner = PythonProjectScanner()

    @Test
    fun `returns null when no python files present`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects uv project from uv_lock`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[project]\nname = \"test\"\n")
        File(dir.toFile(), "uv.lock").writeText("")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.PYTHON), result.buildTools)
        assertTrue(result.commands.any { it.label == "uv sync" })
        assertTrue(result.commands.any { it.label == "uv build" })
    }

    @Test
    fun `detects uv project from tool_uv section`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[project]\nname = \"test\"\n\n[tool.uv]\ndev-dependencies = []\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "uv sync" })
    }

    @Test
    fun `detects poetry project from poetry_lock`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[project]\nname = \"test\"\n")
        File(dir.toFile(), "poetry.lock").writeText("")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "poetry install" })
        assertTrue(result.commands.any { it.label == "poetry build" })
    }

    @Test
    fun `detects poetry project from tool_poetry section`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[tool.poetry]\nname = \"test\"\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "poetry install" })
    }

    @Test
    fun `falls back to pip for bare pyproject_toml`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[project]\nname = \"test\"\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "pip install -e ." })
    }

    @Test
    fun `detects requirements_txt fallback`(@TempDir dir: Path) {
        File(dir.toFile(), "requirements.txt").writeText("flask\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "pip install -r requirements.txt" })
    }

    @Test
    fun `uv takes priority over poetry when both locks exist`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[project]\nname = \"test\"\n")
        File(dir.toFile(), "uv.lock").writeText("")
        File(dir.toFile(), "poetry.lock").writeText("")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "uv sync" })
        assertFalse(result.commands.any { it.label == "poetry install" })
    }

    @Test
    fun `parses project scripts from pyproject_toml`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("""
            [project]
            name = "test"

            [project.scripts]
            my-cli = "myapp.cli:main"
            serve = "myapp.server:run"
        """.trimIndent())
        File(dir.toFile(), "uv.lock").writeText("")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "uv run my-cli" })
        assertTrue(result.commands.any { it.label == "uv run serve" })
    }

    @Test
    fun `all commands have correct build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "pyproject.toml").writeText("[project]\nname = \"test\"\n")
        File(dir.toFile(), "uv.lock").writeText("")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach {
            assertEquals(BuildTool.PYTHON, it.buildTool)
            assertEquals(dir.toString(), it.workingDirectory)
        }
    }
}
