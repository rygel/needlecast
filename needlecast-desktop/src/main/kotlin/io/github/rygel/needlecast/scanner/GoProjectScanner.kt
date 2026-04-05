package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Go projects via `go.mod`.
 *
 * Generates standard Go commands. Detects the module name from go.mod
 * for the run command.
 */
class GoProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val goMod = dir.resolve("go.mod").toFile()
        if (!goMod.exists()) return null

        val content = try { goMod.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val commands = mutableListOf<CommandDescriptor>()

        commands += cmd("go build ./...", directory, "go", "build", "./...")
        commands += cmd("go test ./...", directory, "go", "test", "./...")
        commands += cmd("go test -v ./...", directory, "go", "test", "-v", "./...")
        commands += cmd("go vet ./...", directory, "go", "vet", "./...")
        commands += cmd("go fmt ./...", directory, "go", "fmt", "./...")
        commands += cmd("go mod tidy", directory, "go", "mod", "tidy")
        commands += cmd("go mod download", directory, "go", "mod", "download")

        // If there's a main.go in the root, add go run .
        if (dir.resolve("main.go").toFile().exists()) {
            commands.add(0, cmd("go run .", directory, "go", "run", "."))
        }

        // Detect cmd/ subdirectories (common Go project layout)
        val cmdDir = dir.resolve("cmd").toFile()
        if (cmdDir.isDirectory) {
            cmdDir.listFiles()?.filter { it.isDirectory }?.sorted()?.forEach { sub ->
                commands += cmd("go build ./cmd/${sub.name}", directory, "go", "build", "./cmd/${sub.name}")
                commands += cmd("go run ./cmd/${sub.name}", directory, "go", "run", "./cmd/${sub.name}")
            }
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.GO),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(
            label = label,
            buildTool = BuildTool.GO,
            argv = if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            workingDirectory = dir.path,
        )
}
