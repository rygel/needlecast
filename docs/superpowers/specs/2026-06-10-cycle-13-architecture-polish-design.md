# Cycle 13: Error Handling, Deduplication, Tests, Decomposition — Design Spec

**Date:** 2026-06-10
**Cycle:** 13

---

## 1. Extract DesktopUtils — Kill 3x Duplication

### Problem

The "open in file manager" and "reveal in file manager" logic is duplicated across three locations:
- `ExplorerPanel.openInFileManager()` (lines 371-384)
- `ExplorerPanel.revealInFileManager()` (lines 386-395)
- `ProjectTreePanel.openInFileManager()` (lines 1310-1325)

All three implement the same platform-branching pattern with `Desktop.getDesktop().open()`, `explorer.exe`, `open`, `xdg-open`.

### Fix

Create `io.github.rygel.needlecast.ui.util.DesktopUtils` with two static methods:
- `openInFileManager(file: File)` — opens a directory in the OS file manager
- `revealInFileManager(file: File)` — selects a file in the OS file manager (Windows: `explorer /select,`, macOS: `open -R`, Linux: open parent)

Replace all three call sites with `DesktopUtils.openInFileManager()` / `DesktopUtils.revealInFileManager()`.

Also extract the platform-conditional label string (`"Open in Explorer"` / `"Open in Finder"` / `"Open in File Manager"`) as a constant `openInFileManagerLabel` / `revealInFileManagerLabel`.

---

## 2. Fix Silent Catch Blocks

### Problem

~70 instances of `catch (_: Exception) {}` across the main source tree. Errors are completely invisible — users never know why operations fail, and developers can't debug from logs.

### Fix

Replace every silent `catch (_: Exception) {}` with a logger call. The pattern:

```kotlin
} catch (e: Exception) {
    logger.warn("Failed to <description>", e)
}
```

For files that don't have a logger, add one: `private val logger = org.slf4j.LoggerFactory.getLogger(ClassName::class.java)`.

For cases where the exception is truly expected/benign (e.g., `BadLocationException` in text rendering), use `logger.debug()` instead of `logger.warn()`.

Priority files by catch count:
1. `ExplorerPanel.kt` (8 catches)
2. `DirectoryPanel.kt` (7 catches)
3. `SkillLibraryStore.kt` (6 catches)
4. `GitLogPanel.kt` (5 catches)
5. `CommandPanel.kt` (4 catches)
6. `BuildFileWatcher.kt` (4 catches)
7. All remaining files with 1-3 catches

---

## 3. Add Tests for SearchPanel + LogParser

### Problem

`SearchPanel.kt` (831 lines) contains a complete file search engine — regex matching, file walking, result collection — with zero tests. `LogParser.kt` (134 lines) contains pure parsing logic with zero tests. Both are pure algorithmic code trivially testable without Swing.

### Fix

**SearchPanel:** Extract the search engine logic (file walking, regex matching, result collection) from the Swing UI into a testable `FileSearchEngine` class. The engine takes a root directory, pattern, and file filter, and returns a list of results. The UI wraps it. Add unit tests for:
- Regex matching (literal, case-insensitive, regex mode)
- File extension filtering
- Result collection and ranking
- Encoding handling

**LogParser:** Already a standalone class. Add unit tests for:
- Standard log line parsing
- Multi-line stacktrace handling
- Timestamp extraction
- Log level detection

---

## 4. ProjectTreePanel Decomposition

### Problem

`ProjectTreePanel.kt` at 1518 lines is the largest file in the codebase. It mixes:
- Tree model management (insert/remove/update nodes)
- Context menu construction (~220 lines)
- Inline dialog builders (shell settings, env vars, script dirs, colors)
- Filter logic (text filter + activeOnly)
- Scan orchestration (trigger scan, handle results)
- Persistence (save/restore tree state from config)

### Fix

Extract three focused classes:

**4a. `ProjectTreeContextMenu`** (~250 lines)
- Builds the right-click context menu for project entries
- Handles: open terminal, activate/deactivate, edit color, edit tags, edit env/shell/scriptDirs, open in file manager, scan, remove
- Uses `DesktopUtils` for "open in file manager"
- Callbacks back to ProjectTreePanel for mutations

**4b. `ProjectTreeDialogs`** (~150 lines)
- Static dialog builders: `editShellSettings()`, `editEnvVars()`, `editScriptDirs()`, `editTags()`, `pickColor()`
- Each returns the edited value or null (cancelled)
- Removes inline dialog construction from ProjectTreePanel

**4c. Color presets constant** — extract the duplicated `listOf("Red" to "#E53935", ...)` to a companion `val COLOR_PRESETS` on `ProjectTreePanel` (or in the dialogs class).

After extraction, `ProjectTreePanel` should be ~900-1000 lines — still substantial but focused on tree model, scanning, and filtering.

---

## Files

### New files:
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/util/DesktopUtils.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/search/FileSearchEngine.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/tree/ProjectTreeContextMenu.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/tree/ProjectTreeDialogs.kt`
- `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/search/FileSearchEngineTest.kt`
- `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/logviewer/LogParserTest.kt`

### Modified files:
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SearchPanel.kt`
- All files with silent catch blocks (~15 files)

## Out of Scope

- ExplorerPanel decomposition (deferred)
- DirectoryPanel / ProjectTreePanel overlap (requires design decision about DirectoryPanel's future)
- Magic timer intervals
- ShellDetector hardcoded paths
