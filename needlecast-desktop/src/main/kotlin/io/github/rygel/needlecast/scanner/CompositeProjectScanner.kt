package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import org.slf4j.LoggerFactory

class CompositeProjectScanner(
    private val scanners: List<ProjectScanner> = listOf(
        MavenProjectScanner(),
        GradleProjectScanner(),
        DotNetProjectScanner(),
        IntellijRunConfigScanner(),
        NpmProjectScanner(),
        ApmProjectScanner(),
        PythonProjectScanner(),
        RustProjectScanner(),
        GoProjectScanner(),
        PhpProjectScanner(),
        RubyProjectScanner(),
        SwiftProjectScanner(),
        DartProjectScanner(),
        CMakeProjectScanner(),
        SbtProjectScanner(),
        ElixirProjectScanner(),
        ZigProjectScanner(),
    ),
) : ProjectScanner {

    private val logger = LoggerFactory.getLogger(CompositeProjectScanner::class.java)

    override fun scan(directory: ProjectDirectory): DetectedProject {
        val results = scanners.mapNotNull { scanner ->
            try {
                scanner.scan(directory)
            } catch (e: Exception) {
                logger.warn("Scanner ${scanner::class.simpleName} failed for '${directory.label()}'", e)
                null
            }
        }

        if (results.isEmpty()) {
            return DetectedProject(
                directory = directory,
                buildTools = emptySet(),
                commands = emptyList(),
            )
        }

        return DetectedProject(
            directory = directory,
            buildTools = results.flatMap { it.buildTools }.toSet(),
            commands = results.flatMap { it.commands },
        )
    }
}
