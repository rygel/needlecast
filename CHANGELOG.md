# Changelog

All notable changes to Needlecast are documented here.

## [0.6.0-alpha] — 2026-04-03

> **Alpha release.** Core functionality is working but the app is still under active development. Expect rough edges, missing polish, and breaking config changes between alpha releases.

### Added

#### Package / Identity
- Renamed package from `io.github.quicklaunch` → `io.github.rygel.needlecast`
- Changed license from Apache 2.0 → MIT
- Log file moved to `~/.needlecast/needlecast.log`

#### File Explorer — Image & SVG Viewer
- Click any image file (JPEG, PNG, WebP, GIF, BMP, TIFF, ICO) to open a built-in image viewer
- Click `.svg` files to open a vector SVG viewer (rendered via JSVG — stays crisp at any zoom)
- **Ctrl+scroll** to zoom in/out (6 pt → 3200%); **double-click** to reset to fit-to-panel
- Re-clicking an already-open image/SVG checks `lastModified` + file size and reloads automatically if the file changed on disk
- WebP support via TwelveMonkeys ImageIO

#### File Explorer — Editor Tabs
- **Right-click tab header** → context menu: Close, Close All to the Left, Close All to the Right, Close All
- Tabs with unsaved changes still prompt before closing

#### Editor
- **Ctrl+scroll** to zoom the syntax editor font (6 pt – 72 pt); zoom survives theme changes
- **Syntax theme selector** in Settings → Layout & Terminal: Auto (follows app theme), Monokai, Dark, Druid, IntelliJ IDEA, Eclipse, Default, Default Alt, Visual Studio

#### Terminal
- Terminal background and foreground now automatically inherit colors from the active FlatLaf theme
- Manual color overrides in Settings take priority over theme-derived colors; Reset button restores theme colors

#### Settings — Layout & Terminal tab
- **"Highlight active docking panel border"** checkbox — disables ModernDocking's `ActiveHighlighter` outline (off by default)
- **Syntax theme** dropdown (see above)

#### Project Explorer
- File explorer directory updates when a project is selected **or** activated (terminal launched), not only on selection

### Fixed
- Panel hover highlight (View → "Highlight panel on hover") had no visible effect — `DockablePanel.setHoverHighlight` set the border but never called `repaint()`, so the change was invisible until the next unrelated redraw
- `JsonConfigStoreTest` compared full `AppConfig` objects including randomly-generated UUIDs in default prompt libraries — test now checks individual fields
- Stale compiled classes from old package (`io.github.quicklaunch`) caused silent failures when running without `mvn clean` — documented; always use the correct main class `io.github.rygel.needlecast.MainKt`

### Improved

#### Prompt Library
- Replaced 8 generic placeholder prompts with 17 actionable developer-focused templates
- New categories: Understand (Onboard me, Explain file, Trace feature end-to-end, Map dependencies), Debug (Debug this error, Why is this slow, Find the race condition), Quality (Review these changes, Security audit, Write tests, Find dead code), Develop (Implement feature, Refactor, Migrate to), Git (Commit message, PR description), Docs (Document this)
- Each prompt is concrete and specific — names files, asks for reasoning, and constrains the AI to the existing codebase conventions

#### Command Library
- Replaced 7 thin entries with 37 commands across Git, Maven, Gradle, npm, Docker, Search, and Process/Network
- Git: status, graph log, diff staged, diff from branch, new branch, stash, stash pop, interactive rebase, blame, find commit by text, clean untracked
- Build: Maven verify/package/clean/dependency-tree, Gradle build/test/dependencies, npm install/build/test/outdated
- Docker: build, run interactive, compose up/logs, ps
- Search: find in files, find TODOs, find file by name, large files
- Process: who is on port (Unix + Windows), kill port, Java processes, tail log, disk usage

### Alpha labels
- View menu → "Highlight panel on hover" is now labelled `[alpha]` with a tooltip; the feature was previously broken (see fix above)
- Settings → Layout → "Highlight active docking panel border" labelled `[alpha]` with tooltip
- Image and SVG viewer tabs show a tooltip: `[alpha] Viewer is new and may have rough edges`

### Dependencies
- Added `com.twelvemonkeys.imageio:imageio-webp:3.11.0` — WebP image support
- Added `com.github.weisj:jsvg:2.0.0` — SVG rendering (aligned to version required by `flatlaf-extras:3.7.1`)

---

## [0.5.0] — prior release

- Git log viewer with `git show` on click
- Keyboard shortcut editor (rebind any default shortcut)
- Scanner unit tests (Maven, Gradle, npm, .NET, APM)
- Structured logging (SLF4J + Logback)
- Build-file watcher auto-rescan and command queuing
- Auto OS dark/light theme, README preview, group color coding
- Per-project environment variables
- Global project switcher (`Ctrl+P`), console search, desktop notifications
