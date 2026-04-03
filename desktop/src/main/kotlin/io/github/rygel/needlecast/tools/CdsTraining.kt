package io.github.rygel.needlecast.tools

/**
 * Minimal AppCDS training entrypoint.
 *
 * This should load core classes without initializing any Swing UI so it can run
 * in headless CI environments. Keep this list conservative.
 */
object CdsTraining {
    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("java.awt.headless", "true")

        val classes = listOf(
            "io.github.rygel.needlecast.AppContext",
            "io.github.rygel.needlecast.config.JsonConfigStore",
            "io.github.rygel.needlecast.model.AppConfig",
            "io.github.rygel.needlecast.model.ProjectDirectory",
            "io.github.rygel.needlecast.scanner.CompositeProjectScanner",
            "io.github.rygel.needlecast.scanner.MavenProjectScanner",
            "io.github.rygel.needlecast.scanner.GradleProjectScanner",
            "io.github.rygel.needlecast.scanner.NpmProjectScanner",
            "io.github.rygel.needlecast.process.ProcessCommandRunner",
            "io.github.rygel.needlecast.git.ProcessGitService",
        )

        classes.forEach { name ->
            runCatching { Class.forName(name) }
        }
    }
}
