package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProjectSwitcherFilterTest {

    private fun entry(name: String, path: String, folderPath: String = "Group") =
        ProjectSwitcherDialog.Entry(
            dir        = ProjectDirectory(path = path, displayName = name),
            folderPath = folderPath,
            privacyModeEnabled = false,
        )

    private val entries = listOf(
        entry("Frontend",  "/workspace/frontend"),
        entry("Backend",   "/workspace/backend"),
        entry("Auth API",  "/workspace/auth-api"),
        entry("Database",  "/workspace/db"),
    )

    @Test
    fun `empty query returns all entries`() {
        assertEquals(4, filterEntries(entries, "").size)
        assertEquals(4, filterEntries(entries, "   ").size)
    }

    @Test
    fun `filter matches on display name case-insensitively`() {
        val result = filterEntries(entries, "FRONT")
        assertEquals(1, result.size)
        assertEquals("Frontend", result[0].label)
    }

    @Test
    fun `filter matches on path`() {
        val result = filterEntries(entries, "auth")
        assertEquals(1, result.size)
        assertEquals("Auth API", result[0].label)
    }

    @Test
    fun `filter returns empty list when nothing matches`() {
        assertTrue(filterEntries(entries, "xyzzy").isEmpty())
    }

    @Test
    fun `filter matches multiple entries`() {
        val result = filterEntries(entries, "back")
        assertEquals(1, result.size)
        assertEquals("Backend", result[0].label)
    }

    @Test
    fun `filter matches on path segment shared by multiple entries`() {
        val result = filterEntries(entries, "workspace")
        assertEquals(4, result.size)
    }

    @Test
    fun `private project name is redacted when privacy mode on`() {
        val e = ProjectSwitcherDialog.Entry(
            dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
            folderPath = "Work",
            privacyModeEnabled = true,
        )
        assertEquals("••••••", e.label)
        assertEquals("Work  •  ••••••", e.subtitle)
    }

    @Test
    fun `private project name is visible when privacy mode off`() {
        val e = ProjectSwitcherDialog.Entry(
            dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
            folderPath = "Work",
            privacyModeEnabled = false,
        )
        assertEquals("Secret Project", e.label)
        assertEquals("Work  •  /workspace/secret", e.subtitle)
    }

    @Test
    fun `filter matches on redacted label when privacy mode on`() {
        val entries = listOf(
            ProjectSwitcherDialog.Entry(
                dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
                folderPath = "Work",
                privacyModeEnabled = true,
            ),
        )
        val result = filterEntries(entries, "••••••")
        assertEquals(1, result.size)
    }

    @Test
    fun `filter matches on real path even when privacy mode on`() {
        val entries = listOf(
            ProjectSwitcherDialog.Entry(
                dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
                folderPath = "Work",
                privacyModeEnabled = true,
            ),
        )
        val result = filterEntries(entries, "secret")
        assertEquals(1, result.size)
    }
}
