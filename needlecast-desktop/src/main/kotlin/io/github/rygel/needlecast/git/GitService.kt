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
}
