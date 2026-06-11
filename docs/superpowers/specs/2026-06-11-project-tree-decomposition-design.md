# Cycle 20: ProjectTreePanel Decomposition — Scanning + Filter

## Context

ProjectTreePanel (972 lines) has had four helpers extracted (ContextMenu, DndHandler, Dialogs, CellRenderer) but still owns a complex scanning state machine and duplicated filter logic. This cycle extracts those two areas into testable, focused classes.

## Scope

Two extractions only:
1. **ProjectTreeScanCoordinator** — all scanning infrastructure
2. **ProjectTreeFilter** consolidation — enhance existing object, delete inline duplication

## Extraction 1: ProjectTreeScanCoordinator

**File:** `ui/ProjectTreeScanCoordinator.kt` (~140 lines)

### Owned state

| Field | Type | Moved from |
|-------|------|-----------|
| `scanExecutor` | `ExecutorService` (2-thread pool) | Panel field |
| `scanQueue` | `ConcurrentLinkedQueue<Pair<ProjectDirectory, DetectedProject>>` | Panel field |
| `scanApplyTimer` | `Timer` (25ms drain) | Panel field |
| `scanApplyPending` | `AtomicBoolean` | Panel field |
| `gitStatusCache` | `MutableMap<String, GitStatus>` | Panel field |
| `buildFileWatcher` | `BuildFileWatcher` | Panel field |
| `blinkOn` | `Boolean` | Panel field |
| `blinkTimer` | `Timer` (600ms toggle) | Panel field |
| `repaintTimer` | `Timer` (50ms coalesce) | Panel field |

### Constructor

```kotlin
class ProjectTreeScanCoordinator(
    val ctx: AppContext,
    val onScanResult: (ProjectDirectory, DetectedProject) -> Unit,
    val onGitStatusReady: (String, GitStatus) -> Unit,
    val requestRepaint: () -> Unit,
)
```

No reference to ProjectTreePanel. All interaction via callbacks.

### Methods

- `scanProject(dir: ProjectDirectory)` — submits scan to executor, enqueues result, schedules drain
- `rescheduleProjectScan(path: String, dir: ProjectDirectory)` — for build-file-watcher triggers
- `drainScanQueue(maxPerTick: Int)` — processes up to `maxPerTick` results, calls `onScanResult` for each, triggers git fetch and build-file-watcher registration
- `fetchGitStatus(path: String)` — SwingWorker, calls `onGitStatusReady` callback
- `updateAgentStatus(path: String, status: AgentStatus)` — manages blink timer based on whether any agent is THINKING
- `clearAll()` — clears gitStatusCache, stops timers, called on panel reload
- `dispose()` — shuts down executor, stops all timers

### Callback wiring in ProjectTreePanel

The panel's `drainScanQueue` override:
1. Calls `coordinator.drainScanQueue(10)` — the coordinator calls `onScanResult(dir, result)` per item
2. Panel's `onScanResult` lambda writes to `scanResults[dir.path] = result`, handles pending-select and selection-update logic

The panel's `scanProject()` delegates to `coordinator.scanProject(dir)`.

The panel's `gitStatusCache` accessor delegates to `coordinator.gitStatusCache`.

### What stays in ProjectTreePanel

- `scanResults: MutableMap<String, DetectedProject>` — part of `ProjectTreePanelAccess` interface, written by `onScanResult` callback
- `pendingSelectPath` — selection concern, not scanning
- Selection-update logic inside drain (checking if scanned path matches current selection)

## Extraction 2: ProjectTreeFilter consolidation

**File:** Enhance existing `ui/ProjectTreeFilter.kt` (44 → ~80 lines)

### FilterState data class

```kotlin
data class FilterState(
    val lastFilter: String = "",
    val lastActiveOnly: Boolean = false,
    val cachedEntries: List<ProjectTreeEntry>? = null,
)
```

Tracked by the panel. Moved from individual panel fields (`lastFilter`, `lastActiveOnly`, `cachedAllEntries`).

### Enhanced filterTree()

Current signature only handles text filtering. Add `activeOnly` + `activePaths`:

```kotlin
fun filterTree(
    entries: List<ProjectTreeEntry>,
    textFilter: String,
    activeOnly: Boolean,
    activePaths: Set<String>,
): List<ProjectTreeEntry>
```

The recursive filtering logic merges the existing `matches()` with active-path checking — same logic currently in the panel's inline `filterEntry()` at line 838.

### What is deleted from ProjectTreePanel

- `lastFilter` field
- `lastActiveOnly` field (renamed from `lastActiveOnly`)
- `cachedAllEntries` field
- `filterEntry()` method (lines 838-859)
- `pendingFilterText` stays (it's the debounce buffer, set by the filter field listener)

### What changes in ProjectTreePanel.doApplyFilter()

1. Check `filterState.lastFilter == filter && filterState.lastActiveOnly == activeOnly` for no-op
2. Get source entries from `filterState.cachedEntries ?: migrateOrLoad()`; update cache
3. Call `ProjectTreeFilter.filterTree(source, filter, activeOnly, activePaths)`
4. Rebuild tree nodes from result
5. Update `filterState` with new lastFilter/lastActiveOnly

## Impact

| Metric | Before | After |
|--------|--------|-------|
| ProjectTreePanel lines | 972 | ~820 |
| New file (ScanCoordinator) | 0 | ~140 |
| Enhanced file (Filter) | 44 | ~80 |
| Deleted code | — | ~150 (inline state + filterEntry) |

## Testing

### ProjectTreeScanCoordinatorTest (~8-10 tests)

- Queue drains results and calls `onScanResult` for each
- Respects `maxPerTick` limit
- Fetches git status after successful scan
- Registers build-file-watcher for non-failed scans
- Blink timer starts when any agent is THINKING, stops when none
- `clearAll()` clears caches
- `dispose()` shuts down executor
- Scan failure produces `scanFailed=true` result

### ProjectTreeFilterTest (~4-5 new tests, 12 existing)

- `activeOnly=true` filters to only active paths
- `activeOnly=true` with text filter combines both predicates
- `FilterState` no-op detection (unchanged filter + activeOnly)
- Empty filter + activeOnly=false returns original entries
- Folder with no matching children is excluded

## Files changed

| File | Action |
|------|--------|
| `ui/ProjectTreeScanCoordinator.kt` | New |
| `ui/ProjectTreeFilter.kt` | Enhanced (add FilterState, activeOnly) |
| `ui/ProjectTreePanel.kt` | Modified (delegate to coordinator + filter) |
| `ui/ProjectTreePanelAccess.kt` | No change needed |
| `test/.../ProjectTreeScanCoordinatorTest.kt` | New |
| `test/.../ProjectTreeFilterTest.kt` | Enhanced |

## Constraints

- No changes to `ProjectTreePanelAccess` interface unless `gitStatusCache` must move (check during implementation)
- No changes to existing tests except `ProjectTreeFilterTest` additions
- Follow callback pattern from C18 (ExplorerCallbacks) and C19 (CommandOverrideManager)
- No comments in code (project convention)
