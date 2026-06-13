package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MatchProjectPathTest {
    @Test
    fun `exact match returns the path`() {
        val paths = setOf("/home/user/project-a", "/home/user/project-b")
        assertEquals("/home/user/project-a", matchProjectPath("/home/user/project-a", paths))
    }

    @Test
    fun `subdirectory matches parent project`() {
        val paths = setOf("/home/user/project")
        assertEquals("/home/user/project", matchProjectPath("/home/user/project/src/main", paths))
    }

    @Test
    fun `no match returns null`() {
        val paths = setOf("/home/user/project-a")
        assertNull(matchProjectPath("/home/user/project-b", paths))
    }

    @Test
    fun `prefix must be a directory boundary`() {
        val paths = setOf("/home/user/proj")
        assertNull(matchProjectPath("/home/user/project-extra", paths))
    }

    @Test
    fun `backslash normalised to forward slash`() {
        val paths = setOf("C:/Users/dev/project")
        assertEquals("C:/Users/dev/project", matchProjectPath("C:\\Users\\dev\\project\\src", paths))
    }

    @Test
    fun `backslash in registered paths is normalised`() {
        val paths = setOf("C:\\Users\\dev\\project")
        assertEquals("C:\\Users\\dev\\project", matchProjectPath("C:/Users/dev/project/src", paths))
    }

    @Test
    fun `returns first matching path`() {
        val paths = setOf("/a", "/a/b")
        assertEquals("/a", matchProjectPath("/a/b/c", paths))
    }

    @Test
    fun `empty paths returns null`() {
        assertNull(matchProjectPath("/any/path", emptySet()))
    }

    @Test
    fun `trailing slash on cwd matches`() {
        val paths = setOf("/home/user/project")
        assertEquals("/home/user/project", matchProjectPath("/home/user/project/", paths))
    }
}
