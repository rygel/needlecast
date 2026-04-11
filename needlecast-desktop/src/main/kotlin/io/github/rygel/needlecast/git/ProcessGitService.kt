package io.github.rygel.needlecast.git

import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.process.ProcessExecutor
import java.util.concurrent.TimeUnit

/**
 * Parses the output of `git status --porcelain` into a list of [ChangedFile].
 * Internal so it can be tested directly without spawning a real git process.
 */
internal fun parseChangedFiles(porcelainOutput: String): List<ChangedFile> =
    porcelainOutput.lines()
        .filter { it.length >= 3 }
        .map { ChangedFile(path = it.substring(3), statusCode = it.substring(0, 2)) }

/**
 * [GitService] implementation that shells out to the system `git` binary.
 * All calls block the calling thread; always invoke from a background thread or SwingWorker.
 */
class ProcessGitService : GitService {

    override fun readStatus(dir: String): GitStatus = GitStatus.read(dir)

    override fun log(dir: String, maxEntries: Int): String? =
        runGit(dir, "log", "--oneline", "--no-decorate", "-$maxEntries")

    override fun show(dir: String, hash: String): String? =
        runGit(dir, "show", "--stat", "-p", hash)

    override fun changedFiles(dir: String): List<ChangedFile> {
        val raw = runGit(dir, "status", "--porcelain") ?: return emptyList()
        return parseChangedFiles(raw)
    }

    override fun stage(dir: String, files: List<String>) {
        runGitOrThrow(dir, "add", "--", *files.toTypedArray())
    }

    override fun commit(dir: String, message: String) {
        runGitOrThrow(dir, "commit", "-m", message)
    }

    override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int =
        runGitStreaming(dir, listOf("fetch"), onLine)

    override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int =
        runGitStreaming(dir, listOf("push"), onLine)

    override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int =
        runGitStreaming(dir, listOf("pull"), onLine)

    /** Runs git and returns combined stdout+stderr, or null on failure/timeout. */
    private fun runGit(dir: String, vararg args: String): String? {
        val result = ProcessExecutor.run(listOf("git", "-C", dir) + args.toList(), timeoutMs = 10_000L)
        return result?.output?.ifBlank { null }
    }

    /** Runs git and throws [RuntimeException] if the process exits non-zero. */
    private fun runGitOrThrow(dir: String, vararg args: String): String {
        val result = ProcessExecutor.run(listOf("git", "-C", dir) + args.toList(), timeoutMs = 10_000L)
            ?: throw RuntimeException("git process failed to start or timed out")
        if (result.exitCode != 0)
            throw RuntimeException("git exited with code ${result.exitCode}:\n${result.output}")
        return result.output
    }

    /**
     * Runs git and calls [onLine] for each output line as they arrive.
     * Returns the process exit code, or -1 if the process could not be started.
     * Times out after 120 seconds (generous for push/pull over slow remotes).
     */
    private fun runGitStreaming(dir: String, args: List<String>, onLine: (String) -> Unit): Int {
        val pb = ProcessBuilder(listOf("git", "-C", dir) + args).redirectErrorStream(true)
        val proc = try { pb.start() } catch (_: Exception) { return -1 }
        return try {
            proc.inputStream.bufferedReader().forEachLine { line -> onLine(line) }
            proc.waitFor(120_000L, TimeUnit.MILLISECONDS)
            proc.exitValue()
        } catch (_: Exception) {
            -1
        } finally {
            proc.destroyForcibly()
        }
    }
}
