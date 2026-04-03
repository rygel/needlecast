package io.github.rygel.needlecast.model

import io.github.rygel.needlecast.process.ProcessExecutor

data class GitStatus(
    val branch: String?,   // null = not a git repo or detached HEAD with no branch name
    val isDirty: Boolean,  // true if working tree has uncommitted changes
) {
    companion object {
        val NotARepo = GitStatus(branch = null, isDirty = false)

        /** Reads git status for [dir] synchronously. Safe to call from a background thread. */
        fun read(dir: String): GitStatus {
            return try {
                val branch = runGit(dir, "symbolic-ref", "--short", "HEAD")?.trim()
                    ?: runGit(dir, "rev-parse", "--short", "HEAD")?.trim()?.let { "($it)" }
                val dirty = runGit(dir, "status", "--porcelain")?.isNotEmpty() ?: false
                GitStatus(branch, dirty)
            } catch (_: Exception) {
                NotARepo
            }
        }

        private fun runGit(dir: String, vararg args: String): String? {
            val argv = listOf("git", "-C", dir) + args.toList()
            val result = ProcessExecutor.run(argv, timeoutMs = 5_000L) ?: return null
            return if (result.exitCode == 0) result.output else null
        }
    }
}
