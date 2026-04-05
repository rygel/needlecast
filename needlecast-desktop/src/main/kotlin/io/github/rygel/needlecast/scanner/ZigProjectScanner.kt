package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
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

        val commands = listOf(
            cmd("zig build", directory, "zig", "build"),
            cmd("zig build test", directory, "zig", "build", "test"),
            cmd("zig build run", directory, "zig", "build", "run"),
            cmd("zig fmt", directory, "zig", "fmt", "."),
            cmd("zig test", directory, "zig", "test", "src/main.zig"),
        )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.ZIG),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.ZIG,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
