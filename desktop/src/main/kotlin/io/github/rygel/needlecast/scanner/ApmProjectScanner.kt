package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

class ApmProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        if (!dir.resolve("apm.yml").toFile().exists()) return null

        val commands = listOf("install", "audit", "update", "bundle").map { sub ->
            CommandDescriptor(
                label = "apm $sub",
                buildTool = BuildTool.APM,
                argv = apmCmd(sub, directory.path),
                workingDirectory = directory.path,
            )
        }

        return DetectedProject(
            directory  = directory,
            buildTools = setOf(BuildTool.APM),
            commands   = commands,
        )
    }

    private fun apmCmd(subcommand: String, dir: String): List<String> =
        if (IS_WINDOWS) listOf("cmd", "/c", "apm", subcommand)
        else listOf("apm", subcommand)
}
