package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RustProjectScannerTest {

    private val scanner = RustProjectScanner()

    @Test
    fun `returns null when no Cargo_toml present`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects rust project from Cargo_toml`(@TempDir dir: Path) {
        File(dir.toFile(), "Cargo.toml").writeText("[package]\nname = \"test\"\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.CARGO), result.buildTools)
        assertTrue(result.commands.any { it.label == "cargo build" })
        assertTrue(result.commands.any { it.label == "cargo test" })
        assertTrue(result.commands.any { it.label == "cargo run" })
        assertTrue(result.commands.any { it.label == "cargo check" })
        assertTrue(result.commands.any { it.label == "cargo clippy" })
        assertTrue(result.commands.any { it.label == "cargo fmt" })
    }

    @Test
    fun `detects workspace members and adds per-crate commands`(@TempDir dir: Path) {
        File(dir.toFile(), "Cargo.toml").writeText("""
            [workspace]
            members = [
                "crate-core",
                "crate-cli",
            ]
        """.trimIndent())
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "cargo build -p crate-core" })
        assertTrue(result.commands.any { it.label == "cargo test -p crate-core" })
        assertTrue(result.commands.any { it.label == "cargo build -p crate-cli" })
        assertTrue(result.commands.any { it.label == "cargo test -p crate-cli" })
    }

    @Test
    fun `parses single-line workspace members`(@TempDir dir: Path) {
        File(dir.toFile(), "Cargo.toml").writeText("""
            [workspace]
            members = ["lib-a", "lib-b"]
        """.trimIndent())
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "cargo build -p lib-a" })
        assertTrue(result.commands.any { it.label == "cargo build -p lib-b" })
    }

    @Test
    fun `all commands have correct build tool and working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "Cargo.toml").writeText("[package]\nname = \"test\"\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach {
            assertEquals(BuildTool.CARGO, it.buildTool)
            assertEquals(dir.toString(), it.workingDirectory)
        }
    }
}
