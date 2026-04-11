# Git Write UI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add commit/fetch/push/pull operations to `GitLogPanel` via an IntelliJ-style toolbar that toggles between a log view, a commit view (file checklist + inline message field), and a streaming output view for remote operations.

**Architecture:** `GitService` gains five new methods (changedFiles, stage, commit, fetchStreaming, pushStreaming, pullStreaming); `ProcessGitService` implements them with a new `runGitStreaming` helper using `ProcessBuilder`. `GitLogPanel`'s content area becomes a `CardLayout` with three named cards (`"log"`, `"commit"`, `"output"`), accessed via a toolbar with `JToggleButton` pairs and action buttons.

**Tech Stack:** Kotlin, Swing (CardLayout, SwingWorker, JList, JToggleButton, JCheckBox), ProcessBuilder for streaming git output, JUnit 5 unit tests, assertj-swing for UI tests (run via `mvn verify -Ptest-desktop` inside Podman/Xvfb).

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `git/ChangedFile.kt` | Create | Data class for a file reported by `git status --porcelain` |
| `git/GitService.kt` | Modify | Add 5 new method signatures |
| `git/ProcessGitService.kt` | Modify | `parseChangedFiles` fn + all write method implementations |
| `ui/GitLogPanel.kt` | Modify | Toolbar, three-card layout, commit card, output card |
| `test/.../git/ChangedFileParsingTest.kt` | Create | Unit tests for porcelain-output parsing |
| `test/.../ui/GitLogPanelUiTest.kt` | Modify | Replace inline fake with `FakeGitService`; add toolbar/commit/output tests |

---

### Task 1: `ChangedFile` data class and `GitService` interface extension

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ChangedFile.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/GitService.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt`
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt`

- [ ] **Step 1: Create `ChangedFile.kt`**

```kotlin
package io.github.rygel.needlecast.git

/**
 * A file reported by `git status --porcelain`.
 *
 * @param path       Path of the file relative to the repository root.
 * @param statusCode The two-character XY code from `git status --porcelain`
 *                   (e.g. `" M"`, `"M "`, `"??"`, `"A "`, `"D "`).
 */
data class ChangedFile(val path: String, val statusCode: String)
```

- [ ] **Step 2: Replace `GitService.kt` with the extended interface**

```kotlin
package io.github.rygel.needlecast.git

import io.github.rygel.needlecast.model.GitStatus

/**
 * Abstraction over git operations used by the UI.
 * The default implementation ([ProcessGitService]) shells out to `git`;
 * tests can substitute a fake without spawning processes.
 */
interface GitService {
    /** Returns the current branch and dirty-state for [dir], or [GitStatus.NotARepo] if not a repo. */
    fun readStatus(dir: String): GitStatus

    /**
     * Returns the last [maxEntries] commits as `git log --oneline` output,
     * or null if git is unavailable or the directory is not a repo.
     */
    fun log(dir: String, maxEntries: Int = 40): String?

    /**
     * Returns the full `git show --stat -p` output for [hash],
     * or null if the hash cannot be resolved.
     */
    fun show(dir: String, hash: String): String?

    /**
     * Returns files with uncommitted changes, or an empty list if not a repo.
     * Parses `git status --porcelain` output.
     */
    fun changedFiles(dir: String): List<ChangedFile>

    /**
     * Stages [files] for the next commit (`git add -- <files>`).
     * @throws RuntimeException if git exits with a non-zero code.
     */
    fun stage(dir: String, files: List<String>)

    /**
     * Creates a commit with [message] (`git commit -m <message>`).
     * @throws RuntimeException if git exits with a non-zero code.
     */
    fun commit(dir: String, message: String)

    /**
     * Runs `git fetch`, calling [onLine] per output line from the worker thread.
     * @return the git process exit code (0 = success).
     */
    fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int

    /**
     * Runs `git push`, calling [onLine] per output line from the worker thread.
     * @return the git process exit code (0 = success).
     */
    fun pushStreaming(dir: String, onLine: (String) -> Unit): Int

    /**
     * Runs `git pull`, calling [onLine] per output line from the worker thread.
     * @return the git process exit code (0 = success).
     */
    fun pullStreaming(dir: String, onLine: (String) -> Unit): Int
}
```

- [ ] **Step 3: Add stub implementations to `ProcessGitService.kt`**

Add these five overrides to `ProcessGitService` before the private `runGit` method (they will be properly implemented in Task 2):

```kotlin
override fun changedFiles(dir: String): List<ChangedFile> = TODO("Task 2")
override fun stage(dir: String, files: List<String>): Unit = TODO("Task 2")
override fun commit(dir: String, message: String): Unit = TODO("Task 2")
override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int = TODO("Task 2")
override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int = TODO("Task 2")
override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int = TODO("Task 2")
```

- [ ] **Step 4: Replace the inline fake in `GitLogPanelUiTest.kt` with `FakeGitService`**

Add the following **before** the `GitLogPanelUiTest` class declaration (it is a file-private helper class):

```kotlin
private class FakeGitService(
    val logLines: String = "",
    val showOutput: String = "",
    val changedFilesList: List<io.github.rygel.needlecast.git.ChangedFile> = emptyList(),
    val streamingLines: List<String> = emptyList(),
) : GitService {
    var stagedFiles: List<String>? = null
    var committedMessage: String? = null

    override fun readStatus(dir: String): GitStatus = GitStatus.NotARepo
    override fun log(dir: String, maxEntries: Int): String = logLines
    override fun show(dir: String, hash: String): String = showOutput
    override fun changedFiles(dir: String): List<io.github.rygel.needlecast.git.ChangedFile> = changedFilesList
    override fun stage(dir: String, files: List<String>) { stagedFiles = files }
    override fun commit(dir: String, message: String) { committedMessage = message }
    override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int {
        streamingLines.forEach { onLine(it) }
        return 0
    }
    override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int {
        streamingLines.forEach { onLine(it) }
        return 0
    }
    override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int {
        streamingLines.forEach { onLine(it) }
        return 0
    }
}
```

In the existing `large diffs render incrementally without blocking` test, replace the inline anonymous `GitService` object:

```kotlin
// BEFORE — remove this:
val gitService = object : GitService {
    override fun readStatus(dir: String): GitStatus = GitStatus.NotARepo
    override fun log(dir: String, maxEntries: Int): String = "abc123 Commit one\n"
    override fun show(path: String, hash: String): String = huge
}
panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(gitService) }

// AFTER — replace with:
val fake = FakeGitService(logLines = "abc123 Commit one\n", showOutput = huge)
panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
```

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -am -q`

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ChangedFile.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/GitService.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt
git commit -m "feat: add ChangedFile type and write method signatures to GitService"
```

---

### Task 2: `ProcessGitService` write operations

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/git/ChangedFileParsingTest.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt`

- [ ] **Step 1: Create the failing parsing tests**

```kotlin
package io.github.rygel.needlecast.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangedFileParsingTest {

    @Test
    fun `returns empty list for blank input`() {
        assertEquals(emptyList<ChangedFile>(), parseChangedFiles(""))
    }

    @Test
    fun `parses a modified file`() {
        val result = parseChangedFiles(" M src/Main.kt")
        assertEquals(1, result.size)
        assertEquals(ChangedFile("src/Main.kt", " M"), result[0])
    }

    @Test
    fun `parses an untracked file`() {
        val result = parseChangedFiles("?? new-file.txt")
        assertEquals(1, result.size)
        assertEquals(ChangedFile("new-file.txt", "??"), result[0])
    }

    @Test
    fun `parses multiple files of different status types`() {
        val result = parseChangedFiles(" M src/Main.kt\n?? new.txt\nD  deleted.kt")
        assertEquals(3, result.size)
        assertEquals(ChangedFile("src/Main.kt", " M"), result[0])
        assertEquals(ChangedFile("new.txt",     "??"), result[1])
        assertEquals(ChangedFile("deleted.kt",  "D "), result[2])
    }

    @Test
    fun `skips lines shorter than 3 characters`() {
        assertEquals(emptyList<ChangedFile>(), parseChangedFiles("M\n??"))
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn test -pl needlecast-desktop -Dtest=ChangedFileParsingTest -q`

Expected: `BUILD FAILURE` — `parseChangedFiles` is not yet defined.

- [ ] **Step 3: Replace `ProcessGitService.kt` with full implementation**

```kotlin
package io.github.rygel.needlecast.git

import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.process.ProcessExecutor
import java.util.concurrent.TimeUnit

/**
 * Parses the output of `git status --porcelain` into a list of [ChangedFile].
 * Internal so it can be tested directly without spawning a real git process.
 */
internal fun parseChangedFiles(porcelainOutput: String): List<ChangedFile> =
    porcelainOutput.lines()
        .filter { it.length >= 3 }
        .map { ChangedFile(path = it.substring(3), statusCode = it.substring(0, 2)) }

/**
 * [GitService] implementation that shells out to the system `git` binary.
 * All calls block the calling thread; always invoke from a background thread or SwingWorker.
 */
class ProcessGitService : GitService {

    override fun readStatus(dir: String): GitStatus = GitStatus.read(dir)

    override fun log(dir: String, maxEntries: Int): String? =
        runGit(dir, "log", "--oneline", "--no-decorate", "-$maxEntries")

    override fun show(dir: String, hash: String): String? =
        runGit(dir, "show", "--stat", "-p", hash)

    override fun changedFiles(dir: String): List<ChangedFile> {
        val raw = runGit(dir, "status", "--porcelain") ?: return emptyList()
        return parseChangedFiles(raw)
    }

    override fun stage(dir: String, files: List<String>) {
        runGitOrThrow(dir, "add", "--", *files.toTypedArray())
    }

    override fun commit(dir: String, message: String) {
        runGitOrThrow(dir, "commit", "-m", message)
    }

    override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int =
        runGitStreaming(dir, listOf("fetch"), onLine)

    override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int =
        runGitStreaming(dir, listOf("push"), onLine)

    override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int =
        runGitStreaming(dir, listOf("pull"), onLine)

    /** Runs git and returns combined stdout+stderr, or null on failure/timeout. */
    private fun runGit(dir: String, vararg args: String): String? {
        val result = ProcessExecutor.run(listOf("git", "-C", dir) + args.toList(), timeoutMs = 10_000L)
        return result?.output?.ifBlank { null }
    }

    /** Runs git and throws [RuntimeException] if the process exits non-zero. */
    private fun runGitOrThrow(dir: String, vararg args: String): String {
        val result = ProcessExecutor.run(listOf("git", "-C", dir) + args.toList(), timeoutMs = 10_000L)
            ?: throw RuntimeException("git process failed to start or timed out")
        if (result.exitCode != 0)
            throw RuntimeException("git exited with code ${result.exitCode}:\n${result.output}")
        return result.output
    }

    /**
     * Runs git and calls [onLine] for each output line as they arrive.
     * Returns the process exit code, or -1 if the process could not be started.
     * Times out after 120 seconds (generous for push/pull over slow remotes).
     */
    private fun runGitStreaming(dir: String, args: List<String>, onLine: (String) -> Unit): Int {
        val pb = ProcessBuilder(listOf("git", "-C", dir) + args).redirectErrorStream(true)
        val proc = try { pb.start() } catch (_: Exception) { return -1 }
        return try {
            proc.inputStream.bufferedReader().forEachLine { line -> onLine(line) }
            proc.waitFor(120_000L, TimeUnit.MILLISECONDS)
            proc.exitValue()
        } catch (_: Exception) {
            -1
        } finally {
            proc.destroyForcibly()
        }
    }
}
```

- [ ] **Step 4: Run parsing tests — verify they pass**

Run: `mvn test -pl needlecast-desktop -Dtest=ChangedFileParsingTest -q`

Expected: `BUILD SUCCESS`, 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/git/ProcessGitService.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/git/ChangedFileParsingTest.kt
git commit -m "feat: implement git write operations in ProcessGitService"
```

---

### Task 3: `GitLogPanel` — toolbar and three-card structure

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt`
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt`

This task wraps the existing log in a `CardLayout` and adds the toolbar. The commit and output cards are empty `JPanel()` placeholders filled in Tasks 4 and 5.

- [ ] **Step 1: Write a failing test for the toolbar**

Add to `GitLogPanelUiTest`:

```kotlin
@Test
fun `toolbar has Log and Commit toggle buttons and Fetch Push Pull action buttons`() {
    val fake = FakeGitService()
    panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
    fixture = showInFrame(panel)

    fixture.button("toggle-log").requireVisible()
    fixture.button("toggle-commit").requireVisible()
    fixture.button("btn-fetch").requireVisible()
    fixture.button("btn-push").requireVisible()
    fixture.button("btn-pull").requireVisible()
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run (requires Podman/Xvfb): `mvn verify -Ptest-desktop -pl needlecast-desktop -q`

Expected: the new test fails — buttons not found.

- [ ] **Step 3: Replace `GitLogPanel.kt` with the card-layout structure**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.git.ChangedFile
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.git.ProcessGitService
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

private data class GitCommit(val hash: String, val subject: String)

/**
 * Git panel with three views switched via a toolbar:
 * - Log: read-only commit history (existing behaviour)
 * - Commit: staging checklist + commit message field
 * - Output: streaming text for fetch/push/pull
 */
class GitLogPanel(private val gitService: GitService = ProcessGitService()) : JPanel(BorderLayout()) {

    // ── Log view ──────────────────────────────────────────────────────────────
    private val logModel = DefaultListModel<GitCommit>()
    private val logList = JList(logModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(CommitCellRenderer())
        fixedCellHeight = 28
    }
    private val diffArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        lineWrap = false
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }

    // ── Commit view (wired in Task 4) ─────────────────────────────────────────
    private val fileListModel = DefaultListModel<ChangedFile>()
    private val checkedFiles  = mutableSetOf<String>()
    private val fileList = JList(fileListModel).apply { name = "changed-files-list" }
    private val commitMessageField = JTextField().apply {
        name = "commit-message"
        putClientProperty("JTextField.placeholderText", "Commit message…")
    }
    private val commitButton = JButton("Commit").apply { name = "btn-commit-ok" }
    private val cancelButton = JButton("Cancel").apply { name = "btn-commit-cancel" }

    // ── Output view (wired in Task 5) ─────────────────────────────────────────
    private val outputLabel = JLabel("").apply {
        name = "output-label"
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val outputArea = JTextArea().apply {
        name = "output-area"
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val closeButton = JButton("Close").apply { name = "btn-output-close"; isEnabled = false }

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private val logToggle    = JToggleButton("Log").apply    { name = "toggle-log";    isSelected = true }
    private val commitToggle = JToggleButton("Commit").apply { name = "toggle-commit" }
    private val fetchButton  = JButton("Fetch").apply { name = "btn-fetch" }
    private val pushButton   = JButton("Push").apply  { name = "btn-push"  }
    private val pullButton   = JButton("Pull").apply  { name = "btn-pull"  }

    // ── Card layout ───────────────────────────────────────────────────────────
    private val cardLayout = CardLayout()
    private val cardPanel  = JPanel(cardLayout)

    private var currentPath: String? = null
    private var pendingDiffWorker: SwingWorker<String, Void>? = null
    private val maxDiffChars = 400_000

    init {
        minimumSize = Dimension(0, 0)

        // Log card: existing split pane
        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(logList).apply { minimumSize = Dimension(0, 0) },
            JScrollPane(diffArea).apply { minimumSize = Dimension(0, 0) },
        ).apply { resizeWeight = 0.4 }

        logList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val commit = logList.selectedValue ?: return@addListSelectionListener
                showCommit(commit.hash)
            }
        }

        cardPanel.add(split,    "log")
        cardPanel.add(JPanel(), "commit")   // placeholder — replaced in Task 4
        cardPanel.add(JPanel(), "output")   // placeholder — replaced in Task 5

        ButtonGroup().apply { add(logToggle); add(commitToggle) }
        logToggle.addActionListener    { cardLayout.show(cardPanel, "log") }
        commitToggle.addActionListener { cardLayout.show(cardPanel, "commit") }
        fetchButton.addActionListener  { }   // wired in Task 5
        pushButton.addActionListener   { }   // wired in Task 5
        pullButton.addActionListener   { }   // wired in Task 5

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(logToggle); add(commitToggle)
            add(fetchButton); add(pushButton); add(pullButton)
        }

        add(toolbar,   BorderLayout.NORTH)
        add(cardPanel, BorderLayout.CENTER)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadProject(path: String?) {
        currentPath = path
        logModel.clear()
        TextChunker.cancel(diffArea)
        diffArea.text = if (path == null) "" else "Loading commits\u2026"
        if (path == null) return

        object : SwingWorker<List<GitCommit>, Void>() {
            override fun doInBackground(): List<GitCommit> =
                gitService.log(path)
                    ?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.mapNotNull { line ->
                        val space = line.indexOf(' ')
                        if (space < 0) null else GitCommit(line.substring(0, space), line.substring(space + 1))
                    }
                    ?: emptyList()

            override fun done() {
                val commits = try { get() } catch (_: Exception) { return }
                commits.forEach { logModel.addElement(it) }
                if (logModel.size > 0) {
                    diffArea.text = "Select a commit to view details."
                    diffArea.caretPosition = 0
                } else {
                    diffArea.text = "No commits found."
                }
            }
        }.execute()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private class CommitCellRenderer : ListCellRenderer<GitCommit> {
        private val panel = JPanel(BorderLayout(6, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
        private val hashLabel = JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            foreground = Color(0x888888)
        }
        private val subjectLabel = JLabel().apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        }

        init {
            panel.add(hashLabel,    BorderLayout.WEST)
            panel.add(subjectLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out GitCommit>, value: GitCommit?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            hashLabel.text    = value?.hash    ?: ""
            subjectLabel.text = value?.subject ?: ""
            val bg = if (isSelected) list.selectionBackground else list.background
            panel.background        = bg
            panel.isOpaque          = true
            subjectLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            return panel
        }
    }
}
```

- [ ] **Step 4: Run all UI tests — verify they pass**

Run (requires Podman/Xvfb): `mvn verify -Ptest-desktop -pl needlecast-desktop -q`

Expected: `BUILD SUCCESS` — new toolbar test passes, existing large-diff test still passes.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt
git commit -m "feat: add toolbar and CardLayout structure to GitLogPanel"
```

---

### Task 4: `GitLogPanel` — commit card

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt`
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt`

- [ ] **Step 1: Write failing tests for the commit card**

Add these two tests to `GitLogPanelUiTest`. They also need `import org.junit.jupiter.api.Assertions.assertEquals` and `import io.github.rygel.needlecast.git.ChangedFile` at the top of the file.

```kotlin
@Test
fun `commit card shows changed files returned by git service`() {
    val files = listOf(
        io.github.rygel.needlecast.git.ChangedFile("src/Main.kt", " M"),
        io.github.rygel.needlecast.git.ChangedFile("new-file.txt", "??"),
    )
    val fake = FakeGitService(changedFilesList = files)
    panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
    fixture = showInFrame(panel)
    GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }

    fixture.button("toggle-commit").click()
    robot.waitForIdle()
    Thread.sleep(200)   // allow refreshChangedFiles SwingWorker to complete
    robot.waitForIdle()

    val fileList = robot.finder().findByName(panel, "changed-files-list", JList::class.java, true)
    val count = GuiActionRunner.execute(object : GuiQuery<Int>() {
        override fun executeInEDT(): Int = fileList.model.size
    })
    assertEquals(2, count)
}

@Test
fun `commit button stages checked files and commits with the typed message`() {
    val files = listOf(io.github.rygel.needlecast.git.ChangedFile("src/Main.kt", " M"))
    val fake = FakeGitService(changedFilesList = files)
    panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
    fixture = showInFrame(panel)
    GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }

    fixture.button("toggle-commit").click()
    robot.waitForIdle()
    Thread.sleep(200)
    robot.waitForIdle()

    fixture.textBox("commit-message").enterText("my commit message")
    fixture.button("btn-commit-ok").click()
    Thread.sleep(200)
    robot.waitForIdle()

    assertEquals(listOf("src/Main.kt"), fake.stagedFiles)
    assertEquals("my commit message", fake.committedMessage)
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run (requires Podman/Xvfb): `mvn verify -Ptest-desktop -pl needlecast-desktop -q`

Expected: the two new tests fail — `changed-files-list` and `commit-message` components not found (commit card is still an empty `JPanel()`).

- [ ] **Step 3: Implement the commit card in `GitLogPanel.kt`**

Make the following four changes to `GitLogPanel.kt`:

**3a.** In `init`, replace the line:
```kotlin
cardPanel.add(JPanel(), "commit")   // placeholder — replaced in Task 4
```
with:
```kotlin
cardPanel.add(buildCommitCard(), "commit")
```

**3b.** In `init`, replace the line:
```kotlin
commitToggle.addActionListener { cardLayout.show(cardPanel, "commit") }
```
with:
```kotlin
commitToggle.addActionListener { refreshChangedFiles(); cardLayout.show(cardPanel, "commit") }
```

**3c.** Add the `buildCommitCard()`, `refreshChangedFiles()`, and `onCommitClicked()` methods to the class (before the `showCommit` method):

```kotlin
private fun buildCommitCard(): JPanel {
    fileList.setCellRenderer(FileCheckboxRenderer())
    fileList.addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: java.awt.event.MouseEvent) {
            val index = fileList.locationToIndex(e.point)
            if (index < 0 || index >= fileListModel.size) return
            val file = fileListModel.getElementAt(index)
            if (file.path in checkedFiles) checkedFiles.remove(file.path)
            else checkedFiles.add(file.path)
            fileList.repaint()
        }
    })

    commitButton.addActionListener { onCommitClicked() }
    cancelButton.addActionListener {
        commitMessageField.text = ""
        commitMessageField.border = null
        logToggle.isSelected = true
        cardLayout.show(cardPanel, "log")
    }

    val bottomPanel = JPanel(BorderLayout(4, 0)).apply {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        add(commitMessageField, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(commitButton); add(cancelButton)
        }, BorderLayout.EAST)
    }

    return JPanel(BorderLayout()).apply {
        add(JScrollPane(fileList), BorderLayout.CENTER)
        add(bottomPanel,           BorderLayout.SOUTH)
    }
}

private fun refreshChangedFiles() {
    val path = currentPath ?: run { fileListModel.clear(); return }
    object : SwingWorker<List<ChangedFile>, Void>() {
        override fun doInBackground(): List<ChangedFile> = gitService.changedFiles(path)
        override fun done() {
            val files = try { get() } catch (_: Exception) { return }
            fileListModel.clear()
            checkedFiles.clear()
            files.forEach {
                fileListModel.addElement(it)
                checkedFiles.add(it.path)   // all checked by default
            }
        }
    }.execute()
}

private fun onCommitClicked() {
    val message = commitMessageField.text.trim()
    if (message.isEmpty()) {
        commitMessageField.border = BorderFactory.createLineBorder(Color.RED)
        return
    }
    commitMessageField.border = null
    val path = currentPath ?: return
    val filesToStage = (0 until fileListModel.size)
        .map { fileListModel.getElementAt(it) }
        .filter { it.path in checkedFiles }
        .map { it.path }

    commitButton.isEnabled = false
    cancelButton.isEnabled = false

    object : SwingWorker<Unit, Void>() {
        override fun doInBackground() {
            gitService.stage(path, filesToStage)
            gitService.commit(path, message)
        }
        override fun done() {
            commitButton.isEnabled = true
            cancelButton.isEnabled = true
            try {
                get()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this@GitLogPanel,
                    e.cause?.message ?: e.message,
                    "Commit failed",
                    JOptionPane.ERROR_MESSAGE,
                )
                return
            }
            commitMessageField.text = ""
            logToggle.isSelected = true
            cardLayout.show(cardPanel, "log")
            loadProject(currentPath)
        }
    }.execute()
}
```

**3d.** Add the `FileCheckboxRenderer` inner class after `CommitCellRenderer`:

```kotlin
private inner class FileCheckboxRenderer : ListCellRenderer<ChangedFile> {
    private val checkBox = JCheckBox().apply { isOpaque = true }

    override fun getListCellRendererComponent(
        list: JList<out ChangedFile>, value: ChangedFile?,
        index: Int, isSelected: Boolean, cellHasFocus: Boolean,
    ): Component {
        val file = value ?: return checkBox
        val badge = file.statusCode.trim().firstOrNull()?.toString() ?: "?"
        checkBox.text       = "[$badge] ${file.path}"
        checkBox.isSelected = file.path in checkedFiles
        checkBox.background = if (isSelected) list.selectionBackground else list.background
        checkBox.foreground = statusColor(file.statusCode)
        return checkBox
    }

    private fun statusColor(statusCode: String): Color = when {
        statusCode.any { it == 'M' } -> Color(0x4070C0)   // modified — blue
        statusCode.any { it == 'A' } -> Color(0x40A040)   // added — green
        statusCode.any { it == 'D' } -> Color(0xC04040)   // deleted — red
        else                          -> Color(0x888888)   // untracked / other — grey
    }
}
```

- [ ] **Step 4: Run all UI tests — verify they pass**

Run (requires Podman/Xvfb): `mvn verify -Ptest-desktop -pl needlecast-desktop -q`

Expected: `BUILD SUCCESS` — all five tests pass (toolbar, commit-files, commit-button, large-diff, plus the original test).

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt
git commit -m "feat: implement commit card in GitLogPanel"
```

---

### Task 5: `GitLogPanel` — output card and remote operations

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt`
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt`

- [ ] **Step 1: Write failing tests for the output card**

Add to `GitLogPanelUiTest`. Also needs `import org.junit.jupiter.api.Assertions.assertTrue` if not already present.

```kotlin
@Test
fun `clicking Fetch switches to output card and streams git output`() {
    val fake = FakeGitService(streamingLines = listOf("remote: Counting objects: 3", "remote: done."))
    panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
    fixture = showInFrame(panel)
    GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }
    robot.waitForIdle()

    fixture.button("btn-fetch").click()
    Thread.sleep(300)   // allow SwingWorker background + done() to complete
    robot.waitForIdle()

    val area = robot.finder().findByName(panel, "output-area", JTextArea::class.java, true)
    val text = GuiActionRunner.execute(object : GuiQuery<String>() {
        override fun executeInEDT(): String = area.text
    })
    assertTrue(text.contains("remote: Counting objects: 3"), "Expected first streamed line in output area")
    assertTrue(text.contains("remote: done."),               "Expected second streamed line in output area")
    assertTrue(text.contains("✓ Done"),                      "Expected done marker in output area")
    fixture.button("btn-output-close").requireEnabled()
}

@Test
fun `clicking Close on output card returns to log view`() {
    val fake = FakeGitService(streamingLines = emptyList())
    panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
    fixture = showInFrame(panel)
    GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }
    robot.waitForIdle()

    fixture.button("btn-fetch").click()
    Thread.sleep(300)
    robot.waitForIdle()

    fixture.button("btn-output-close").click()
    robot.waitForIdle()

    // After close, log toggle should be selected again and log card visible
    val logToggleSelected = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
        override fun executeInEDT(): Boolean =
            robot.finder().findByName(panel, "toggle-log", javax.swing.JToggleButton::class.java, true).isSelected
    })
    assertTrue(logToggleSelected, "Expected Log toggle to be selected after Close")
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run (requires Podman/Xvfb): `mvn verify -Ptest-desktop -pl needlecast-desktop -q`

Expected: the two new tests fail — Fetch button does nothing (empty action listener), output card is still a blank `JPanel()`.

- [ ] **Step 3: Implement the output card in `GitLogPanel.kt`**

Make the following three changes:

**3a.** In `init`, replace:
```kotlin
cardPanel.add(JPanel(), "output")   // placeholder — replaced in Task 5
```
with:
```kotlin
cardPanel.add(buildOutputCard(), "output")
```

**3b.** In `init`, replace the three empty remote button listeners:
```kotlin
fetchButton.addActionListener  { }   // wired in Task 5
pushButton.addActionListener   { }   // wired in Task 5
pullButton.addActionListener   { }   // wired in Task 5
```
with:
```kotlin
fetchButton.addActionListener { runRemoteOp("Fetch") { dir, cb -> gitService.fetchStreaming(dir, cb) } }
pushButton.addActionListener  { runRemoteOp("Push")  { dir, cb -> gitService.pushStreaming(dir, cb)  } }
pullButton.addActionListener  { runRemoteOp("Pull")  { dir, cb -> gitService.pullStreaming(dir, cb)  } }
```

**3c.** Add `buildOutputCard()`, `runRemoteOp()`, and `setRemoteButtonsEnabled()` methods (before `showCommit()`):

```kotlin
private fun buildOutputCard(): JPanel {
    closeButton.addActionListener {
        logToggle.isSelected = true
        cardLayout.show(cardPanel, "log")
        loadProject(currentPath)
    }
    return JPanel(BorderLayout()).apply {
        add(outputLabel, BorderLayout.NORTH)
        add(JScrollPane(outputArea), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(closeButton) }, BorderLayout.SOUTH)
    }
}

private fun runRemoteOp(label: String, op: (String, (String) -> Unit) -> Int) {
    val path = currentPath ?: return
    outputLabel.text = "$label\u2026"
    outputArea.text  = ""
    closeButton.isEnabled = false
    setRemoteButtonsEnabled(false)
    cardLayout.show(cardPanel, "output")

    object : SwingWorker<Int, String>() {
        override fun doInBackground(): Int = op(path) { line -> publish(line) }
        override fun process(chunks: List<String>) {
            chunks.forEach { outputArea.append("$it\n") }
            outputArea.caretPosition = outputArea.document.length
        }
        override fun done() {
            val exitCode = try { get() } catch (_: Exception) { -1 }
            if (exitCode == 0) {
                outputArea.append("\u2713 Done\n")
                outputLabel.text = "$label \u2014 Done"
            } else {
                outputArea.append("\u2717 Failed (exit $exitCode)\n")
                outputLabel.text = "$label \u2014 Failed"
            }
            closeButton.isEnabled = true
            setRemoteButtonsEnabled(true)
        }
    }.execute()
}

private fun setRemoteButtonsEnabled(enabled: Boolean) {
    fetchButton.isEnabled = enabled
    pushButton.isEnabled  = enabled
    pullButton.isEnabled  = enabled
}
```

- [ ] **Step 4: Run all UI tests — verify they all pass**

Run (requires Podman/Xvfb): `mvn verify -Ptest-desktop -pl needlecast-desktop -q`

Expected: `BUILD SUCCESS` — all seven tests pass.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GitLogPanel.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/GitLogPanelUiTest.kt
git commit -m "feat: implement output card and remote git operations in GitLogPanel"
```

---

## Self-Review

**Spec coverage:**
- ✅ Toolbar with `[Log] [Commit]` toggles and `[Fetch] [Push] [Pull]` action buttons — Task 3
- ✅ Commit view: file checklist with checkboxes, inline message field, Commit/Cancel — Task 4
- ✅ Commit flow: stage checked files → commit → return to log — Task 4
- ✅ Blank message validation (red border) — Task 4 `onCommitClicked`
- ✅ All files checked by default — Task 4 `refreshChangedFiles`
- ✅ Output card: label, streaming text area, Close button — Task 5
- ✅ Remote ops disable buttons while running, re-enable on completion — Task 5
- ✅ Close returns to log card and refreshes log — Task 5
- ✅ Status color coding in file list (M=blue, A=green, D=red, ??=grey) — Task 4 `FileCheckboxRenderer`
- ✅ `changedFiles`, `stage`, `commit` in `GitService`/`ProcessGitService` — Tasks 1-2
- ✅ `fetchStreaming`, `pushStreaming`, `pullStreaming` with streaming output — Tasks 1-2
- ✅ `parseChangedFiles` unit-tested — Task 2

**No placeholder text or TODOs remaining in implementation code.**

**Type consistency:** `ChangedFile` is defined in Task 1 and used identically across Tasks 2, 3, 4. `FakeGitService` defined in Task 1 is used unchanged in Tasks 3, 4, 5. `runRemoteOp` signature `(String, (String) -> Unit) -> Int` matches `fetchStreaming`/`pushStreaming`/`pullStreaming` signatures.
