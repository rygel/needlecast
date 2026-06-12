package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Zig projects via `build.zig`.
 */
class ZigProjectScanner : ProjectScanner {
    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        if (!dir.resolve("build.zig").toFile().exists()) return null

        val commands =
            listOf(
                scannerCmd("zig build", directory, BuildTool.ZIG, "zig", "build"),
                scannerCmd("zig build test", directory, BuildTool.ZIG, "zig", "build", "test"),
                scannerCmd("zig build run", directory, BuildTool.ZIG, "zig", "build", "run"),
                scannerCmd("zig fmt", directory, BuildTool.ZIG, "zig", "fmt", "."),
                scannerCmd("zig test", directory, BuildTool.ZIG, "zig", "test", "src/main.zig"),
            )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.ZIG),
            commands = commands,
        )
    }

}
