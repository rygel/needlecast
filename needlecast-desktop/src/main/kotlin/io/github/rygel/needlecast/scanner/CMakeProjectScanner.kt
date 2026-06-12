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
        val hasMakefile =
            dir.resolve("Makefile").toFile().exists() ||
                dir.resolve("makefile").toFile().exists()

        if (!hasCMake && !hasMakefile) return null

        val commands = mutableListOf<CommandDescriptor>()

        if (hasCMake) {
            commands += scannerCmd("cmake -B build", directory, BuildTool.CMAKE, "cmake", "-B", "build")
            commands += scannerCmd("cmake --build build", directory, BuildTool.CMAKE, "cmake", "--build", "build")
            commands +=
                scannerCmd(
                    "cmake --build build --config Release",
                    directory,
                    BuildTool.CMAKE,
                    "cmake",
                    "--build",
                    "build",
                    "--config",
                    "Release",
                )
            commands += scannerCmd("ctest --test-dir build", directory, BuildTool.CMAKE, "ctest", "--test-dir", "build")
            commands += scannerCmd("cmake --install build", directory, BuildTool.CMAKE, "cmake", "--install", "build")
        }

        if (hasMakefile) {
            commands += scannerCmd("make", directory, BuildTool.MAKE, "make")
            commands += scannerCmd("make clean", directory, BuildTool.MAKE, "make", "clean")
            commands += scannerCmd("make test", directory, BuildTool.MAKE, "make", "test")
            commands += scannerCmd("make install", directory, BuildTool.MAKE, "make", "install")
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
}
