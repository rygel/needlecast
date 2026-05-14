# Diff Viewer Separation Design

## Problem
The DiffViewerPanel is buried inside GitLogPanel's JSplitPane, sharing a tab with Commands in the right rail. The diff viewer is too cramped and hard to find.

## Solution
Extract DiffViewerPanel into its own dockable panel at the bottom of the window, spanning full width. GitLogPanel keeps the commit list, staging card, and remote output card.

## Layout Before
```
+------------------+--------------------+----------------+
| Projects|Explorer| Terminal | Editor  |Cmds|GitLog(ALL)|
+------------------+--------------------+----------------+
```
GitLogPanel contains: commit list (top) + diff viewer (bottom) in a JSplitPane, plus commit staging and remote ops in cards.

## Layout After
```
+------------------+--------------------+----------------+
| Projects|Explorer| Terminal | Editor  |Cmds|GitLog     |
|                  |                    |   (list+commit)|
+------------------+--------------------+----------------+
|               Diff Viewer (bottom panel, full width)    |
+---------------------------------------------------------+
```

## Changes

### 1. New DiffDockable
- Wrap `DiffViewerPanel` in a `DockablePanel(id="diff-viewer", title="Diff")`
- Register with ModernDocking
- Dock SOUTH of the terminal pane with ~25% height proportion
- Toggle from Windows > Panels menu

### 2. GitLogPanel simplification
- Remove the `DiffViewerPanel` field and the vertical JSplitPane from the log card
- The log card becomes just `JScrollPane(logList)` filling the whole panel
- Add a callback `onCommitSelected: ((DiffResult) -> Unit)?` that publishes parsed diff results to external consumers
- Keep commit staging card and remote output card unchanged

### 3. MainWindow wiring
- Instantiate `DiffViewerPanel` separately (not inside GitLogPanel)
- Create `diffDockable = DockablePanel(diffViewer, "diff-viewer", "Diff")`
- Register and dock it SOUTH of terminal
- Wire `GitLogPanel.onCommitSelected` to call `diffViewer.display(result)`
- Remove old `gitLogDockable`'s internal diff viewer reference
- Add "Diff" checkbox to Windows > Panels menu
- Update `setupDefaultDockingLayout()` to place diff viewer at the bottom

### 4. Data flow
- User selects commit in GitLogPanel
- `showCommit()` parses diff via DiffParser in background SwingWorker
- On completion, calls `onCommitSelected?.invoke(result)`
- MainWindow receives it and calls `diffViewer.display(result)`
- If diff dockable is not visible, MainWindow auto-shows it

### 5. File opener callback
- `DiffViewerPanel` still accepts `fileOpener: ((String) -> Unit)?`
- MainWindow passes the same lambda that opens files in ExplorerPanel
- This is wired at the MainWindow level, not through GitLogPanel

## Files Modified
- `GitLogPanel.kt` — remove DiffViewerPanel, add onCommitSelected callback, simplify log card layout
- `MainWindow.kt` — instantiate DiffViewerPanel separately, create diffDockable, wire callbacks, update docking layout
- `GitLogPanelUiTest.kt` — update tests for new GitLogPanel structure (no internal diff viewer)
- `DiffViewerE2ETest.kt` — update to work with new callback-based architecture

## Files Unchanged
- All files in `ui/diff/` package (DiffViewerPanel, DiffContentPanel, DiffEditorPane, etc.) remain unchanged
- `DockablePanel.kt` unchanged
- `DiffColors.kt` unchanged
