# IntelliJ-Style Diff Viewer Design

## Overview

Replace the plain `JTextArea` diff display in `GitLogPanel` with a fully custom, IntelliJ-quality diff viewer built entirely in Swing. The viewer provides color-coded diff lines with inline word-level highlighting, side-by-side (two-pane) view with synchronized scrolling, a file tree navigator with stats, gutter line numbers and markers, a minimap-style overview bar, search-within-diff, prev/next change navigation, and click-to-open file integration.

## Approach

**Custom JPanel rendering (Approach A)** — pure Swing with `JTextPane` + `StyledDocument`, `JTree`, and custom `JComponent`s. No new dependencies. FlatLaf theming works automatically.

## Features

- Side-by-side view (default) with toggle to unified (single-pane)
- Color-coded lines: green for added, red for removed, with inline word-level diff highlighting
- Line numbers on both sides with proper old/new numbering
- Gutter markers (colored stripe per line showing added/removed)
- File tree panel with collapsible list, +/- line counts, click-to-navigate
- Minimap-style overview bar showing all changes at a glance, click-to-jump
- Search within diff (Ctrl+F) with match highlighting and prev/next
- Prev/Next change navigation buttons
- Double-click file to open in EditorPanel (Jump to Source)
- Synchronized scrolling between side-by-side panes
- FlatLaf theme-aware colors (all 30+ bundled themes)

## Architecture

### Package: `io.github.rygel.needlecast.ui.diff`

All new diff viewer components live in a dedicated `diff` sub-package under `ui`.

### Component Structure

```
DiffViewerPanel (top-level container)
├── Toolbar
│   ├── Side-by-side / Unified toggle
│   ├── Prev/Next change buttons
│   └── Ctrl+F triggers DiffSearchBar
├── DiffFileTree (left, JTree)
│   └── Changed files with +/- stats
├── DiffContentPanel (center)
│   ├── Side-by-side mode: two DiffEditorPanes in JSplitPane
│   └── Unified mode: single DiffEditorPane
├── DiffOverviewBar (right, custom JComponent)
└── DiffSearchBar (collapsible, below toolbar)
```

### Components

**DiffViewerPanel** — Top-level `JPanel` with `BorderLayout`. Contains the toolbar (NORTH), a horizontal split of file tree + content + overview bar (CENTER), and the collapsible search bar. Accepts a `DiffResult` via `display(diffResult)`. Manages view mode (side-by-side vs unified). Holds a reference to a `fileOpener: ((String) -> Unit)?` callback for click-to-open.

**DiffContentPanel** — Holds the `DiffEditorPane`(s). Manages:
- Switching between side-by-side and unified layouts
- `SynchronizedScrollListener` for coordinated scrolling in side-by-side mode
- Alignment padding (blank lines inserted on the shorter side to keep changed lines aligned)

**DiffEditorPane** — A `JTextPane` with `StyledDocument`. Renders:
- Color-coded line backgrounds via `Highlighter.HighlightPainter`
- Gutter stripe (thin colored bar per line)
- Line numbers via a custom gutter `JComponent` painted to the left
- Inline word-level diff highlighting via character-level attributes
- Search match highlighting via `Highlighter` API

**DiffFileTree** — A `JTree` with custom `TreeCellRenderer`. Displays:
- File name (last path segment) as primary text
- Parent directory in gray secondary text
- `+N` in green, `-N` in red as a badge on the right
- Click navigates to that file's diff section
- Double-click fires the `fileOpener` callback to open in `EditorPanel`

**DiffOverviewBar** — Custom `JComponent` with `paintComponent`. Renders:
- Green/red rectangles for each change block, proportional to their size
- Semi-transparent viewport rectangle at the current scroll position
- Click-to-jump: converts click y-position to scroll position

**DiffSearchBar** — Collapsible panel (appears on Ctrl+F, hides on Escape). Contains:
- Search field with match count (`3 of 17`)
- Previous/Next buttons
- Close button
- Highlights matches in `DiffEditorPane` via `Highlighter` API with yellow background

## Data Model

```
DiffResult
├── files: List<FileDiff>
│   ├── filePath: String
│   ├── oldPath: String? (for renames)
│   ├── additions: Int
│   ├── deletions: Int
│   ├── binary: Boolean
│   └── hunks: List<Hunk>
│       ├── oldStart: Int, oldCount: Int
│       ├── newStart: Int, newCount: Int
│       └── lines: List<DiffLine>
│           ├── type: CONTEXT | ADDED | REMOVED
│           ├── oldLineNum: Int?
│           ├── newLineNum: Int?
│           ├── content: String
│           └── wordDiffs: List<WordDiff>
│               ├── type: ADDED | REMOVED
│               └── text: String
└── stats: DiffStats (total additions/deletions)
```

## Diff Parsing

**DiffParser** — Parses raw `git show --stat -p --no-color` output (what `GitService.show()` returns):
- Lines before the first `diff --git` are skipped (commit header, stat summary)
- `diff --git a/... b/...` headers → new `FileDiff`
- `--- a/path` and `+++ b/path` → file paths (handles renames)
- `@@ -oldStart,oldCount +newStart,newCount @@` → new `Hunk`
- Lines starting with `+`/`-`/` ` → `DiffLine` entries
- `Binary files differ` → `FileDiff.binary = true`
- Stat summary line is ignored (computed from parsed data)

**GitService change:** `ProcessGitService.show()` adds `--no-color` flag to prevent ANSI escape codes in the output.

**WordDiffCalculator** — For consecutive REMOVED → ADDED line pairs in a hunk:
- Tokenize both lines by whitespace and punctuation
- Run Myers' diff algorithm on tokens
- Mark changed spans as `WordDiff` ranges on both lines

## Rendering Details

### Line Colors

| Line type | Background | Gutter stripe | Inline word highlight |
|-----------|-----------|---------------|----------------------|
| ADDED | `rgba(70, 180, 70, 0.12)` | `#4caf50` | `rgba(70, 180, 70, 0.35)`, fg `#6a8759` |
| REMOVED | `rgba(255, 70, 70, 0.12)` | `#c75b5b` | `rgba(255, 70, 70, 0.35)`, fg `#c75b5b` |
| CONTEXT | normal | none | n/a |

All colors use `UIManager.getColor()` lookups where possible with fallback defaults. FlatLaf's `@background`, `@foreground`, `@selectionBackground` are used so the viewer adapts to all bundled themes.

### Line Number Gutter

Custom `JComponent` painted to the left of each `DiffEditorPane`:
- Right-aligned monospace text
- Colors: `#606060` for context lines, line numbers skip for added/removed depending on side
- Width auto-sizes to maximum line count

### Synchronized Scrolling

`SynchronizedScrollListener` on both `JScrollPane` vertical scrollbars:
- Updates the other pane's scroll position proportionally
- Prevents re-entrant updates with `isSyncing` flag
- Alignment gaps handled by inserting blank padding lines on the shorter side

### Overview Bar

`paintComponent` queries the `DiffResult` to compute y-positions for each change block relative to total line count. Viewport rectangle position is computed from current scroll position. `MouseListener` converts click y-position to scroll position.

## Integration with GitLogPanel

### What Changes

The `showCommit()` method changes from:
```
raw text → TextChunker.setTextChunked(diffArea, rawText)
```
To:
```
raw text → DiffParser.parse(rawText) → diffViewerPanel.display(diffResult)
```

The `DiffViewerPanel` replaces the `JScrollPane(diffArea)` in the `JSplitPane`. The commit list (top pane) stays unchanged.

### File Opener Callback

`MainWindow` passes a `fileOpener` callback to `DiffViewerPanel` that calls the existing `ExplorerPanel.openFile(path)` mechanism. `DiffFileTree` fires the callback on double-click.

### Large Diffs

The existing 400K character truncation limit remains. For very large files within a diff, `JTextPane` handles scrolling naturally via `JScrollPane`.

### Binary Files

Shown in the file tree with a "(binary)" label in the content area. No attempt to render binary content.

### Empty Diffs

Show "No changes" message in the content area.

### Keyboard Shortcuts

- Ctrl+F: Open search bar (registered via `JComponent.registerKeyboardAction` on `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`)
- Escape: Close search bar
- Enter: Next match (when search bar is open)

### Prev/Next Change Navigation

Toolbar ◀/▶ buttons jump to the previous/next hunk boundary. Target line numbers are computed from the `DiffResult`'s hunk positions. Both panes scroll.

### Performance

- Diff parsing runs on the `SwingWorker` background thread (same as current)
- `display()` is called on EDT with the pre-parsed `DiffResult`
- `StyledDocument` is built in a single batch insert to avoid repaint storms

## Files Changed

| File | Change |
|------|--------|
| `ui/diff/DiffViewerPanel.kt` | New — top-level diff viewer container |
| `ui/diff/DiffContentPanel.kt` | New — side-by-side/unified content area |
| `ui/diff/DiffEditorPane.kt` | New — colored diff text pane with gutter |
| `ui/diff/DiffLineNumberGutter.kt` | New — line number gutter component |
| `ui/diff/DiffFileTree.kt` | New — file tree navigator |
| `ui/diff/DiffOverviewBar.kt` | New — minimap overview bar |
| `ui/diff/DiffSearchBar.kt` | New — search-within-diff bar |
| `ui/diff/DiffParser.kt` | New — parses raw git diff output |
| `ui/diff/DiffModel.kt` | New — data classes (DiffResult, FileDiff, Hunk, DiffLine, WordDiff) |
| `ui/diff/WordDiffCalculator.kt` | New — Myers' diff for word-level changes |
| `ui/diff/SynchronizedScrollListener.kt` | New — coordinates scrolling between panes |
| `ui/diff/DiffColors.kt` | New — theme-aware color constants |
| `ui/GitLogPanel.kt` | Modified — replaces JTextArea with DiffViewerPanel |
| `ui/MainWindow.kt` | Modified — passes fileOpener callback |
| `git/GitService.kt` | No changes needed |
| `git/ProcessGitService.kt` | Modified — adds `--no-color` flag to `show()` command |
