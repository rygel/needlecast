package io.github.rygel.needlecast.process

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RunningProcessTest {
    @Test
    fun `isAlive returns true for running process`() {
        val pb =
            if (isWindows()) {
                ProcessBuilder("ping", "-n", "10", "127.0.0.1")
            } else {
                ProcessBuilder("sleep", "10")
            }
        val process = pb.start()
        try {
            val running = RunningProcess(process)
            assertTrue(running.isAlive, "Process should be alive immediately after creation")
        } finally {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `isAlive returns false after process exits`() {
        val argv = if (isWindows()) listOf("cmd", "/c", "echo", "done") else listOf("echo", "done")
        val process = ProcessBuilder(argv).start()
        process.waitFor(5, TimeUnit.SECONDS)

        val running = RunningProcess(process)
        assertFalse(running.isAlive, "Process should not be alive after exit")
    }

    @Test
    fun `cancel destroys process forcibly`() {
        val pb =
            if (isWindows()) {
                ProcessBuilder("ping", "-n", "30", "127.0.0.1")
            } else {
                ProcessBuilder("sleep", "30")
            }
        val process = pb.start()
        val running = RunningProcess(process)

        assertTrue(running.isAlive, "Process should be alive before cancel")
        running.cancel()

        val exited = process.waitFor(5, TimeUnit.SECONDS)
        assertTrue(exited, "Process should exit within timeout after cancel")
        assertFalse(running.isAlive, "Process should not be alive after cancel")
    }

    @Test
    fun `cancel interrupts reader thread`() {
        val pb =
            if (isWindows()) {
                ProcessBuilder("ping", "-n", "30", "127.0.0.1")
            } else {
                ProcessBuilder("sleep", "30")
            }
        val process = pb.start()
        val readerThread =
            Thread({
                try {
                    process.inputStream.bufferedReader().use { it.forEachLine { _ -> } }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }, "test-reader").apply {
                isDaemon = true
                start()
            }

        val running = RunningProcess(process, readerThread)
        running.cancel()

        readerThread.join(5_000)
        assertFalse(readerThread.isAlive, "Reader thread should be terminated after cancel")
        process.waitFor(5, TimeUnit.SECONDS)
    }

    @Test
    fun `cancel on already-exited process does not throw`() {
        val argv = if (isWindows()) listOf("cmd", "/c", "echo", "done") else listOf("echo", "done")
        val process = ProcessBuilder(argv).start()
        process.waitFor(5, TimeUnit.SECONDS)

        val running = RunningProcess(process)
        assertDoesNotThrow { running.cancel() }
    }

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
}
