package io.github.rygel.needlecast.git

import io.github.rygel.needlecast.model.GitStatus

/**
 * Abstraction over git operations used by the UI.
 * The default implementation ([ProcessGitService]) shells out to `git`;
 * tests can substitute a fake without spawning processes.
 */
interface GitService {
    /** Returns the current branch and dirty-state for [dir], or [GitStatus.NotARepo] if not a repo. */
    fun readStatus(dir: String): GitStatus

    /**
     * Returns the last [maxEntries] commits as `git log --oneline` output,
     * or null if git is unavailable or the directory is not a repo.
     */
    fun log(dir: String, maxEntries: Int = 40): String?

    /**
     * Returns the full `git show --stat -p` output for [hash],
     * or null if the hash cannot be resolved.
     */
    fun show(dir: String, hash: String): String?

    /**
     * Returns files with uncommitted changes, or an empty list if not a repo.
     * Parses `git status --porcelain` output.
     */
    fun changedFiles(dir: String): List<ChangedFile>

    /**
     * Stages [files] for the next commit (`git add -- <files>`).
     * @throws RuntimeException if git exits with a non-zero code.
     */
    fun stage(dir: String, files: List<String>)

    /**
     * Creates a commit with [message] (`git commit -m <message>`).
     * @throws RuntimeException if git exits with a non-zero code.
     */
    fun commit(dir: String, message: String)

    /**
     * Runs `git fetch`, calling [onLine] per output line from the worker thread.
     * @return the git process exit code (0 = success).
     */
    fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int

    /**
     * Runs `git push`, calling [onLine] per output line from the worker thread.
     * @return the git process exit code (0 = success).
     */
    fun pushStreaming(dir: String, onLine: (String) -> Unit): Int

    /**
     * Runs `git pull`, calling [onLine] per output line from the worker thread.
     * @return the git process exit code (0 = success).
     */
    fun pullStreaming(dir: String, onLine: (String) -> Unit): Int
}
