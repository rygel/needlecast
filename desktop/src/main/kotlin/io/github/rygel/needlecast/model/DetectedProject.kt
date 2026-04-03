package io.github.rygel.needlecast.model

data class DetectedProject(
    val directory: ProjectDirectory,
    val buildTools: Set<BuildTool>,
    val commands: List<CommandDescriptor>,
    /** True when the scanner threw an exception; the project is shown in the list with a warning badge. */
    val scanFailed: Boolean = false,
) {
    val hasCommands: Boolean get() = commands.isNotEmpty()
}
