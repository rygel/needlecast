package io.github.rygel.needlecast.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FrontmatterParserTest {

    @Test
    fun `parses simple frontmatter`() {
        val raw = "---\nname: Hello\ndescription: A test\n---\nBody content here."
        val (fm, body) = FrontmatterParser.split(raw)
        assertEquals("Hello", fm["name"])
        assertEquals("A test", fm["description"])
        assertEquals("Body content here.", body)
    }

    @Test
    fun `returns empty map and raw body when no frontmatter`() {
        val raw = "Just a body with no frontmatter."
        val (fm, body) = FrontmatterParser.split(raw)
        assertTrue(fm.isEmpty())
        assertEquals(raw, body)
    }

    @Test
    fun `returns empty map when opening delimiter has no closing`() {
        val raw = "---\nname: Hello\nno closing delimiter"
        val (fm, body) = FrontmatterParser.split(raw)
        assertTrue(fm.isEmpty())
        assertEquals(raw, body)
    }

    @Test
    fun `handles empty body`() {
        val raw = "---\nname: Hello\n---\n"
        val (fm, body) = FrontmatterParser.split(raw)
        assertEquals("Hello", fm["name"])
        assertEquals("", body)
    }

    @Test
    fun `handles value with colons`() {
        val raw = "---\nname: Key: Value\n---\nbody"
        val (fm, body) = FrontmatterParser.split(raw)
        assertEquals("Key: Value", fm["name"])
        assertEquals("body", body)
    }

    @Test
    fun `trims body whitespace`() {
        val raw = "---\nname: X\n---\n\n  Hello  \n"
        val (fm, body) = FrontmatterParser.split(raw)
        assertEquals("Hello", body)
    }
}
