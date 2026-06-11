package io.github.rygel.needlecast.ui.diagnostics

import org.slf4j.Logger
import javax.swing.SwingUtilities

internal class EdtStallMonitor(
    private val logger: Logger,
    private val periodMs: Long = 50L,
    private val thresholdMs: Long = 200L,
    private val throttleMs: Long = 2_000L,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread =
            Thread({
                var lastReportAt = 0L
                while (running) {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    val scheduledAt = System.nanoTime()
                    SwingUtilities.invokeLater { latch.countDown() }
                    val ok =
                        try {
                            latch.await(thresholdMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            true
                        }
                    if (!ok) {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastReportAt >= throttleMs) {
                            lastReportAt = nowMs
                            val delayMs = (System.nanoTime() - scheduledAt) / 1_000_000
                            val edt = Thread.getAllStackTraces().keys.firstOrNull { it.name.startsWith("AWT-EventQueue") }
                            if (edt != null) {
                                val stack =
                                    Thread
                                        .getAllStackTraces()[edt]
                                        ?.joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                                        ?: "(stack unavailable)"
                                logger.warn("EDT stall detected: {} ms\n{}", delayMs, stack)
                            } else {
                                logger.warn("EDT stall detected: {} ms (EDT thread not found)", delayMs)
                            }
                        }
                    }
                    try {
                        Thread.sleep(periodMs)
                    } catch (_: InterruptedException) {
                    }
                }
            }, "edt-stall-monitor").apply {
                isDaemon = true
                start()
            }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    val isRunning: Boolean get() = running
}
