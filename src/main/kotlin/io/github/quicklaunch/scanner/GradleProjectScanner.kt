package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.CommandDescriptor
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProjectDirectory
import java.nio.file.Path

class GradleProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val hasBuildFile = dir.resolve("build.gradle").toFile().exists() ||
            dir.resolve("build.gradle.kts").toFile().exists()
        if (!hasBuildFile) return null

        val hasWrapper = if (IS_WINDOWS) dir.resolve("gradlew.bat").toFile().exists()
                         else dir.resolve("gradlew").toFile().exists()

        val tasks = listOf("build", "test", "clean", "assemble", "check")
        val commands = tasks.map { task ->
            CommandDescriptor(
                label = if (hasWrapper) "./gradlew $task" else "gradle $task",
                buildTool = BuildTool.GRADLE,
                argv = gradleCommand(directory.path, task, hasWrapper),
                workingDirectory = directory.path,
            )
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.GRADLE),
            commands = commands,
        )
    }

    private fun gradleCommand(dirPath: String, task: String, hasWrapper: Boolean): List<String> {
        return when {
            hasWrapper && IS_WINDOWS -> listOf("cmd", "/c", "gradlew.bat", task)
            hasWrapper -> listOf("./gradlew", task)
            IS_WINDOWS -> listOf("cmd", "/c", "gradle", task)
            else -> listOf("gradle", task)
        }
    }
}
