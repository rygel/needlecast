package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Swift Package Manager projects via `Package.swift`.
 */
class SwiftProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        if (!dir.resolve("Package.swift").toFile().exists()) return null

        val commands = listOf(
            cmd("swift build", directory, "swift", "build"),
            cmd("swift build -c release", directory, "swift", "build", "-c", "release"),
            cmd("swift test", directory, "swift", "test"),
            cmd("swift run", directory, "swift", "run"),
            cmd("swift package resolve", directory, "swift", "package", "resolve"),
            cmd("swift package update", directory, "swift", "package", "update"),
        )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.SPM),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.SPM,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
