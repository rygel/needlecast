package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.BuildTool
import io.github.quicklaunch.model.ProjectDirectory
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
}
