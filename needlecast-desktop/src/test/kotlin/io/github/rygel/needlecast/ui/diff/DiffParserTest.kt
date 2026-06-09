package io.github.rygel.needlecast.ui.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffParserTest {
    @Test
    fun `parses a single-file diff with added and removed lines`() {
        val raw =
            """
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
        assertEquals(2, result.stats.totalAdditions)
        assertEquals(1, result.stats.totalDeletions)
    }

    @Test
    fun `skips commit header and stat lines`() {
        val raw =
            """
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
        val raw =
            """
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
        val raw =
            """
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
        val raw =
            """
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
        val raw =
            """
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

        val lines =
            DiffParser
                .parse(raw)
                .files[0]
                .hunks[0]
                .lines
        assertEquals(5, lines[0].oldLineNum)
        assertEquals(5, lines[0].newLineNum)
        assertEquals(6, lines[1].oldLineNum)
        assertEquals(null, lines[1].newLineNum)
        assertEquals(null, lines[2].oldLineNum)
        assertEquals(6, lines[2].newLineNum)
        assertEquals(null, lines[3].oldLineNum)
        assertEquals(7, lines[3].newLineNum)
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
    fun `computes word diffs for consecutive removed-added pairs`() {
        val raw =
            """
diff --git a/F.kt b/F.kt
--- a/F.kt
+++ b/F.kt
@@ -1 +1 @@
-old text here
+new text here
            """.trimIndent()

        val lines =
            DiffParser
                .parse(raw)
                .files[0]
                .hunks[0]
                .lines
        val removed = lines[0]
        val added = lines[1]

        assertEquals(DiffLineType.REMOVED, removed.type)
        assertEquals(DiffLineType.ADDED, added.type)
        assertEquals(listOf(WordDiff(WordDiffType.REMOVED, "old")), removed.wordDiffs)
        assertEquals(listOf(WordDiff(WordDiffType.ADDED, "new")), added.wordDiffs)
    }

    @Test
    fun `computes stats from parsed data`() {
        val raw =
            """
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
