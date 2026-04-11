package io.github.rygel.needlecast.git

/**
 * A file reported by `git status --porcelain`.
 *
 * @param path       Path of the file relative to the repository root.
 * @param statusCode The two-character XY code from `git status --porcelain`
 *                   (e.g. `" M"`, `"M "`, `"??"`, `"A "`, `"D "`).
 */
data class ChangedFile(val path: String, val statusCode: String)
