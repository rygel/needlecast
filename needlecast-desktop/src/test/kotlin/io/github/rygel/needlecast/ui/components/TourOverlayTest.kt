package io.github.rygel.needlecast.ui.components

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TourOverlayTest {
    @Test
    fun `TourStep holds required data`() {
        val step = TourStep("Test", "Description", "panel-id")
        assertEquals("Test", step.title)
        assertEquals("Description", step.description)
        assertEquals("panel-id", step.panelId)
    }

    @Test
    fun `TourStep with optional tab`() {
        val step = TourStep("Test", "Desc", "panel-id", tabName = "Git")
        assertEquals("Git", step.tabName)
    }
}
