package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Rust projects via `Cargo.toml`.
 *
 * Generates standard Cargo commands. Detects workspace members for
 * per-crate commands (e.g. `cargo test -p my-crate`).
 */
class RustProjectScanner : ProjectScanner {
    private val logger = org.slf4j.LoggerFactory.getLogger(RustProjectScanner::class.java)

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val cargoToml = dir.resolve("Cargo.toml").toFile()
        if (!cargoToml.exists()) return null

        val content =
            try {
                cargoToml.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                logger.warn("Failed to read {}", cargoToml.name, e)
                ""
            }
        val commands = mutableListOf<CommandDescriptor>()

        // Standard commands
        commands += scannerCmd("cargo build", directory, BuildTool.CARGO, "cargo", "build")
        commands += scannerCmd("cargo build --release", directory, BuildTool.CARGO, "cargo", "build", "--release")
        commands += scannerCmd("cargo test", directory, BuildTool.CARGO, "cargo", "test")
        commands += scannerCmd("cargo run", directory, BuildTool.CARGO, "cargo", "run")
        commands += scannerCmd("cargo check", directory, BuildTool.CARGO, "cargo", "check")
        commands += scannerCmd("cargo clippy", directory, BuildTool.CARGO, "cargo", "clippy")
        commands += scannerCmd("cargo fmt", directory, BuildTool.CARGO, "cargo", "fmt")
        commands += scannerCmd("cargo doc --open", directory, BuildTool.CARGO, "cargo", "doc", "--open")
        commands += scannerCmd("cargo update", directory, BuildTool.CARGO, "cargo", "update")

        // Workspace members — add per-crate test/build/run
        val members = parseWorkspaceMembers(content)
        for (member in members) {
            val crate = member.substringAfterLast('/')
            commands += scannerCmd("cargo build -p $crate", directory, BuildTool.CARGO, "cargo", "build", "-p", crate)
            commands += scannerCmd("cargo test -p $crate", directory, BuildTool.CARGO, "cargo", "test", "-p", crate)
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.CARGO),
            commands = commands,
        )
    }

    /** Parse `[workspace] members = ["crate-a", "crate-b"]` from Cargo.toml. */
    private fun parseWorkspaceMembers(content: String): List<String> {
        val members = mutableListOf<String>()
        var inWorkspace = false
        var inMembers = false
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed == "[workspace]") {
                inWorkspace = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed != "[workspace]") {
                inWorkspace = false
                inMembers = false
                continue
            }
            if (inWorkspace && trimmed.startsWith("members")) {
                // Single-line: members = ["a", "b"]
                val bracket = trimmed.substringAfter("[", "")
                if (bracket.isNotEmpty()) {
                    bracket.substringBefore("]").split(",").forEach { entry ->
                        val name = entry.trim().removeSurrounding("\"").trim()
                        if (name.isNotEmpty()) members += name
                    }
                    if ("]" in trimmed) continue
                }
                inMembers = true
                continue
            }
            if (inMembers) {
                if (trimmed == "]") {
                    inMembers = false
                    continue
                }
                val name =
                    trimmed
                        .removeSuffix(",")
                        .trim()
                        .removeSurrounding("\"")
                        .trim()
                if (name.isNotEmpty()) members += name
            }
        }
        return members
    }
}
