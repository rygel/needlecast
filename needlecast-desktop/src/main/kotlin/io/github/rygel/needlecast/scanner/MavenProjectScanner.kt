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

        // Plugin-specific goals detected from the pom
        commands += pluginGoals(pom, directory.path)

        // Submodule commands: parse <modules> from pom.xml
        for (module in parseModules(pom)) {
            val modulePom = dir.resolve(module).resolve("pom.xml").toFile()
            if (!modulePom.exists()) continue
            val modulePomText = try { modulePom.readText() } catch (_: Exception) { continue }

            // -pl module targets a specific module, -am builds its dependencies
            commands += cmd("mvn -pl $module -am compile", "-pl $module -am compile", directory.path)
            commands += cmd("mvn -pl $module -am package", "-pl $module -am package", directory.path)
            commands += cmd("mvn -pl $module -am test", "-pl $module -am test", directory.path)

            // Plugin-specific goals for the submodule
            for (goal in pluginGoals(modulePomText, directory.path, module)) {
                commands += goal
            }
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.MAVEN),
            commands = commands,
        )
    }

    /** Detect plugin-specific goals from a pom.xml. If [module] is set, prefix with -pl. */
    private fun pluginGoals(pom: String, workDir: String, module: String? = null): List<CommandDescriptor> {
        val commands = mutableListOf<CommandDescriptor>()
        val pl = if (module != null) "-pl $module " else ""

        // JavaFX
        if (pom.contains("org.openjfx")) {
            commands += cmd("mvn ${pl}javafx:run", "${pl}javafx:run", workDir)
            commands += cmd("mvn ${pl}javafx:compile", "${pl}javafx:compile", workDir)
            if (pom.contains("jlink"))  commands += cmd("mvn ${pl}javafx:jlink", "${pl}javafx:jlink", workDir)
            if (pom.contains("jimage")) commands += cmd("mvn ${pl}javafx:jimage", "${pl}javafx:jimage", workDir)
        }

        // exec-maven-plugin
        if (pom.contains("exec-maven-plugin") || pom.contains("<goal>java</goal>")) {
            commands += cmd("mvn ${pl}exec:java", "${pl}exec:java", workDir)
        }

        // Spring Boot
        if (pom.contains("spring-boot-maven-plugin")) {
            commands += cmd("mvn ${pl}spring-boot:run", "${pl}spring-boot:run", workDir)
        }

        // Quarkus
        if (pom.contains("quarkus-maven-plugin")) {
            commands += cmd("mvn ${pl}quarkus:dev", "${pl}quarkus:dev", workDir)
        }

        // Jetty
        if (pom.contains("jetty-maven-plugin") || pom.contains("maven-jetty-plugin")) {
            commands += cmd("mvn ${pl}jetty:run", "${pl}jetty:run", workDir)
        }

        // Tomcat
        if (pom.contains("tomcat7-maven-plugin") || pom.contains("tomcat-maven-plugin")) {
            commands += cmd("mvn ${pl}tomcat7:run", "${pl}tomcat7:run", workDir)
        }

        // Liberty
        if (pom.contains("liberty-maven-plugin")) {
            commands += cmd("mvn ${pl}liberty:dev", "${pl}liberty:dev", workDir)
        }

        // Kotlin compile (only for root, not submodules — it's a lifecycle binding, not a standalone goal)
        if (module == null && pom.contains("kotlin-maven-plugin")) {
            commands += cmd("mvn compile exec:java", "compile exec:java", workDir)
        }

        return commands
    }

    /** Parse <module> elements from a pom.xml to find submodule directory names. */
    private fun parseModules(pom: String): List<String> {
        val pattern = Regex("""<module>\s*([^<]+?)\s*</module>""")
        return pattern.findAll(pom).map { it.groupValues[1] }.toList()
    }

    private fun cmd(label: String, goal: String, workingDirectory: String) = CommandDescriptor(
        label = label,
        buildTool = BuildTool.MAVEN,
        argv = if (IS_WINDOWS) listOf("cmd", "/c", "mvn") + goal.split(" ")
               else listOf("mvn") + goal.split(" "),
        workingDirectory = workingDirectory,
    )
}
