# Cycle 16: SearchPanel + LogFileScanner + LogViewer Test Coverage

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract testable pure-logic functions from SearchPanel (831 lines, 0 tests) and LogFileScanner (35 lines, 0 tests), then write comprehensive unit tests. Also add appLogFiles + LogViewer tail logic tests.

**Architecture:** SearchPanel mixes Swing UI with pure search logic (glob parsing, path matching, ripgrep arg building, ripgrep output parsing, binary detection, result formatting). We extract the pure functions into a companion `SearchEngine` object so they can be tested without Swing. LogFileScanner is already an object with a single `scan()` method — we test it with temp directories. The `appLogFiles()` top-level function in LogViewerPanel gets tested with a temp directory.

**Tech Stack:** JUnit 5, Kotlin, `@TempDir` for filesystem tests, no mocking frameworks.

---

### Task 1: Extract SearchEngine object from SearchPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SearchEngine.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SearchPanel.kt` (call SearchEngine instead of private methods)

The following functions are pure logic with no Swing dependency. Extract them into a new `SearchEngine` object:

```kotlin
package io.github.rygel.needlecast.ui

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object SearchEngine {
    val SKIP_DIRS = setOf(
        ".git", ".hg", ".svn", ".idea", ".gradle", ".mvn", ".cache",
        "node_modules", "target", "build", "dist", "out", "vendor",
    )

    val SKIP_FILE_EXTENSIONS = setOf(
        "class", "jar", "war", "ear", "zip", "gz", "bz2", "7z",
        "png", "jpg", "jpeg", "gif", "bmp", "ico", "webp", "tif", "tiff", "svg", "pdf",
        "mp3", "mp4", "mov", "avi", "mkv",
        "exe", "dll", "so", "dylib", "bin", "dat",
        "ttf", "otf", "woff", "woff2",
        "pyc", "pyo",
    )

    fun parseGlobs(input: String): List<String> =
        input.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

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
            val namePath = try { Path.of(fileName) } catch (_: Exception) { null }
            if (namePath != null && matchers.any { it.matches(namePath) }) return true
        }
        return false
    }

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

    fun buildRipgrepArgs(
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
        includeGlobs: List<String>,
        excludeGlobs: List<String>,
        sizeLimitBytes: Long?,
    ): List<String> {
        val argv = mutableListOf("rg", "--vimgrep", "--no-messages")
        if (!regex) argv += "--fixed-strings"
        argv += if (caseSensitive) "-s" else "-i"
        if (wholeWord) argv += "-w"
        if (sizeLimitBytes != null) {
            val mb = (sizeLimitBytes / (1024L * 1024L)).coerceAtLeast(1)
            argv += listOf("--max-filesize", "${mb}M")
        }
        includeGlobs.forEach { argv += listOf("-g", it) }
        excludeGlobs.forEach { argv += listOf("-g", if (it.startsWith("!")) it else "!$it") }
        argv += query
        argv += "."
        return argv
    }

    data class RipgrepHit(
        val path: String,
        val line: Int,
        val column: Int,
        val text: String,
    )

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
        matches: Int,
        filesWithMatches: Int,
        durationMs: Long,
        skippedLarge: Int,
        skippedBinary: Int,
        skippedDirs: Int,
        skippedFiles: Int,
        truncated: Boolean,
        maxResults: Int,
    ): String {
        val base = if (matches == 0) {
            "No matches."
        } else {
            "$matches match${if (matches == 1) "" else "es"} in $filesWithMatches file${if (filesWithMatches == 1) "" else "s"}"
        }
        val extra = buildString {
            append(" (${"%.2f".format(durationMs / 1000.0)}s")
            if (skippedLarge > 0) append(", $skippedLarge large skipped")
            if (skippedBinary > 0) append(", $skippedBinary binary skipped")
            if (skippedDirs + skippedFiles > 0) append(", ${skippedDirs + skippedFiles} ignored")
            append(")")
        }
        return if (truncated) {
            "$base$extra — results capped at $maxResults."
        } else {
            "$base$extra"
        }
    }
}
```

Then update `SearchPanel.kt` to delegate to `SearchEngine`:
- Replace private `parseGlobs` → `SearchEngine.parseGlobs`
- Replace private `buildMatchers` → `SearchEngine.buildMatchers`
- Replace private `matchesAny` → `SearchEngine.matchesAny`
- Replace private `buildMatcher` → `SearchEngine.buildMatcher`
- Replace private `buildRipgrepArgs` → `SearchEngine.buildRipgrepArgs`
- Replace private `parseRipgrepLine` → `SearchEngine.parseRipgrepLine`
- Replace private `preview` → `SearchEngine.preview`
- Replace private `shouldSkipDir` → `SearchEngine.shouldSkipDir`
- Replace private `shouldSkipFile` → `SearchEngine.shouldSkipFile`
- Replace private `formatSummary(stats)` → `SearchEngine.formatSummary(stats.matches, ...)`
- Remove the private companion const/data duplicates (`SKIP_DIRS`, `SKIP_FILE_EXTENSIONS`, `RipgrepHit`, `formatSummary`)
- Delete the now-dead private methods from `SearchPanel`

- [ ] **Step 1: Create `SearchEngine.kt`** with the object above

- [ ] **Step 2: Update `SearchPanel.kt`** to delegate to SearchEngine, remove dead private methods and companion duplicates

- [ ] **Step 3: Run ktlint**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 4: Verify existing tests still pass**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: same pass/fail counts as before (4 pre-existing failures)

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SearchEngine.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SearchPanel.kt
git commit -m "refactor(search): extract SearchEngine object with pure logic from SearchPanel"
```

---

### Task 2: SearchEngine unit tests

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/SearchEngineTest.kt`

```kotlin
package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SearchEngineTest {

    // ── parseGlobs ──────────────────────────────────────────────────────────

    @Test
    fun `parseGlobs splits on comma and semicolon`() {
        val result = SearchEngine.parseGlobs("*.kt, *.java; *.xml")
        assertEquals(listOf("*.kt", "*.java", "*.xml"), result)
    }

    @Test
    fun `parseGlobs trims whitespace and filters empty`() {
        val result = SearchEngine.parseGlobs("  *.kt  ,  , ; *.java ")
        assertEquals(listOf("*.kt", "*.java"), result)
    }

    @Test
    fun `parseGlobs returns empty for blank input`() {
        assertTrue(SearchEngine.parseGlobs("").isEmpty())
        assertTrue(SearchEngine.parseGlobs("   ").isEmpty())
    }

    // ── buildMatchers ───────────────────────────────────────────────────────

    @Test
    fun `buildMatchers returns empty for empty input`() {
        assertTrue(SearchEngine.buildMatchers("").isEmpty())
    }

    @Test
    fun `buildMatchers creates working glob matchers`() {
        val matchers = SearchEngine.buildMatchers("*.kt")
        assertTrue(matchers.any { it.matches(Path.of("Foo.kt")) })
        assertFalse(matchers.any { it.matches(Path.of("Foo.java")) })
    }

    @Test
    fun `buildMatchers throws on invalid glob`() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchEngine.buildMatchers("[invalid")
        }
    }

    @Test
    fun `buildMatchers with stripNegation removes exclamation marks`() {
        val matchers = SearchEngine.buildMatchers("!*.class, !*.jar", stripNegation = true)
        assertEquals(2, matchers.size)
    }

    // ── matchesAny ──────────────────────────────────────────────────────────

    @Test
    fun `matchesAny returns false for empty matchers`() {
        assertFalse(SearchEngine.matchesAny(Path.of("test.kt"), "test.kt", emptyList()))
    }

    @Test
    fun `matchesAny matches on path`() {
        val matchers = SearchEngine.buildMatchers("*.kt")
        assertTrue(SearchEngine.matchesAny(Path.of("src/Foo.kt"), "Foo.kt", matchers))
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

    // ── buildMatcher ────────────────────────────────────────────────────────

    @Test
    fun `buildMatcher plain case-insensitive finds match`() {
        val matcher = SearchEngine.buildMatcher("hello", caseSensitive = false, wholeWord = false, regex = false)
        assertEquals(7, matcher("Say Hello world"))
    }

    @Test
    fun `buildMatcher plain case-sensitive returns null on mismatch`() {
        val matcher = SearchEngine.buildMatcher("Hello", caseSensitive = true, wholeWord = false, regex = false)
        assertNull(matcher("say hello world"))
    }

    @Test
    fun `buildMatcher regex finds pattern`() {
        val matcher = SearchEngine.buildMatcher("\\d+", caseSensitive = false, wholeWord = false, regex = true)
        assertEquals(5, matcher("test 123 end"))
    }

    @Test
    fun `buildMatcher whole word only matches whole words`() {
        val matcher = SearchEngine.buildMatcher("cat", caseSensitive = true, wholeWord = true, regex = false)
        assertEquals(0, matcher("cat"))
        assertNull(matcher("concatenate"))
    }

    @Test
    fun `buildMatcher returns null when no match`() {
        val matcher = SearchEngine.buildMatcher("xyz", caseSensitive = false, wholeWord = false, regex = false)
        assertNull(matcher("hello world"))
    }

    // ── buildRipgrepArgs ────────────────────────────────────────────────────

    @Test
    fun `buildRipgrepArgs basic invocation`() {
        val args = SearchEngine.buildRipgrepArgs(
            query = "TODO",
            caseSensitive = false,
            wholeWord = false,
            regex = false,
            includeGlobs = emptyList(),
            excludeGlobs = emptyList(),
            sizeLimitBytes = null,
        )
        assertEquals(listOf("rg", "--vimgrep", "--no-messages", "--fixed-strings", "-i", "TODO", "."), args)
    }

    @Test
    fun `buildRipgrepArgs with all options`() {
        val args = SearchEngine.buildRipgrepArgs(
            query = "TODO",
            caseSensitive = true,
            wholeWord = true,
            regex = true,
            includeGlobs = listOf("*.kt"),
            excludeGlobs = listOf("*.class"),
            sizeLimitBytes = 2L * 1024L * 1024L,
        )
        assertTrue("-s" in args)
        assertTrue("-w" in args)
        assertFalse("--fixed-strings" in args)
        assertTrue("--max-filesize" in args)
        assertTrue("2M" in args)
        assertTrue(args.contains("-g") && args.contains("*.kt"))
        assertTrue(args.contains("!*.class"))
    }

    // ── parseRipgrepLine ────────────────────────────────────────────────────

    @Test
    fun `parseRipgrepLine parses valid vimgrep output`() {
        val hit = SearchEngine.parseRipgrepLine("src/Main.kt:42:10:val x = 1")
        assertNotNull(hit)
        assertEquals("src/Main.kt", hit!!.path)
        assertEquals(42, hit.line)
        assertEquals(10, hit.column)
        assertEquals("val x = 1", hit.text)
    }

    @Test
    fun `parseRipgrepLine returns null for malformed input`() {
        assertNull(SearchEngine.parseRipgrepLine("no colons here"))
        assertNull(SearchEngine.parseRipgrepLine("only:two:colons"))
        assertNull(SearchEngine.parseRipgrepLine("a:b:c:d:e:f"))
    }

    @Test
    fun `parseRipgrepLine handles path with colons in text`() {
        val hit = SearchEngine.parseRipgrepLine("src/Main.kt:10:5:key: value")
        assertNotNull(hit)
        assertEquals("key: value", hit!!.text)
    }

    // ── preview ─────────────────────────────────────────────────────────────

    @Test
    fun `preview returns trimmed line as-is when short`() {
        assertEquals("hello world", SearchEngine.preview("  hello world  "))
    }

    @Test
    fun `preview truncates to 237 chars plus ellipsis`() {
        val long = "x".repeat(300)
        val result = SearchEngine.preview(long)
        assertEquals(240, result.length)
        assertTrue(result.endsWith("..."))
    }

    // ── shouldSkipDir / shouldSkipFile ──────────────────────────────────────

    @Test
    fun `shouldSkipDir skips known directories`() {
        assertTrue(SearchEngine.shouldSkipDir(".git"))
        assertTrue(SearchEngine.shouldSkipDir("node_modules"))
        assertTrue(SearchEngine.shouldSkipDir("TARGET")) // case-insensitive
    }

    @Test
    fun `shouldSkipDir allows normal directories`() {
        assertFalse(SearchEngine.shouldSkipDir("src"))
        assertFalse(SearchEngine.shouldSkipDir("my-folder"))
        assertFalse(SearchEngine.shouldSkipDir(null))
    }

    @Test
    fun `shouldSkipFile skips dotfiles and known extensions`() {
        assertTrue(SearchEngine.shouldSkipFile(".gitignore"))
        assertTrue(SearchEngine.shouldSkipFile("app.jar"))
        assertTrue(SearchEngine.shouldSkipFile("photo.PNG"))
    }

    @Test
    fun `shouldSkipFile allows normal files`() {
        assertFalse(SearchEngine.shouldSkipFile("Main.kt"))
        assertFalse(SearchEngine.shouldSkipFile("config.xml"))
        assertFalse(SearchEngine.shouldSkipFile("README"))
    }

    // ── formatSummary ───────────────────────────────────────────────────────

    @Test
    fun `formatSummary with matches`() {
        val result = SearchEngine.formatSummary(
            matches = 5, filesWithMatches = 3, durationMs = 1500,
            skippedLarge = 1, skippedBinary = 0, skippedDirs = 2, skippedFiles = 0,
            truncated = false, maxResults = 10000,
        )
        assertTrue(result.startsWith("5 matches in 3 files"))
        assertTrue(result.contains("1 large skipped"))
        assertTrue(result.contains("2 ignored"))
    }

    @Test
    fun `formatSummary with no matches`() {
        val result = SearchEngine.formatSummary(
            matches = 0, filesWithMatches = 0, durationMs = 500,
            skippedLarge = 0, skippedBinary = 0, skippedDirs = 0, skippedFiles = 0,
            truncated = false, maxResults = 10000,
        )
        assertTrue(result.startsWith("No matches."))
    }

    @Test
    fun `formatSummary with truncation`() {
        val result = SearchEngine.formatSummary(
            matches = 10000, filesWithMatches = 500, durationMs = 3000,
            skippedLarge = 0, skippedBinary = 0, skippedDirs = 0, skippedFiles = 0,
            truncated = true, maxResults = 10000,
        )
        assertTrue(result.contains("results capped at 10000"))
    }
}
```

- [ ] **Step 1: Create `SearchEngineTest.kt`**

- [ ] **Step 2: Run tests**

```bash
mvn test -pl needlecast-desktop -Dtest="SearchEngineTest" -q
```

Expected: all pass

- [ ] **Step 3: Run ktlint**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/SearchEngineTest.kt
git commit -m "test(search): add SearchEngine unit tests (30 tests)"
```

---

### Task 3: LogFileScanner tests

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/LogFileScannerTest.kt`

```kotlin
package io.github.rygel.needlecast.ui.logviewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LogFileScannerTest {

    @Test
    fun `scan returns empty for non-existent directory`(@TempDir dir: File) {
        val result = LogFileScanner.scan(File(dir, "nope").absolutePath)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scan finds log files in root directory`(@TempDir dir: File) {
        File(dir, "app.log").writeText("log entry")
        File(dir, "debug.log").writeText("debug")
        File(dir, "readme.txt").writeText("not a log")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(2, result.size)
        val names = result.map { it.name }
        assertTrue(names.contains("app.log"))
        assertTrue(names.contains("debug.log"))
    }

    @Test
    fun `scan finds log files in target subdirectory`(@TempDir dir: File) {
        val target = File(dir, "target").apply { mkdirs() }
        File(target, "build.log").writeText("build output")
        File(dir, "other.txt").writeText("not log")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("build.log", result[0].name)
    }

    @Test
    fun `scan finds log files in logs subdirectory`(@TempDir dir: File) {
        val logs = File(dir, "logs").apply { mkdirs() }
        File(logs, "app.log.1").writeText("rotated log")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("app.log.1", result[0].name)
    }

    @Test
    fun `scan finds rotated log files`(@TempDir dir: File) {
        File(dir, "server.log").writeText("current")
        File(dir, "server.log.1").writeText("rotated1")
        File(dir, "server.log.5").writeText("rotated5")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(3, result.size)
    }

    @Test
    fun `scan ignores non-log extensions`(@TempDir dir: File) {
        File(dir, "app.txt").writeText("text")
        File(dir, "app.out").writeText("output")
        File(dir, "app.log").writeText("log")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("app.log", result[0].name)
    }

    @Test
    fun `scan results sorted by last-modified descending`(@TempDir dir: File) throws InterruptedException {
        val log1 = File(dir, "a.log").apply { writeText("old") }
        Thread.sleep(50)
        val log2 = File(dir, "b.log").apply { writeText("new") }

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(2, result.size)
        assertEquals(log2.name, result[0].name)
        assertEquals(log1.name, result[1].name)
    }

    @Test
    fun `scan searches nested log dirs two levels deep`(@TempDir dir: File) {
        val nested = File(dir, "target/surefire-reports").apply { mkdirs() }
        File(nested, "test.log").writeText("test output")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("test.log", result[0].name)
    }

    @Test
    fun `scan is case-insensitive for log extension`(@TempDir dir: File) {
        File(dir, "app.LOG").writeText("uppercase log")

        val result = LogFileScanner.scan(dir.absolutePath)
        assertEquals(1, result.size)
    }
}
```

- [ ] **Step 1: Create `LogFileScannerTest.kt`**

- [ ] **Step 2: Run tests**

```bash
mvn test -pl needlecast-desktop -Dtest="LogFileScannerTest" -q
```

Expected: all pass

- [ ] **Step 3: Run ktlint**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/LogFileScannerTest.kt
git commit -m "test(logviewer): add LogFileScanner tests (9 tests)"
```

---

### Task 4: appLogFiles tests

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/AppLogFilesTest.kt`

The `appLogFiles(logDir)` function already exists and is `internal`. There's already an `AppLogFilesTest.kt` — check it first. If it exists with sufficient tests, skip this task and merge the commit from the previous task.

- [ ] **Step 1: Check if `AppLogFilesTest.kt` already covers the function adequately**

```bash
cat needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/AppLogFilesTest.kt
```

If adequate, skip to Task 5. If missing or insufficient, add tests for:
- Returns empty list when no log files exist
- Returns base log when only needlecast.log exists
- Returns rotated logs in correct order (needlecast.log, needlecast.log.1, ..., needlecast.log.5)
- Skips missing rotation files (e.g., .log.3 doesn't exist)

- [ ] **Step 2: Run tests and commit if new tests added**

---

### Task 5: Run full test suite, ktlint, commit, merge to develop

- [ ] **Step 1: Run ktlint on all changed files**

```bash
mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop -q
```

- [ ] **Step 2: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: 4 pre-existing failures (configVersion, IconLoadingTest), all new tests pass

- [ ] **Step 3: Check git status and stage all changes**

```bash
git status
git add -A
```

- [ ] **Step 4: Commit any remaining ktlint fixes**

```bash
git commit -m "style: ktlint formatting"
```

(only if there are unstaged changes)

- [ ] **Step 5: Create branch, push, merge to develop**

```bash
git checkout -b feat/cycle-16-search-logfile-tests
git push -u origin feat/cycle-16-search-logfile-tests
git checkout develop
git merge --no-ff feat/cycle-16-search-logfile-tests -m "Cycle 16: SearchPanel extraction + SearchEngine, LogFileScanner, appLogFiles tests"
git push origin develop
```

---

## Self-Review Checklist

**1. Spec coverage:** All tasks have actual code — no placeholders. SearchPanel pure logic fully extracted (Task 1). SearchEngine fully tested (Task 2, ~30 tests). LogFileScanner fully tested (Task 3, 9 tests). appLogFiles verified/extended (Task 4). Final integration verified (Task 5).

**2. Placeholder scan:** No TBD, TODO, "implement later", "add validation", "similar to" patterns found.

**3. Type consistency:** `SearchEngine.RipgrepHit` is a public data class. `SearchEngine.parseRipgrepLine` returns `RipgrepHit?`. All parameter names match between SearchEngine methods and SearchPanel callers. `formatSummary` takes individual parameters (not a SearchStats object) to avoid coupling the test to SearchPanel's private inner class.
