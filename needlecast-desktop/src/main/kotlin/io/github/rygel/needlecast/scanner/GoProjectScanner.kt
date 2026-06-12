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
    private val logger = org.slf4j.LoggerFactory.getLogger(GoProjectScanner::class.java)

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val goMod = dir.resolve("go.mod").toFile()
        if (!goMod.exists()) return null

        val content =
            try {
                goMod.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                logger.warn("Failed to read {}", goMod.name, e)
                ""
            }
        val commands = mutableListOf<CommandDescriptor>()

        commands += scannerCmd("go build ./...", directory, BuildTool.GO, "go", "build", "./...")
        commands += scannerCmd("go test ./...", directory, BuildTool.GO, "go", "test", "./...")
        commands += scannerCmd("go test -v ./...", directory, BuildTool.GO, "go", "test", "-v", "./...")
        commands += scannerCmd("go vet ./...", directory, BuildTool.GO, "go", "vet", "./...")
        commands += scannerCmd("go fmt ./...", directory, BuildTool.GO, "go", "fmt", "./...")
        commands += scannerCmd("go mod tidy", directory, BuildTool.GO, "go", "mod", "tidy")
        commands += scannerCmd("go mod download", directory, BuildTool.GO, "go", "mod", "download")

        // If there's a main.go in the root, add go run .
        if (dir.resolve("main.go").toFile().exists()) {
            commands.add(0, scannerCmd("go run .", directory, BuildTool.GO, "go", "run", "."))
        }

        // Detect cmd/ subdirectories (common Go project layout)
        val cmdDir = dir.resolve("cmd").toFile()
        if (cmdDir.isDirectory) {
            cmdDir.listFiles()?.filter { it.isDirectory }?.sorted()?.forEach { sub ->
                commands += scannerCmd("go build ./cmd/${sub.name}", directory, BuildTool.GO, "go", "build", "./cmd/${sub.name}")
                commands += scannerCmd("go run ./cmd/${sub.name}", directory, BuildTool.GO, "go", "run", "./cmd/${sub.name}")
            }
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.GO),
            commands = commands,
        )
    }

}
