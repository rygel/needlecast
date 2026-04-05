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
    PHP("PHP",          "php",    "#777BB4"),
    RUBY("Ruby",        "ruby",   "#CC342D"),
    SWIFT("Swift",      "swift",  "#F05138"),
    DART("Dart",        "dart",   "#0175C2"),
    CMAKE("CMake",      "cmake",  "#064F8C"),
    MAKE("Make",        "make",   "#6D6E71"),
    SBT("sbt",          "sbt",    "#DC322F"),
    ELIXIR("Elixir",    "elixir", "#6E4A7E"),
    ZIG("Zig",          "zig",    "#F7A41D"),
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
