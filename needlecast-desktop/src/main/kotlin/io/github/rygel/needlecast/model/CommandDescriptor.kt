package io.github.rygel.needlecast.model

enum class BuildTool(val displayName: String, val tagLabel: String, val tagColor: String) {
    MAVEN("Maven",      "mvn",    "#2E7D32"),
    GRADLE("Gradle",    "gradle", "#1565C0"),
    DOTNET(".NET",      ".net",   "#6A0DAD"),
    INTELLIJ_RUN("Run Config", "run", "#E65100"),
    NPM("npm",          "npm",    "#CB3837"),
    APM("apm",          "apm",    "#0078D4"),
    PYTHON("Python",    "py",     "#3776AB"),
    RUST("Rust",        "cargo",  "#DEA584"),
    GO("Go",            "go",     "#00ADD8"),
}

data class CommandDescriptor(
    val label: String,
    val buildTool: BuildTool,
    val argv: List<String>,
    val workingDirectory: String,
    val env: Map<String, String> = emptyMap(),
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
