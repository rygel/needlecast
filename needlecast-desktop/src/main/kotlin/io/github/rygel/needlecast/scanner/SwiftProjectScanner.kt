package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
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

        val commands =
            listOf(
                scannerCmd("swift build", directory, BuildTool.SPM, "swift", "build"),
                scannerCmd("swift build -c release", directory, BuildTool.SPM, "swift", "build", "-c", "release"),
                scannerCmd("swift test", directory, BuildTool.SPM, "swift", "test"),
                scannerCmd("swift run", directory, BuildTool.SPM, "swift", "run"),
                scannerCmd("swift package resolve", directory, BuildTool.SPM, "swift", "package", "resolve"),
                scannerCmd("swift package update", directory, BuildTool.SPM, "swift", "package", "update"),
            )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.SPM),
            commands = commands,
        )
    }
}
