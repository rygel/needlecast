package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExplorerDropHandlerTest {
    @Test
    fun `single file URI returns one file`() {
        val result = parseUriList("file:///C:/tmp/hello.txt")
        assertEquals(1, result.size)
        assertEquals("hello.txt", result[0].name)
    }

    @Test
    fun `multiple file URIs returns two files`() {
        val input = "file:///C:/tmp/a.txt\r\nfile:///C:/tmp/b.txt"
        val result = parseUriList(input)
        assertEquals(2, result.size)
        assertEquals(listOf("a.txt", "b.txt"), result.map { it.name })
    }

    @Test
    fun `skips comment lines starting with hash`() {
        val input = "# this is a comment\r\nfile:///C:/tmp/data.csv"
        val result = parseUriList(input)
        assertEquals(1, result.size)
        assertEquals("data.csv", result[0].name)
    }

    @Test
    fun `skips empty lines`() {
        val input = "\r\n\r\nfile:///C:/tmp/readme.md\r\n\r\n"
        val result = parseUriList(input)
        assertEquals(1, result.size)
        assertEquals("readme.md", result[0].name)
    }

    @Test
    fun `skips non-file URIs`() {
        val input = "https://example.com\r\nhttp://example.org\r\nfile:///C:/tmp/local.txt"
        val result = parseUriList(input)
        assertEquals(1, result.size)
        assertEquals("local.txt", result[0].name)
    }

    @Test
    fun `empty input returns empty list`() {
        val result = parseUriList("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `comments only returns empty list`() {
        val input = "# comment one\r\n# comment two\r\n"
        val result = parseUriList(input)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed valid and invalid lines returns only valid file URIs`() {
        val input =
            "# header\r\n" +
                "https://example.com\r\n" +
                "\r\n" +
                "file:///C:/tmp/first.txt\r\n" +
                "http://ignore.org\r\n" +
                "file:///C:/tmp/second.txt\r\n" +
                "# footer"
        val result = parseUriList(input)
        assertEquals(2, result.size)
        assertEquals(listOf("first.txt", "second.txt"), result.map { it.name })
    }
}
