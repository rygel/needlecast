package io.github.quicklaunch.model

enum class BuildTool(val displayName: String) {
    MAVEN("Maven"),
    GRADLE("Gradle"),
    DOTNET(".NET"),
    INTELLIJ_RUN("Run Config"),
    NPM("npm"),
}

data class CommandDescriptor(
    val label: String,
    val buildTool: BuildTool,
    val argv: List<String>,
    val workingDirectory: String,
) {
    val isSupported: Boolean get() = !argv.firstOrNull().orEmpty().startsWith("<unsupported")
}

data class CommandHistoryEntry(
    val label: String,
    val argv: List<String>,
    val workingDirectory: String,
    val exitCode: Int,
    val ranAt: Long = System.currentTimeMillis(),
)
