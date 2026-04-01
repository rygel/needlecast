package io.github.quicklaunch.model

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
            val cmd = listOf("git", "-C", dir) + args.toList()
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exit = proc.waitFor()
            return if (exit == 0) output else null
        }
    }
}
