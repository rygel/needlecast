package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class TextChunkerTest {
    private lateinit var textArea: JTextArea

    @BeforeEach
    fun setUp() {
        SwingUtilities.invokeAndWait { textArea = JTextArea() }
    }

    @AfterEach
    fun tearDown() {
        TextChunker.cancel(textArea)
    }

    @Test
    fun `setTextChunked with empty text sets empty and fires onDone`() {
        var done = false
        TextChunker.setTextChunked(textArea, "", onDone = { done = true })
        assertEquals("", textArea.text)
        assertTrue(done)
    }

    @Test
    fun `setTextChunked with short text sets immediately and fires onDone`() {
        var done = false
        TextChunker.setTextChunked(textArea, "hello", chunkSize = 32_000, onDone = { done = true })
        assertEquals("hello", textArea.text)
        assertTrue(done)
    }

    @Test
    fun `setTextChunked with large text uses timer`() {
        val large = "a".repeat(100_000)
        var done = false
        TextChunker.setTextChunked(textArea, large, chunkSize = 1_000, delayMs = 5, onDone = { done = true })
        // Wait for chunking to complete
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !done) {
            Thread.sleep(50)
        }
        assertTrue(done, "onDone was never called")
        assertEquals(large.length, textArea.text.length)
        assertEquals(large, textArea.text)
    }

    @Test
    fun `setTextChunked replaces existing text`() {
        textArea.text = "old content"
        TextChunker.setTextChunked(textArea, "new")
        assertEquals("new", textArea.text)
    }

    @Test
    fun `cancel stops pending chunking`() {
        val large = "x".repeat(50_000)
        TextChunker.setTextChunked(textArea, large, chunkSize = 500, delayMs = 100)
        // Immediately cancel
        TextChunker.cancel(textArea)
        Thread.sleep(200)
        // Text should not be fully loaded
        assertTrue(textArea.text.length < large.length)
    }

    @Test
    fun `setTextChunked called twice cancels previous`() {
        textArea.text = ""
        val first = "a".repeat(50_000)
        val second = "b".repeat(50_000)
        TextChunker.setTextChunked(textArea, first, chunkSize = 1_000, delayMs = 50)
        Thread.sleep(30) // let some chunks happen
        TextChunker.setTextChunked(textArea, second, chunkSize = 1_000, delayMs = 5)
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !textArea.text.startsWith("b".repeat(1000))) {
            Thread.sleep(50)
        }
        // Final text should be all 'b's, not a mix
        assertTrue(textArea.text.all { it == 'b' }, "Expected all b's, got mix starting with: ${textArea.text.take(50)}")
    }
}
