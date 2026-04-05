package io.github.rygel.needlecast.ui.logviewer

import java.io.File

/**
 * Discovers log files within a project directory by scanning common locations.
 * Returns files sorted by last-modified descending (newest first).
 */
object LogFileScanner {

    private val LOG_DIRS = listOf("target", "logs", "log", "build", "out")
    private val LOG_PATTERN = Regex(""".*\.log(\.\d+)?$""", RegexOption.IGNORE_CASE)

    fun scan(projectPath: String): List<File> {
        val root = File(projectPath)
        if (!root.isDirectory) return emptyList()

        val found = mutableSetOf<File>()

        // Scan root directory (non-recursive)
        root.listFiles()?.filter { it.isFile && LOG_PATTERN.matches(it.name) }?.let { found.addAll(it) }

        // Scan common subdirectories (one level deep)
        for (dirName in LOG_DIRS) {
            val dir = File(root, dirName)
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(2).filter { it.isFile && LOG_PATTERN.matches(it.name) }.forEach { found.add(it) }
        }

        return found.sortedByDescending { it.lastModified() }
    }
}
