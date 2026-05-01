package io.github.rygel.needlecast.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrivacyModeTest {

    @Test
    fun `label returns display name when not private and privacy mode off`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project")
        assertEquals("My Project", dir.label(privacyModeEnabled = false))
    }

    @Test
    fun `label returns bullet placeholder when private and privacy mode on`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project", isPrivate = true)
        assertEquals("••••••", dir.label(privacyModeEnabled = true))
    }

    @Test
    fun `label returns real name when private but privacy mode off`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project", isPrivate = true)
        assertEquals("My Project", dir.label(privacyModeEnabled = false))
    }

    @Test
    fun `label returns real name when privacy mode on but project not private`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project", isPrivate = false)
        assertEquals("My Project", dir.label(privacyModeEnabled = true))
    }

    @Test
    fun `redactedPath returns bullets when private and privacy mode on`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", isPrivate = true)
        assertEquals("••••••", dir.redactedPath(privacyModeEnabled = true))
    }

    @Test
    fun `redactedPath returns real path when privacy mode off`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", isPrivate = true)
        assertEquals("/home/user/secret-project", dir.redactedPath(privacyModeEnabled = false))
    }

    @Test
    fun `redactedPath returns real path when not private`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", isPrivate = false)
        assertEquals("/home/user/secret-project", dir.redactedPath(privacyModeEnabled = true))
    }

    @Test
    fun `label falls back to path segment when no displayName`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", isPrivate = false)
        assertEquals("my-project", dir.label(privacyModeEnabled = false))
    }

    @Test
    fun `privacyModeEnabled defaults to false in AppConfig`() {
        val config = AppConfig()
        assertFalse(config.privacyModeEnabled)
    }

    @Test
    fun `private defaults to false in ProjectDirectory`() {
        val dir = ProjectDirectory(path = "/test")
        assertFalse(dir.isPrivate)
    }
}
