package io.github.rygel.needlecast.git

import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.process.ProcessExecutor

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

    override fun changedFiles(dir: String): List<ChangedFile> = TODO("Task 2")
    override fun stage(dir: String, files: List<String>): Unit = TODO("Task 2")
    override fun commit(dir: String, message: String): Unit = TODO("Task 2")
    override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int = TODO("Task 2")
    override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int = TODO("Task 2")
    override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int = TODO("Task 2")

    private fun runGit(dir: String, vararg args: String): String? {
        val result = ProcessExecutor.run(listOf("git", "-C", dir) + args.toList(), timeoutMs = 10_000L)
        return result?.output?.ifBlank { null }
    }
}
