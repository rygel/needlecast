package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path

class SearchEngineTest {
    @Test
    fun `parseGlobs splits on comma`() {
        assertEquals(listOf("*.kt", "*.java"), SearchEngine.parseGlobs("*.kt,*.java"))
    }

    @Test
    fun `parseGlobs splits on semicolon`() {
        assertEquals(listOf("*.kt", "*.java"), SearchEngine.parseGlobs("*.kt;*.java"))
    }

    @Test
    fun `parseGlobs trims whitespace and filters empty`() {
        assertEquals(listOf("*.kt", "*.java"), SearchEngine.parseGlobs(" *.kt , , *.java "))
    }

    @Test
    fun `parseGlobs returns empty for blank input`() {
        assertTrue(SearchEngine.parseGlobs("").isEmpty())
        assertTrue(SearchEngine.parseGlobs("   ").isEmpty())
    }

    @Test
    fun `buildMatchers returns empty for empty input`() {
        assertTrue(SearchEngine.buildMatchers("").isEmpty())
    }

    @Test
    fun `buildMatchers creates working glob matchers`() {
        val matchers = SearchEngine.buildMatchers("*.kt")
        assertTrue(matchers.size == 1)
        assertTrue(matchers[0].matches(Path.of("Foo.kt")))
        assertFalse(matchers[0].matches(Path.of("Foo.java")))
    }

    @Test
    fun `buildMatchers throws on invalid glob`() {
        assertThrows<IllegalArgumentException> {
            SearchEngine.buildMatchers("[invalid")
        }
    }

    @Test
    fun `buildMatchers with stripNegation removes exclamation marks`() {
        val matchers = SearchEngine.buildMatchers("!*.kt", stripNegation = true)
        assertEquals(1, matchers.size)
        assertTrue(matchers[0].matches(Path.of("Foo.kt")))
    }

    @Test
    fun `matchesAny returns false for empty matchers`() {
        assertFalse(SearchEngine.matchesAny(Path.of("Foo.kt"), "Foo.kt", emptyList()))
    }

    @Test
    fun `matchesAny matches on full path`() {
        val matchers = SearchEngine.buildMatchers("**/*.kt")
        assertTrue(SearchEngine.matchesAny(Path.of("src/Foo.kt"), null, matchers))
    }

    @Test
    fun `matchesAny matches on filename only`() {
        val matchers = SearchEngine.buildMatchers("*.kt")
        assertTrue(SearchEngine.matchesAny(Path.of("src/Foo.java"), "Foo.kt", matchers))
    }

    @Test
    fun `matchesAny returns false when nothing matches`() {
        val matchers = SearchEngine.buildMatchers("*.kt")
        assertFalse(SearchEngine.matchesAny(Path.of("src/Foo.java"), "Foo.java", matchers))
    }

    @Test
    fun `buildMatcher plain case-insensitive finds match`() {
        val matcher = SearchEngine.buildMatcher("hello", caseSensitive = false, wholeWord = false, regex = false)
        assertEquals(4, matcher("say hello world"))
    }

    @Test
    fun `buildMatcher plain case-sensitive returns null on mismatch`() {
        val matcher = SearchEngine.buildMatcher("Hello", caseSensitive = true, wholeWord = false, regex = false)
        assertNull(matcher("say hello world"))
    }

    @Test
    fun `buildMatcher plain case-sensitive finds exact case match`() {
        val matcher = SearchEngine.buildMatcher("Hello", caseSensitive = true, wholeWord = false, regex = false)
        assertEquals(4, matcher("say Hello world"))
    }

    @Test
    fun `buildMatcher regex finds pattern`() {
        val matcher = SearchEngine.buildMatcher("\\d+", caseSensitive = false, wholeWord = false, regex = true)
        assertEquals(4, matcher("abc 123 def"))
    }

    @Test
    fun `buildMatcher whole word only matches whole words`() {
        val matcher = SearchEngine.buildMatcher("hello", caseSensitive = false, wholeWord = true, regex = false)
        assertNull(matcher("sayhelloworld"))
    }

    @Test
    fun `buildMatcher whole word matches isolated word`() {
        val matcher = SearchEngine.buildMatcher("hello", caseSensitive = false, wholeWord = true, regex = false)
        assertEquals(4, matcher("say hello world"))
    }

    @Test
    fun `buildMatcher returns null when no match`() {
        val matcher = SearchEngine.buildMatcher("xyz", caseSensitive = false, wholeWord = false, regex = false)
        assertNull(matcher("hello world"))
    }

    @Test
    fun `buildRipgrepArgs basic invocation`() {
        val opts =
            SearchOptions(
                query = "needlecast",
                caseSensitive = false,
                wholeWord = false,
                regex = false,
                includeGlobs = emptyList(),
                excludeGlobs = emptyList(),
                includeMatchers = emptyList(),
                excludeMatchers = emptyList(),
                sizeLimitBytes = null,
                useRipgrep = true,
            )
        val args = SearchEngine.buildRipgrepArgs(opts)
        assertEquals(listOf("rg", "--vimgrep", "--no-messages", "--fixed-strings", "-i", "needlecast", "."), args)
    }

    @Test
    fun `buildRipgrepArgs with all options`() {
        val opts =
            SearchOptions(
                query = "needlecast",
                caseSensitive = true,
                wholeWord = true,
                regex = true,
                includeGlobs = listOf("*.kt"),
                excludeGlobs = listOf("*.class"),
                includeMatchers = emptyList(),
                excludeMatchers = emptyList(),
                sizeLimitBytes = 5L * 1024L * 1024L,
                useRipgrep = true,
            )
        val args = SearchEngine.buildRipgrepArgs(opts)
        assertTrue(args.contains("-s"))
        assertTrue(args.contains("-w"))
        assertFalse(args.contains("--fixed-strings"))
        assertTrue(args.contains("--max-filesize"))
        assertTrue(args.contains("5M"))
        assertTrue(args.contains("-g"))
        assertTrue(args.contains("*.kt"))
        assertTrue(args.contains("!*.class"))
    }

    @Test
    fun `buildRipgrepArgs sizeLimitBytes coerces to at least 1M`() {
        val opts =
            SearchOptions(
                query = "test",
                caseSensitive = false,
                wholeWord = false,
                regex = false,
                includeGlobs = emptyList(),
                excludeGlobs = emptyList(),
                includeMatchers = emptyList(),
                excludeMatchers = emptyList(),
                sizeLimitBytes = 100L,
                useRipgrep = true,
            )
        val args = SearchEngine.buildRipgrepArgs(opts)
        assertTrue(args.contains("1M"))
    }

    @Test
    fun `parseRipgrepLine parses valid vimgrep output`() {
        val hit = SearchEngine.parseRipgrepLine("src/Main.kt:10:5:fun main()")!!
        assertEquals("src/Main.kt", hit.path)
        assertEquals(10, hit.line)
        assertEquals(5, hit.column)
        assertEquals("fun main()", hit.text)
    }

    @Test
    fun `parseRipgrepLine returns null for malformed input`() {
        assertNull(SearchEngine.parseRipgrepLine("no colons here"))
        assertNull(SearchEngine.parseRipgrepLine(""))
    }

    @Test
    fun `parseRipgrepLine handles Windows-style path with drive letter`() {
        val hit = SearchEngine.parseRipgrepLine("C:/src/Main.kt:1:1:text")!!
        assertEquals("C:/src/Main.kt", hit.path)
        assertEquals(1, hit.line)
        assertEquals(1, hit.column)
        assertEquals("text", hit.text)
    }

    @Test
    fun `parseRipgrepLine returns null for too few colons`() {
        assertNull(SearchEngine.parseRipgrepLine("path:10:text"))
    }

    @Test
    fun `preview returns trimmed line when short`() {
        assertEquals("hello world", SearchEngine.preview("  hello world  "))
    }

    @Test
    fun `preview truncates to 240 chars with ellipsis when long`() {
        val long = "a".repeat(300)
        val result = SearchEngine.preview(long)
        assertTrue(result.endsWith("..."))
        assertEquals(240, result.length)
    }

    @Test
    fun `preview does not truncate at exactly 240 chars`() {
        val exact = "a".repeat(240)
        assertEquals(exact, SearchEngine.preview(exact))
    }

    @Test
    fun `shouldSkipDir skips known dirs case-insensitive`() {
        assertTrue(SearchEngine.shouldSkipDir(".git"))
        assertTrue(SearchEngine.shouldSkipDir("node_modules"))
        assertTrue(SearchEngine.shouldSkipDir("BUILD"))
        assertTrue(SearchEngine.shouldSkipDir("Target"))
    }

    @Test
    fun `shouldSkipDir allows normal dirs`() {
        assertFalse(SearchEngine.shouldSkipDir("src"))
        assertFalse(SearchEngine.shouldSkipDir("my-project"))
    }

    @Test
    fun `shouldSkipDir returns false for null`() {
        assertFalse(SearchEngine.shouldSkipDir(null))
    }

    @Test
    fun `shouldSkipFile skips dotfiles`() {
        assertTrue(SearchEngine.shouldSkipFile(".env"))
        assertTrue(SearchEngine.shouldSkipFile(".gitignore"))
    }

    @Test
    fun `shouldSkipFile skips known extensions`() {
        assertTrue(SearchEngine.shouldSkipFile("image.png"))
        assertTrue(SearchEngine.shouldSkipFile("lib.jar"))
        assertTrue(SearchEngine.shouldSkipFile("archive.zip"))
    }

    @Test
    fun `shouldSkipFile allows normal files`() {
        assertFalse(SearchEngine.shouldSkipFile("Main.kt"))
        assertFalse(SearchEngine.shouldSkipFile("build.gradle"))
    }

    @Test
    fun `formatSummary with matches and skip stats`() {
        val stats =
            SearchStats(
                filesScanned = 100,
                filesWithMatches = 3,
                matches = 10,
                skippedLarge = 2,
                skippedBinary = 1,
                skippedDirs = 5,
                skippedFiles = 3,
                truncated = false,
                durationMs = 1500,
            )
        val result = SearchEngine.formatSummary(stats, 100)
        assertTrue(result.startsWith("10 matches in 3 files"))
        assertTrue(result.contains("50") && result.contains("s"))
        assertTrue(result.contains("2 large skipped"))
        assertTrue(result.contains("1 binary skipped"))
        assertTrue(result.contains("8 ignored"))
    }

    @Test
    fun `formatSummary with no matches`() {
        val stats = SearchStats(durationMs = 500)
        val result = SearchEngine.formatSummary(stats, 100)
        assertTrue(result.startsWith("No matches."))
        assertTrue(result.contains("50") && result.contains("s"))
    }

    @Test
    fun `formatSummary with truncation`() {
        val stats =
            SearchStats(
                filesWithMatches = 1,
                matches = 1,
                truncated = true,
                durationMs = 100,
            )
        val result = SearchEngine.formatSummary(stats, 50)
        assertTrue(result.contains("results capped at 50"))
    }

    @Test
    fun `formatSummary singular match and file`() {
        val stats =
            SearchStats(
                filesWithMatches = 1,
                matches = 1,
                durationMs = 200,
            )
        val result = SearchEngine.formatSummary(stats, 100)
        assertTrue(result.startsWith("1 match in 1 file"))
    }
}
