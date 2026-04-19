# Terminal Resize/Scroll Fix — Work Notes

**Branch:** `fix/macos-terminal-resize-scroll`
**Date:** 2026-04-19
**Issue:** Terminal window resize and scrolling inside the terminal don't work on macOS (behaves differently than Windows).

## Root Cause

`TerminalPanel.kt` was calling `PtyProcessBuilder.setUseWinConPty(true)` on all platforms. WinConPty is a Windows-only PTY backend; on macOS it silently breaks the PTY, preventing resize and scroll events from propagating to child processes.

**Fix:** Made conditional: `if (IS_WINDOWS) builder.setUseWinConPty(true)`

## Resize Chain (how it works)

```
User resizes window
  → Swing componentResized event
    → JediTerm TerminalPanel.sizeTerminalFromComponent()
      → TerminalStarter.postResize()
        → TtyConnector.resize(Dimension)
          → PtyProcessTtyConnector.resize()
            → PtyProcess.setWinSize()
              → kernel sends SIGWINCH to PTY's foreground process group
                → bash/zsh/opencode receives SIGWINCH and redraws
```

JediTerm's `TerminalPanel` uses a `ComponentAdapter` on `componentResized` that calls `sizeTerminalFromComponent()`. This computes new col/row dimensions from the panel pixel size and font metrics, then calls `postResize()` which eventually calls `PtyProcess.setWinSize()` to update the kernel PTY and send SIGWINCH.

## Debug Logging Added

Resize events are logged via SLF4J at INFO level to `~/.needlecast/needlecast.log`:

- `TerminalPanel` — PTY startup (platform, pid, alive status)
- `TerminalPanel` — componentResized (panel pixels, terminal cols/rows)
- `TerminalPanel` — TtyConnector.resize (cols, rows, connected)
- `ObservingTtyConnector` — resize passthrough (cols, rows)

## Key Findings

1. **Resize chain works at every Swing layer** — confirmed via debug logging
2. **PTY `setWinSize()` correctly updates kernel PTY dimensions** — bash sees new dimensions via `tput cols/lines` and `stty size`
3. **`isConnected()` remains `true` after resize** — no false disconnects
4. **`ShrinkableJediTermWidget` with `getPreferredSize() = Dimension(1,1)` does NOT block resize** — BorderLayout still stretches it to fill available space
5. **`ObservingTtyConnector` properly delegates `resize()` to inner connector** — Kotlin `by delegate` correctly forwards the method
6. **SIGWINCH propagates to child processes** — TUI apps (opencode, vim, etc.) receive the signal and redraw
7. **Fix confirmed working on macOS** — both resize and scroll work with opencode TUI

## Component Hierarchy

```
MainWindow
  └─ [docking framework]
       └─ TerminalManager (CardLayout)
            └─ ProjectTerminalPane (BorderLayout)
                 └─ JTabbedPane
                      └─ TerminalPanel (BorderLayout)
                           └─ termContainer (BorderLayout, prefSize=1x1)
                                └─ ShrinkableJediTermWidget (prefSize=1x1)
                                     └─ JediTerm's TerminalPanel (inner)
                                          └─ ComponentAdapter → sizeTerminalFromComponent()
```

## Changes Made

### TerminalPanel.kt
- Made `setUseWinConPty(true)` conditional on `IS_WINDOWS`
- Removed unused `readMouseMode()` reflection helper
- Simplified `remoteMouseWheelConsumer` lambda
- Added SLF4J logging for PTY startup, component resize, and TtyConnector resize
- Added `ComponentListener` on `embeddedTerminalPanel` for resize tracing
- Wrapped `PtyProcessTtyConnector` with resize-logging delegate

### ObservingTtyConnector.kt
- Added explicit `resize()` override with SLF4J logging
- Added KDoc

### Deleted files (tests referencing removed code)
- `MouseReportingIntegrationTest.kt` — used removed `readMouseMode()`
- `RealPtyMouseReportingTest.kt` — used removed `readMouseMode()`
- `UpdateCheckerConfigTest.kt` — referenced removed `buildSparkle4jInstance`

## Regression Tests

| Test Suite | Tests | Platform | Description |
|---|---|---|---|
| `TerminalResizeTest` | 6 | All | Mock connector resize chain — verifies JediTerm propagates resize to TtyConnector |
| `TerminalMouseWheelRoutingTest` | 4 | All | Scroll wheel routing — remote vs local, Ctrl+zoom, Shift override |
| `TerminalPtyPlatformTest` | 9 | All | Platform conditionals — IS_WINDOWS flag, shell resolution, OS detection |
| `RealPtyResizeTest` | 3 | macOS/Linux | Real PTY + bash — SIGWINCH propagation, isConnected, buffer dimensions |
| `RealTuiResizeTest` | 3 | macOS/Linux | Real TUI — bash `tput cols/lines`, `stty size`, `setWinSize()` direct |
| `ShrinkableWidgetResizeTest` | 1 | All | Verifies `ShrinkableJediTermWidget` with `getPreferredSize(1,1)` still resizes |
| `ZshResizeTest` | 3 | macOS/Linux | zsh-specific — `tput`, `stty size`, direct `setWinSize()` |
| `PowerShellResizeTest` | 2 | Windows | WinConPty — isConnected after resize, PowerShell window size |

**31 tests total, all passing** (4 skipped on macOS: 2 PowerShell + 2 Windows-only platform tests).

### Test details

**TerminalResizeTest** — Uses a `ResizeTrackingConnector` mock that counts `resize()` calls and captures dimensions. Verifies that resizing a `JFrame` containing `JediTermWidget` triggers `TtyConnector.resize()` with correct dimensions.

**RealPtyResizeTest** — Launches real `/bin/bash` via `PtyProcessBuilder`, connects through `PtyProcessTtyConnector` to `JediTermWidget`. Verifies `PtyProcess.winSize` changes, `isConnected` stays true, and terminal buffer dimensions match.

**RealTuiResizeTest** — Same setup but uses `tput cols/lines` and `stty size` inside bash to verify the subprocess actually sees the new dimensions. Also tests direct `setWinSize(120,40)` call.

**ZshResizeTest** — Same as RealTuiResizeTest but with `/bin/zsh --login`. Verifies zsh receives SIGWINCH correctly.

**ShrinkableWidgetResizeTest** — Replicates the exact widget hierarchy from `TerminalPanel` (outer container with `prefSize=1,1` → ShrinkableWidget → inner TerminalPanel). Confirms inner panel still receives resize events.

**PowerShellResizeTest** — Windows-only. Tests `powershell.exe` and `cmd.exe` with `setUseWinConPty(true)`. Verifies `isConnected` after resize.
