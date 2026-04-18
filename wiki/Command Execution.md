# Command Execution

## Overview

The **Commands** panel (right column) lists auto-detected build commands for the active project and provides controls to run them, queue them, and review history. Commands are sourced by [[Build Tool Detection|build-tool scanners]] — there is no way to add ad-hoc commands to the list (use the [[Terminal]] for that).

---

## Running a Command

**Double-click** a command to run it immediately. Alternatively, single-click to select it and then press the **▶ Run** button.

While a command is running:
- The **▶ Run** button is disabled
- The **⏹ Cancel** button becomes active
- Output streams line-by-line into the **Output Console**
- The **status bar** shows `Running: <label>`

When it finishes:
- Status bar shows `Finished successfully (exit 0)` or `Finished with exit code N`
- The run is appended to **Command History**
- A **desktop notification** fires if the Needlecast window is not in focus (type INFO on success, ERROR on failure)
- If another command is queued, it starts automatically

> [!note]
> Only one command can run at a time. The Run button is disabled while a process is active.

---

## Toolbar Buttons

| Button | Label | Enabled when |
|---|---|---|
| **▶** | Run | A command is selected **and** nothing is currently running |
| **⏹** | Cancel | A command is currently running |
| **⏭** | Queue | A command is selected (adds it to the queue) |
| **⏳** | History (toggle) | Always — shows/hides the history list |
| **≡** | Queue (toggle) | Always — shows/hides the queue panel |

---

## Cancel / Stop

Clicking **⏹ Cancel** calls `destroyForcibly()` on the OS process. The process is killed immediately (not gracefully). The exit code is reported as **−1** when the process is forcibly terminated.

---

## Command Queue

Chain commands to run sequentially, one after the other.

### Building a Queue

1. Select a command in the list.
2. Click **⏭ Queue** (instead of Run).
3. Repeat for additional commands.

The Queue panel appears automatically when the first item is added. Items are shown as labels only.

### Running the Queue

Click **▶ Run** on the first command, or click the queue's own **Run** button. When each command finishes successfully, the next starts. If a command exits with a non-zero code the queue pauses.

### Managing the Queue

| Action | How |
|---|---|
| Clear all items | Click **Clear Queue** button |
| Remove one item | Click the × next to it |

---

## Command History

Each project stores the last **20** command runs. Click the **⏳ History** toggle to show or hide the list below the command list.

### History Row Layout

Each row shows two lines:
- **Left:** Command label
- **Right:** `exit CODE  HH:mm` — green (#4CAF50) for exit 0, red (#F44336) for non-zero

**Double-click** any history entry to re-run it with the same arguments and working directory.

History is persisted per-project in `~/.needlecast/config.json` under `commandHistory[projectPath]`.

---

## Output Console

The Output Console captures everything written to stdout and stderr.

- A header line shows the full command `argv` before output starts.
- Output is batch-flushed every **50 ms** from a concurrent queue (prevents UI stalls on high-throughput processes).
- The console **always scrolls to the newest line** (no scroll-lock option).
- There is **no maximum buffer size** — output accumulates for the lifetime of the session.
- **No ANSI color support** — raw escape codes appear as text (use the [[Terminal]] if you need colors).

### Console Context Menu (Right-Click)

| Item | Shortcut |
|---|---|
| **Copy** | `Ctrl+C` / `Cmd+C` |
| **Select All** | `Ctrl+A` / `Cmd+A` |
| **Clear** | — |

### Console Find Bar (`Ctrl+F`)

| Feature | Detail |
|---|---|
| Case sensitivity | Case-insensitive (always) |
| Incremental | Yes — matches rebuild on every document change |
| Current match highlight | Orange (#FF8C00) |
| All other matches | Gold (#FFD700) |
| Navigate | `Enter` = next, `Shift+Enter` = previous, ↑/↓ buttons |
| Status | "N matches" / "X / N" (green); "Not found" (red) |
| Close | `Escape` or ✕ button |

---

## README Preview

The bottom of the Commands panel shows the first **20 lines** of the project's README, if one exists.

Files checked in order: `README.md`, `readme.md`, `README.txt`, `readme.txt`

The preview is **plain text** — no markdown rendering. It is loaded asynchronously on project switch.

---

## Desktop Notifications

When a command finishes and the Needlecast window does not have OS focus:
- **Success (exit 0):** INFO-level notification
- **Failure (non-zero):** ERROR-level notification

Notifications are suppressed when the window is focused.

---

## Related

- [[Build Tool Detection]] — where the command list comes from
- [[Terminal]] — for interactive or ad-hoc commands
- [[Project Management#Environment Variables|Per-project environment variables]] — injected into every command run
