# IntelliJ-Style Diff Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain `JTextArea` diff display with an IntelliJ-quality side-by-side diff viewer featuring color-coded lines, inline word diff, file tree navigation, overview bar, and search.

**Architecture:** Pure Swing — `JTextPane` with `StyledDocument` for colored rendering, `JTree` for file navigation, custom `JComponent`s for overview bar and line gutter. A `DiffParser` converts raw `git show` output into structured `DiffResult` data that drives all rendering. No new dependencies.

**Tech Stack:** Kotlin 2.2, Swing, FlatLaf 3.7.1, JUnit 6.0.3, AssertJ Swing 3.17.1

---

## File Structure

| File | Responsibility |
|------|---------------|
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffModel.kt` | Data classes: DiffResult, FileDiff, Hunk, DiffLine, WordDiff, DiffStats, DiffLineType |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffParser.kt` | Parses raw `git show --stat -p --no-color` output into DiffResult |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/WordDiffCalculator.kt` | Myers' diff algorithm for word-level inline changes |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffColors.kt` | Theme-aware color constants and helpers |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffEditorPane.kt` | JTextPane subclass with styled diff rendering and line gutter |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffLineNumberGutter.kt` | Custom JComponent that paints line numbers alongside a DiffEditorPane |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/SynchronizedScrollListener.kt` | Coordinates vertical scrolling between two JScrollPanes |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffContentPanel.kt` | Manages side-by-side or unified layout with DiffEditorPanes |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffFileTree.kt` | JTree showing changed files with +/- stats |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffOverviewBar.kt` | Custom JComponent minimap showing change positions |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffSearchBar.kt` | Collapsible search-within-diff bar |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffViewerPanel.kt` | Top-level container orchestrating all diff components |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt` | Modified — adds `--no-color` flag |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt` | Modified — replaces JTextArea with DiffViewerPanel |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt` | Modified — passes fileOpener callback |
| `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffParserTest.kt` | Unit tests for DiffParser |
| `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/WordDiffCalculatorTest.kt` | Unit tests for WordDiffCalculator |
| `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffEditorPaneTest.kt` | Unit tests for DiffEditorPane rendering |

---

### Task 1: Diff Model Data Classes

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffModel.kt`
- Test: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffParserTest.kt` (uses these types)

- [ ] **Step 1: Create DiffModel.kt with all data classes**

```kotlin
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
```

- [ ] **Step 2: Create the test directory**

```bash
mkdir -p needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff
```

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffModel.kt
git commit -m "feat(diff): add diff data model classes"
```

---

### Task 2: Diff Parser

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffParser.kt`
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffParserTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffParserTest {

    @Test
    fun `parses a single-file diff with added and removed lines`() {
        val raw = """
commit abc123
Author: Test
Date:   Now

    test commit

 1 file changed, 2 insertions(+), 1 deletion(-)

diff --git a/src/Main.kt b/src/Main.kt
index 111..222 100644
--- a/src/Main.kt
+++ b/src/Main.kt
@@ -10,7 +10,8 @@ class Main {
     fun old() {
-        println("old")
+        println("new")
+        println("extra")
     }
 }
        """.trimIndent()

        val result = DiffParser.parse(raw)
        assertEquals(1, result.files.size)
        assertEquals("src/Main.kt", result.files[0].filePath)
        assertEquals(2, result.files[0].additions)
        assertEquals(1, result.files[0].deletions)
        assertEquals(1, result.files[0].hunks.size)
        assertEquals(3, result.stats.totalAdditions)
        assertEquals(1, result.stats.totalDeletions)
    }

    @Test
    fun `skips commit header and stat lines`() {
        val raw = """
commit deadbeef
Author: Test
Date:   Now

    subject

 3 files changed, 10 insertions(+), 5 deletions(-)

diff --git a/A.kt b/A.kt
--- a/A.kt
+++ b/A.kt
@@ -1,3 +1,3 @@
 line1
-line2
+LINE2
 line3
        """.trimIndent()

        val result = DiffParser.parse(raw)
        assertEquals(1, result.files.size)
        assertEquals("A.kt", result.files[0].filePath)
    }

    @Test
    fun `handles binary files`() {
        val raw = """
diff --git a/image.png b/image.png
Binary files /dev/null and b/image.png differ
        """.trimIndent()

        val result = DiffParser.parse(raw)
        assertEquals(1, result.files.size)
        assertTrue(result.files[0].binary)
        assertEquals(0, result.files[0].hunks.size)
    }

    @Test
    fun `handles file renames`() {
        val raw = """
diff --git a/old.txt b/new.txt
similarity index 100%
rename from old.txt
rename to new.txt
--- a/old.txt
+++ b/new.txt
@@ -1 +1 @@
-old content
+new content
        """.trimIndent()

        val result = DiffParser.parse(raw)
        assertEquals(1, result.files.size)
        assertEquals("new.txt", result.files[0].filePath)
        assertEquals("old.txt", result.files[0].oldPath)
    }

    @Test
    fun `parses multi-file diff`() {
        val raw = """
diff --git a/A.kt b/A.kt
--- a/A.kt
+++ b/A.kt
@@ -1 +1 @@
-old
+new
diff --git a/B.kt b/B.kt
--- a/B.kt
+++ b/B.kt
@@ -1 +1,2 @@
 old
+added
        """.trimIndent()

        val result = DiffParser.parse(raw)
        assertEquals(2, result.files.size)
        assertEquals("A.kt", result.files[0].filePath)
        assertEquals("B.kt", result.files[1].filePath)
        assertEquals(1, result.files[1].additions)
        assertEquals(0, result.files[1].deletions)
    }

    @Test
    fun `assigns correct line numbers`() {
        val raw = """
diff --git a/F.kt b/F.kt
--- a/F.kt
+++ b/F.kt
@@ -5,4 +5,5 @@ context
 context1
-removed
+added1
+added2
 context2
        """.trimIndent()

        val lines = DiffParser.parse(raw).files[0].hunks[0].lines
        assertEquals(5, lines[0].oldLineNum)
        assertEquals(5, lines[0].newLineNum)
        assertEquals(6, lines[1].oldLineNum)
        assertEquals(null, lines[1].newLineNum)
        assertEquals(null, lines[2].newLineNum)
        assertEquals(6, lines[2].oldLineNum)
        assertEquals(null, lines[3].newLineNum)
        assertEquals(7, lines[3].oldLineNum)
        assertEquals(7, lines[4].newLineNum)
        assertEquals(7, lines[4].oldLineNum)
        assertEquals(8, lines[4].newLineNum)
    }

    @Test
    fun `returns empty result for empty input`() {
        val result = DiffParser.parse("")
        assertEquals(0, result.files.size)
        assertEquals(0, result.stats.totalAdditions)
    }

    @Test
    fun `computes stats from parsed data`() {
        val raw = """
diff --git a/A.kt b/A.kt
--- a/A.kt
+++ b/A.kt
@@ -1,3 +1,4 @@
 line1
-line2
+LINE2
+extra
 line3
        """.trimIndent()

        val result = DiffParser.parse(raw)
        assertEquals(2, result.stats.totalAdditions)
        assertEquals(1, result.stats.totalDeletions)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=DiffParserTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `DiffParser` does not exist

- [ ] **Step 3: Write DiffParser implementation**

```kotlin
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
                val fileStart = i
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
                            val hunk = parseHunk(lines, i, filePath)
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

    private fun parseHunk(lines: List<String>, headerIndex: Int, filePath: String): HunkParseResult {
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
                cur.startsWith("\\ ") -> { /* "No newline at end of file" — skip */ }
                else -> break
            }
            j++
        }

        return HunkParseResult(Hunk(oldStart, oldCount, newStart, newCount, diffLines), j)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=DiffParserTest`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffParser.kt \
        needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffParserTest.kt
git commit -m "feat(diff): add DiffParser with tests"
```

---

### Task 3: Word Diff Calculator

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/WordDiffCalculator.kt`
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/WordDiffCalculatorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WordDiffCalculatorTest {

    @Test
    fun `detects single word change`() {
        val removed = """        println("old")"""
        val added = """        println("new")"""
        val result = WordDiffCalculator.compute(removed, added)

        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "old")), result.removed)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "new")), result.added)
    }

    @Test
    fun `detects multiple word changes`() {
        val removed = "foo bar baz"
        val added = "foo qux baz"
        val result = WordDiffCalculator.compute(removed, added)

        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "bar")), result.removed)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "qux")), result.added)
    }

    @Test
    fun `returns empty for identical lines`() {
        val line = "same content"
        val result = WordDiffCalculator.compute(line, line)

        assertEquals(emptyList<WordDiff>(), result.removed)
        assertEquals(emptyList<WordDiff>(), result.added)
    }

    @Test
    fun `handles entirely different lines`() {
        val result = WordDiffCalculator.compute("aaa bbb", "ccc ddd")

        assertEquals(listOf(
            WordDiff(WordDiffType.REMOVED, "aaa"),
            WordDiff(WordDiffType.REMOVED, "bbb"),
        ), result.removed)
        assertEquals(listOf(
            WordDiff(WordDiffType.ADDED, "ccc"),
            WordDiff(WordDiffType.ADDED, "ddd"),
        ), result.added)
    }

    @Test
    fun `handles empty lines`() {
        val result = WordDiffCalculator.compute("", "")
        assertEquals(emptyList<WordDiff>(), result.removed)
        assertEquals(emptyList<WordDiff>(), result.added)
    }

    @Test
    fun `handles added line vs empty`() {
        val result = WordDiffCalculator.compute("", "added")
        assertEquals(emptyList<WordDiff>(), result.removed)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "added")), result.added)
    }

    @Test
    fun `handles removed line vs empty`() {
        val result = WordDiffCalculator.compute("removed", "")
        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "removed")), result.removed)
        assertEquals(emptyList<WordDiff>(), result.added)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=WordDiffCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `WordDiffCalculator` does not exist

- [ ] **Step 3: Write WordDiffCalculator implementation**

```kotlin
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
                DiffLineType.CONTEXT -> { /* unchanged — skip */ }
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
            val v = trace[currD - 1]
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=WordDiffCalculatorTest`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/WordDiffCalculator.kt \
        needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/WordDiffCalculatorTest.kt
git commit -m "feat(diff): add WordDiffCalculator with Myers diff algorithm"
```

---

### Task 4: Wire Word Diffs into DiffParser

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffParser.kt`

- [ ] **Step 1: Update DiffParser to compute word diffs on consecutive REMOVED/ADDED pairs**

Add this private method to `DiffParser` after `parseHunk`:

```kotlin
private fun computeWordDiffs(lines: List<DiffLine>): List<DiffLine> {
    val result = lines.toMutableList()
    var i = 0
    while (i < result.size) {
        if (result[i].type == DiffLineType.REMOVED) {
            val removedStart = i
            var removedEnd = i
            while (removedEnd < result.size && result[removedEnd].type == DiffLineType.REMOVED) {
                removedEnd++
            }
            var addedEnd = removedEnd
            while (addedEnd < result.size && result[addedEnd].type == DiffLineType.ADDED) {
                addedEnd++
            }
            val removedCount = removedEnd - removedStart
            val addedCount = addedEnd - removedEnd

            if (removedCount == 1 && addedCount == 1) {
                val wd = WordDiffCalculator.compute(result[removedStart].content, result[removedEnd].content)
                result[removedStart] = result[removedStart].copy(wordDiffs = wd.removed)
                result[removedEnd] = result[removedEnd].copy(wordDiffs = wd.added)
            }
            i = addedEnd
        } else {
            i++
        }
    }
    return result
}
```

Then update `parseHunk` to call it — replace the return at the end of `parseHunk`:

```kotlin
return HunkParseResult(
    Hunk(oldStart, oldCount, newStart, newCount, computeWordDiffs(diffLines)),
    j,
)
```

- [ ] **Step 2: Add a word diff test to DiffParserTest**

Append to `DiffParserTest.kt`:

```kotlin
@Test
fun `computes word diffs for consecutive removed-added pairs`() {
    val raw = """
diff --git a/F.kt b/F.kt
--- a/F.kt
+++ b/F.kt
@@ -1 +1 @@
-old text here
-new text here
    """.trimIndent()

    val lines = DiffParser.parse(raw).files[0].hunks[0].lines
    val removed = lines[0]
    val added = lines[1]

    assertEquals(DiffLineType.REMOVED, removed.type)
    assertEquals(DiffLineType.ADDED, added.type)
    assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "old")), removed.wordDiffs)
    assertEquals(listOf(WordDiff(WordDiffType.ADDED, "new")), added.wordDiffs)
}
```

- [ ] **Step 3: Run all diff tests**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=DiffParserTest,WordDiffCalculatorTest`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffParser.kt \
        needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffParserTest.kt
git commit -m "feat(diff): wire word diff computation into DiffParser"
```

---

### Task 5: Theme-Aware Color Constants

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffColors.kt`

- [ ] **Step 1: Create DiffColors.kt**

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.Color
import javax.swing.UIManager

object DiffColors {

    val addedBackground: Color
        get() = resolveColor("Diff.addedBackground") { Color(70, 180, 70, 30) }

    val removedBackground: Color
        get() = resolveColor("Diff.removedBackground") { Color(255, 70, 70, 30) }

    val addedInline: Color
        get() = resolveColor("Diff.addedInline") { Color(70, 180, 70, 90) }

    val removedInline: Color
        get() = resolveColor("Diff.removedInline") { Color(255, 70, 70, 90) }

    val addedForeground: Color
        get() = resolveColor("Diff.addedForeground") { Color(106, 135, 89) }

    val removedForeground: Color
        get() = resolveColor("Diff.removedForeground") { Color(199, 91, 91) }

    val gutterStripeAdded: Color
        get() = resolveColor("Diff.gutterStripeAdded") { Color(0x4C, 0xAF, 0x50) }

    val gutterStripeRemoved: Color
        get() = resolveColor("Diff.gutterStripeRemoved") { Color(0xC7, 0x5B, 0x5B) }

    val lineNumberColor: Color
        get() = resolveColor("Diff.lineNumberColor") { Color(0x60, 0x60, 0x60) }

    val overviewAdded: Color
        get() = resolveColor("Diff.overviewAdded") { Color(0x4C, 0xAF, 0x50, 180) }

    val overviewRemoved: Color
        get() = resolveColor("Diff.overviewRemoved") { Color(0xC7, 0x5B, 0x5B, 180) }

    val overviewViewport: Color
        get() = resolveColor("Diff.overviewViewport") { Color(255, 255, 255, 20) }

    val searchHighlight: Color
        get() = resolveColor("Diff.searchHighlight") { Color(255, 255, 0, 100) }

    val contextForeground: Color
        get() = UIManager.getColor("TextPane.foreground") ?: Color(0xA9, 0xB7, 0xC6)

    private inline fun resolveColor(key: String, fallback: () -> Color): Color {
        return UIManager.getColor(key) ?: fallback()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffColors.kt
git commit -m "feat(diff): add theme-aware color constants"
```

---

### Task 6: DiffEditorPane — Colored Diff Text Rendering

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffEditorPane.kt`
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffEditorPaneTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import javax.swing.SwingUtilities
import javax.swing.text.StyleConstants

class DiffEditorPaneTest {

    @Test
    fun `renders added line with correct background`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        val lines = listOf(
            DiffLine(DiffLineType.ADDED, null, 1, "added content"),
        )
        SwingUtilities.invokeAndWait { pane.renderLines(lines) }
        assertEquals(1, pane.styledDocument.length)
        assertTrue(pane.styledDocument.getText(0, pane.styledDocument.length).contains("added content"))
    }

    @Test
    fun `renders context line with no background highlight`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        val lines = listOf(
            DiffLine(DiffLineType.CONTEXT, 1, 1, "context"),
        )
        SwingUtilities.invokeAndWait { pane.renderLines(lines) }
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("context"))
    }

    @Test
    fun `clears previous content on re-render`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        SwingUtilities.invokeAndWait {
            pane.renderLines(listOf(DiffLine(DiffLineType.CONTEXT, 1, 1, "first")))
            pane.renderLines(listOf(DiffLine(DiffLineType.CONTEXT, 1, 1, "second")))
        }
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("second"))
        assertTrue(!text.contains("first"))
    }

    @Test
    fun `exposes line type map for gutter rendering`() {
        val pane = DiffEditorPane(DiffEditorPane.Side.NEW)
        val lines = listOf(
            DiffLine(DiffLineType.CONTEXT, 1, 1, "ctx"),
            DiffLine(DiffLineType.ADDED, null, 2, "add"),
        )
        SwingUtilities.invokeAndWait { pane.renderLines(lines) }
        assertEquals(2, pane.lineCount)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=DiffEditorPaneTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL

- [ ] **Step 3: Write DiffEditorPane implementation**

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Element
import javax.swing.text.Highlighter
import javax.swing.text.MutableAttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class DiffEditorPane(val side: Side) : JTextPane() {

    enum class Side { OLD, NEW, UNIFIED }

    private val lineTypes = mutableListOf<DiffLineType>()

    val lineCount: Int get() = lineTypes.size

    private val monospaceFont = Font(Font.MONOSPACED, Font.PLAIN, 12)

    init {
        isEditable = false
        font = monospaceFont
        margin = Insets(0, 0, 0, 0)
        putClientProperty("JTextPane.style", "width: 100%")
    }

    fun renderLines(lines: List<DiffLine>) {
        val doc = styledDocument
        doc.remove(0, doc.length)
        lineTypes.clear()

        val attrs = SimpleAttributeSet()
        StyleConstants.setFontFamily(attrs, Font.MONOSPACED)
        StyleConstants.setFontSize(attrs, 12)

        for ((index, line) in lines.withIndex()) {
            lineTypes.add(line.type)

            val lineAttrs = SimpleAttributeSet()
            StyleConstants.setFontFamily(lineAttrs, Font.MONOSPACED)
            StyleConstants.setFontSize(lineAttrs, 12)

            when (line.type) {
                DiffLineType.ADDED -> {
                    StyleConstants.setBackground(lineAttrs, DiffColors.addedBackground)
                    StyleConstants.setForeground(lineAttrs, DiffColors.addedForeground)
                }
                DiffLineType.REMOVED -> {
                    StyleConstants.setBackground(lineAttrs, DiffColors.removedBackground)
                    StyleConstants.setForeground(lineAttrs, DiffColors.removedForeground)
                }
                DiffLineType.CONTEXT -> {
                    StyleConstants.setForeground(lineAttrs, DiffColors.contextForeground)
                }
            }

            if (line.wordDiffs.isNotEmpty() && line.content.isNotEmpty()) {
                appendWithWordDiffs(doc, line, lineAttrs)
            } else {
                try {
                    doc.insertString(doc.length, line.content, lineAttrs)
                } catch (_: BadLocationException) {}
            }

            if (index < lines.size - 1) {
                try {
                    doc.insertString(doc.length, "\n", attrs)
                } catch (_: BadLocationException) {}
            }
        }
    }

    private fun appendWithWordDiffs(doc: StyledDocument, line: DiffLine, baseAttrs: MutableAttributeSet) {
        val content = line.content
        val isAdded = line.type == DiffLineType.ADDED
        val inlineColor = if (isAdded) DiffColors.addedInline else DiffColors.removedInline

        val diffTexts = line.wordDiffs.map { it.text }
        var pos = 0
        var inDiff = false
        var diffIdx = 0

        val sb = StringBuilder()
        while (pos < content.length && diffIdx < diffTexts.size) {
            val nextDiff = content.indexOf(diffTexts[diffIdx], pos)
            if (nextDiff < 0) {
                sb.append(content.substring(pos))
                pos = content.length
                break
            }
            if (nextDiff > pos) {
                sb.append(content.substring(pos, nextDiff))
            }
            try {
                doc.insertString(doc.length, sb.toString(), baseAttrs)
            } catch (_: BadLocationException) {}
            sb.clear()

            val diffAttrs = SimpleAttributeSet()
            StyleConstants.copyAttributes(baseAttrs, diffAttrs)
            StyleConstants.setBackground(diffAttrs, inlineColor)

            try {
                doc.insertString(doc.length, diffTexts[diffIdx], diffAttrs)
            } catch (_: BadLocationException) {}

            pos = nextDiff + diffTexts[diffIdx].length
            diffIdx++
        }

        if (pos < content.length) {
            sb.append(content.substring(pos))
        }
        if (sb.isNotEmpty()) {
            try {
                doc.insertString(doc.length, sb.toString(), baseAttrs)
            } catch (_: BadLocationException) {}
        }
    }

    fun getLineBounds(lineIndex: Int): Rectangle? {
        if (lineIndex < 0 || lineIndex >= lineTypes.size) return null
        return try {
            val root = styledDocument.defaultRootElement
            if (lineIndex >= root.elementCount) return null
            val elem = root.getElement(lineIndex)
            modelToView(elem.startOffset)
        } catch (_: Exception) {
            null
        }
    }

    fun getLineTypeAt(lineIndex: Int): DiffLineType? {
        return lineTypes.getOrNull(lineIndex)
    }

    fun scrollToLine(lineIndex: Int) {
        val bounds = getLineBounds(lineIndex) ?: return
        scrollRectToVisible(bounds)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl needlecast-desktop -T 4 -Dtest=DiffEditorPaneTest`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffEditorPane.kt \
        needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/diff/DiffEditorPaneTest.kt
git commit -m "feat(diff): add DiffEditorPane with styled diff rendering"
```

---

### Task 7: Line Number Gutter

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffLineNumberGutter.kt`

- [ ] **Step 1: Write DiffLineNumberGutter**

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max

class DiffLineNumberGutter(
    private val textPane: DiffEditorPane,
    private val scrollPane: JScrollPane,
) : JComponent() {

    private data class LineInfo(val number: Int?, val type: DiffLineType)

    private var lineInfos = listOf<LineInfo>()
    private var maxDigits = 4

    private val gutterWidth get() = maxDigits * charWidth + PADDING * 2
    private val charWidth: Int get() = textPane.getFontMetrics(textPane.font).charWidth('0')

    private val docListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = updateFromDocument()
        override fun removeUpdate(e: DocumentEvent?) = updateFromDocument()
        override fun changedUpdate(e: DocumentEvent?) = updateFromDocument()
    }

    private val viewportListener = ChangeListener { repaint() }

    init {
        isOpaque = false
        textPane.document.addDocumentListener(docListener)
        scrollPane.viewport.addChangeListener(viewportListener)
    }

    fun setLineInfos(infos: List<LineInfo>) {
        lineInfos = infos
        maxDigits = max(4, infos.mapNotNull { it.number }.maxOfOrNull { it.toString().length } ?: 4)
        revalidate()
        repaint()
    }

    override fun getPreferredSize() = java.awt.Dimension(gutterWidth, textPane.preferredSize.height.toInt())

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val font = Font(Font.MONOSPACED, Font.PLAIN, textPane.font.size)
        g2.font = font
        val fm = g2.getFontMetrics(font)
        val lineHeight = fm.height
        val viewRect = scrollPane.viewport.viewRect
        val yOffset = -viewRect.y

        val root = textPane.styledDocument.defaultRootElement
        for (i in lineInfos.indices) {
            if (i >= root.elementCount) break
            val elem = root.getElement(i)
            val y = yOffset + try { textPane.modelToView(elem.startOffset).y.toInt() } catch (_: Exception) { continue }

            if (y + lineHeight < 0 || y > height) continue

            val info = lineInfos[i]
            if (info.number != null) {
                g2.color = DiffColors.lineNumberColor
                val text = info.number.toString()
                val textX = width - PADDING - fm.stringWidth(text)
                g2.drawString(text, textX, y + fm.ascent)
            }
        }
    }

    private fun updateFromDocument() {
        SwingUtilities.invokeLater { revalidate(); repaint() }
    }

    companion object {
        private const val PADDING = 6
    }
}

private typealias SwingUtilities = javax.swing.SwingUtilities
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffLineNumberGutter.kt
git commit -m "feat(diff): add DiffLineNumberGutter component"
```

---

### Task 8: Synchronized Scroll Listener

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/SynchronizedScrollListener.kt`

- [ ] **Step 1: Write SynchronizedScrollListener**

```kotlin
package io.github.rygel.needlecast.ui.diff

import javax.swing.JScrollPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class SynchronizedScrollListener(
    private val source: JScrollPane,
    private val target: JScrollPane,
) : ChangeListener {

    @Volatile
    private var isSyncing = false

    override fun stateChanged(e: ChangeEvent?) {
        if (isSyncing) return
        isSyncing = true
        try {
            val sourceBar = source.verticalScrollBar
            val targetBar = target.verticalScrollBar
            if (sourceBar.maximum == sourceBar.minimum) return
            val ratio = sourceBar.value.toDouble() / (sourceBar.maximum - sourceBar.visibleAmount).coerceAtLeast(1)
            targetBar.value = (ratio * (targetBar.maximum - targetBar.visibleAmount)).toInt()
        } finally {
            isSyncing = false
        }
    }

    fun install() {
        source.verticalScrollBar.model.addChangeListener(this)
        target.verticalScrollBar.model.addChangeListener(object : ChangeListener {
            override fun stateChanged(e: ChangeEvent?) {
                if (isSyncing) return
                isSyncing = true
                try {
                    val targetBar = target.verticalScrollBar
                    val sourceBar = source.verticalScrollBar
                    if (targetBar.maximum == targetBar.minimum) return
                    val ratio = targetBar.value.toDouble() / (targetBar.maximum - targetBar.visibleAmount).coerceAtLeast(1)
                    sourceBar.value = (ratio * (sourceBar.maximum - sourceBar.visibleAmount)).toInt()
                } finally {
                    isSyncing = false
                }
            }
        })
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/SynchronizedScrollListener.kt
git commit -m "feat(diff): add SynchronizedScrollListener for side-by-side scrolling"
```

---

### Task 9: Diff Content Panel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffContentPanel.kt`

- [ ] **Step 1: Write DiffContentPanel**

This panel manages the side-by-side/unified layout, creates the `DiffEditorPane`(s), `DiffLineNumberGutter`(s), and installs synchronized scrolling.

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ScrollPaneConstants

class DiffContentPanel : JPanel(BorderLayout()) {

    enum class ViewMode { SIDE_BY_SIDE, UNIFIED }

    var viewMode: ViewMode = ViewMode.SIDE_BY_SIDE
        private set

    private val leftPane = DiffEditorPane(DiffEditorPane.Side.OLD)
    private val rightPane = DiffEditorPane(DiffEditorPane.Side.NEW)
    private val unifiedPane = DiffEditorPane(DiffEditorPane.Side.UNIFIED)

    private val leftScroll = JScrollPane(leftPane).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        border = BorderFactory.createEmptyBorder()
    }
    private val rightScroll = JScrollPane(rightPane).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        border = BorderFactory.createEmptyBorder()
    }
    private val unifiedScroll = JScrollPane(unifiedPane).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        border = BorderFactory.createEmptyBorder()
    }

    private val leftGutter = DiffLineNumberGutter(leftPane, leftScroll)
    private val rightGutter = DiffLineNumberGutter(rightPane, rightScroll)
    private val unifiedGutter = DiffLineNumberGutter(unifiedPane, unifiedScroll)

    private val syncScroll = SynchronizedScrollListener(leftScroll, rightScroll)

    private var currentResult: DiffResult? = null
    private var currentFileIndex: Int = 0

    init {
        leftScroll.setRowHeaderView(leftGutter)
        rightScroll.setRowHeaderView(rightGutter)
        unifiedScroll.setRowHeaderView(unifiedGutter)
        syncScroll.install()
        showSideBySide()
    }

    fun setViewMode(mode: ViewMode) {
        if (mode == viewMode) return
        viewMode = mode
        if (mode == ViewMode.SIDE_BY_SIDE) showSideBySide() else showUnified()
        redisplay()
    }

    fun display(result: DiffResult, fileIndex: Int = 0) {
        currentResult = result
        currentFileIndex = fileIndex
        redisplay()
    }

    fun displayEmpty(message: String) {
        currentResult = null
        renderPane(emptyList(), DiffEditorPane.Side.OLD)
        if (viewMode == ViewMode.SIDE_BY_SIDE) {
            renderPane(emptyList(), DiffEditorPane.Side.NEW)
        }
    }

    val leftScrollPane: JScrollPane get() = if (viewMode == ViewMode.SIDE_BY_SIDE) leftScroll else unifiedScroll
    val rightScrollPane: JScrollPane get() = if (viewMode == ViewMode.SIDE_BY_SIDE) rightScroll else unifiedScroll

    fun getHunkLinePositions(): List<Pair<Int, Int>> {
        val result = currentResult ?: return emptyList()
        if (currentFileIndex >= result.files.size) return emptyList()
        val file = result.files[currentFileIndex]
        val positions = mutableListOf<Pair<Int, Int>>()
        var lineIdx = 0
        for (hunk in file.hunks) {
            val startLine = lineIdx
            lineIdx += hunk.lines.size
            positions.add(startLine to lineIdx)
        }
        return positions
    }

    private fun redisplay() {
        val result = currentResult ?: run { displayEmpty("No diff"); return }
        if (result.files.isEmpty()) { displayEmpty("No changes"); return }
        if (currentFileIndex >= result.files.size) currentFileIndex = 0
        val file = result.files[currentFileIndex]
        if (file.binary) { displayEmpty("(binary file)"); return }

        if (viewMode == ViewMode.SIDE_BY_SIDE) {
            val split = splitLinesForSideBySide(file.hunks.flatMap { it.lines })
            renderPane(split.left, DiffEditorPane.Side.OLD)
            renderPane(split.right, DiffEditorPane.Side.NEW)
            updateGutter(split.left, leftGutter, DiffEditorPane.Side.OLD)
            updateGutter(split.right, rightGutter, DiffEditorPane.Side.NEW)
        } else {
            renderPane(file.hunks.flatMap { it.lines }, DiffEditorPane.Side.UNIFIED)
            updateGutter(file.hunks.flatMap { it.lines }, unifiedGutter, DiffEditorPane.Side.UNIFIED)
        }
    }

    private fun showSideBySide() {
        removeAll()
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll).apply {
            resizeWeight = 0.5
            dividerSize = 2
            border = BorderFactory.createEmptyBorder()
        }
        add(split, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun showUnified() {
        removeAll()
        add(unifiedScroll, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun renderPane(lines: List<DiffLine>, side: DiffEditorPane.Side) {
        val pane = when (side) {
            DiffEditorPane.Side.OLD -> leftPane
            DiffEditorPane.Side.NEW -> rightPane
            DiffEditorPane.Side.UNIFIED -> unifiedPane
        }
        pane.renderLines(lines)
    }

    private fun updateGutter(lines: List<DiffLine>, gutter: DiffLineNumberGutter, side: DiffEditorPane.Side) {
        val infos = lines.map { line ->
            DiffLineNumberGutter.LineInfo(
                number = when (side) {
                    DiffEditorPane.Side.OLD -> line.oldLineNum
                    DiffEditorPane.Side.NEW -> line.newLineNum
                    DiffEditorPane.Side.UNIFIED -> line.oldLineNum ?: line.newLineNum
                },
                type = line.type,
            )
        }
        gutter.setLineInfos(infos)
    }

    private data class SideBySideSplit(val left: List<DiffLine>, val right: List<DiffLine>)

    private fun splitLinesForSideBySide(lines: List<DiffLine>): SideBySideSplit {
        val left = mutableListOf<DiffLine>()
        val right = mutableListOf<DiffLine>()
        var i = 0
        while (i < lines.size) {
            when (lines[i].type) {
                DiffLineType.CONTEXT -> {
                    left.add(lines[i])
                    right.add(lines[i])
                    i++
                }
                DiffLineType.REMOVED -> {
                    val removedStart = i
                    while (i < lines.size && lines[i].type == DiffLineType.REMOVED) {
                        left.add(lines[i])
                        i++
                    }
                    val addedStart = i
                    val removedCount = i - removedStart
                    while (i < lines.size && lines[i].type == DiffLineType.ADDED) {
                        right.add(lines[i])
                        i++
                    }
                    val addedCount = i - addedStart
                    val padCount = removedCount - addedCount
                    if (padCount > 0) {
                        repeat(padCount) { right.add(DiffLine(DiffLineType.CONTEXT, null, null, "")) }
                    } else if (padCount < 0) {
                        repeat(-padCount) { left.add(DiffLine(DiffLineType.CONTEXT, null, null, "")) }
                    }
                }
                DiffLineType.ADDED -> {
                    right.add(lines[i])
                    left.add(DiffLine(DiffLineType.CONTEXT, null, null, ""))
                    i++
                }
            }
        }
        return SideBySideSplit(left, right)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffContentPanel.kt
git commit -m "feat(diff): add DiffContentPanel with side-by-side and unified layouts"
```

---

### Task 10: Diff File Tree

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffFileTree.kt`

- [ ] **Step 1: Write DiffFileTree**

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class DiffFileTree : JTree() {

    private val rootNode = DefaultMutableTreeNode("Changed Files")
    private val treeModel = DefaultTreeModel(rootNode)

    var onFileSelected: ((index: Int) -> Unit)? = null
    var onFileDoubleClicked: ((filePath: String) -> Unit)? = null

    private var fileDiffs: List<FileDiff> = emptyList()

    init {
        model = treeModel
        isRootVisible = false
        showsRootHandles = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        setCellRenderer(FileNodeRenderer())

        addTreeSelectionListener { event ->
            val node = lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val index = node.userObject as? Int ?: return@addTreeSelectionListener
            onFileSelected?.invoke(index)
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val row = getRowForLocation(e.x, e.y) ?: return
                val node = getPathForRow(row)?.lastPathComponent as? DefaultMutableTreeNode ?: return
                val index = node.userObject as? Int ?: return
                if (index < fileDiffs.size) {
                    onFileDoubleClicked?.invoke(fileDiffs[index].filePath)
                }
            }
        })
    }

    fun setFiles(files: List<FileDiff>) {
        fileDiffs = files
        rootNode.removeAllChildren()
        files.forEachIndexed { index, file ->
            rootNode.add(DefaultMutableTreeNode(index))
        }
        treeModel.reload()
        if (files.isNotEmpty()) {
            setSelectionRow(0)
        }
    }

    fun selectFile(index: Int) {
        if (index < 0 || index >= fileDiffs.size) return
        setSelectionRow(index)
        scrollRowToVisible(index)
    }

    private inner class FileNodeRenderer : DefaultTreeCellRenderer() {
        private val nameLabel = JLabel().apply { font = Font(Font.SANS_SERIF, Font.PLAIN, 11) }
        private val dirLabel = JLabel().apply { font = Font(Font.SANS_SERIF, Font.PLAIN, 9) }
        private val statsLabel = JLabel().apply { font = Font(Font.MONOSPACED, Font.PLAIN, 10) }
        private val panel = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            isOpaque = true
            add(nameLabel, BorderLayout.CENTER)
            add(statsLabel, BorderLayout.EAST)
        }

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean,
        ): Component {
            val index = (value as? DefaultMutableTreeNode)?.userObject as? Int
            if (index == null || index >= fileDiffs.size) return panel
            val file = fileDiffs[index]

            val slashIdx = file.filePath.lastIndexOf('/')
            if (slashIdx >= 0) {
                nameLabel.text = file.filePath.substring(slashIdx + 1)
            } else {
                nameLabel.text = file.filePath
            }

            val stats = buildString {
                if (file.additions > 0) append("+${file.additions}")
                if (file.additions > 0 && file.deletions > 0) append(" ")
                if (file.deletions > 0) append("-${file.deletions}")
            }
            statsLabel.text = stats

            nameLabel.foreground = if (sel) tree.selectionForeground else tree.foreground
            statsLabel.foreground = if (sel) tree.selectionForeground else Color(0x88, 0x88, 0x88)
            panel.background = if (sel) tree.selectionBackground else tree.background

            return panel
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffFileTree.kt
git commit -m "feat(diff): add DiffFileTree with file navigation"
```

---

### Task 11: Diff Overview Bar

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffOverviewBar.kt`

- [ ] **Step 1: Write DiffOverviewBar**

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JScrollPane

class DiffOverviewBar(
    private val scrollPane: JScrollPane,
) : JComponent() {

    private data class ChangeBlock(val startLine: Int, val endLine: Int, val type: DiffLineType)

    private var totalLines: Int = 0
    private var changeBlocks = listOf<ChangeBlock>()

    var onJumpToLine: ((lineIndex: Int) -> Unit)? = null

    init {
        preferredSize = java.awt.Dimension(OVERVIEW_WIDTH, 0)
        object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = jumpToY(e.y)
            override fun mousePressed(e: MouseEvent) = jumpToY(e.y)
            override fun mouseDragged(e: MouseEvent) = jumpToY(e.y)
        }.also {
            addMouseListener(it)
            addMouseMotionListener(it)
        }
    }

    fun setDiffData(lines: List<DiffLine>) {
        totalLines = lines.size
        val blocks = mutableListOf<ChangeBlock>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].type != DiffLineType.CONTEXT) {
                val start = i
                val type = lines[i].type
                while (i < lines.size && lines[i].type == type) i++
                blocks.add(ChangeBlock(start, i, type))
            } else {
                i++
            }
        }
        changeBlocks = blocks
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (totalLines == 0 || height == 0) return
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (block in changeBlocks) {
            val y1 = (block.startLine.toLong() * height / totalLines).toInt()
            val y2 = (block.endLine.toLong() * height / totalLines).toInt()
            g2.color = when (block.type) {
                DiffLineType.ADDED -> DiffColors.overviewAdded
                DiffLineType.REMOVED -> DiffColors.overviewRemoved
                DiffLineType.CONTEXT -> Color.TRANSLUCENT
            }
            g2.fillRect(2, y1, width - 4, (y2 - y1).coerceAtLeast(2))
        }

        val viewRect = scrollPane.viewport.viewRect
        val contentHeight = scrollPane.viewport.view?.height ?: 0
        if (contentHeight > 0) {
            val vpStart = (viewRect.y.toLong() * height / contentHeight).toInt()
            val vpEnd = ((viewRect.y + viewRect.height).toLong() * height / contentHeight).toInt()
            g2.color = DiffColors.overviewViewport
            g2.drawRect(0, vpStart, width - 1, (vpEnd - vpStart).coerceAtLeast(4))
        }
    }

    private fun jumpToY(y: Int) {
        val contentHeight = scrollPane.viewport.view?.height ?: return
        if (height == 0 || contentHeight == 0) return
        val targetScrollY = (y.toLong() * contentHeight / height).toInt()
        val bar = scrollPane.verticalScrollBar
        bar.value = targetScrollY.coerceIn(0, bar.maximum - bar.visibleAmount)
    }

    companion object {
        private const val OVERVIEW_WIDTH = 18
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffOverviewBar.kt
git commit -m "feat(diff): add DiffOverviewBar minimap component"
```

---

### Task 12: Diff Search Bar

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffSearchBar.kt`

- [ ] **Step 1: Write DiffSearchBar**

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.text.Highlighter

class DiffSearchBar : JPanel(BorderLayout()) {

    private val searchField = JTextField().apply {
        preferredSize = Dimension(200, 28)
        maximumSize = Dimension(400, 28)
    }
    private val countLabel = JLabel("")
    private val prevButton = JButton("\u25C0").apply {
        toolTipText = "Previous match"
        isFocusable = false
    }
    private val nextButton = JButton("\u25B6").apply {
        toolTipText = "Next match"
        isFocusable = false
    }
    private val closeButton = JButton("\u2715").apply {
        toolTipText = "Close (Escape)"
        isFocusable = false
    }

    private var targetPanes: List<DiffEditorPane> = emptyList()
    private var highlights = mutableListOf<List<Highlighter.Highlight>>()
    private var currentMatchIndex = -1
    private var totalMatches = 0

    var onClose: (() -> Unit)? = null

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, java.awt.Color(0x3C, 0x3C, 0x3C)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8),
        )

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Find:"))
            add(searchField)
            add(prevButton)
            add(nextButton)
            add(countLabel)
        }
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            add(closeButton)
        }

        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)

        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) nextMatch()
                if (e.keyCode == KeyEvent.VK_ESCAPE) { onClose?.invoke(); return }
                performSearch()
            }
        })

        nextButton.addActionListener { nextMatch() }
        prevButton.addActionListener { prevMatch() }
        closeButton.addActionListener { onClose?.invoke() }
    }

    fun setTargetPanes(panes: List<DiffEditorPane>) {
        targetPanes = panes
    }

    fun activate() {
        isVisible = true
        searchField.text = ""
        countLabel.text = ""
        currentMatchIndex = -1
        totalMatches = 0
        searchField.requestFocusInWindow()
    }

    fun deactivate() {
        clearHighlights()
        isVisible = false
    }

    private fun performSearch() {
        clearHighlights()
        val query = searchField.text
        if (query.isEmpty()) {
            countLabel.text = ""
            return
        }

        val painter = object : DefaultHighlighter.DefaultHighlightPainter(DiffColors.searchHighlight) {}
        highlights.clear()
        totalMatches = 0

        for (pane in targetPanes) {
            val paneHighlights = mutableListOf<Highlighter.Highlight>()
            val doc = pane.styledDocument
            val text = doc.getText(0, doc.length)
            var pos = 0
            while (pos < text.length) {
                val idx = text.indexOf(query, pos, ignoreCase = false)
                if (idx < 0) break
                try {
                    val hl = pane.highlighter.addHighlight(idx, idx + query.length, painter)
                    paneHighlights.add(hl)
                    totalMatches++
                } catch (_: Exception) {}
                pos = idx + 1
            }
            highlights.add(paneHighlights)
        }

        if (totalMatches > 0) {
            currentMatchIndex = 0
            countLabel.text = "1 of $totalMatches"
        } else {
            currentMatchIndex = -1
            countLabel.text = "No matches"
        }
    }

    private fun nextMatch() {
        if (totalMatches == 0) return
        currentMatchIndex = (currentMatchIndex + 1) % totalMatches
        countLabel.text = "${currentMatchIndex + 1} of $totalMatches"
        scrollToMatch(currentMatchIndex)
    }

    private fun prevMatch() {
        if (totalMatches == 0) return
        currentMatchIndex = if (currentMatchIndex <= 0) totalMatches - 1 else currentMatchIndex - 1
        countLabel.text = "${currentMatchIndex + 1} of $totalMatches"
        scrollToMatch(currentMatchIndex)
    }

    private fun scrollToMatch(matchIndex: Int) {
        var offset = 0
        for (paneIdx in targetPanes.indices) {
            val paneHighlights = highlights.getOrNull(paneIdx) ?: continue
            if (matchIndex < offset + paneHighlights.size) {
                val hl = paneHighlights[matchIndex - offset]
                targetPanes[paneIdx].caretPosition = hl.startOffset
                targetPanes[paneIdx].scrollRectToVisible(
                    try { targetPanes[paneIdx].modelToView(hl.startOffset) } catch (_: Exception) { return }
                )
                return
            }
            offset += paneHighlights.size
        }
    }

    private fun clearHighlights() {
        for (pane in targetPanes) {
            pane.highlighter.removeAllHighlights()
        }
        highlights.clear()
    }
}

private typealias DefaultHighlighter = javax.swing.text.DefaultHighlighter
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffSearchBar.kt
git commit -m "feat(diff): add DiffSearchBar with match highlighting"
```

---

### Task 13: DiffViewerPanel — Top-Level Orchestrator

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffViewerPanel.kt`

- [ ] **Step 1: Write DiffViewerPanel**

This is the top-level container that wires together all diff components.

```kotlin
package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.KeyStroke

class DiffViewerPanel(
    val fileOpener: ((String) -> Unit)? = null,
) : JPanel(BorderLayout()) {

    private val fileTree = DiffFileTree()
    private val contentPanel = DiffContentPanel()
    private val overviewBar = DiffOverviewBar(contentPanel.leftScrollPane)
    private val searchBar = DiffSearchBar()

    private val sideBySideToggle = JToggleButton("Side-by-side").apply {
        isSelected = true
        isFocusable = false
    }
    private val unifiedToggle = JToggleButton("Unified").apply {
        isFocusable = false
    }
    private val prevChangeButton = JButton("\u25C0 Change").apply {
        toolTipText = "Previous change"
        isFocusable = false
    }
    private val nextChangeButton = JButton("Change \u25B6").apply {
        toolTipText = "Next change"
        isFocusable = false
    }

    private var currentResult: DiffResult? = null
    private var currentFileIndex: Int = 0
    private var currentHunkIndex: Int = -1

    init {
        minimumSize = Dimension(0, 0)

        ButtonGroup().apply { add(sideBySideToggle); add(unifiedToggle) }

        sideBySideToggle.addActionListener {
            contentPanel.setViewMode(DiffContentPanel.ViewMode.SIDE_BY_SIDE)
            rewireOverviewBar()
        }
        unifiedToggle.addActionListener {
            contentPanel.setViewMode(DiffContentPanel.ViewMode.UNIFIED)
            rewireOverviewBar()
        }

        prevChangeButton.addActionListener { navigateChange(-1) }
        nextChangeButton.addActionListener { navigateChange(1) }

        fileTree.onFileSelected = { index -> selectFile(index) }
        fileTree.onFileDoubleClicked = { path -> fileOpener?.invoke(path) }
        searchBar.onClose = { searchBar.deactivate() }

        searchBar.setTargetPanes(listOf(contentPanel.leftPane, contentPanel.rightPane))

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(sideBySideToggle)
            add(unifiedToggle)
            add(prevChangeButton)
            add(nextChangeButton)
        }

        val fileTreeScroll = JScrollPane(fileTree).apply {
            preferredSize = Dimension(200, 0)
            minimumSize = Dimension(100, 0)
            border = BorderFactory.createEmptyBorder()
        }

        val centerSplit = javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT,
            fileTreeScroll,
            contentPanel,
        ).apply {
            resizeWeight = 0.0
            dividerSize = 2
            border = BorderFactory.createEmptyBorder()
        }

        val contentWithOverview = JPanel(BorderLayout()).apply {
            add(centerSplit, BorderLayout.CENTER)
            add(overviewBar, BorderLayout.EAST)
        }

        add(toolbar, BorderLayout.NORTH)
        add(contentWithOverview, BorderLayout.CENTER)
        add(searchBar, BorderLayout.SOUTH)

        searchBar.isVisible = false

        registerKeyboardShortcuts()
    }

    fun display(result: DiffResult) {
        currentResult = result
        currentFileIndex = 0
        currentHunkIndex = -1
        fileTree.setFiles(result.files)
        if (result.files.isNotEmpty()) {
            contentPanel.display(result, 0)
            updateOverviewBar()
        } else {
            contentPanel.displayEmpty("No changes")
        }
    }

    fun displayEmpty(message: String) {
        currentResult = null
        contentPanel.displayEmpty(message)
        fileTree.setFiles(emptyList())
    }

    private fun selectFile(index: Int) {
        val result = currentResult ?: return
        if (index < 0 || index >= result.files.size) return
        currentFileIndex = index
        currentHunkIndex = -1
        contentPanel.display(result, index)
        updateOverviewBar()
    }

    private fun navigateChange(direction: Int) {
        val positions = contentPanel.getHunkLinePositions()
        if (positions.isEmpty()) return

        if (currentHunkIndex < 0) {
            currentHunkIndex = if (direction > 0) 0 else positions.size - 1
        } else {
            currentHunkIndex += direction
            if (currentHunkIndex < 0) currentHunkIndex = positions.size - 1
            if (currentHunkIndex >= positions.size) currentHunkIndex = 0
        }

        val (startLine, _) = positions[currentHunkIndex]
        contentPanel.leftPane.scrollToLine(startLine)
        contentPanel.rightPane.scrollToLine(startLine)
    }

    private fun updateOverviewBar() {
        val result = currentResult ?: return
        if (currentFileIndex >= result.files.size) return
        val file = result.files[currentFileIndex]
        val lines = if (contentPanel.viewMode == DiffContentPanel.ViewMode.UNIFIED) {
            file.hunks.flatMap { it.lines }
        } else {
            val split = contentPanel.splitLinesForSideBySide(file.hunks.flatMap { it.lines })
            split.left
        }
        overviewBar.setDiffData(lines)
    }

    private fun rewireOverviewBar() {
        updateOverviewBar()
    }

    private fun registerKeyboardShortcuts() {
        registerKeyboardAction(
            { searchBar.activate() },
            "openSearch",
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
        )
    }
}
```

Note: `splitLinesForSideBySide` is currently private in `DiffContentPanel`. It needs to be made `internal` for `DiffViewerPanel` to call it for the overview bar. Add the `internal` modifier to that method in `DiffContentPanel`.

- [ ] **Step 2: Make `splitLinesForSideBySide` internal in DiffContentPanel**

Change `private fun splitLinesForSideBySide` to `internal fun splitLinesForSideBySide` in `DiffContentPanel.kt`.

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffViewerPanel.kt \
        needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/diff/DiffContentPanel.kt
git commit -m "feat(diff): add DiffViewerPanel top-level orchestrator"
```

---

### Task 14: Add `--no-color` to ProcessGitService

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt:28`

- [ ] **Step 1: Add `--no-color` flag**

Change line 28 from:
```kotlin
    override fun show(dir: String, hash: String): String? =
        runGit(dir, "show", "--stat", "-p", hash)
```
To:
```kotlin
    override fun show(dir: String, hash: String): String? =
        runGit(dir, "show", "--stat", "-p", "--no-color", hash)
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt
git commit -m "feat(diff): add --no-color flag to git show command"
```

---

### Task 15: Integrate DiffViewerPanel into GitLogPanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt`

This is the critical integration task. The `JTextArea diffArea` is replaced with `DiffViewerPanel`.

- [ ] **Step 1: Update GitLogPanel imports and constructor**

Add imports at the top of the file (after existing imports):

```kotlin
import io.github.rygel.needlecast.ui.diff.DiffParser
import io.github.rygel.needlecast.ui.diff.DiffViewerPanel
```

Add `fileOpener` parameter to the constructor:

Change:
```kotlin
class GitLogPanel(private val gitService: GitService = ProcessGitService()) : JPanel(BorderLayout()) {
```
To:
```kotlin
class GitLogPanel(
    private val gitService: GitService = ProcessGitService(),
    private val fileOpener: ((String) -> Unit)? = null,
) : JPanel(BorderLayout()) {
```

- [ ] **Step 2: Replace JTextArea with DiffViewerPanel**

Replace:
```kotlin
    private val diffArea = JTextArea().apply {
        name = "diff-area"
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        lineWrap = false
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
```
With:
```kotlin
    private val diffViewer = DiffViewerPanel(fileOpener)
```

- [ ] **Step 3: Update the split pane to use DiffViewerPanel**

Replace:
```kotlin
        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(logList).apply { minimumSize = Dimension(0, 0) },
            JScrollPane(diffArea).apply { minimumSize = Dimension(0, 0) },
        ).apply { resizeWeight = 0.4 }
```
With:
```kotlin
        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(logList).apply { minimumSize = Dimension(0, 0) },
            diffViewer,
        ).apply { resizeWeight = 0.4 }
```

- [ ] **Step 4: Update loadProject() to use DiffViewerPanel**

Replace:
```kotlin
        TextChunker.cancel(diffArea)
        diffArea.text = if (path == null) "" else "Loading commits\u2026"
        if (path == null) return
```
With:
```kotlin
        if (path == null) {
            diffViewer.displayEmpty("")
            return
        }
        diffViewer.displayEmpty("Loading commits\u2026")
```

Replace in `done()`:
```kotlin
                if (logModel.size > 0) {
                    diffArea.text = "Select a commit to view details."
                    diffArea.caretPosition = 0
                } else {
                    diffArea.text = "No commits found."
                }
```
With:
```kotlin
                if (logModel.size > 0) {
                    diffViewer.displayEmpty("Select a commit to view details.")
                } else {
                    diffViewer.displayEmpty("No commits found.")
                }
```

- [ ] **Step 5: Update showCommit() to parse and display structured diff**

Replace the entire `showCommit` method:
```kotlin
    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker = object : SwingWorker<String, Void>() {
            override fun doInBackground(): String =
                gitService.show(path, hash) ?: "Could not load commit $hash"
            override fun done() {
                if (isCancelled) return
                val text = try { get() } catch (_: Exception) { return }
                val rendered = if (text.length > maxDiffChars) {
                    val omitted = text.length - maxDiffChars
                    text.take(maxDiffChars) + "\n\n[Diff truncated: omitted ${omitted} characters]"
                } else text
                TextChunker.setTextChunked(diffArea, rendered) { diffArea.caretPosition = 0 }
            }
        }.also { it.execute() }
    }
```
With:
```kotlin
    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker = object : SwingWorker<io.github.rygel.needlecast.ui.diff.DiffResult, Void>() {
            override fun doInBackground(): io.github.rygel.needlecast.ui.diff.DiffResult {
                val raw = gitService.show(path, hash) ?: return io.github.rygel.needlecast.ui.diff.DiffResult(
                    emptyList(), io.github.rygel.needlecast.ui.diff.DiffStats(0, 0)
                )
                val truncated = if (raw.length > maxDiffChars) raw.take(maxDiffChars) else raw
                return DiffParser.parse(truncated)
            }
            override fun done() {
                if (isCancelled) return
                val result = try { get() } catch (_: Exception) { return }
                diffViewer.display(result)
            }
        }.also { it.execute() }
    }
```

Add the import for `DiffResult` and `DiffStats` (already covered by the wildcard imports from `io.github.rygel.needlecast.ui.diff.DiffParser` and `io.github.rygel.needlecast.ui.diff.DiffViewerPanel` — but since we reference them by fully qualified name in `doInBackground`, no extra import is needed). Actually, let's use the already-imported `DiffParser` and reference the types directly. Simplify:

```kotlin
    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker = object : SwingWorker<DiffParser.DiffResult, Void>() {
```

Wait — `DiffResult` is a top-level data class in `DiffModel.kt`, not nested in `DiffParser`. So the import `io.github.rygel.needlecast.ui.diff.DiffResult` would work. But we already have the star import from the `DiffParser` import. Let's just use the short names since they're in the same `ui.diff` package we imported. Update `showCommit` to:

```kotlin
    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker = object : SwingWorker<DiffResult, Void>() {
            override fun doInBackground(): DiffResult {
                val raw = gitService.show(path, hash) ?: return DiffResult(emptyList(), DiffStats(0, 0))
                val truncated = if (raw.length > maxDiffChars) raw.take(maxDiffChars) else raw
                return DiffParser.parse(truncated)
            }
            override fun done() {
                if (isCancelled) return
                val result = try { get() } catch (_: Exception) { return }
                diffViewer.display(result)
            }
        }.also { it.execute() }
    }
```

And add to imports:
```kotlin
import io.github.rygel.needlecast.ui.diff.DiffResult
import io.github.rygel.needlecast.ui.diff.DiffStats
```

- [ ] **Step 6: Remove unused imports**

Remove the now-unused `TextChunker` import and `JTextArea` import if no longer referenced. The `TextChunker` class is only used for `diffArea` which is now gone. Remove the `import io.github.rygel.needlecast.ui.TextChunker` line if it exists.

Wait — `TextChunker` is not currently imported (it's in the same package `io.github.rygel.needlecast.ui`). But it's referenced as `TextChunker.cancel(diffArea)` and `TextChunker.setTextChunked(diffArea, ...)`. Both calls are removed. No import to remove.

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt
git commit -m "feat(diff): integrate DiffViewerPanel into GitLogPanel"
```

---

### Task 16: Wire File Opener in MainWindow

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt:95`

- [ ] **Step 1: Pass fileOpener callback to GitLogPanel**

Change line 95 from:
```kotlin
    private val gitLogPanel   = GitLogPanel(ctx.gitService)
```
To:
```kotlin
    private val gitLogPanel   = GitLogPanel(ctx.gitService) { path ->
        explorerPanel.openFile(java.io.File(path))
    }
```

Note: `ExplorerPanel.openFile` accepts a `File` parameter. The callback receives the file path as a `String` from the diff viewer. We construct a `File` from it. If the path is relative, it needs to be resolved against the current project directory. The `DiffFileTree` stores the path as reported by git (relative to repo root). For now this works if `explorerPanel` has the right root set — and it does, because `applyProjectSelection` calls `explorerPanel.setRootDirectory(File(project.directory.path))` when a project is selected.

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat(diff): wire file opener callback in MainWindow"
```

---

### Task 17: Build Verification and Bug Fixes

**Files:**
- All files from previous tasks

- [ ] **Step 1: Run full compilation**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: BUILD SUCCESS

If there are compilation errors, fix them before proceeding.

- [ ] **Step 2: Run all non-UI tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: All tests PASS

- [ ] **Step 3: Run full verify**

Run: `mvn verify -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 4: Fix any issues found and commit**

```bash
git add -A
git commit -m "fix(diff): address build issues from integration"
```

---

## Self-Review

**Spec coverage:**
- Color-coded diff lines with inline word diff → Tasks 4, 6, 8
- Side-by-side view (default) with toggle → Tasks 8, 9, 13
- Line numbers on both sides → Tasks 7, 9
- Gutter markers → Task 6 (DiffEditorPane renders gutter stripe colors)
- File tree panel with stats → Task 10
- Minimap overview bar → Task 11
- Search within diff → Task 12
- Prev/Next change navigation → Task 13
- Click-to-open file → Tasks 10, 13, 16
- FlatLaf theme-aware → Task 5
- Diff parsing → Tasks 2, 4
- GitService --no-color → Task 14
- GitLogPanel integration → Task 15
- Binary files → Task 9 (handled in `redisplay`)

**Gap found:** The gutter stripe (thin colored bar per line) is described in the spec but not explicitly implemented in DiffEditorPane. The DiffEditorPane sets line background colors via StyledDocument, but a separate thin colored stripe on the left edge of each line is missing. This is a visual refinement that can be added to DiffEditorPane using a custom `LayerUI` or `paintComponent` overlay. For the initial implementation, the background coloring serves the same purpose — the stripe can be added as a follow-up.

**Placeholder scan:** No TBD, TODO, or placeholder patterns found.

**Type consistency:** All method signatures, data class fields, and references are consistent across tasks. `DiffResult`, `FileDiff`, `Hunk`, `DiffLine`, `WordDiff` are defined in Task 1 and used consistently throughout.
