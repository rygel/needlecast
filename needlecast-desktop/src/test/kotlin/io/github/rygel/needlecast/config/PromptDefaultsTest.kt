package io.github.rygel.needlecast.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PromptDefaultsTest {
    @Test
    fun `defaultPromptLibrary returns non-empty list`() {
        val prompts = defaultPromptLibrary()
        assertTrue(prompts.isNotEmpty())
    }

    @Test
    fun `all prompt templates have non-blank names`() {
        for (p in defaultPromptLibrary()) {
            assertTrue(p.name.isNotBlank(), "Template has blank name: $p")
        }
    }

    @Test
    fun `all prompt templates have non-blank bodies`() {
        for (p in defaultPromptLibrary()) {
            assertTrue(p.body.isNotBlank(), "Template '${p.name}' has blank body")
        }
    }

    @Test
    fun `all prompt templates have categories`() {
        for (p in defaultPromptLibrary()) {
            assertTrue(p.category.isNotBlank(), "Template '${p.name}' has blank category")
        }
    }

    @Test
    fun `prompt categories are from expected set`() {
        val valid = setOf("Explore", "Fix", "Review", "Write", "Git", "DevOps")
        for (p in defaultPromptLibrary()) {
            assertTrue(p.category in valid, "Template '${p.name}' has unexpected category: ${p.category}")
        }
    }

    @Test
    fun `prompt names are unique`() {
        val names = defaultPromptLibrary().map { it.name }
        assertEquals(names.size, names.toSet().size, "Duplicate prompt names found")
    }

    @Test
    fun `all prompt templates have unique IDs`() {
        val ids = defaultPromptLibrary().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate prompt IDs found")
    }

    @Test
    fun `defaultCommandLibrary returns non-empty list`() {
        val commands = defaultCommandLibrary()
        assertTrue(commands.isNotEmpty())
    }

    @Test
    fun `all command templates have non-blank names`() {
        for (c in defaultCommandLibrary()) {
            assertTrue(c.name.isNotBlank(), "Command has blank name: $c")
        }
    }

    @Test
    fun `all command templates have non-blank bodies`() {
        for (c in defaultCommandLibrary()) {
            assertTrue(c.body.isNotBlank(), "Command '${c.name}' has blank body")
        }
    }

    @Test
    fun `command categories are from expected set`() {
        val valid = setOf("Git", "Build", "Docker", "Search", "Process")
        for (c in defaultCommandLibrary()) {
            assertTrue(c.category in valid, "Command '${c.name}' has unexpected category: ${c.category}")
        }
    }

    @Test
    fun `command names are unique`() {
        val names = defaultCommandLibrary().map { it.name }
        assertEquals(names.size, names.toSet().size, "Duplicate command names found")
    }

    @Test
    fun `variable placeholders use curly braces`() {
        val prompts = defaultPromptLibrary()
        val withPlaceholders = prompts.filter { "{" in it.body && "}" in it.body }
        assertTrue(withPlaceholders.isNotEmpty(), "Expected some templates to have {variable} placeholders")
    }
}
