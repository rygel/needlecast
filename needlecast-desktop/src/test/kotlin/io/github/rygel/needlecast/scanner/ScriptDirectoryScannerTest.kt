package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ScriptDirectoryScannerTest {

    private val scanner = ScriptDirectoryScanner()

    @Test
    fun `returns null when no scripts bin or extraScanDirs`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `auto-detects scripts dir`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        val script = File(scriptsDir, "deploy.sh").also { it.writeText("#!/bin/bash") }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.SCRIPT), result.buildTools)
        assertEquals(1, result.commands.size)
        val cmd = result.commands[0]
        assertEquals("scripts/deploy.sh", cmd.label)
        assertEquals(listOf("bash", script.canonicalPath), cmd.argv)
        assertEquals(dir.toString(), cmd.workingDirectory)
    }

    @Test
    fun `auto-detects bin dir`(@TempDir dir: Path) {
        val binDir = File(dir.toFile(), "bin").also { it.mkdir() }
        File(binDir, "run").writeText("#!/bin/sh")                   // no recognised extension — skipped
        val script = File(binDir, "start.py").also { it.writeText("# python") }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(1, result.commands.size)
        assertEquals("bin/start.py", result.commands[0].label)
        assertEquals(listOf("python3", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `unrecognised extension is skipped`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        File(scriptsDir, "README.md").writeText("# docs")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `ts extension produces npx ts-node argv`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        val script = File(scriptsDir, "build.ts").also { it.writeText("// ts") }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(listOf("npx", "ts-node", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `extraScanDirs relative path resolves against project root`(@TempDir dir: Path) {
        val toolsDir = File(dir.toFile(), "tools").also { it.mkdir() }
        val script = File(toolsDir, "build.sh").also { it.writeText("#!/bin/bash") }

        val result = scanner.scan(
            ProjectDirectory(dir.toString(), extraScanDirs = listOf("tools"))
        )!!

        assertEquals(1, result.commands.size)
        assertEquals("tools/build.sh", result.commands[0].label)
        assertEquals(listOf("bash", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `extraScanDirs absolute path outside project root is accepted`(@TempDir root: Path) {
        val projectDir = Files.createDirectories(root.resolve("myproject"))
        val externalDir = Files.createDirectories(root.resolve("external"))
        val script = File(externalDir.toFile(), "deploy.rb").also { it.writeText("# ruby") }

        val result = scanner.scan(
            ProjectDirectory(
                path = projectDir.toString(),
                extraScanDirs = listOf(externalDir.toString()),
            )
        )!!

        assertEquals(1, result.commands.size)
        assertEquals(script.canonicalPath, result.commands[0].label)
        assertEquals(listOf("ruby", script.canonicalPath), result.commands[0].argv)
    }

    @Test
    fun `deduplicates dirs when extraScanDirs contains auto-detected dir`(@TempDir dir: Path) {
        val scriptsDir = File(dir.toFile(), "scripts").also { it.mkdir() }
        File(scriptsDir, "deploy.sh").writeText("#!/bin/bash")

        // "scripts" is also auto-detected — command must appear only once
        val result = scanner.scan(
            ProjectDirectory(dir.toString(), extraScanDirs = listOf("scripts"))
        )!!

        assertEquals(1, result.commands.size)
    }
}
