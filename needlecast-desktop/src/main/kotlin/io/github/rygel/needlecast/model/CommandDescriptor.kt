package io.github.rygel.needlecast.model

enum class BuildTool(val displayName: String, val tagLabel: String, val tagColor: String) {
    MAVEN("Maven",      "mvn",    "#2E7D32"),
    GRADLE("Gradle",    "gradle", "#1565C0"),
    DOTNET(".NET",      ".net",   "#6A0DAD"),
    INTELLIJ_RUN("Run Config", "run", "#E65100"),
    NPM("npm",          "npm",    "#CB3837"),
    APM("apm",          "apm",    "#0078D4"),
    UV("uv",            "uv",       "#3776AB"),
    POETRY("Poetry",    "poetry",   "#3776AB"),
    PIP("pip",          "pip",      "#3776AB"),
    CARGO("Cargo",      "cargo",    "#DEA584"),
    GO("Go",            "go",       "#00ADD8"),
    COMPOSER("Composer", "composer", "#777BB4"),
    BUNDLER("Bundler",  "bundle",   "#CC342D"),
    SPM("SPM",          "spm",      "#F05138"),
    PUB("pub",          "pub",      "#0175C2"),
    FLUTTER("Flutter",  "flutter",  "#0175C2"),
    CMAKE("CMake",      "cmake",    "#064F8C"),
    MAKE("Make",        "make",     "#6D6E71"),
    SBT("sbt",          "sbt",      "#DC322F"),
    MIX("Mix",          "mix",      "#6E4A7E"),
    ZIG("Zig",          "zig",      "#F7A41D"),
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
