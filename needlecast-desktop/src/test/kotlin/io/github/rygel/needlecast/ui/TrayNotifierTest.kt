package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.awt.TrayIcon

class TrayNotifierTest {
    @Test
    fun `notify does not throw when tray is unavailable`() {
        assertDoesNotThrow {
            TrayNotifier.notify("Test", "Message", TrayIcon.MessageType.INFO)
        }
    }

    @Test
    fun `notify does not throw with empty caption`() {
        assertDoesNotThrow {
            TrayNotifier.notify("", "Message", TrayIcon.MessageType.INFO)
        }
    }

    @Test
    fun `multiple notify calls do not throw`() {
        assertDoesNotThrow {
            repeat(5) { i ->
                TrayNotifier.notify("Caption $i", "Text $i", TrayIcon.MessageType.INFO)
            }
        }
    }
}
