package io.github.rygel.needlecast.ui.diff

object DiffParser {

    fun parse(raw: String): DiffResult {
        if (raw.isBlank()) return DiffResult(emptyList(), DiffStats(0, 0))

        val files = mutableListOf<FileDiff>()
        val lines = raw.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("diff --git ")) {
                val filePath = extractFilePath(line)
                var oldPath: String? = null
                var isBinary = false
                val hunks = mutableListOf<Hunk>()
                var additions = 0
                var deletions = 0

                i++
                while (i < lines.size) {
                    val cur = lines[i]
                    when {
                        cur.startsWith("diff --git ") -> break
                        cur.startsWith("--- ") -> {
                            val p = cur.removePrefix("--- ").removePrefix("a/")
                            if (p != "/dev/null") oldPath = p
                            i++
                        }
                        cur.startsWith("+++ ") -> i++
                        cur.startsWith("Binary files") || cur.startsWith("GIT binary patch") -> {
                            isBinary = true
                            i++
                        }
                        cur.startsWith("@@ ") -> {
                            val hunk = parseHunk(lines, i)
                            hunks.add(hunk.hunk)
                            i = hunk.nextIndex
                            additions += hunk.hunk.lines.count { it.type == DiffLineType.ADDED }
                            deletions += hunk.hunk.lines.count { it.type == DiffLineType.REMOVED }
                        }
                        else -> i++
                    }
                }

                files.add(FileDiff(
                    filePath = filePath,
                    oldPath = oldPath,
                    additions = additions,
                    deletions = deletions,
                    binary = isBinary,
                    hunks = hunks,
                ))
            } else {
                i++
            }
        }

        val totalAdd = files.sumOf { it.additions }
        val totalDel = files.sumOf { it.deletions }
        return DiffResult(files, DiffStats(totalAdd, totalDel))
    }

    private fun extractFilePath(diffLine: String): String {
        val rest = diffLine.removePrefix("diff --git ")
        val bIdx = rest.indexOf(" b/")
        return if (bIdx >= 0) rest.substring(bIdx + 3) else rest.substringAfter(' ')
    }

    private data class HunkParseResult(val hunk: Hunk, val nextIndex: Int)

    private fun parseHunk(lines: List<String>, headerIndex: Int): HunkParseResult {
        val header = lines[headerIndex]
        val hunkHeaderRegex = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
        val match = hunkHeaderRegex.find(header) ?: return HunkParseResult(
            Hunk(0, 0, 0, 0, emptyList()), headerIndex + 1
        )

        val oldStart = match.groupValues[1].toInt()
        val oldCount = match.groupValues[2].toIntOrNull() ?: 1
        val newStart = match.groupValues[3].toInt()
        val newCount = match.groupValues[4].toIntOrNull() ?: 1

        val diffLines = mutableListOf<DiffLine>()
        var oldLine = oldStart
        var newLine = newStart
        var j = headerIndex + 1

        while (j < lines.size) {
            val cur = lines[j]
            when {
                cur.startsWith("diff --git ") || cur.startsWith("@@ ") -> break
                cur.startsWith("+") -> {
                    diffLines.add(DiffLine(DiffLineType.ADDED, null, newLine, cur.removePrefix("+")))
                    newLine++
                }
                cur.startsWith("-") -> {
                    diffLines.add(DiffLine(DiffLineType.REMOVED, oldLine, null, cur.removePrefix("-")))
                    oldLine++
                }
                cur.startsWith(" ") -> {
                    val content = cur.removePrefix(" ")
                    diffLines.add(DiffLine(DiffLineType.CONTEXT, oldLine, newLine, content))
                    oldLine++
                    newLine++
                }
                cur.startsWith("\\ ") -> { }
                else -> break
            }
            j++
        }

        return HunkParseResult(Hunk(oldStart, oldCount, newStart, newCount, diffLines), j)
    }
}
