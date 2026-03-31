package io.github.quicklaunch.model

data class DetectedProject(
    val directory: ProjectDirectory,
    val buildTools: Set<BuildTool>,
    val commands: List<CommandDescriptor>,
) {
    val hasCommands: Boolean get() = commands.isNotEmpty()
}
