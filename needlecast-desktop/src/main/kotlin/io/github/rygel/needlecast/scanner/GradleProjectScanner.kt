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

        val rootBuildScript = readBuildScript(dir)

        val hasWrapper = if (IS_WINDOWS) dir.resolve("gradlew.bat").toFile().exists()
                         else dir.resolve("gradlew").toFile().exists()

        val commands = mutableListOf<CommandDescriptor>()

        // Root project standard tasks
        for (task in listOf("build", "test", "clean", "assemble", "check")) {
            commands += cmd(task, directory.path, hasWrapper)
        }

        // Root project plugin-specific tasks
        commands += pluginTasks(rootBuildScript, null, directory.path, hasWrapper)

        // Subproject tasks (parsed from settings.gradle / settings.gradle.kts)
        for (subproject in parseSubprojects(dir)) {
            val subDir = dir.resolve(subproject.path).toFile()
            if (!subDir.isDirectory) continue
            val subBuildScript = readBuildScript(subDir.toPath())
            if (subBuildScript.isEmpty()) continue

            val prefix = ":${subproject.name}"
            commands += pluginTasks(subBuildScript, prefix, directory.path, hasWrapper)

            // Always add :module:build and :module:test for subprojects
            commands += cmd("$prefix:build", directory.path, hasWrapper)
            commands += cmd("$prefix:test", directory.path, hasWrapper)
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.GRADLE),
            commands = commands,
        )
    }

    /** Detect plugin-specific tasks from a build script. [prefix] is null for root, ":module" for subprojects. */
    private fun pluginTasks(
        buildScript: String,
        prefix: String?,
        dirPath: String,
        hasWrapper: Boolean,
    ): List<CommandDescriptor> {
        val p = prefix?.let { "$it:" } ?: ""
        val commands = mutableListOf<CommandDescriptor>()

        val hasJavaFx = buildScript.contains("org.openjfx") ||
                        buildScript.contains("javafx") && buildScript.contains("application")
        if (hasJavaFx) {
            commands += cmd("${p}run", dirPath, hasWrapper)
            commands += cmd("${p}jlink", dirPath, hasWrapper)
            commands += cmd("${p}jpackage", dirPath, hasWrapper)
            return commands
        }

        // application plugin
        if (hasApplicationPlugin(buildScript)) {
            commands += cmd("${p}run", dirPath, hasWrapper)
        }

        // Spring Boot
        if (buildScript.contains("org.springframework.boot") || buildScript.contains("spring-boot")) {
            commands += cmd("${p}bootRun", dirPath, hasWrapper)
        }

        // Shadow JAR
        if (buildScript.contains("com.github.johnrengelman.shadow") || buildScript.contains("shadow")) {
            commands += cmd("${p}shadowJar", dirPath, hasWrapper)
        }

        // Compose Desktop
        if (buildScript.contains("org.jetbrains.compose") || buildScript.contains("compose.desktop")) {
            commands += cmd("${p}run", dirPath, hasWrapper)
        }

        return commands
    }

    private fun hasApplicationPlugin(buildScript: String): Boolean =
        buildScript.contains("application") &&
            (buildScript.contains("id(\"application\")") ||
             buildScript.contains("id 'application'") ||
             buildScript.contains("id \"application\"") ||
             buildScript.contains("apply plugin: 'application'") ||
             buildScript.contains("apply plugin: \"application\"") ||
             Regex("""id\s*\(\s*["']application["']\s*\)""").containsMatchIn(buildScript))

    private fun readBuildScript(dir: Path): String {
        val groovy = dir.resolve("build.gradle").toFile()
        val kts = dir.resolve("build.gradle.kts").toFile()
        return when {
            groovy.exists() -> try { groovy.readText() } catch (_: Exception) { "" }
            kts.exists()    -> try { kts.readText() } catch (_: Exception) { "" }
            else            -> ""
        }
    }

    private data class Subproject(val name: String, val path: String)

    /** Parse include() directives from settings.gradle(.kts) to find subproject names and paths. */
    private fun parseSubprojects(rootDir: Path): List<Subproject> {
        val settingsFile = rootDir.resolve("settings.gradle.kts").toFile()
            .takeIf { it.exists() }
            ?: rootDir.resolve("settings.gradle").toFile()
                .takeIf { it.exists() }
            ?: return emptyList()

        val content = try { settingsFile.readText() } catch (_: Exception) { return emptyList() }

        // Match include(":desktop"), include(":web", ":core"), include ":app"
        val includePattern = Regex("""include\s*\(?\s*([^)\n]+)\s*\)?""")
        val modulePattern = Regex("""["']:([^"']+)["']""")

        return includePattern.findAll(content).flatMap { match ->
            modulePattern.findAll(match.groupValues[1]).map { mod ->
                val name = mod.groupValues[1]
                // Convert ":sub:module" to "sub/module" path
                val path = name.replace(':', '/')
                Subproject(name, path)
            }
        }.toList()
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
