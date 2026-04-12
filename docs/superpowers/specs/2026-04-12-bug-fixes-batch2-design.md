# Bug Fixes Batch 2 — Design Spec

## Goal

Fix four remaining items from issue #138: intermittent missing build-system badges,
Explorer "open in file manager" button, persistent command editing, and a comprehensive
CI/CD screenshot suite for the user manual.

---

## Fix 1 — Build-system badges sometimes missing

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt`

`CompositeProjectScanner.scan()` currently calls `scanners.mapNotNull { it.scan(directory) }`.
If any individual scanner throws (e.g. malformed `package.json`, file permission error), the
exception propagates through `mapNotNull` and is caught by the outer try-catch in
`ProjectTreePanel.scanProject()`, which stores a `scanFailed = true` result with no build tools.
The symptom: a project with both `pom.xml` and `package.json` shows only the Maven badge, or
neither, depending on which scanner threw.

**Fix:** Wrap each individual scanner call in its own try-catch inside `CompositeProjectScanner`:

```kotlin
val results = scanners.mapNotNull { scanner ->
    try { scanner.scan(directory) }
    catch (e: Exception) {
        logger.warn("Scanner ${scanner::class.simpleName} failed for '${directory.label()}'", e)
        null
    }
}
```

This makes each scanner's success/failure independent. Maven succeeds even if npm throws.

**Test:** `CompositeProjectScannerTest` — one scanner throws, verify the other's buildTools still
appear in the merged result. One scanner returns null (absent), verify it is silently excluded.

---

## Fix 2 — Explorer "open in file manager" button

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

ExplorerPanel's address-bar toolbar has three buttons (↑ up, ↻ refresh, ⊙ hidden-files toggle)
but no way to open the current directory in the OS file manager. The equivalent action exists in
`ProjectTreePanel`'s context menu but is not reachable from the Explorer panel itself.

**Fix:** Add an "Open in File Manager" button (⧉ `\u29c9` or ▤ `\u25a4`) to the right side of
the address bar (after the refresh button). On click, open `currentDir` using
`Desktop.getDesktop().open(currentDir)`. On Windows, fall back to
`Runtime.exec(["explorer.exe", path])` if `Desktop` is unavailable; on macOS,
`Runtime.exec(["open", path])`.

Extract the platform open logic into a private `openInFileManager(dir: File)` helper in
`ExplorerPanel` (mirroring the pattern already in `ProjectTreePanel`). No new model changes.
Tooltip: "Open in File Manager".

No dedicated unit test (AWT Desktop interaction, covered by existing UI pattern).

---

## Fix 3 — Persistent command editing

**Files:**
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`
- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

`EditCommandDialog` modifies a `CommandDescriptor` in-memory only. Rescanning (or restarting
the app) regenerates commands from project files, discarding user edits.

**New data class** (in `AppConfig.kt`, above `AppConfig`):

```kotlin
data class CommandOverride(
    val originalArgv: List<String>,
    val label: String,
    val argv: List<String>,
)
```

**AppConfig change** — add field with default:
```kotlin
val commandOverrides: Map<String, List<CommandOverride>> = emptyMap(),
```
Outer key = working directory path. This field serialises/deserialises with the existing
`JsonConfigStore` without any schema migration (new field with default = backwards-compatible).

**CommandPanel change:**

1. Accept `ctx: AppContext` (already present) and use it to read/write overrides.
2. In `loadProject(path, detected, ctx)`, after building `commandModel` from scanner output,
   apply overrides: look up `ctx.config.commandOverrides[workingDir]` and for each
   `CommandOverride`, find the `CommandDescriptor` whose `argv == override.originalArgv` and
   replace it with `descriptor.copy(label = override.label, argv = override.argv)`.
3. In `editSelectedCommand()`, after the dialog returns a non-null result, also persist:
   ```kotlin
   val key = original.argv   // fingerprint of the scanner-generated command
   val override = CommandOverride(key, updated.label, updated.argv)
   val existing = ctx.config.commandOverrides[workingDir]?.filterNot { it.originalArgv == key }
       ?: emptyList()
   ctx.updateConfig(ctx.config.copy(
       commandOverrides = ctx.config.commandOverrides + (workingDir to existing + override)
   ))
   ```

**Tests:** `CommandOverrideTest` — verify that applying overrides replaces the matching
descriptor, that unmatched overrides are ignored, and that a round-trip through JSON
serialisation preserves the override.

---

## Fix 4 — CI/CD screenshot suite

**Files:**
- New: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/screenshot/ScreenshotHarness.kt`
- New: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/screenshot/AllScreenshotsTest.kt`
- Modify: `.github/workflows/ci.yml` — upload artifact step

**Scope:** ~45 PNG files covering every dockable panel (empty + populated state where applicable),
every Settings sub-panel, and every dialog. Output to `target/screenshots/`, named with a
zero-padded prefix for ordering.

**Harness design:**

`ScreenshotHarness` is an abstract JUnit 5 base class that:
- Creates a `@TempDir` fixture directory with `pom.xml`, `package.json`, `src/Main.java`,
  `README.md`, and a fake `needlecast.log`
- Builds a `DetectedProject` with `{MAVEN, NPM}` build tools and ~8 synthetic commands
- Constructs an `AppContext` from a `JsonConfigStore` backed by a temp config file, pre-loaded
  with command history, prompt templates, and the fixture project in the tree
- Opens a `MainWindow` at 1280×800 via `GuiActionRunner.execute`
- Exposes `capture(name, component)` which calls `robot.waitForIdle()` then
  `Robot.createScreenCapture(component.locationOnScreen to component.size)` and writes to
  `target/screenshots/<name>.png`
- Exposes `captureDialog(name, dialog)` which shows the dialog, captures it, then disposes it

**Screenshot catalog in `AllScreenshotsTest`:**

| # | File | Panel / state |
|---|------|--------------|
| 01 | `main-window-overview` | Full 1280×800 main window with projects loaded |
| 02 | `project-tree-populated` | Tree with Maven+npm badges, tags, color stripe |
| 03 | `project-tree-filter` | Filter field active, filtered results |
| 04 | `project-tree-context-menu` | Right-click context menu open on a project row |
| 05 | `project-tree-missing-dir` | A project entry marked as missing (red strikethrough) |
| 06 | `commands-populated` | Command list with Maven + npm commands |
| 07 | `commands-history` | History toggle open, last 3 commands shown |
| 08 | `commands-queue` | Queue toggle open, commands enqueued |
| 09 | `commands-edit-dialog` | EditCommandDialog open |
| 10 | `commands-context-menu` | Right-click menu on a command row |
| 11 | `terminal-idle` | Terminal panel, no active project |
| 12 | `terminal-active` | Terminal panel after activateProject() |
| 13 | `git-log-commit-list` | GitLogPanel with synthetic commits |
| 14 | `git-log-diff` | File diff expanded in a commit |
| 15 | `explorer-directory` | File listing of fixture directory |
| 16 | `explorer-hidden-files` | Hidden files visible (toggle active) |
| 17 | `explorer-context-menu` | Right-click context menu on a file row |
| 18 | `editor-source-file` | Java source file open in editor |
| 19 | `editor-multi-tab` | Two tabs open |
| 20 | `renovate-not-installed` | Button disabled, "not found" status |
| 21 | `renovate-installed` | Button enabled, version shown |
| 22 | `console-empty` | Output panel, no output |
| 23 | `console-with-output` | Output panel with streamed text |
| 24 | `log-viewer-app-log` | needlecast.log loaded, entries shown |
| 25 | `log-viewer-filter` | Filter field active, filtered entries |
| 26 | `search-empty` | Search panel, blank state |
| 27 | `search-results` | Search panel with results |
| 28 | `docs-available` | DocViewerPanel with available + unavailable targets |
| 29 | `prompt-input-default` | Prompt Input panel |
| 30 | `prompt-input-library` | Prompt Library picker open |
| 31 | `command-input-default` | Command Input panel |
| 32 | `doc-viewer-empty` | Doc Viewer panel, no docs |
| 33 | `settings-appearance` | Settings dialog, Appearance tab |
| 34 | `settings-layout` | Settings dialog, Layout tab |
| 35 | `settings-terminal` | Settings dialog, Terminal tab |
| 36 | `settings-editors` | Settings dialog, External Editors tab |
| 37 | `settings-ai-tools` | Settings dialog, AI Tools tab |
| 38 | `settings-renovate` | Settings dialog, Renovate tab |
| 39 | `settings-apm` | Settings dialog, APM tab |
| 40 | `settings-shortcuts` | Settings dialog, Shortcuts tab |
| 41 | `settings-language` | Settings dialog, Language tab |
| 42 | `prompt-library-dialog` | Prompt Library dialog with templates list |
| 43 | `env-editor-dialog` | Env Editor dialog with some variables |
| 44 | `project-switcher-dialog` | Project Switcher dialog |
| 45 | `variable-resolution-dialog` | Variable Resolution dialog with a prompt |

**GitHub Actions:** In the `ui-tests` job (`.github/workflows/ci.yml`), add after the Maven step:

```yaml
- name: Upload screenshots
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: needlecast-screenshots
    path: needlecast-desktop/target/screenshots/
    retention-days: 30
```
