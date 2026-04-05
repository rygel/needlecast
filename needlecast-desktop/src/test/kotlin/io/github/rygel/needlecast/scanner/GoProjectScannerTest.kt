package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GoProjectScannerTest {

    private val scanner = GoProjectScanner()

    @Test
    fun `returns null when no go_mod present`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects go project from go_mod`(@TempDir dir: Path) {
        File(dir.toFile(), "go.mod").writeText("module example.com/myapp\n\ngo 1.22\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.GO), result.buildTools)
        assertTrue(result.commands.any { it.label == "go build ./..." })
        assertTrue(result.commands.any { it.label == "go test ./..." })
        assertTrue(result.commands.any { it.label == "go vet ./..." })
        assertTrue(result.commands.any { it.label == "go fmt ./..." })
        assertTrue(result.commands.any { it.label == "go mod tidy" })
    }

    @Test
    fun `adds go run when main_go exists`(@TempDir dir: Path) {
        File(dir.toFile(), "go.mod").writeText("module example.com/myapp\n")
        File(dir.toFile(), "main.go").writeText("package main\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "go run ." })
        // go run should be first
        assertEquals("go run .", result.commands[0].label)
    }

    @Test
    fun `does not add go run when no main_go`(@TempDir dir: Path) {
        File(dir.toFile(), "go.mod").writeText("module example.com/mylib\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertFalse(result.commands.any { it.label == "go run ." })
    }

    @Test
    fun `detects cmd subdirectories`(@TempDir dir: Path) {
        File(dir.toFile(), "go.mod").writeText("module example.com/myapp\n")
        val cmdDir = File(dir.toFile(), "cmd")
        File(cmdDir, "server").mkdirs()
        File(cmdDir, "cli").mkdirs()
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "go run ./cmd/cli" })
        assertTrue(result.commands.any { it.label == "go run ./cmd/server" })
        assertTrue(result.commands.any { it.label == "go build ./cmd/cli" })
        assertTrue(result.commands.any { it.label == "go build ./cmd/server" })
    }

    @Test
    fun `all commands have correct build tool and working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "go.mod").writeText("module example.com/test\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach {
            assertEquals(BuildTool.GO, it.buildTool)
            assertEquals(dir.toString(), it.workingDirectory)
        }
    }
}
