package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.CommandDescriptor
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProjectDirectory
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class IntellijRunConfigScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val xmlFiles = mutableListOf<File>()

        dir.resolve(".idea/runConfigurations").toFile()
            .takeIf { it.isDirectory }
            ?.listFiles { f -> f.extension == "xml" }
            ?.let { xmlFiles.addAll(it) }

        dir.resolve(".run").toFile()
            .takeIf { it.isDirectory }
            ?.listFiles { f -> f.extension == "xml" }
            ?.let { xmlFiles.addAll(it) }

        if (xmlFiles.isEmpty()) return null

        val commands = xmlFiles.mapNotNull { file -> parseRunConfig(file, directory.path) }
        if (commands.isEmpty()) return null

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.INTELLIJ_RUN),
            commands = commands,
        )
    }

    private fun parseRunConfig(file: File, workingDirectory: String): CommandDescriptor? {
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            doc.documentElement.normalize()

            val configNodes = doc.getElementsByTagName("configuration")
            if (configNodes.length == 0) return null

            val config = configNodes.item(0) as Element
            val name = config.getAttribute("name").ifBlank { file.nameWithoutExtension }
            val type = config.getAttribute("type")

            val argv = resolveArgv(config, type)

            CommandDescriptor(
                label = name,
                buildTool = BuildTool.INTELLIJ_RUN,
                argv = argv,
                workingDirectory = workingDirectory,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveArgv(config: Element, type: String): List<String> {
        return when (type) {
            "Application" -> resolveApplicationArgv(config)
            "JUnit" -> resolveJUnitArgv(config)
            "MavenRunConfiguration" -> resolveMavenArgv(config)
            else -> listOf("<unsupported: $type>")
        }
    }

    private fun resolveApplicationArgv(config: Element): List<String> {
        val options = getOptions(config)
        val mainClass = options["MAIN_CLASS_NAME"] ?: return listOf("<unsupported: Application - no main class>")
        val vmParams = options["VM_PARAMETERS"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        val programParams = options["PROGRAM_PARAMETERS"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()

        val cmd = mutableListOf("java")
        cmd.addAll(vmParams)
        cmd.add(mainClass)
        cmd.addAll(programParams)
        return if (IS_WINDOWS) listOf("cmd", "/c") + cmd else cmd
    }

    private fun resolveJUnitArgv(config: Element): List<String> {
        val options = getOptions(config)
        val vmParams = options["VM_PARAMETERS"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        val cmd = mutableListOf("java") + vmParams + listOf("-cp", ".", "org.junit.platform.console.ConsoleLauncher", "--scan-classpath")
        return if (IS_WINDOWS) listOf("cmd", "/c") + cmd else cmd
    }

    private fun resolveMavenArgv(config: Element): List<String> {
        val runnerSettings = config.getElementsByTagName("MavenSettings")
        val goals = if (runnerSettings.length > 0) {
            (runnerSettings.item(0) as Element).getAttribute("goals").ifBlank { "verify" }
        } else "verify"

        return if (IS_WINDOWS) listOf("cmd", "/c", "mvn") + goals.split(" ")
        else listOf("mvn") + goals.split(" ")
    }

    private fun getOptions(config: Element): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val options = config.getElementsByTagName("option")
        for (i in 0 until options.length) {
            val opt = options.item(i) as? Element ?: continue
            val name = opt.getAttribute("name")
            val value = opt.getAttribute("value")
            if (name.isNotBlank()) result[name] = value
        }
        return result
    }
}
