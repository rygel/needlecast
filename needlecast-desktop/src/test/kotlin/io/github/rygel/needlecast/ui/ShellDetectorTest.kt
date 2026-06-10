package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellDetectorTest {
    @Test
    fun `detect returns non-empty list`() {
        val shells = ShellDetector.detect()
        assertTrue(shells.isNotEmpty(), "Expected at least one shell to be detected")
    }

    @Test
    fun `all detected shells have non-blank displayName and command`() {
        val shells = ShellDetector.detect()
        for (shell in shells) {
            assertTrue(shell.displayName.isNotBlank(), "Shell has blank displayName: $shell")
            assertTrue(shell.command.isNotBlank(), "Shell has blank command: $shell")
        }
    }

    @Test
    fun `detected shells contain platform-appropriate entries`() {
        val shells = ShellDetector.detect()
        val commands = shells.map { it.command }
        when {
            IS_WINDOWS -> {
                assertTrue(
                    commands.any { it.endsWith("cmd.exe") || it == "cmd.exe" },
                    "Expected cmd.exe on Windows, got: $commands",
                )
            }

            IS_MAC -> {
                assertTrue(
                    commands.any { it.endsWith("/zsh") || it.endsWith("/bash") || it.endsWith("/sh") },
                    "Expected zsh/bash/sh on macOS, got: $commands",
                )
            }

            else -> {
                assertTrue(
                    commands.any { it.endsWith("/bash") || it.endsWith("/zsh") || it.endsWith("/sh") },
                    "Expected bash/zsh/sh on Unix, got: $commands",
                )
            }
        }
    }

    @Test
    fun `ShellInfo equality works`() {
        val a = ShellInfo("Bash", "/bin/bash")
        val b = ShellInfo("Bash", "/bin/bash")
        val c = ShellInfo("Zsh", "/bin/zsh")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
