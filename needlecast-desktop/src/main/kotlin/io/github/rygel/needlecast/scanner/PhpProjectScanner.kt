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
    private val logger = org.slf4j.LoggerFactory.getLogger(PhpProjectScanner::class.java)
    private val mapper = ObjectMapper()

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val composerJson = dir.resolve("composer.json").toFile()
        if (!composerJson.exists()) return null

        val commands = mutableListOf<CommandDescriptor>()
        val hasArtisan = dir.resolve("artisan").toFile().exists()

        commands += scannerCmd("composer install", directory, BuildTool.COMPOSER, "composer", "install")
        commands += scannerCmd("composer update", directory, BuildTool.COMPOSER, "composer", "update")

        // Parse scripts from composer.json
        try {
            val root = mapper.readTree(composerJson)
            val scripts = root.path("scripts")
            if (!scripts.isMissingNode && scripts.isObject) {
                scripts
                    .fieldNames()
                    .asSequence()
                    .filter { !it.startsWith("pre-") && !it.startsWith("post-") }
                    .sorted()
                    .forEach { script ->
                        commands += scannerCmd("composer run $script", directory, BuildTool.COMPOSER, "composer", "run", script)
                    }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse {}", composerJson.name, e)
        }

        // Laravel detection
        if (hasArtisan) {
            commands += scannerCmd("php artisan serve", directory, BuildTool.COMPOSER, "php", "artisan", "serve")
            commands += scannerCmd("php artisan migrate", directory, BuildTool.COMPOSER, "php", "artisan", "migrate")
            commands += scannerCmd("php artisan test", directory, BuildTool.COMPOSER, "php", "artisan", "test")
            commands += scannerCmd("php artisan tinker", directory, BuildTool.COMPOSER, "php", "artisan", "tinker")
        }

        commands += scannerCmd("composer dump-autoload", directory, BuildTool.COMPOSER, "composer", "dump-autoload")

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.COMPOSER),
            commands = commands,
        )
    }
}
