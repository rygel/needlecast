package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class GradleProjectScannerTest {

    private val scanner = GradleProjectScanner()

    @Test
    fun `returns null when no build file present`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `detects Gradle project from build_gradle`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins {}")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.GRADLE), result.buildTools)
        assertEquals(5, result.commands.size)
        val labels = result.commands.map { it.label }
        assertTrue(labels.any { it.contains("build") })
        assertTrue(labels.any { it.contains("test") })
        assertTrue(labels.any { it.contains("clean") })
    }

    @Test
    fun `detects Gradle project from build_gradle_kts`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle.kts").writeText("plugins {}")

        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNotNull(result)
        assertEquals(setOf(BuildTool.GRADLE), result!!.buildTools)
    }

    @Test
    fun `uses gradlew wrapper when present`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins {}")
        if (IS_WINDOWS) {
            File(dir.toFile(), "gradlew.bat").writeText("@echo off")
        } else {
            File(dir.toFile(), "gradlew").apply { writeText("#!/bin/sh"); setExecutable(true) }
        }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.all { cmd ->
            cmd.label.startsWith("./gradlew") || cmd.label.startsWith("./gradlew")
                || cmd.argv.any { it.contains("gradlew") }
        })
    }

    @Test
    fun `detects subproject tasks from settings_gradle`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins {}")
        File(dir.toFile(), "settings.gradle").writeText("""
            rootProject.name = 'myapp'
            include ':desktop', ':web'
        """.trimIndent())

        // Create subproject with application plugin
        val desktop = File(dir.toFile(), "desktop").also { it.mkdirs() }
        File(desktop, "build.gradle").writeText("""
            plugins {
                id 'application'
            }
        """.trimIndent())

        // Create subproject without application plugin
        val web = File(dir.toFile(), "web").also { it.mkdirs() }
        File(web, "build.gradle").writeText("plugins { id 'java' }")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }

        // Root tasks
        assertTrue(labels.any { it.contains("build") && !it.contains(":") })

        // Desktop subproject: should have :desktop:run (application plugin), :desktop:build, :desktop:test
        assertTrue(labels.any { it.contains(":desktop:run") }, "Missing :desktop:run, got: $labels")
        assertTrue(labels.any { it.contains(":desktop:build") }, "Missing :desktop:build, got: $labels")
        assertTrue(labels.any { it.contains(":desktop:test") }, "Missing :desktop:test, got: $labels")

        // Web subproject: should have :web:build, :web:test but NOT :web:run (no application plugin)
        assertTrue(labels.any { it.contains(":web:build") }, "Missing :web:build, got: $labels")
        assertTrue(labels.any { it.contains(":web:test") }, "Missing :web:test, got: $labels")
        assertFalse(labels.any { it.contains(":web:run") }, "Unexpected :web:run, got: $labels")
    }

    @Test
    fun `detects subproject tasks from settings_gradle_kts`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle.kts").writeText("plugins {}")
        File(dir.toFile(), "settings.gradle.kts").writeText("""
            rootProject.name = "myapp"
            include(":app")
        """.trimIndent())

        val app = File(dir.toFile(), "app").also { it.mkdirs() }
        File(app, "build.gradle.kts").writeText("""
            plugins {
                id("org.springframework.boot") version "3.2.0"
            }
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }

        assertTrue(labels.any { it.contains(":app:bootRun") }, "Missing :app:bootRun, got: $labels")
        assertTrue(labels.any { it.contains(":app:build") }, "Missing :app:build, got: $labels")
    }

    @Test
    fun `detects Spring Boot and Shadow tasks`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("""
            plugins {
                id 'org.springframework.boot' version '3.2.0'
                id 'com.github.johnrengelman.shadow' version '8.0.0'
            }
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }

        assertTrue(labels.any { it.contains("bootRun") }, "Missing bootRun, got: $labels")
        assertTrue(labels.any { it.contains("shadowJar") }, "Missing shadowJar, got: $labels")
    }

    @Test
    fun `ignores subproject with no build file`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("plugins {}")
        File(dir.toFile(), "settings.gradle").writeText("include ':ghost'")
        // No ghost/ directory or build file

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertFalse(labels.any { it.contains(":ghost:") }, "Ghost subproject should be ignored, got: $labels")
    }
}
