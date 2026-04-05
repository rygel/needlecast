package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory

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
    ),
) : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject {
        val results = scanners.mapNotNull { it.scan(directory) }

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
