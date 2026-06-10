package io.github.rygel.needlecast.ui

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class SearchResult(
    val file: File,
    val relPath: String,
    val line: Int,
    val column: Int,
    val preview: String,
)

data class SearchStats(
    var filesScanned: Int = 0,
    var filesWithMatches: Int = 0,
    var matches: Int = 0,
    var skippedLarge: Int = 0,
    var skippedBinary: Int = 0,
    var skippedDirs: Int = 0,
    var skippedFiles: Int = 0,
    var truncated: Boolean = false,
    var durationMs: Long = 0,
)

data class SearchOptions(
    val query: String,
    val caseSensitive: Boolean,
    val wholeWord: Boolean,
    val regex: Boolean,
    val includeGlobs: List<String>,
    val excludeGlobs: List<String>,
    val includeMatchers: List<PathMatcher>,
    val excludeMatchers: List<PathMatcher>,
    val sizeLimitBytes: Long?,
    val useRipgrep: Boolean,
)

data class RipgrepHit(
    val path: String,
    val line: Int,
    val column: Int,
    val text: String,
)

object SearchEngine {
    val SKIP_DIRS =
        setOf(
            ".git",
            ".hg",
            ".svn",
            ".idea",
            ".gradle",
            ".mvn",
            ".cache",
            "node_modules",
            "target",
            "build",
            "dist",
            "out",
            "vendor",
        )
    val SKIP_FILE_EXTENSIONS =
        setOf(
            "class",
            "jar",
            "war",
            "ear",
            "zip",
            "gz",
            "bz2",
            "7z",
            "png",
            "jpg",
            "jpeg",
            "gif",
            "bmp",
            "ico",
            "webp",
            "tif",
            "tiff",
            "svg",
            "pdf",
            "mp3",
            "mp4",
            "mov",
            "avi",
            "mkv",
            "exe",
            "dll",
            "so",
            "dylib",
            "bin",
            "dat",
            "ttf",
            "otf",
            "woff",
            "woff2",
            "pyc",
            "pyo",
        )

    fun parseGlobs(input: String): List<String> =
        input
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun buildMatchers(
        input: String,
        stripNegation: Boolean = false,
    ): List<PathMatcher> {
        val raw = parseGlobs(input)
        val patterns = if (stripNegation) raw.mapNotNull { it.trimStart('!').ifBlank { null } } else raw
        if (patterns.isEmpty()) return emptyList()
        val fs = FileSystems.getDefault()
        return patterns.map { pattern ->
            try {
                fs.getPathMatcher("glob:$pattern")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid glob: $pattern")
            }
        }
    }

    fun matchesAny(
        path: Path,
        fileName: String?,
        matchers: List<PathMatcher>,
    ): Boolean {
        if (matchers.isEmpty()) return false
        if (matchers.any { it.matches(path) }) return true
        if (fileName != null) {
            val namePath =
                try {
                    Path.of(fileName)
                } catch (_: Exception) {
                    null
                }
            if (namePath != null && matchers.any { it.matches(namePath) }) return true
        }
        return false
    }

    @Throws(PatternSyntaxException::class)
    fun buildMatcher(
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
    ): (String) -> Int? {
        if (regex || wholeWord) {
            val base = if (regex) query else Pattern.quote(query)
            val wrapped = if (wholeWord) "\\b$base\\b" else base
            val flags = if (caseSensitive) 0 else (Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            val pattern = Pattern.compile(wrapped, flags)
            return { line ->
                val m = pattern.matcher(line)
                if (m.find()) m.start() else null
            }
        }
        val needle = if (caseSensitive) query else query.lowercase()
        return { line ->
            val hay = if (caseSensitive) line else line.lowercase()
            val idx = hay.indexOf(needle)
            if (idx >= 0) idx else null
        }
    }

    fun buildRipgrepArgs(opts: SearchOptions): List<String> {
        val argv = mutableListOf("rg", "--vimgrep", "--no-messages")
        if (!opts.regex) argv += "--fixed-strings"
        argv += if (opts.caseSensitive) "-s" else "-i"
        if (opts.wholeWord) argv += "-w"
        if (opts.sizeLimitBytes != null) {
            val mb = (opts.sizeLimitBytes / (1024L * 1024L)).coerceAtLeast(1)
            argv += listOf("--max-filesize", "${mb}M")
        }
        opts.includeGlobs.forEach { argv += listOf("-g", it) }
        opts.excludeGlobs.forEach { argv += listOf("-g", if (it.startsWith("!")) it else "!$it") }
        argv += opts.query
        argv += "."
        return argv
    }

    fun parseRipgrepLine(line: String): RipgrepHit? {
        val last = line.lastIndexOf(':')
        if (last <= 0) return null
        val prev = line.lastIndexOf(':', last - 1)
        if (prev <= 0) return null
        val prev2 = line.lastIndexOf(':', prev - 1)
        if (prev2 <= 0) return null
        val path = line.substring(0, prev2)
        val lineNum = line.substring(prev2 + 1, prev).toIntOrNull() ?: return null
        val colNum = line.substring(prev + 1, last).toIntOrNull() ?: return null
        val text = line.substring(last + 1)
        return RipgrepHit(path, lineNum, colNum, text)
    }

    fun preview(line: String): String {
        val trimmed = line.trim()
        return if (trimmed.length <= 240) trimmed else trimmed.take(237) + "..."
    }

    fun shouldSkipDir(name: String?): Boolean = name != null && SKIP_DIRS.contains(name.lowercase())

    fun shouldSkipFile(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith(".")) return true
        val ext = lower.substringAfterLast('.', "")
        return ext.isNotEmpty() && SKIP_FILE_EXTENSIONS.contains(ext)
    }

    fun formatSummary(
        stats: SearchStats,
        maxResults: Int,
    ): String {
        val results = stats.matches
        val files = stats.filesWithMatches
        val base =
            if (results == 0) {
                "No matches."
            } else {
                "$results match${if (results == 1) "" else "es"} in $files file${if (files == 1) "" else "s"}"
            }
        val extra =
            buildString {
                append(" (${"%.2f".format(stats.durationMs / 1000.0)}s")
                if (stats.skippedLarge > 0) append(", ${stats.skippedLarge} large skipped")
                if (stats.skippedBinary > 0) append(", ${stats.skippedBinary} binary skipped")
                if (stats.skippedDirs + stats.skippedFiles > 0) append(", ${stats.skippedDirs + stats.skippedFiles} ignored")
                append(")")
            }
        return if (stats.truncated) {
            "$base$extra — results capped at $maxResults."
        } else {
            "$base$extra"
        }
    }
}
