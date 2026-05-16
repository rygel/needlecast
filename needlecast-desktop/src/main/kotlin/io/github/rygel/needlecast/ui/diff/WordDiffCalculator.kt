package io.github.rygel.needlecast.ui.diff

object WordDiffCalculator {

    data class WordDiffResult(
        val removed: List<WordDiff>,
        val added: List<WordDiff>,
    )

    private val TOKEN_REGEX = Regex("""(\s+|\w+|[^\s\w]+)""")

    fun compute(removedLine: String, addedLine: String): WordDiffResult {
        if (removedLine.isEmpty() && addedLine.isEmpty()) return WordDiffResult(emptyList(), emptyList())
        if (removedLine.isEmpty()) return WordDiffResult(emptyList(), tokenize(addedLine).map { WordDiff(WordDiffType.ADDED, it) })
        if (addedLine.isEmpty()) return WordDiffResult(tokenize(removedLine).map { WordDiff(WordDiffType.REMOVED, it) }, emptyList())

        val oldTokens = tokenize(removedLine)
        val newTokens = tokenize(addedLine)
        val ops = myersDiff(oldTokens, newTokens)

        val removedDiffs = mutableListOf<WordDiff>()
        val addedDiffs = mutableListOf<WordDiff>()

        for (op in ops) {
            when (op.type) {
                DiffLineType.REMOVED -> removedDiffs.add(WordDiff(WordDiffType.REMOVED, oldTokens[op.index]))
                DiffLineType.ADDED -> addedDiffs.add(WordDiff(WordDiffType.ADDED, newTokens[op.index]))
                DiffLineType.CONTEXT -> { }
            }
        }

        return WordDiffResult(removedDiffs, addedDiffs)
    }

    private fun tokenize(line: String): List<String> = TOKEN_REGEX.findAll(line).map { it.value }.toList()

    private data class Op(val type: DiffLineType, val index: Int)

    private fun myersDiff(old: List<String>, new: List<String>): List<Op> {
        val n = old.size
        val m = new.size
        val max = n + m
        if (max == 0) return emptyList()

        val v = IntArray(2 * max + 1) { -1 }
        val trace = mutableListOf<IntArray>()
        val offset = max

        v[offset + 1] = 0
        var foundD = -1

        for (d in 0..max) {
            trace.add(v.copyOf())
            for (k in -d..d step 2) {
                val kIdx = k + offset
                var x: Int
                if (k == -d || (k != d && v[kIdx - 1] < v[kIdx + 1])) {
                    x = v[kIdx + 1]
                } else {
                    x = v[kIdx - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && old[x] == new[y]) {
                    x++
                    y++
                }
                v[kIdx] = x
                if (x >= n && y >= m) {
                    foundD = d
                    break
                }
            }
            if (foundD >= 0) break
        }

        if (foundD < 0) foundD = max
        return backtrack(trace, old, new, foundD, offset)
    }

    private fun backtrack(
        trace: List<IntArray>,
        old: List<String>,
        new: List<String>,
        d: Int,
        offset: Int,
    ): List<Op> {
        val ops = mutableListOf<Op>()
        var x = old.size
        var y = new.size

        for (currD in d downTo 1) {
            val v = trace[currD]
            val k = x - y
            val kIdx = k + offset

            val prevK: Int
            if (k == -currD || (k != currD && v[kIdx - 1] < v[kIdx + 1])) {
                prevK = k + 1
            } else {
                prevK = k - 1
            }

            val prevX = v[prevK + offset]
            val prevY = prevX - prevK

            while (x > prevX + (if (prevK < k) 1 else 0) && y > prevY + (if (prevK > k) 1 else 0)) {
                x--
                y--
            }

            if (currD > 0) {
                if (x == prevX) {
                    y--
                    ops.add(Op(DiffLineType.ADDED, y))
                } else {
                    x--
                    ops.add(Op(DiffLineType.REMOVED, x))
                }
            }
        }

        ops.reverse()
        return ops
    }
}
