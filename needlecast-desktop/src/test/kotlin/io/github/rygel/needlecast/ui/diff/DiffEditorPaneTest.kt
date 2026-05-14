package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class DiffEditorPaneTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUp() {
            System.setProperty("java.awt.headless", "true")
        }
    }

    @Test
    fun `renders added line with correct text`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        val lines = listOf(
            DiffLine(DiffLineType.ADDED, null, 1, "added content"),
        )
        SwingUtilities.invokeAndWait { pane.renderLines(lines) }
        assertTrue(pane.styledDocument.getText(0, pane.styledDocument.length).contains("added content"))
    }

    @Test
    fun `renders context line with correct text`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        val lines = listOf(
            DiffLine(DiffLineType.CONTEXT, 1, 1, "context"),
        )
        SwingUtilities.invokeAndWait { pane.renderLines(lines) }
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("context"))
    }

    @Test
    fun `clears previous content on re-render`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        SwingUtilities.invokeAndWait {
            pane.renderLines(listOf(DiffLine(DiffLineType.CONTEXT, 1, 1, "first")))
            pane.renderLines(listOf(DiffLine(DiffLineType.CONTEXT, 1, 1, "second")))
        }
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("second"))
        assertTrue(!text.contains("first"))
    }

    @Test
    fun `exposes line count for gutter rendering`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        val lines = listOf(
            DiffLine(DiffLineType.CONTEXT, 1, 1, "ctx"),
            DiffLine(DiffLineType.ADDED, null, 2, "add"),
        )
        SwingUtilities.invokeAndWait { pane.renderLines(lines) }
        assertEquals(2, pane.lineCount)
    }
}
