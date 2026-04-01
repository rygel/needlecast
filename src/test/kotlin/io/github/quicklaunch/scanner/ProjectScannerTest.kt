package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Integration-style tests for the full [CompositeProjectScanner] pipeline.
 * Each test creates a synthetic project fixture inside a [TempDir] and verifies
 * that the correct [BuildTool] values are detected and that [CommandDescriptor]
 * entries have the expected labels, build tools and working directories.
 */
class ProjectScannerTest {

    private val scanner = CompositeProjectScanner()

    // -------------------------------------------------------------------------
    // Maven
    // -------------------------------------------------------------------------

    @Test
    fun `Maven project - detects MAVEN build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.MAVEN in result.buildTools,
            "Expected MAVEN in buildTools but got ${result.buildTools}")
    }

    @Test
    fun `Maven project - produces six mvn commands`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val mavenCommands = result.commands.filter { it.buildTool == BuildTool.MAVEN }

        assertEquals(6, mavenCommands.size,
            "Expected 6 Maven commands but got ${mavenCommands.size}")
    }

    @Test
    fun `Maven project - command labels include standard goals`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val labels = result.commands.filter { it.buildTool == BuildTool.MAVEN }.map { it.label }

        assertTrue(labels.contains("mvn clean"), "Missing 'mvn clean'")
        assertTrue(labels.contains("mvn compile"), "Missing 'mvn compile'")
        assertTrue(labels.contains("mvn test"), "Missing 'mvn test'")
        assertTrue(labels.contains("mvn package"), "Missing 'mvn package'")
        assertTrue(labels.contains("mvn verify"), "Missing 'mvn verify'")
        assertTrue(labels.contains("mvn install"), "Missing 'mvn install'")
    }

    @Test
    fun `Maven project - all commands have correct working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        result.commands.filter { it.buildTool == BuildTool.MAVEN }.forEach { cmd ->
            assertEquals(dir.toString(), cmd.workingDirectory,
                "Command '${cmd.label}' has wrong workingDirectory")
        }
    }

    @Test
    fun `Maven project - argv contains the goal name`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val verifyCmd = result.commands.first { it.label == "mvn verify" }

        assertTrue(verifyCmd.argv.contains("verify"),
            "Expected argv to contain 'verify' but got ${verifyCmd.argv}")
    }

    // -------------------------------------------------------------------------
    // Gradle
    // -------------------------------------------------------------------------

    @Test
    fun `Gradle project with build_gradle - detects GRADLE build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins { }")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.GRADLE in result.buildTools,
            "Expected GRADLE in buildTools but got ${result.buildTools}")
    }

    @Test
    fun `Gradle project with build_gradle_kts - detects GRADLE build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle.kts").writeText("plugins { }")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.GRADLE in result.buildTools)
    }

    @Test
    fun `Gradle project - produces five commands`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins { }")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val gradleCommands = result.commands.filter { it.buildTool == BuildTool.GRADLE }

        assertEquals(5, gradleCommands.size,
            "Expected 5 Gradle commands but got ${gradleCommands.size}")
    }

    @Test
    fun `Gradle project - command labels include standard tasks`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins { }")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val labels = result.commands.filter { it.buildTool == BuildTool.GRADLE }.map { it.label }

        assertTrue(labels.any { "build" in it }, "Missing a 'build' task label")
        assertTrue(labels.any { "test" in it }, "Missing a 'test' task label")
        assertTrue(labels.any { "clean" in it }, "Missing a 'clean' task label")
        assertTrue(labels.any { "assemble" in it }, "Missing an 'assemble' task label")
        assertTrue(labels.any { "check" in it }, "Missing a 'check' task label")
    }

    @Test
    fun `Gradle project without wrapper - uses gradle executable`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins { }")
        // Deliberately no gradlew / gradlew.bat present

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val buildCmd = result.commands.filter { it.buildTool == BuildTool.GRADLE }
            .first { "build" in it.label }

        assertTrue(buildCmd.argv.any { it == "gradle" || it == "gradle" },
            "Without wrapper, argv should reference 'gradle' but got ${buildCmd.argv}")
    }

    @Test
    fun `Gradle project with gradlew wrapper - uses wrapper`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins { }")
        if (IS_WINDOWS) {
            File(dir.toFile(), "gradlew.bat").writeText("@echo off")
        } else {
            File(dir.toFile(), "gradlew").apply { writeText("#!/bin/sh"); setExecutable(true) }
        }

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val gradleCommands = result.commands.filter { it.buildTool == BuildTool.GRADLE }

        assertTrue(gradleCommands.all { cmd -> cmd.argv.any { "gradlew" in it } },
            "With wrapper, all Gradle commands should reference gradlew")
    }

    // -------------------------------------------------------------------------
    // npm
    // -------------------------------------------------------------------------

    @Test
    fun `npm project with minimal package_json - detects NPM build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"name":"app"}""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.NPM in result.buildTools,
            "Expected NPM in buildTools but got ${result.buildTools}")
    }

    @Test
    fun `npm project - always produces npm install command`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"name":"app"}""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(result.commands.any { it.label == "npm install" && it.buildTool == BuildTool.NPM },
            "Expected 'npm install' command")
    }

    @Test
    fun `npm project with scripts - produces npm run commands`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText(
            """
            {
              "name": "my-app",
              "scripts": {
                "build": "vite build",
                "test":  "vitest",
                "dev":   "vite"
              }
            }
            """.trimIndent()
        )

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val labels = result.commands.filter { it.buildTool == BuildTool.NPM }.map { it.label }

        assertTrue(labels.contains("npm run build"), "Missing 'npm run build'")
        assertTrue(labels.contains("npm run test"), "Missing 'npm run test'")
        assertTrue(labels.contains("npm run dev"), "Missing 'npm run dev'")
        assertTrue(labels.contains("npm install"), "Missing 'npm install'")
    }

    @Test
    fun `npm project - preferred scripts come before others`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText(
            """
            {
              "scripts": {
                "build":   "vite build",
                "test":    "vitest",
                "dev":     "vite",
                "prepare": "husky"
              }
            }
            """.trimIndent()
        )

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val labels = result.commands.filter { it.buildTool == BuildTool.NPM }.map { it.label }

        // "dev" is a preferred script; "prepare" is not — dev must come first
        assertTrue(labels.indexOf("npm run dev") < labels.indexOf("npm run prepare"),
            "Preferred script 'dev' should appear before non-preferred 'prepare'")
    }

    @Test
    fun `npm project - all commands have correct build tool and working directory`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"start":"node ."}}""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        result.commands.filter { it.buildTool == BuildTool.NPM }.forEach { cmd ->
            assertEquals(BuildTool.NPM, cmd.buildTool)
            assertEquals(dir.toString(), cmd.workingDirectory,
                "Command '${cmd.label}' has wrong workingDirectory")
        }
    }

    @Test
    fun `npm project - malformed package_json falls back to npm install only`(@TempDir dir: Path) {
        File(dir.toFile(), "package.json").writeText("not { valid } json <<<")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        val npmCommands = result.commands.filter { it.buildTool == BuildTool.NPM }

        assertEquals(1, npmCommands.size,
            "Malformed package.json should produce only the npm install fallback")
        assertEquals("npm install", npmCommands[0].label)
    }

    // -------------------------------------------------------------------------
    // Empty directory (no build files)
    // -------------------------------------------------------------------------

    @Test
    fun `empty directory - no build tools detected`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(result.buildTools.isEmpty(),
            "Expected no build tools for empty directory but got ${result.buildTools}")
    }

    @Test
    fun `empty directory - no commands produced`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(result.commands.isEmpty(),
            "Expected no commands for empty directory but got ${result.commands.size}")
    }

    @Test
    fun `empty directory - hasCommands returns false`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertFalse(result.hasCommands)
    }

    // -------------------------------------------------------------------------
    // Mixed / monorepo
    // -------------------------------------------------------------------------

    @Test
    fun `Maven and npm in same directory - both build tools detected`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        File(dir.toFile(), "package.json").writeText("""{"scripts":{"build":"webpack"}}""")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.MAVEN in result.buildTools)
        assertTrue(BuildTool.NPM in result.buildTools)
    }

    @Test
    fun `Maven and Gradle in same directory - both build tools detected`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        File(dir.toFile(), "build.gradle").writeText("plugins { }")

        val result = scanner.scan(ProjectDirectory(dir.toString()))

        assertTrue(BuildTool.MAVEN in result.buildTools)
        assertTrue(BuildTool.GRADLE in result.buildTools)
    }
}
