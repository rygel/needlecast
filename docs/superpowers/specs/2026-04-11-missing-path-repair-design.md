# Missing-Path Repair via Drag-and-Drop — Design Spec

**Date:** 2026-04-11  
**Status:** Approved

---

## Overview

When a project's stored directory no longer exists, Needlecast marks it with a red ⚠ icon. This feature lets the user repair the broken path by dragging the new directory (from Finder/Explorer) and dropping it anywhere on the project tree. If the dropped directory's final name matches a missing project's final name, a confirmation dialog fires — one per match — and accepted ones update the project's path in place rather than adding a new entry.

---

## User-Facing Behaviour

- An external directory drop (onto any part of the tree — a folder, a project node, or between rows) is pre-scanned before any inserts happen.
- For each dropped directory, if its final path segment matches the final path segment of a **missing** project anywhere in the tree, a modal confirmation dialog is shown:

  ```
  ┌──────────────────────────────────────────────┐
  │  Replace missing project path?               │
  │                                              │
  │  Project:  myapp                             │
  │  Old path: /Users/alex/work/old/myapp        │
  │  New path: /Volumes/usb/myapp                │
  │                                              │
  │              [Replace]   [Add as new project]│
  └──────────────────────────────────────────────┘
  ```

- **Replace** — the missing project's `path` is updated to the dropped directory's absolute path. The ⚠ icon clears. A background scan is triggered. The dropped directory is **not** inserted as a new entry.
- **Add as new project** — the dropped directory proceeds through the normal insert flow (appended to the target folder). The missing project is left as-is.
- Dialogs fire sequentially in the order the directories appear in the drop payload. All repairs and inserts are batched; config is saved once after all dialogs are resolved.
- If two missing projects share the same final directory name, the first one encountered in depth-first tree order is the candidate for repair.
- Name matching is **case-insensitive on Windows** (`IS_WINDOWS == true`), **case-sensitive on macOS/Linux**, consistent with how each OS's filesystem treats directory names.

---

## Architecture

### Changed files

| File | Change |
|---|---|
| `ProjectTreePanel.kt` | Add `findMissingMatch()`, `confirmRepairPath()`. Modify `importExternal()`. |
| `ProjectTreePanelExternalDropUiTest.kt` | Add unit-style tests for `findMissingMatch()` |

No new files needed.

### New functions on `ProjectTreePanel`

#### `internal fun findMissingMatch(droppedName: String): DefaultMutableTreeNode?`

Walks the tree model depth-first. For each node whose `userObject` is a `ProjectTreeEntry.Project` **and** whose path is in `missingPaths`, compares the final segment of `directory.path` to `droppedName` using the platform comparison. Returns the first match, or `null`.

Platform comparison:
```kotlin
private fun namesMatch(a: String, b: String) =
    if (IS_WINDOWS) a.equals(b, ignoreCase = true) else a == b
```

#### `private fun confirmRepairPath(missingNode: DefaultMutableTreeNode, newPath: String): Boolean`

Must be called **on the EDT**. Shows a `JOptionPane` modal. On confirmation:

1. Gets the `ProjectTreeEntry.Project` from the node.
2. Replaces its `directory` with a copy pointing at `newPath`.
3. Calls `updateMissingPath(newPath)` to clear the missing flag for the new path and `missingPaths.remove(oldPath)` to clear the old one.
4. Calls `treeModel.nodeChanged(missingNode)` to repaint.
5. Saves config via `ctx.saveConfig(buildConfig())`.
6. Calls `scanProject(updatedDirectory)` to trigger a background rescan.
7. Returns `true` (consumed) so the dropped dir is not added as a new entry.

Returns `false` if the user chose "Add as new project".

### Modified: `importExternal(support: TransferSupport): Boolean`

Current flow:
```
dirs, files = entriesFromExternal(support)
(newParent, startIndex) = resolveDropTarget(...)
return doImportExternal(dirs, files, newParent, startIndex)
```

New flow:
```
dirs, files = entriesFromExternal(support)
(newParent, startIndex) = resolveDropTarget(...)

val remainingDirs = mutableListOf<File>()
for (dir in dirs) {
    val name = dir.name
    val match = findMissingMatch(name)
    if (match != null) {
        // show dialog on EDT (we're already on EDT inside importData)
        val consumed = confirmRepairPath(match, dir.absolutePath)
        if (!consumed) remainingDirs += dir
    } else {
        remainingDirs += dir
    }
}
return doImportExternal(remainingDirs, files, newParent, startIndex)
```

`importData` is called on the EDT, so `confirmRepairPath` can safely call `JOptionPane.showOptionDialog` directly.

---

## Error Handling

| Situation | Behaviour |
|---|---|
| Dropped dir doesn't match any missing project | Normal insert (unchanged behaviour) |
| User clicks "Add as new project" | Normal insert for that dir; missing project unchanged |
| Two missing projects share the same dir name | First in tree order is the candidate; second can be repaired in a separate drop |
| Dropped dir matches a missing project but the new path also doesn't exist | `updateMissingPath` marks it missing again immediately; ⚠ stays; the path is still updated so the user can see the new path |

---

## Testing

New tests in `ProjectTreePanelExternalDropUiTest.kt` (non-UI, headless):

- `findMissingMatch returns node when name matches missing project`
- `findMissingMatch returns null when name matches a present project`
- `findMissingMatch returns null when name matches no project`
- `findMissingMatch is case-insensitive on Windows` (guarded by `IS_WINDOWS`)
- `findMissingMatch returns first match in tree order when two missing projects share a name`

The dialog itself (`confirmRepairPath`) is not unit-tested — it is a thin wrapper around `JOptionPane` with no logic of its own.

---

## Out of Scope

- Repairing paths via any mechanism other than drag-and-drop
- Bulk-repair without individual confirmation
- Watching the filesystem for the missing directory to reappear automatically
