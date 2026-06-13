package io.github.rygel.needlecast.ui.diagnostics

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class EdtStallMonitorTest {
    private val logger = LoggerFactory.getLogger("test.edt")

    @Test
    fun `isRunning is false initially`() {
        val monitor = EdtStallMonitor(logger)
        assertFalse(monitor.isRunning)
    }

    @Test
    fun `start sets isRunning to true`() {
        val monitor = EdtStallMonitor(logger, periodMs = 1000, thresholdMs = 5000)
        monitor.start()
        try {
            assertTrue(monitor.isRunning)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun `stop sets isRunning to false`() {
        val monitor = EdtStallMonitor(logger, periodMs = 1000, thresholdMs = 5000)
        monitor.start()
        Thread.sleep(50)
        monitor.stop()
        assertFalse(monitor.isRunning)
    }

    @Test
    fun `stop is idempotent`() {
        val monitor = EdtStallMonitor(logger)
        monitor.stop()
        monitor.stop()
        assertFalse(monitor.isRunning)
    }

    @Test
    fun `start is idempotent`() {
        val monitor = EdtStallMonitor(logger, periodMs = 1000, thresholdMs = 5000)
        monitor.start()
        try {
            monitor.start()
            assertTrue(monitor.isRunning)
        } finally {
            monitor.stop()
        }
    }
}
