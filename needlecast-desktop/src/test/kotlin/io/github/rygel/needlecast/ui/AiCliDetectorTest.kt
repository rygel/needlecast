package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiCliDetectorTest {
    @Test
    fun `detectAiClis returns one entry per known CLI`() {
        val result = detectAiClis()
        assertEquals(KNOWN_AI_CLIS.size, result.size)
    }

    @Test
    fun `each entry has a valid AiCli and boolean`() {
        val result = detectAiClis()
        for ((cli, found) in result) {
            assertTrue(cli.name.isNotBlank(), "CLI has blank name: $cli")
            assertTrue(cli.command.isNotBlank(), "CLI has blank command: $cli")
            assertTrue(cli.description.isNotBlank(), "CLI has blank description: $cli")
            assertNotNull(found)
        }
    }

    @Test
    fun `detected entries match the known CLI list in order`() {
        val result = detectAiClis()
        for (i in KNOWN_AI_CLIS.indices) {
            assertEquals(KNOWN_AI_CLIS[i].name, result[i].first.name)
        }
    }

    @Test
    fun `AiCli data class equality works`() {
        val a = AiCli("Claude Code", "claude", "Anthropic Claude Code")
        val b = AiCli("Claude Code", "claude", "Anthropic Claude Code")
        val c = AiCli("Copilot", "copilot", "GitHub Copilot CLI")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `KNOWN_AI_CLIS contains expected entries`() {
        val names = KNOWN_AI_CLIS.map { it.name }
        assertTrue("Claude Code" in names)
        assertTrue("Copilot" in names)
        assertTrue("Gemini CLI" in names)
        assertTrue("Aider" in names)
        assertTrue("OpenAI Codex" in names)
    }

    @Test
    fun `CLI commands are unique`() {
        val commands = KNOWN_AI_CLIS.map { it.command }
        assertEquals(commands.size, commands.toSet().size, "Duplicate CLI commands found")
    }
}
