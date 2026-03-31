package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.CommandDescriptor
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProjectDirectory
import java.nio.file.Path

class MavenProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        if (!dir.resolve("pom.xml").toFile().exists()) return null

        val goals = listOf("clean", "compile", "test", "package", "verify", "install")
        val commands = goals.map { goal ->
            CommandDescriptor(
                label = "mvn $goal",
                buildTool = BuildTool.MAVEN,
                argv = mvnCommand(goal),
                workingDirectory = directory.path,
            )
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.MAVEN),
            commands = commands,
        )
    }

    private fun mvnCommand(goal: String): List<String> =
        if (IS_WINDOWS) listOf("cmd", "/c", "mvn", goal)
        else listOf("mvn", goal)
}
