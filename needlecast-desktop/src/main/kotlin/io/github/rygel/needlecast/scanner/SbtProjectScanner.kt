package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Scala projects via `build.sbt`.
 */
class SbtProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        if (!dir.resolve("build.sbt").toFile().exists()) return null

        val commands = listOf(
            cmd("sbt compile", directory, "sbt", "compile"),
            cmd("sbt test", directory, "sbt", "test"),
            cmd("sbt run", directory, "sbt", "run"),
            cmd("sbt clean", directory, "sbt", "clean"),
            cmd("sbt assembly", directory, "sbt", "assembly"),
            cmd("sbt console", directory, "sbt", "console"),
            cmd("sbt update", directory, "sbt", "update"),
        )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.SBT),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.SBT,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
