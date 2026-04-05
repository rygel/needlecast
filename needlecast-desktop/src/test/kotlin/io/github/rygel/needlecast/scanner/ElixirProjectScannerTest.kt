package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ElixirProjectScannerTest {

    private val scanner = ElixirProjectScanner()

    @Test
    fun `returns null when no mix_exs`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects elixir project`(@TempDir dir: Path) {
        File(dir.toFile(), "mix.exs").writeText("defmodule MyApp.MixProject do\nend\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.MIX), result.buildTools)
        assertTrue(result.commands.any { it.label == "mix compile" })
        assertTrue(result.commands.any { it.label == "mix test" })
    }

    @Test
    fun `detects phoenix project`(@TempDir dir: Path) {
        File(dir.toFile(), "mix.exs").writeText("defmodule MyApp do\n  {:phoenix, \"~> 1.7\"}\nend\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "mix phx.server" })
        assertTrue(result.commands.any { it.label == "mix ecto.migrate" })
    }
}
