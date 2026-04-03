package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

class DotNetProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path).toFile()

        // Prefer a .sln file; fall back to any project file in the directory
        val hasSln = dir.listFiles { f -> f.extension == "sln" }?.isNotEmpty() == true
        val hasProject = dir.listFiles { f -> f.extension in PROJECT_EXTENSIONS }?.isNotEmpty() == true

        if (!hasSln && !hasProject) return null

        // Use solution file as the target if one exists, otherwise let dotnet discover
        val target: String? = if (hasSln) {
            dir.listFiles { f -> f.extension == "sln" }!!.first().name
        } else {
            dir.listFiles { f -> f.extension in PROJECT_EXTENSIONS }!!.first().name
        }

        val commands = TASKS.map { (label, verb) ->
            CommandDescriptor(
                label = "dotnet $label",
                buildTool = BuildTool.DOTNET,
                argv = dotnetCommand(verb, target, directory.path),
                workingDirectory = directory.path,
            )
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.DOTNET),
            commands = commands,
        )
    }

    private fun dotnetCommand(verb: String, target: String?, workDir: String): List<String> {
        val base = if (IS_WINDOWS) listOf("cmd", "/c", "dotnet", verb)
                   else listOf("dotnet", verb)
        return if (target != null) base + target else base
    }

    companion object {
        private val PROJECT_EXTENSIONS = setOf("csproj", "vbproj", "fsproj")
        private val TASKS = listOf(
            "build"   to "build",
            "test"    to "test",
            "run"     to "run",
            "clean"   to "clean",
            "publish" to "publish",
            "restore" to "restore",
        )
    }
}
