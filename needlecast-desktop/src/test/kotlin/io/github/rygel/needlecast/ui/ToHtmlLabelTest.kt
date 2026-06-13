package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToHtmlLabelTest {
    @Test
    fun `plain text is wrapped in html tags`() {
        assertEquals("<html>hello world</html>", "hello world".toHtmlLabel())
    }

    @Test
    fun `ampersand is escaped`() {
        assertEquals("<html>tom&amp;jerry</html>", "tom&jerry".toHtmlLabel())
    }

    @Test
    fun `less than is escaped`() {
        assertEquals("<html>a&lt;b</html>", "a<b".toHtmlLabel())
    }

    @Test
    fun `greater than is escaped`() {
        assertEquals("<html>a&gt;b</html>", "a>b".toHtmlLabel())
    }

    @Test
    fun `all special characters are escaped together`() {
        assertEquals("<html>&amp;&lt;&gt;</html>", "&<>".toHtmlLabel())
    }

    @Test
    fun `empty string produces empty html body`() {
        assertEquals("<html></html>", "".toHtmlLabel())
    }

    @Test
    fun `multiple ampersands are all escaped`() {
        assertEquals("<html>a&amp;b&amp;c</html>", "a&b&c".toHtmlLabel())
    }

    @Test
    fun `already-escaped ampersand is double-escaped`() {
        assertEquals("<html>&amp;amp;</html>", "&amp;".toHtmlLabel())
    }
}
