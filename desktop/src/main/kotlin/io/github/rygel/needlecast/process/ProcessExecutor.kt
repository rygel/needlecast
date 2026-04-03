package io.github.rygel.needlecast.process

import java.io.File
import java.util.concurrent.TimeUnit

private val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().contains("win")

/**
 * Centralized helper for running short-lived processes and capturing their output.
 *
 * All methods block the calling thread. **Never call from the EDT** — always use a
 * `SwingWorker` or daemon thread.
 *
 * This replaces four divergent `ProcessBuilder` patterns scattered across the codebase
 * (`GitStatus`, `GitLogPanel`, `AiCliDetector`, `SettingsDialog`) with a single,
 * timeout-aware implementation.
 */
object ProcessExecutor {

    data class Result(val output: String, val exitCode: Int)

    /**
     * Runs [argv] and returns the combined stdout+stderr output and exit code,
     * or null if the process could not be started or exceeds [timeoutMs].
     */
    fun run(
        argv: List<String>,
        workingDir: String? = null,
        timeoutMs: Long = 10_000L,
    ): Result? {
        return try {
            val pb = ProcessBuilder(argv).redirectErrorStream(true)
            workingDir?.let { pb.directory(File(it)) }
            val proc = pb.start()

            // Read output on a daemon thread so the timeout can be enforced concurrently.
            var output = ""
            val reader = Thread({ output = proc.inputStream.bufferedReader().readText() }, "process-output-reader")
                .apply { isDaemon = true; start() }

            if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                return null
            }
            reader.join(1_000L)  // brief wait for the reader to drain any remaining bytes
            Result(output.trimEnd(), proc.exitValue())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true if [command] is found on PATH.
     * Uses `where` on Windows and `which` elsewhere.
     */
    fun isOnPath(command: String): Boolean {
        val probe = if (IS_WINDOWS) listOf("where", command) else listOf("which", command)
        return run(probe, timeoutMs = 3_000L)?.exitCode == 0
    }
}
