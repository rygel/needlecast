# Bug Fixes Batch 1 — Design Spec

## Goal

Fix four independent bugs from issue #138: Renovate scan button enabled when renovate is absent,
Command panel symbols and emoji rendering as boxes, terminal font ordering, and the log viewer
not surfacing the app's own log file.

---

## Fix 1 — Renovate scan button guard

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/RenovatePanel.kt`

Add a `renovateFound: Boolean` guard field (default `false`). Disable `runButton` initially.
In `checkRenovateStatus().done()`, set `renovateFound = found` and `runButton.isEnabled = found`.
Change `setButtonsEnabled(enabled)` so it sets `runButton.isEnabled = enabled && renovateFound` —
this prevents the button from re-enabling after a scan if renovate was not found.

---

## Fix 2 — Command panel symbols and emoji

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

**Symbol replacements:** U+23F9 (⏹, Miscellaneous Technical — not in standard JVM fonts) and
U+23ED (⏭, same block) render as boxes. Replace with characters from the Geometric Shapes block
(U+25A0–U+25FF), which is present in every JVM font:

| Old | New | Where |
|-----|-----|-------|
| `\u23F9` (⏹) | `\u25A0` (■) | `cancelButton` label |
| `\u23ED` (⏭) | `\u25B6\u25B6` (▶▶) | `queueButton` label, `queueToggle` label, context menu item |

**Emoji in command labels:** `CommandCellRenderer` and `HistoryCellRenderer.nameLabel` set
`label.text` directly. FlatLaf overrides the default LAF font with a physical font (Segoe UI /
Helvetica Neue) that lacks emoji glyphs and bypasses Java's automatic font-substitution pipeline.
Wrapping label text in `<html>…</html>` activates Java's HTML renderer, which re-engages the
platform font fallback chain (including Segoe UI Emoji on Windows, Apple Color Emoji on macOS).
HTML entities (`&`, `<`, `>`) must be escaped. Apply to both renderers.

---

## Fix 3 — Terminal and viewer font ordering

**Files:**
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/QuickLaunchTerminalSettings.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtils.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/logviewer/LogViewerPanel.kt`

In the Windows branch of each font-preference list, move "Cascadia Code" before "Cascadia Mono".
Cascadia Code has broader Unicode coverage; once selected, Java's AWT pipeline falls back to
Segoe UI Emoji for any glyph not in Cascadia Code.

---

## Fix 4 — Log viewer: always surface app log

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/logviewer/LogViewerPanel.kt`

The app writes to `${user.home}/.needlecast/needlecast.log` (logback rolling appender, up to 5
archived files `.log.1`–`.log.5`). `LogFileScanner.scan()` only searches the active project
directory and never finds these files.

Add `internal fun appLogFiles(logDir: File = File(System.getProperty("user.home"), ".needlecast")): List<File>`
(top-level in the package, `internal` so it is testable). It returns `needlecast.log` followed by
`.log.1`–`.log.5`, filtering to files that exist.

Modify `loadProject()`:
- When `path == null`: populate the combo with `appLogFiles()` only.
- When `path != null`: prepend `appLogFiles()` ahead of the project files returned by the scanner.

Update the file combo renderer to show `"${f.name} (app)"` for files inside `~/.needlecast/`,
and the existing project-relative path otherwise.
