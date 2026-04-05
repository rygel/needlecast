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

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val cargoToml = dir.resolve("Cargo.toml").toFile()
        if (!cargoToml.exists()) return null

        val content = try { cargoToml.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val commands = mutableListOf<CommandDescriptor>()

        // Standard commands
        commands += cmd("cargo build", directory, "cargo", "build")
        commands += cmd("cargo build --release", directory, "cargo", "build", "--release")
        commands += cmd("cargo test", directory, "cargo", "test")
        commands += cmd("cargo run", directory, "cargo", "run")
        commands += cmd("cargo check", directory, "cargo", "check")
        commands += cmd("cargo clippy", directory, "cargo", "clippy")
        commands += cmd("cargo fmt", directory, "cargo", "fmt")
        commands += cmd("cargo doc --open", directory, "cargo", "doc", "--open")
        commands += cmd("cargo update", directory, "cargo", "update")

        // Workspace members — add per-crate test/build/run
        val members = parseWorkspaceMembers(content)
        for (member in members) {
            val crate = member.substringAfterLast('/')
            commands += cmd("cargo build -p $crate", directory, "cargo", "build", "-p", crate)
            commands += cmd("cargo test -p $crate", directory, "cargo", "test", "-p", crate)
        }

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.RUST),
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
            if (trimmed == "[workspace]") { inWorkspace = true; continue }
            if (trimmed.startsWith("[") && trimmed != "[workspace]") { inWorkspace = false; inMembers = false; continue }
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
                if (trimmed == "]") { inMembers = false; continue }
                val name = trimmed.removeSuffix(",").trim().removeSurrounding("\"").trim()
                if (name.isNotEmpty()) members += name
            }
        }
        return members
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(
            label = label,
            buildTool = BuildTool.RUST,
            argv = if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            workingDirectory = dir.path,
        )
}
