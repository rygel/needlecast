package io.github.rygel.needlecast.ui.update

import io.github.rygel.needlecast.ui.StatusBar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JPanel

class UpdateCheckControllerTest {
    private val statusBar = StatusBar()

    @Test
    fun `updateTimer has correct initial delay`() {
        val ctrl = UpdateCheckController(JPanel(), statusBar) { "1.0.0" }
        assertEquals(30_000, ctrl.updateTimer.initialDelay)
    }

    @Test
    fun `updateTimer repeats`() {
        val ctrl = UpdateCheckController(JPanel(), statusBar) { "1.0.0" }
        assertTrue(ctrl.updateTimer.isRepeats)
    }

    @Test
    fun `updateTimer is not running initially`() {
        val ctrl = UpdateCheckController(JPanel(), statusBar) { "1.0.0" }
        assertFalse(ctrl.updateTimer.isRunning)
    }

    @Test
    fun `buildSparkle4jInstance creates instance with valid inputs`() {
        val instance =
            buildSparkle4jInstance(
                version = "1.0.0",
                intervalHours = 24,
            )
        assertNotNull(instance)
    }

    @Test
    fun `versionProvider returning null does not crash periodic check`() {
        val ctrl = UpdateCheckController(JPanel(), statusBar) { null }
        ctrl.checkForUpdates()
        Thread.sleep(500)
    }
}
