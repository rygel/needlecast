package io.github.rygel.needlecast.ui.diff

enum class DiffLineType {
    CONTEXT, ADDED, REMOVED
}

enum class WordDiffType {
    ADDED, REMOVED
}

data class WordDiff(
    val type: WordDiffType,
    val text: String,
)

data class DiffLine(
    val type: DiffLineType,
    val oldLineNum: Int?,
    val newLineNum: Int?,
    val content: String,
    val wordDiffs: List<WordDiff> = emptyList(),
)

data class Hunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val lines: List<DiffLine>,
)

data class FileDiff(
    val filePath: String,
    val oldPath: String?,
    val additions: Int,
    val deletions: Int,
    val binary: Boolean,
    val hunks: List<Hunk>,
)

data class DiffStats(
    val totalAdditions: Int,
    val totalDeletions: Int,
)

data class DiffResult(
    val files: List<FileDiff>,
    val stats: DiffStats,
)
