package io.github.rygel.needlecast.scanner

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects PHP projects via `composer.json`.
 *
 * Extracts Composer scripts and detects Laravel (artisan).
 */
class PhpProjectScanner : ProjectScanner {

    private val mapper = ObjectMapper()

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val composerJson = dir.resolve("composer.json").toFile()
        if (!composerJson.exists()) return null

        val commands = mutableListOf<CommandDescriptor>()
        val hasArtisan = dir.resolve("artisan").toFile().exists()

        commands += cmd("composer install", directory, "composer", "install")
        commands += cmd("composer update", directory, "composer", "update")

        // Parse scripts from composer.json
        try {
            val root = mapper.readTree(composerJson)
            val scripts = root.path("scripts")
            if (!scripts.isMissingNode && scripts.isObject) {
                scripts.fieldNames().asSequence()
                    .filter { !it.startsWith("pre-") && !it.startsWith("post-") }
                    .sorted()
                    .forEach { script ->
                        commands += cmd("composer run $script", directory, "composer", "run", script)
                    }
            }
        } catch (_: Exception) { }

        // Laravel detection
        if (hasArtisan) {
            commands += cmd("php artisan serve", directory, "php", "artisan", "serve")
            commands += cmd("php artisan migrate", directory, "php", "artisan", "migrate")
            commands += cmd("php artisan test", directory, "php", "artisan", "test")
            commands += cmd("php artisan tinker", directory, "php", "artisan", "tinker")
        }

        commands += cmd("composer dump-autoload", directory, "composer", "dump-autoload")

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.PHP),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.PHP,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
