package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
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

        val commands =
            listOf(
                scannerCmd("sbt compile", directory, BuildTool.SBT, "sbt", "compile"),
                scannerCmd("sbt test", directory, BuildTool.SBT, "sbt", "test"),
                scannerCmd("sbt run", directory, BuildTool.SBT, "sbt", "run"),
                scannerCmd("sbt clean", directory, BuildTool.SBT, "sbt", "clean"),
                scannerCmd("sbt assembly", directory, BuildTool.SBT, "sbt", "assembly"),
                scannerCmd("sbt console", directory, BuildTool.SBT, "sbt", "console"),
                scannerCmd("sbt update", directory, BuildTool.SBT, "sbt", "update"),
            )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.SBT),
            commands = commands,
        )
    }

}
