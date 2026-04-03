package io.github.rygel.needlecast.scanner

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

class NpmProjectScanner : ProjectScanner {

    private val mapper = ObjectMapper()

    // Scripts surfaced first, in this order; anything else follows alphabetically
    private val preferredScripts = listOf("dev", "start", "build", "test", "lint", "preview", "serve")

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val packageJson = dir.resolve("package.json").toFile()
        if (!packageJson.exists()) return null

        val commands = mutableListOf<CommandDescriptor>()

        try {
            val root = mapper.readTree(packageJson)
            val scripts = root.path("scripts")
            if (!scripts.isMissingNode && scripts.isObject) {
                val all = scripts.fieldNames().asSequence().toList()
                val ordered = preferredScripts.filter { it in all } +
                              all.filter { it !in preferredScripts }.sorted()
                ordered.forEach { script ->
                    commands += CommandDescriptor(
                        label = "npm run $script",
                        buildTool = BuildTool.NPM,
                        argv = npmRun(script, directory.path),
                        workingDirectory = directory.path,
                    )
                }
            }
        } catch (_: Exception) { }

        // Always include install as a fallback command
        commands += CommandDescriptor(
            label = "npm install",
            buildTool = BuildTool.NPM,
            argv = if (IS_WINDOWS) listOf("cmd", "/c", "npm", "install")
                   else listOf("npm", "install"),
            workingDirectory = directory.path,
        )

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.NPM),
            commands = commands,
        )
    }

    private fun npmRun(script: String, dir: String): List<String> =
        if (IS_WINDOWS) listOf("cmd", "/c", "npm", "run", script)
        else listOf("npm", "run", script)
}
