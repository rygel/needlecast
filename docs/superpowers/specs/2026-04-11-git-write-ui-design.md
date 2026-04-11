# Git Write UI — Design Spec

## Goal

Add commit/fetch/push/pull operations to the existing `GitLogPanel` using a toggle-view toolbar, keeping everything in one dockable panel with no new top-level panels.

## Architecture

`GitLogPanel` stays a single dockable panel. Its content area becomes a `CardLayout` with three named cards: `"log"` (existing commit log, unchanged), `"commit"` (staging area + message field), and `"output"` (streaming text area for remote operations).

A toolbar above the card area has two groups: toggle buttons `[Log] [Commit]` in a `ButtonGroup` (switching cards), and action buttons `[Fetch] [Push] [Pull]` that switch to the output card, run the git operation, then restore the log card on completion.

`GitService` interface gains five new methods; `ProcessGitService` implements them. Commit/stage use the existing `runGit()` helper. Fetch/Push/Pull use a new `runGitStreaming()` helper that reads `ProcessBuilder` output line by line via a callback.

## Data Model

New type added to the `git` package:

```kotlin
data class ChangedFile(
    val path: String,
    val statusCode: String,  // two-char porcelain code: "M ", " M", "??", "A ", "D ", etc.
)
```

`GitService` gains five new methods:

```kotlin
fun changedFiles(dir: String): List<ChangedFile>   // git status --porcelain
fun stage(dir: String, files: List<String>)         // git add -- <files>
fun commit(dir: String, message: String)            // git commit -m <message>
fun fetchStreaming(dir: String, onLine: (String) -> Unit)
fun pushStreaming(dir: String, onLine: (String) -> Unit)
fun pullStreaming(dir: String, onLine: (String) -> Unit)
```

All non-streaming methods throw on non-zero exit. The streaming methods call `onLine` from the worker thread; callers dispatch to EDT via `SwingUtilities.invokeLater`.

## Toolbar

A `JPanel(FlowLayout(LEFT))` pinned to `BorderLayout.NORTH` in `GitLogPanel`:

- `JToggleButton("Log")` — selected by default
- `JToggleButton("Commit")` — switches to commit card, triggers file list refresh
- Both in a `ButtonGroup`
- `JButton("Fetch")`, `JButton("Push")`, `JButton("Pull")` — action buttons

## Commit View

The commit card is a `JPanel(BorderLayout)`:

- **Center**: `JScrollPane` wrapping a `JList<ChangedFile>` with a custom `CheckBoxListCellRenderer`. Each row shows a checkbox, status badge (`M`/`A`/`D`/`?`), and the file path. Status colors: modified = blue, added = green, deleted = red, untracked = grey. All files checked by default.
- **South**: a thin `JPanel(BorderLayout)` with:
  - A `JTextField` (placeholder `"Commit message…"`) filling the center
  - `[Commit]` and `[Cancel]` buttons on the right

On **Commit** click:
1. Validate message is non-empty (highlight field red if blank)
2. Collect checked `ChangedFile` paths
3. `gitService.stage(projectPath, checkedFiles)` via `SwingWorker`
4. On success: `gitService.commit(projectPath, message)`
5. On success: clear message field, switch to Log card, reload log + file list
6. On error: show error in a `JOptionPane`

On **Cancel**: clear field, switch back to Log card.

The file list is refreshed every time the Commit card is shown.

## Output View

The output card is a `JPanel(BorderLayout)`:

- **North**: `JLabel` showing the operation name (e.g., `"Fetching…"`)
- **Center**: `JScrollPane` wrapping a non-editable `JTextArea` (monospace font), auto-scrolls to bottom
- **South**: `[Close]` button — disabled while operation runs, enabled on completion

Flow for Fetch/Push/Pull:
1. Switch to output card, set label, clear text area, disable `[Close]`
2. Disable all three action buttons to prevent concurrent ops
3. Launch `SwingWorker` calling `gitService.fetchStreaming()` (or push/pull); each line appended via `SwingUtilities.invokeLater`
4. On `done()`: append `"✓ Done"` or `"✗ Failed (exit N)"`, re-enable `[Close]` and action buttons
5. `[Close]` switches back to Log card and refreshes the log

## ProcessGitService Additions

```kotlin
// Streaming helper — merges stdout+stderr, calls onLine per line from a background thread
private fun runGitStreaming(dir: String, args: List<String>, onLine: (String) -> Unit)

fun changedFiles(dir: String): List<ChangedFile> {
    val raw = runGit(dir, "status", "--porcelain") ?: return emptyList()
    return raw.lines().filter { it.length >= 3 }.map {
        ChangedFile(path = it.substring(3), statusCode = it.substring(0, 2))
    }
}

fun stage(dir: String, files: List<String>) {
    runGit(dir, "add", "--", *files.toTypedArray())
}

fun commit(dir: String, message: String) {
    runGit(dir, "commit", "-m", message)
}

fun fetchStreaming(dir: String, onLine: (String) -> Unit) =
    runGitStreaming(dir, listOf("fetch"), onLine)

fun pushStreaming(dir: String, onLine: (String) -> Unit) =
    runGitStreaming(dir, listOf("push"), onLine)

fun pullStreaming(dir: String, onLine: (String) -> Unit) =
    runGitStreaming(dir, listOf("pull"), onLine)
```

## File Structure

```
needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/
  git/
    ChangedFile.kt                  — new data class
    GitService.kt                   — add 5 new method signatures
    ProcessGitService.kt            — implement new methods + runGitStreaming
  ui/
    GitLogPanel.kt                  — add toolbar, commit card, output card
```

No new panels, no MainWindow changes.

## What Is NOT in Scope

- Authentication / credential helpers (relies on system git config / ssh-agent)
- Branch creation, checkout, merge, rebase
- Diff viewer for staged/unstaged files
- Amend, stash, cherry-pick
- Any remote management (add/remove remotes)
