package io.github.rygel.needlecast.model

data class GitStatus(
    val branch: String?,   // null = not a git repo or detached HEAD with no branch name
    val isDirty: Boolean,  // true if working tree has uncommitted changes
) {
    companion object {
        val NotARepo = GitStatus(branch = null, isDirty = false)
    }
}
