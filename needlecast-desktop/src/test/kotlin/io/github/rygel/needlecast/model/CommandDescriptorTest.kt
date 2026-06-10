package io.github.rygel.needlecast.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandDescriptorTest {
    @Test
    fun `BuildTool enum has expected entries`() {
        val names = BuildTool.entries.map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("MAVEN", "GRADLE", "NPM", "CARGO", "GO")))
    }

    @Test
    fun `BuildTool has display names and tag colors`() {
        for (tool in BuildTool.entries) {
            assertTrue(tool.displayName.isNotBlank(), "${tool.name} has blank displayName")
            assertTrue(tool.tagLabel.isNotBlank(), "${tool.name} has blank tagLabel")
            assertTrue(tool.tagColor.startsWith("#"), "${tool.name} tagColor should start with #")
        }
    }

    @Test
    fun `isSupported returns true for normal command`() {
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("mvn", "verify"), "/project")
        assertTrue(cmd.isSupported)
    }

    @Test
    fun `isSupported returns false for unsupported placeholder`() {
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("<unsupported-mvn>"), "/project")
        assertFalse(cmd.isSupported)
    }

    @Test
    fun `isSupported returns true for empty argv`() {
        val cmd = CommandDescriptor("noop", BuildTool.SCRIPT, emptyList(), "/project")
        assertTrue(cmd.isSupported)
    }

    @Test
    fun `CommandHistoryEntry has default timestamp`() {
        val entry = CommandHistoryEntry("test", listOf("echo"), "/tmp", 0)
        assertTrue(entry.ranAt > 0)
    }

    @Test
    fun `CommandDescriptor equality works`() {
        val a = CommandDescriptor("test", BuildTool.NPM, listOf("npm", "test"), "/app")
        val b = CommandDescriptor("test", BuildTool.NPM, listOf("npm", "test"), "/app")
        assertEquals(a, b)
    }
}
