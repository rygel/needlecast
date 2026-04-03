package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

class GradleProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val buildGroovy = dir.resolve("build.gradle").toFile()
        val buildKts    = dir.resolve("build.gradle.kts").toFile()
        if (!buildGroovy.exists() && !buildKts.exists()) return null

        val buildScript = when {
            buildGroovy.exists() -> try { buildGroovy.readText() } catch (_: Exception) { "" }
            else                 -> try { buildKts.readText()    } catch (_: Exception) { "" }
        }

        val hasWrapper = if (IS_WINDOWS) dir.resolve("gradlew.bat").toFile().exists()
                         else dir.resolve("gradlew").toFile().exists()

        val commands = mutableListOf<CommandDescriptor>()

        // Standard tasks
        for (task in listOf("build", "test", "clean", "assemble", "check")) {
            commands += cmd(task, directory.path, hasWrapper)
        }

        // JavaFX Gradle plugin — detect via org.openjfx or javafx block in build script
        val hasJavaFx = buildScript.contains("org.openjfx") ||
                        buildScript.contains("javafx") && buildScript.contains("application")
        if (hasJavaFx) {
            commands += cmd("run",     directory.path, hasWrapper)
            commands += cmd("jlink",   directory.path, hasWrapper)
            commands += cmd("jpackage",directory.path, hasWrapper)
        } else {
            // application plugin — add run task even without JavaFX
            val hasAppPlugin = buildScript.contains("application") ||
                               buildScript.contains("id(\"application\")") ||
                               buildScript.contains("apply plugin: 'application'") ||
                               buildScript.contains("apply plugin: \"application\"")
            if (hasAppPlugin) {
                commands += cmd("run", directory.path, hasWrapper)
            }
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.GRADLE),
            commands = commands,
        )
    }

    private fun cmd(task: String, dirPath: String, hasWrapper: Boolean): CommandDescriptor {
        val argv = when {
            hasWrapper && IS_WINDOWS -> listOf("cmd", "/c", "gradlew.bat", task)
            hasWrapper               -> listOf("./gradlew", task)
            IS_WINDOWS               -> listOf("cmd", "/c", "gradle", task)
            else                     -> listOf("gradle", task)
        }
        return CommandDescriptor(
            label = if (hasWrapper) (if (IS_WINDOWS) "gradlew $task" else "./gradlew $task") else "gradle $task",
            buildTool = BuildTool.GRADLE,
            argv = argv,
            workingDirectory = dirPath,
        )
    }
}
