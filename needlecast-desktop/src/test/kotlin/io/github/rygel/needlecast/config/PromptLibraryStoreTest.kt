package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.PromptTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PromptLibraryStoreTest {

    @Test
    fun `loadPrompts reads markdown files from category directories`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val commandsDir = base.resolve("commands")
        Files.createDirectories(promptsDir.resolve("Explore"))
        Files.writeString(promptsDir.resolve("Explore/onboarding.md"), """
            ---
            name: Onboard me to this repo
            description: Quick orientation for an unfamiliar codebase.
            ---
            Give me a 2-minute developer onboarding:
            1. What does this project do?
        """.trimIndent())

        val store = PromptLibraryStore(promptsDir, commandsDir)
        val prompts = store.loadPrompts()

        assertEquals(1, prompts.size)
        assertEquals("Onboard me to this repo", prompts[0].name)
        assertEquals("Explore", prompts[0].category)
        assertEquals("Quick orientation for an unfamiliar codebase.", prompts[0].description)
        assertTrue(prompts[0].body.startsWith("Give me a 2-minute developer onboarding:"))
        assertNotNull(prompts[0].id)
    }

    @Test
    fun `loadPrompts returns empty list when directory does not exist`(@TempDir base: Path) {
        val store = PromptLibraryStore(base.resolve("nonexistent-prompts"), base.resolve("nonexistent-commands"))
        assertTrue(store.loadPrompts().isEmpty())
        assertTrue(store.loadCommands().isEmpty())
    }

    @Test
    fun `loadPrompts handles multiple categories`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        Files.createDirectories(promptsDir.resolve("Explore"))
        Files.createDirectories(promptsDir.resolve("Fix"))
        Files.writeString(promptsDir.resolve("Explore/onboarding.md"), """
            ---
            name: Onboard me
            description: Orientation.
            ---
            Onboard text.
        """.trimIndent())
        Files.writeString(promptsDir.resolve("Fix/fix-error.md"), """
            ---
            name: Fix this error
            description: Diagnose and fix.
            ---
            Fix text.
        """.trimIndent())

        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val prompts = store.loadPrompts()

        assertEquals(2, prompts.size)
        assertEquals(listOf("Explore", "Fix"), prompts.map { it.category }.sorted())
    }

    @Test
    fun `ID is deterministic from relative path`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        Files.createDirectories(promptsDir.resolve("Explore"))
        val content = """
            ---
            name: Onboard me
            description: desc
            ---
            Body.
        """.trimIndent()
        Files.writeString(promptsDir.resolve("Explore/onboarding.md"), content)

        val store1 = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val store2 = PromptLibraryStore(promptsDir, base.resolve("commands"))

        assertEquals(store1.loadPrompts()[0].id, store2.loadPrompts()[0].id)
    }

    @Test
    fun `loadCommands reads from commands directory`(@TempDir base: Path) {
        val commandsDir = base.resolve("commands")
        Files.createDirectories(commandsDir.resolve("Git"))
        Files.writeString(commandsDir.resolve("Git/status.md"), """
            ---
            name: Status
            description: Short status with branch name.
            ---
            git status -sb
        """.trimIndent())

        val store = PromptLibraryStore(base.resolve("prompts"), commandsDir)
        val commands = store.loadCommands()

        assertEquals(1, commands.size)
        assertEquals("Status", commands[0].name)
        assertEquals("Git", commands[0].category)
        assertEquals("git status -sb", commands[0].body.trim())
    }

    @Test
    fun `save creates file in category directory`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val template = PromptTemplate(
            name = "Fix this error",
            category = "Fix",
            description = "Diagnose and fix.",
            body = "Find the root cause.",
        )

        store.save(template, isCommand = false)

        val file = promptsDir.resolve("Fix/fix-this-error.md")
        assertTrue(Files.exists(file))
        val content = Files.readString(file)
        assertTrue(content.contains("name: Fix this error"))
        assertTrue(content.contains("description: Diagnose and fix."))
        assertTrue(content.contains("Find the root cause."))
    }

    @Test
    fun `save moves file when category changes`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val original = PromptTemplate(name = "Test", category = "Old", description = "", body = "body")
        store.save(original, isCommand = false)
        val loaded = store.loadPrompts()
        val id = loaded[0].id

        val moved = loaded[0].copy(category = "New")
        store.save(moved, isCommand = false)

        assertFalse(Files.exists(promptsDir.resolve("Old/test.md")))
        assertTrue(Files.exists(promptsDir.resolve("New/test.md")))
        val reloaded = store.loadPrompts()
        assertEquals("Test", reloaded[0].name)
        assertEquals("New", reloaded[0].category)
        assertEquals("body", reloaded[0].body)
    }

    @Test
    fun `delete removes file and empty directory`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val template = PromptTemplate(name = "Test", category = "Solo", description = "", body = "body")
        store.save(template, isCommand = false)

        val loaded = store.loadPrompts()
        store.delete(loaded[0], isCommand = false)

        assertFalse(Files.exists(promptsDir.resolve("Solo/test.md")))
        assertFalse(Files.exists(promptsDir.resolve("Solo")))
        assertTrue(store.loadPrompts().isEmpty())
    }

    @Test
    fun `slugify handles special characters`() {
        assertEquals("fix-this-error", PromptLibraryStore.slugify("Fix this error"))
        assertEquals("maven-package-skip-tests", PromptLibraryStore.slugify("Maven package (skip tests)"))
        assertEquals("what-does-this-file-do", PromptLibraryStore.slugify("What does this file do"))
        assertEquals("untitled", PromptLibraryStore.slugify("!!!"))
    }
}
