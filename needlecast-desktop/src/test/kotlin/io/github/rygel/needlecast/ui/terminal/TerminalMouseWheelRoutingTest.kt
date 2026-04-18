package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel

class TerminalMouseWheelRoutingTest {

    @Test
    fun `plain remote wheel events are consumed to stop outer scrolling`() {
        val event = mouseWheelEvent()

        assertTrue(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = true))
    }

    @Test
    fun `ctrl plus remote wheel stays available for font zoom`() {
        val event = mouseWheelEvent(modifiersEx = MouseEvent.CTRL_DOWN_MASK)

        assertFalse(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = true))
    }

    @Test
    fun `local wheel events are not consumed by remote routing guard`() {
        val event = mouseWheelEvent()

        assertFalse(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = false))
    }

    @Test
    fun `already consumed wheel events are ignored`() {
        val event = mouseWheelEvent().apply { consume() }

        assertFalse(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = true))
    }

    private fun mouseWheelEvent(modifiersEx: Int = 0): MouseWheelEvent = MouseWheelEvent(
        JPanel(),
        MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        modifiersEx,
        10,
        10,
        10,
        10,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        3,
        1,
        1.0,
    )
}
