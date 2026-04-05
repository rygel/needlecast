package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Python projects via `pyproject.toml` or `requirements.txt`.
 *
 * Tool priority:
 * 1. **uv** — detected by `uv.lock` or `[tool.uv]` in pyproject.toml
 * 2. **Poetry** — detected by `poetry.lock` or `[tool.poetry]` in pyproject.toml
 * 3. **pip** — fallback when only `pyproject.toml` or `requirements.txt` exists
 */
class PythonProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val pyproject = dir.resolve("pyproject.toml").toFile()
        val requirements = dir.resolve("requirements.txt").toFile()

        if (!pyproject.exists() && !requirements.exists()) return null

        val pyprojectContent = if (pyproject.exists()) {
            try { pyproject.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        } else ""

        val hasUvLock = dir.resolve("uv.lock").toFile().exists()
        val hasPoetryLock = dir.resolve("poetry.lock").toFile().exists()

        val tool = when {
            hasUvLock || "[tool.uv]" in pyprojectContent -> PythonTool.UV
            hasPoetryLock || "[tool.poetry]" in pyprojectContent -> PythonTool.POETRY
            else -> PythonTool.PIP
        }

        val commands = mutableListOf<CommandDescriptor>()

        when (tool) {
            PythonTool.UV -> {
                commands += cmd("uv sync", directory, "uv", "sync")
                commands += cmd("uv run python", directory, "uv", "run", "python")
                commands += cmd("uv build", directory, "uv", "build")
                commands += cmd("uv test", directory, "uv", "run", "pytest")
                commands += cmd("uv lock", directory, "uv", "lock")
                commands += cmd("uv add", directory, "uv", "add")
            }
            PythonTool.POETRY -> {
                commands += cmd("poetry install", directory, "poetry", "install")
                commands += cmd("poetry run python", directory, "poetry", "run", "python")
                commands += cmd("poetry build", directory, "poetry", "build")
                commands += cmd("poetry run pytest", directory, "poetry", "run", "pytest")
                commands += cmd("poetry lock", directory, "poetry", "lock")
                commands += cmd("poetry add", directory, "poetry", "add")
            }
            PythonTool.PIP -> {
                if (pyproject.exists()) {
                    commands += cmd("pip install -e .", directory, "pip", "install", "-e", ".")
                }
                if (requirements.exists()) {
                    commands += cmd("pip install -r requirements.txt", directory, "pip", "install", "-r", "requirements.txt")
                }
                commands += cmd("python -m pytest", directory, "python", "-m", "pytest")
            }
        }

        // Detect scripts in pyproject.toml [project.scripts] or [tool.poetry.scripts]
        if (pyprojectContent.isNotEmpty()) {
            parseScripts(pyprojectContent).forEach { script ->
                val label = when (tool) {
                    PythonTool.UV -> "uv run $script"
                    PythonTool.POETRY -> "poetry run $script"
                    PythonTool.PIP -> script
                }
                val argv = when (tool) {
                    PythonTool.UV -> shellArgv("uv", "run", script)
                    PythonTool.POETRY -> shellArgv("poetry", "run", script)
                    PythonTool.PIP -> shellArgv(script)
                }
                commands += CommandDescriptor(label, BuildTool.PYTHON, argv, directory.path)
            }
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.PYTHON),
            commands = commands,
        )
    }

    /** Extract script names from [project.scripts] section of pyproject.toml. */
    private fun parseScripts(content: String): List<String> {
        val scripts = mutableListOf<String>()
        var inScripts = false
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed == "[project.scripts]" || trimmed == "[tool.poetry.scripts]") {
                inScripts = true
                continue
            }
            if (inScripts) {
                if (trimmed.startsWith("[")) break // next section
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val name = trimmed.substringBefore("=").trim().removeSurrounding("\"")
                if (name.isNotEmpty()) scripts += name
            }
        }
        return scripts
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.PYTHON, shellArgv(*args), dir.path)

    private fun shellArgv(vararg args: String): List<String> =
        if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList()

    private enum class PythonTool { UV, POETRY, PIP }
}
