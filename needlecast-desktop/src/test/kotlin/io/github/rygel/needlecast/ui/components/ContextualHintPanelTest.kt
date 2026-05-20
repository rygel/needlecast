package io.github.rygel.needlecast.ui.components

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContextualHintPanelTest {

    @Test
    fun `builds panel with headline and description`() {
        val panel = ContextualHintPanel(
            hintId = "test-hint",
            headline = "No project selected",
            description = "Double-click a project to start.",
        )
        assertEquals("test-hint", panel.hintId)
        assertFalse(panel.isDismissed)
    }

    @Test
    fun `dismiss sets dismissed flag`() {
        val panel = ContextualHintPanel(
            hintId = "test-hint",
            headline = "Test",
            description = "Desc",
        )
        panel.dismiss()
        assertTrue(panel.isDismissed)
    }
}
