package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.io.File
import java.nio.file.Path

class DotNetProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path).toFile()

        val hasSln = dir.listFiles { f -> f.extension == "sln" }?.isNotEmpty() == true
        val hasProject = dir.listFiles { f -> f.extension in PROJECT_EXTENSIONS }?.isNotEmpty() == true

        if (!hasSln && !hasProject) return null

        val commands = mutableListOf<CommandDescriptor>()

        if (hasSln) {
            val slnFile = dir.listFiles { f -> f.extension == "sln" }!!.first()

            // Solution-level commands (build all, test all, restore, clean)
            for ((label, verb) in SOLUTION_TASKS) {
                commands += dotnetCmd("dotnet $label", verb, slnFile.name, directory.path)
            }

            // Parse .sln to find individual projects and generate per-project commands
            val projects = parseSolutionProjects(slnFile, dir)
            for (project in projects) {
                val projectFile = File(dir, project.relativePath)
                if (!projectFile.exists()) continue
                val content = try { projectFile.readText() } catch (_: Exception) { continue }

                val kind = detectProjectKind(content)
                val relPath = project.relativePath

                if (kind.isRunnable) {
                    commands += dotnetCmd(
                        "dotnet run --project ${project.name}",
                        "run", null, directory.path,
                        extraArgs = listOf("--project", relPath),
                    )
                }
                if (kind.isWeb) {
                    commands += dotnetCmd(
                        "dotnet watch run --project ${project.name}",
                        "watch", null, directory.path,
                        extraArgs = listOf("run", "--project", relPath),
                    )
                }
                if (kind.isTest) {
                    commands += dotnetCmd(
                        "dotnet test ${project.name}",
                        "test", relPath, directory.path,
                    )
                }
            }
        } else {
            // Single project file (no solution)
            val projectFile = dir.listFiles { f -> f.extension in PROJECT_EXTENSIONS }!!.first()
            val content = try { projectFile.readText() } catch (_: Exception) { "" }
            val kind = detectProjectKind(content)

            for ((label, verb) in SOLUTION_TASKS) {
                commands += dotnetCmd("dotnet $label", verb, projectFile.name, directory.path)
            }
            if (kind.isRunnable) {
                commands += dotnetCmd("dotnet run", "run", null, directory.path)
            }
            if (kind.isWeb) {
                commands += dotnetCmd("dotnet watch run", "watch", null, directory.path, extraArgs = listOf("run"))
            }
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.DOTNET),
            commands = commands,
        )
    }

    private data class SolutionProject(val name: String, val relativePath: String)

    /** Parse a .sln file to extract project references and their relative paths. */
    private fun parseSolutionProjects(slnFile: File, rootDir: File): List<SolutionProject> {
        val content = try { slnFile.readText() } catch (_: Exception) { return emptyList() }
        // Format: Project("{GUID}") = "Name", "path\to\project.csproj", "{GUID}"
        val pattern = Regex("""Project\("[^"]*"\)\s*=\s*"([^"]+)",\s*"([^"]+\.[cfv][sb]proj)"""")
        return pattern.findAll(content).map { match ->
            val name = match.groupValues[1]
            val path = match.groupValues[2].replace('\\', '/')
            SolutionProject(name, path)
        }.toList()
    }

    private data class ProjectKind(val isRunnable: Boolean, val isWeb: Boolean, val isTest: Boolean)

    /** Detect what kind of .NET project this is by inspecting the .csproj content. */
    private fun detectProjectKind(csproj: String): ProjectKind {
        // SDK type: Microsoft.NET.Sdk.Web = web app, Microsoft.NET.Sdk.Worker = background service
        val isWeb = csproj.contains("Microsoft.NET.Sdk.Web")
        val isWorker = csproj.contains("Microsoft.NET.Sdk.Worker")

        // Output type: Exe means it's runnable
        val isExe = csproj.contains("<OutputType>Exe</OutputType>") ||
                    csproj.contains("<OutputType>WinExe</OutputType>")

        // Test frameworks detected by PackageReference
        val isTest = csproj.contains("Microsoft.NET.Test.Sdk") ||
                     csproj.contains("xunit") ||
                     csproj.contains("NUnit") ||
                     csproj.contains("MSTest")

        val isRunnable = isWeb || isExe || isWorker

        return ProjectKind(isRunnable = isRunnable, isWeb = isWeb, isTest = isTest)
    }

    private fun dotnetCmd(
        label: String,
        verb: String,
        target: String?,
        workDir: String,
        extraArgs: List<String> = emptyList(),
    ): CommandDescriptor {
        val base = if (IS_WINDOWS) listOf("cmd", "/c", "dotnet", verb)
                   else listOf("dotnet", verb)
        val args = base + extraArgs + listOfNotNull(target)
        return CommandDescriptor(
            label = label,
            buildTool = BuildTool.DOTNET,
            argv = args,
            workingDirectory = workDir,
        )
    }

    companion object {
        private val PROJECT_EXTENSIONS = setOf("csproj", "vbproj", "fsproj")
        private val SOLUTION_TASKS = listOf(
            "build"   to "build",
            "test"    to "test",
            "clean"   to "clean",
            "restore" to "restore",
        )
    }
}
