package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects C/C++ projects via `CMakeLists.txt` or `Makefile`.
 *
 * CMake projects get configure + build commands.
 * Makefile-only projects get standard make targets.
 */
class CMakeProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val hasCMake = dir.resolve("CMakeLists.txt").toFile().exists()
        val hasMakefile = dir.resolve("Makefile").toFile().exists() ||
            dir.resolve("makefile").toFile().exists()

        if (!hasCMake && !hasMakefile) return null

        val commands = mutableListOf<CommandDescriptor>()

        if (hasCMake) {
            commands += cmd("cmake -B build", directory, BuildTool.CMAKE, "cmake", "-B", "build")
            commands += cmd("cmake --build build", directory, BuildTool.CMAKE, "cmake", "--build", "build")
            commands += cmd("cmake --build build --config Release", directory, BuildTool.CMAKE,
                "cmake", "--build", "build", "--config", "Release")
            commands += cmd("ctest --test-dir build", directory, BuildTool.CMAKE, "ctest", "--test-dir", "build")
            commands += cmd("cmake --install build", directory, BuildTool.CMAKE, "cmake", "--install", "build")
        }

        if (hasMakefile) {
            commands += cmd("make", directory, BuildTool.MAKE, "make")
            commands += cmd("make clean", directory, BuildTool.MAKE, "make", "clean")
            commands += cmd("make test", directory, BuildTool.MAKE, "make", "test")
            commands += cmd("make install", directory, BuildTool.MAKE, "make", "install")
        }

        val buildTools = mutableSetOf<BuildTool>()
        if (hasCMake) buildTools += BuildTool.CMAKE
        if (hasMakefile) buildTools += BuildTool.MAKE

        return DetectedProject(
            directory = directory,
            buildTools = buildTools,
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, tool: BuildTool, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, tool,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
