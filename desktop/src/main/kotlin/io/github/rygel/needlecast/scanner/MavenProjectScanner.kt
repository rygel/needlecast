package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

class MavenProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val pomFile = dir.resolve("pom.xml").toFile()
        if (!pomFile.exists()) return null

        val pom = try { pomFile.readText() } catch (_: Exception) { "" }

        val commands = mutableListOf<CommandDescriptor>()

        // Standard lifecycle goals
        for (goal in listOf("clean", "compile", "test", "package", "verify", "install")) {
            commands += cmd("mvn $goal", goal, directory.path)
        }

        // JavaFX Maven plugin — detect via org.openjfx groupId in pom
        if (pom.contains("org.openjfx")) {
            commands += cmd("mvn javafx:run",     "javafx:run",     directory.path)
            commands += cmd("mvn javafx:compile", "javafx:compile", directory.path)
            if (pom.contains("jlink"))  commands += cmd("mvn javafx:jlink",  "javafx:jlink",  directory.path)
            if (pom.contains("jimage")) commands += cmd("mvn javafx:jimage", "javafx:jimage", directory.path)
        }

        // exec-maven-plugin (exec:java is a common run target for non-framework apps)
        if (pom.contains("exec-maven-plugin") || pom.contains("exec:java")) {
            commands += cmd("mvn exec:java", "exec:java", directory.path)
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.MAVEN),
            commands = commands,
        )
    }

    private fun cmd(label: String, goal: String, workingDirectory: String) = CommandDescriptor(
        label = label,
        buildTool = BuildTool.MAVEN,
        argv = if (IS_WINDOWS) listOf("cmd", "/c", "mvn", goal) else listOf("mvn", goal),
        workingDirectory = workingDirectory,
    )
}
