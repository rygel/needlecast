# Log Viewer

## Overview

The **Log Viewer** is a dockable panel (right column) that auto-discovers `.log` files in the active project, tails them live, parses common log formats, color-codes entries by severity, and supports level filtering and incremental search.

---

## Log File Discovery

When you switch to a project, the Log Viewer scans for log files using this strategy:

**Root directory:** scans directly for `*.log` files (non-recursive)

**Subdirectories** (walked up to 2 levels deep):
- `target/`
- `logs/`
- `log/`
- `build/`
- `out/`

**Pattern matched:** `*.log` or `*.log.N` (rotated log archives)

Results are sorted by last-modified time, newest first. Select a file from the dropdown at the top of the panel to begin viewing it.

---

## Live Tailing

| Parameter | Value |
|---|---|
| Polling interval | 500 ms |
| Max tail size | 512 KB per refresh |
| Render batch interval | 10 ms (up to 200 entries per tick) |

**Log rotation detection:** Needlecast compares the current file size to the last-known offset. If the file is now smaller, it assumes rotation has occurred and resets to position 0 (reattaches to the new file).

---

## Supported Log Formats

| Format | Detection pattern |
|---|---|
| **Logback** | `HH:mm:ss.SSS [thread] LEVEL logger - message` |
| **Log4j2** | `YYYY-MM-DD HH:mm:ss,SSS LEVEL [thread] logger - message` |
| **JSON Lines** | `{"timestamp":…, "level":…, "message":…}` — also recognizes `@timestamp`, `msg`, `thread_name`, `logger_name` keys |
| **Plain text** | Heuristic keyword detection: ERROR, WARN, INFO, DEBUG, TRACE anywhere in the line |

**Stack trace detection:** Lines beginning with whitespace, `at `, `Caused by:`, or `...` are grouped with the preceding log entry and styled in the same level color (non-bold, slightly dimmed).

**Encoding:** UTF-8 (hardcoded).

---

## Color Coding

| Level | Color | Style |
|---|---|---|
| `ERROR` | Red (#F44336) | Bold |
| `WARN` | Orange (#FFA726) | Normal |
| `INFO` | Default foreground | Normal |
| `DEBUG` | Gray (#888888) | Normal |
| `TRACE` | Gray (#888888) | Italic |
| Stack trace continuation | Same as parent level | Non-bold, dimmed |

---

## Level Filter Buttons

Toggle buttons labeled **ERROR**, **WARN**, **INFO**, **DEBUG**, **TRACE**. All are enabled by default. Clicking a button immediately rebuilds the displayed entries to hide that level. Click again to restore it.

---

## Follow Mode

The **↓** button (U+21E3) toggles follow mode.

- **Enabled (default):** The view auto-scrolls to the newest entry.
- **Auto-disabled:** When you manually scroll up more than 16 px from the bottom, follow mode disables itself.
- **Re-enable:** Click the ↓ button.

---

## Search (`Ctrl+F`)

Pressing `Ctrl+F` focuses the inline search field.

| Feature | Detail |
|---|---|
| Type | Incremental, case-insensitive substring |
| Current match highlight | Orange (#FF8C00) |
| All other matches | Gold (#FFD700) |
| Navigate | `Enter` = next, `Shift+Enter` = previous |
| Status | "X / N" or "Not found" |
| Close | `Escape` |

---

## Multiple Files

The panel shows one file at a time. Switching the file selector dropdown resets the display and loads the new file from scratch. Simultaneous tailing of multiple files is not supported.

---

## Related

- [[Project Management]] — switching projects resets the log file list
- [[Settings]] — theme affects log viewer text colors
