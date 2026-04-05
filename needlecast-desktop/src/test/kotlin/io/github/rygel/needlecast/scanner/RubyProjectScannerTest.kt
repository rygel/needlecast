package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RubyProjectScannerTest {

    private val scanner = RubyProjectScanner()

    @Test
    fun `returns null when no Gemfile`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects ruby project`(@TempDir dir: Path) {
        File(dir.toFile(), "Gemfile").writeText("source 'https://rubygems.org'\n")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.RUBY), result.buildTools)
        assertTrue(result.commands.any { it.label == "bundle install" })
    }

    @Test
    fun `detects rails project`(@TempDir dir: Path) {
        File(dir.toFile(), "Gemfile").writeText("gem 'rails'\n")
        File(dir.toFile(), "bin").mkdirs()
        File(dir.toFile(), "bin/rails").writeText("#!/usr/bin/env ruby")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "rails server" })
        assertTrue(result.commands.any { it.label == "rails test" })
    }

    @Test
    fun `detects rakefile`(@TempDir dir: Path) {
        File(dir.toFile(), "Gemfile").writeText("source 'https://rubygems.org'\n")
        File(dir.toFile(), "Rakefile").writeText("task :default")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "rake test" })
    }
}
