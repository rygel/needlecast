# Changelog

All notable changes to Needlecast are documented here.

## [0.6.18] — 2026-04-11

### Added
- **Find-in-files search panel** — full-text search across all files in the active project; supports regex, case-sensitive mode, and file glob filtering; results show file path, line number, and matching line content.
- **OS file-manager drops across the app** — drag folders from macOS Finder or Windows Explorer and drop them anywhere in the Needlecast window to add projects; handles both file and folder drag flavors.
- **VLC media player in Explorer** — click `.mp3`, `.mp4`, `.avi`, `.mkv`, and other common media files in the Explorer panel to open them in VLC (must be installed separately).
- **Font settings for UI, editor, and terminal** — configure UI font family and size, and editor/terminal font, independently from Settings.
- **Renovate scan logs and verbose mode** — the Renovate panel now exposes a collapsible log area showing raw scan output; a new verbose toggle enables detailed dependency scanning.
- **Missing directory indicator in project tree** — projects whose directory no longer exists on disk are highlighted in red with a ⚠ warning icon and a "Directory not found" tooltip; scanning is skipped for missing paths.

### Fixed
- **Mac drag-and-drop reliability** — improved handling of macOS-specific drag flavors so project drag-and-drop works consistently on Mac.

---

## [0.6.17] — 2026-04-09

### Added
- **Diagnostics toggles in Settings** — enable project tree click tracing and EDT stall monitoring on demand; logs go to `~/.needlecast/needlecast.log`.
- **Incremental text rendering helpers** — large text blobs now stream into the UI in chunks to keep the EDT responsive.

### Changed
- **Project tree responsiveness** — background scanning, batched updates, and coalesced repaints reduce selection lag during rapid clicking.
- **UI test profile** — `test-desktop` now controls `*UiTest` exclusion so targeted runs can include UI tests when needed.

### Fixed
- **EDT stalls from large text** — Git Log, Editor, Log Viewer, and Console panels avoid blocking the UI when rendering big diffs, files, or logs.

---

## [0.6.16] — 2026-04-08

### Changed
- **Config directory renamed** — the config folder is now `~/.needlecast` (was `~/.quicklaunch`). Existing configs are migrated automatically on first launch.

---

## [0.6.15] — 2026-04-08

### Added
- **Config backups** — up to 5 rotating numbered backups are created before each config save (`config.json.bak.1` … `.bak.5`). Corrupt or overwritten configs can be recovered from the backup next to the config file.

### Fixed
- **Project list clicks sometimes not registered** — `dragEnabled=true` caused Swing's drag gesture recognizer to intercept every `mousePressed` and defer selection until it could distinguish a click from a drag. Replaced with a `MouseMotionAdapter` that only starts the drag when the mouse actually moves while pressed; normal clicks now register immediately.
- **Terminal cannot be opened by double-click or right-click** — same `dragEnabled=true` issue on the project `JTree`: drag gesture recognizer intercepted mouse events before they reached the double-click and context menu handlers. Removed `dragEnabled` on the tree and replaced with a `MouseMotionAdapter` that initiates drag only when the mouse actually moves.

---

## [0.6.14] — 2026-04-06

### Added
- **Update badge in status bar** — when a newer version is available, a persistent `⬆ x.y.z available` label appears on the right side of the status bar. Clicking it opens the releases page. No dialog, no interruption.
- **Portable builds** — each release now ships a no-install portable archive alongside the installer: `needlecast-VERSION-windows-portable.zip`, `needlecast-VERSION-macos-portable.zip`, `needlecast-VERSION-linux-portable.tar.gz`. Unzip and run, no setup required.

### Fixed
- **main→develop sync conflicts** — the automated sync workflow now uses `-X ours` so screenshot conflicts after a release are resolved automatically without manual intervention.

---

## [0.6.13] — 2026-04-06

### Fixed
- **Project tree drag-and-drop broken** — dropping a project silently did nothing; root cause was a `StackOverflowError` thrown inside `treeModel.insertNodeInto()`. The cell renderer called `tree.getPathBounds()` to compute the indentation offset for full-width sizing, which re-entered `VariableHeightLayoutCache` mid-calculation, looping back into the renderer indefinitely. Fixed by computing the indent from `node.level × (leftChildIndent + rightChildIndent)` directly, avoiding the layout cache entirely.

---

## [0.6.12] — 2026-04-06

### Added
- **ProjectTreeDebug tool** — lightweight harness to reproduce and debug project tree layout issues

### Changed
- **Project tree row rendering** — rows now paint full-width, with tags right-aligned to the control width and branch labels unaffected
- **Screenshot tour** — demo projects are real git repos with branches/dirty state so branch labels are visible in screenshots
- **Screenshots updated** — refreshed UI captures across the docs gallery

## [0.6.10] — 2026-04-05

### Changed
- **Update checks every 15 minutes** — replaces the single startup check with periodic polling (first check 30s after launch)
- **Updated documentation** — README now includes Log Viewer and Renovate panel features with screenshot gallery; User Guide expanded with full sections for Log Viewer, Renovate, and all 21 build tools
- **Screenshot tour** — captures Renovate panel and Log Viewer with realistic demo data; demo projects now include Python (uv), Rust (Cargo), and Go

---

## [0.6.9] — 2026-04-05

### Added
- **Log Viewer panel** — dockable panel that discovers `.log` files in the active project, with live tailing (500ms poll), colour-coded log levels (ERROR/WARN/INFO/DEBUG/TRACE), level filtering, follow mode, and incremental search; supports Logback, Log4j2, JSON structured logs, and plain text
- **Renovate panel** — dockable panel that scans the active project for outdated dependencies using `renovate --platform=local`, shows results in a sortable colour-coded table (major/minor/patch), and applies selected updates directly to project files
- **11 new build system scanners** with full test coverage (49 tests):
  - **Python** — uv (`uv.lock`), Poetry (`poetry.lock`), pip fallback; parses `[project.scripts]`
  - **Rust** — Cargo (`Cargo.toml`); workspace member detection for per-crate commands
  - **Go** — go mod (`go.mod`); `main.go` and `cmd/` subdirectory detection
  - **PHP** — Composer (`composer.json`); script extraction, Laravel artisan detection
  - **Ruby** — Bundler (`Gemfile`); Rails, Rakefile detection
  - **Swift** — SPM (`Package.swift`)
  - **Dart/Flutter** — pub/Flutter (`pubspec.yaml`); distinguishes Flutter from pure Dart
  - **C/C++** — CMake (`CMakeLists.txt`) and Make (`Makefile`), can coexist
  - **Scala** — sbt (`build.sbt`)
  - **Elixir** — Mix (`mix.exs`); Phoenix framework detection
  - **Zig** — Zig (`build.zig`)
- **Settings: embedded output** — Renovate and APM install tabs now stream command output in an embedded panel instead of sending blindly to the main terminal

### Changed
- `BuildTool` enum renamed to use actual tool names (Cargo not Rust, Composer not PHP, Bundler not Ruby, SPM not Swift, Mix not Elixir, etc.)
- Editor mono font selection is now OS-aware (Cascadia Mono on Windows, SF Mono on macOS, JetBrains Mono on Linux)

---

## [0.6.8] — 2026-04-04

### Fixed
- **Appcast workflow now works end-to-end** — grep fix (`\.exe$` → `\.exe `) deployed to main; previous releases had the fix on develop but not main

---

## [0.6.7] — 2026-04-04

### Fixed
- **Appcast generation broken** — grep patterns used `\.exe$` anchor but the output format is `"name size"` per line, so `$` never matched; changed to `\.exe ` (with trailing space)
- Screenshots workflow now only runs on `develop` to prevent binary merge conflicts on every release PR

---

## [0.6.6] — 2026-04-04

### Fixed
- **Editor theme mismatch** — `EditorPanel.applyTheme()` used hardcoded colours instead of reading from the active FlatLaf theme via `UIManager`; now derives bg/fg/caret from `UIManager.getColor("TextArea.*")` and themes the gutter (line numbers)
- **Terminal theme not applied** — `pushStyleToJediTerm()` reflection targeted `JediTermWidget` instead of JediTerm's inner `TerminalPanel` where `myStyleState` actually lives; silently failed every time
- **Terminal starts black** — initial terminal session used hardcoded fallback colours because `UIManager` colours weren't read during construction; now reads them in the settings provider initialiser
- **Terminal placeholder black** — the idle placeholder panel hardcoded `Color(0x1E1E1E)`; now uses `UIManager` and updates on theme switch
- **Terminal selection colours** — hardcoded selection highlight now derived from `UIManager.getColor("TextArea.selectionBackground")`
- **Appcast generation race condition** — `update-appcast` job queried the GitHub API before native assets propagated, producing empty download URLs; now retries up to 60 s and fails hard if assets are missing
- **`verify-update` CI job broken** — replaced missing `xmllint` with `python3` + `grep`; added check for URLs ending in `/`

### Added
- 15 end-to-end UI tests for theming (editor, terminal, consistency across theme variants)
- Updated documentation screenshots

---

## [0.6.5] — 2026-04-04

### Fixed
- **Ctrl+V paste broken in terminal** — keyboard focus sits on JediTerm's inner TerminalPanel, so the `processKeyEvent` override on the widget never fired; replaced with a `KeyEventDispatcher` that intercepts before any component sees the event
- **Nested scroll panes in docked panels** — ModernDocking wraps every dockable in a JScrollPane by default (`isWrappableInScrollpane`); panels like Project Tree and Explorer already have their own, causing scroll-within-scroll

### Added
- End-to-end UI test for terminal Ctrl+V paste (runs in Docker with Xvfb)

---

## [0.6.4] — 2026-04-04

### Changed
- All dockable panels can now shrink freely — Explorer, Git Log, Console no longer force a large minimum width
- Main window minimum size reduced from 1000x600 to 800x500
- Tags moved from project name row to bottom row alongside branch label — always visible, no horizontal overflow
- Output panel mouse wheel scrolling improved (unitIncrement=16, blockIncrement=64)
- `mvn exec:java` now works without `-Dexec.mainClass` (exec-maven-plugin configured in desktop pom)

---

## [0.6.3] — 2026-04-03

### Added
- **End-to-end update verification** — release workflow validates appcast.xml, download URLs, and asset inventory after each release
- **UI regression tests** — 5 new AssertJ Swing tests for project tree: visibility, null displayName, tag preservation after DnD, layout stability
- **Versioned release assets** — all downloads now include version number (e.g. `needlecast-0.6.3-windows.exe`)

### Fixed
- **Projects invisible in project tree** — `projectPanel.getPreferredSize()` returned zero-width before viewport was available, making project rows invisible while folders still showed
- **Inno Setup icon paths** after module rename

---

## [0.6.2] — 2026-04-03

### Added
- **Gradle subproject detection** — parse `settings.gradle(.kts)` for `:module:run`, `:module:build` etc. Detects Spring Boot, Shadow, Compose Desktop, JavaFX plugins per subproject
- **Maven submodule detection** — parse `<modules>` + `<plugins>` for `-pl module` commands (Spring Boot, Quarkus, Jetty, Tomcat, Liberty)
- **.NET solution project detection** — parse `.sln` for per-project commands based on SDK type (`dotnet run --project`, `dotnet watch run`, `dotnet test --project`)
- **Delete from disk** in project tree context menu (under Advanced submenu with confirmation)
- **Double-click command** to run it immediately
- **Output panel context menu** — Copy, Select All, Clear on right-click
- **Check for Updates** menu item in Help menu
- **Improved About dialog** with app icon, author name, clickable GitHub link
- **Auto-release workflow** — creates GitHub release automatically on version bump to main
- **Appcast generation** as release asset for sparkle4j update checking
- **Release/download badges** in README

### Changed
- Renamed modules: `desktop/` → `needlecast-desktop/`, `web/` → `needlecast-web/`
- Console panel renamed to "Output"
- "Remove" in project tree now clearly states it only removes from list
- Claude Code hooks now optional (default: off) — agent status detected by terminal output polling instead, eliminating "Ran N hook" messages
- Upgraded sparkle4j 0.5.0 → 0.5.1
- Rewrote default prompt library with 25 practical developer templates across 7 categories

### Fixed
- Project tree rows growing wider on each project add (layout feedback loop)
- Horizontal scrollbar appearing in project tree when tags overflow
- Screenshot tour hanging indefinitely in CI (added timeouts + force exit)
- Inno Setup icon paths after module rename
- App auto-cleans leftover Claude Code hooks from `~/.claude/settings.json` on startup
- Icon white borders removed

---

## [0.6.1] — 2026-04-03

### Added

#### Windows Installer
- Inno Setup installer (`scripts/needlecast.iss`) wraps jpackage app-image into a proper `.exe` with Start Menu shortcut, uninstaller, and `/PASSIVE` mode for silent updates
- macOS DMG and Linux `.deb` package generation in build scripts

#### App Icon
- Application icon (teal geometric needle on dark background) displayed in title bar, taskbar, system tray, and native installer
- Multi-size `.ico` (16-256px), 1024px `.png`, Inno Setup wizard `.bmp`

#### In-App Update Checking
- Integrated sparkle4j 0.5.0 for automatic update checking on startup
- Checks appcast feed in background, shows update dialog when new version is available

#### CI Screenshots
- `ScreenshotTour` tool generates UI screenshots automatically in CI
- `Dockerfile.screenshots` + `screenshots.yml` workflow uploads screenshots as artifacts on every push

### Fixed
- Maven resource filtering no longer corrupts binary files (`.ico`, `.png`, `.bmp`)
- jpackage `--app-version` mapped from `0.x.y` to `1.x.y` on macOS (Apple requires major version >= 1)
- GitHub Packages auth for sparkle4j dependency in all CI workflows
- PowerShell 5.1 compatibility for `Join-Path` in build scripts
- System tray notification icon uses real app icon instead of blank 16x16

### Dependencies
- Added `io.github.sparkle4j:sparkle4j:0.5.0` — in-app update checking

---

## [0.6.0] — 2026-04-03

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
