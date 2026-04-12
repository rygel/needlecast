package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.CommandOverride
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandOverrideTest {

    @Test
    fun `CommandOverride is present in AppConfig with empty default`() {
        val config = AppConfig()
        assertTrue(config.commandOverrides.isEmpty(), "commandOverrides should default to an empty map")
    }

    @Test
    fun `commandOverrides round-trips through copy`() {
        val override = CommandOverride(
            originalArgv = listOf("mvn", "clean", "install"),
            label = "Build",
            argv = listOf("mvn", "clean", "install", "-DskipTests"),
        )
        val config = AppConfig(
            commandOverrides = mapOf("/home/user/project" to listOf(override))
        )
        val copied = config.copy()
        assertEquals(config.commandOverrides, copied.commandOverrides, "commandOverrides should be identical after copy()")
    }

    @Test
    fun `applying override replaces matching descriptor`() {
        val original = io.github.rygel.needlecast.model.CommandDescriptor(
            label = "clean install",
            buildTool = io.github.rygel.needlecast.model.BuildTool.MAVEN,
            argv = listOf("mvn", "clean", "install"),
            workingDirectory = "/home/user/project",
        )
        val override = CommandOverride(
            originalArgv = listOf("mvn", "clean", "install"),
            label = "Build (skip tests)",
            argv = listOf("mvn", "clean", "install", "-DskipTests"),
        )
        val result = applyCommandOverrides(listOf(original), listOf(override))
        assertEquals(1, result.size, "applyCommandOverrides should return the same number of commands")
        assertEquals("Build (skip tests)", result[0].label, "label should be replaced by the override value")
        assertEquals(listOf("mvn", "clean", "install", "-DskipTests"), result[0].argv, "argv should be replaced by the override value")
    }

    @Test
    fun `override with no matching command is silently ignored`() {
        val original = io.github.rygel.needlecast.model.CommandDescriptor(
            label = "clean install",
            buildTool = io.github.rygel.needlecast.model.BuildTool.MAVEN,
            argv = listOf("mvn", "clean", "install"),
            workingDirectory = "/home/user/project",
        )
        val override = CommandOverride(
            originalArgv = listOf("mvn", "verify"),
            label = "Verify",
            argv = listOf("mvn", "verify"),
        )
        val result = applyCommandOverrides(listOf(original), listOf(override))
        assertEquals(1, result.size, "applyCommandOverrides should return the same number of commands when no override matches")
        assertEquals("clean install", result[0].label, "unmatched override should not modify the original command label")
    }
}
