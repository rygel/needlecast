<p align="center">
  <img src="desktop/src/main/resources/icons/needlecast.png" alt="Needlecast" width="128">
</p>

<h1 align="center">Needlecast</h1>

<p align="center">
  A Swing-based project launcher for developers. Organize projects into groups, run build commands, open terminals, browse files, and track git status — all from one window.
</p>

<p align="center">
  <a href="https://github.com/rygel/needlecast/actions/workflows/ci.yml"><img src="https://github.com/rygel/needlecast/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/status-stable-green.svg" alt="Status">
</p>

## Features

**Project management**
- Organize projects into color-coded groups with a tree-style sidebar
- Fuzzy project switcher (`Ctrl+P`) across all groups
- Git branch and dirty-state indicator per project
- File watcher auto-refreshes command list when build files change
- Environment variables per project, injected into commands and terminals
- File explorer automatically switches to the active project's directory

**Commands**
- Auto-detects Maven, Gradle, npm, .NET, IntelliJ run configs, and APM projects
- Command queue — chain commands to run sequentially (clean → build → run)
- Command history with re-run support (last 20 per project)
- Desktop notification when a command finishes in the background

**Terminal**
- Embedded JediTerm terminal per project
- Multiple tabs per project
- Configurable font size (`Ctrl+scroll`)
- Terminal colors automatically inherit the active UI theme; override per-color in Settings

**Editor**
- Syntax-highlighted editor (RSyntaxTextArea) with multiple tabs
- Selectable syntax theme (Monokai, Eclipse, IntelliJ IDEA, VS, and more) — Settings → Layout & Terminal
- Font zoom (`Ctrl+scroll`, 6–72 pt); zoom survives theme switches
- Find & Replace (`Ctrl+F` / `Ctrl+H`)
- File explorer with right-click context menu (create, rename, delete, copy path)
- Show/hide hidden files toggle
- Tab right-click menu: Close, Close All to the Left, Close All to the Right, Close All

**Image & SVG viewer**
- Click any image file to open an inline viewer (JPEG, PNG, WebP, GIF, BMP, TIFF, ICO)
- Click `.svg` files for a vector viewer — stays crisp at any zoom level
- `Ctrl+scroll` to zoom, double-click to reset to fit
- Re-clicking a file that has changed on disk reloads it automatically

**AI & Prompts**
- Prompt Library — reusable templates with `{variable}` substitution, paste into active terminal
- AI Tools menu with auto-detected CLI tools (Claude, Gemini, Codex, apm, …)

**Appearance & Layout**
- Follows OS dark/light theme automatically (FlatLaf with 30+ bundled themes)
- Dockable, resizable panels — layout persists across sessions
- README preview below the command list when a project is selected
- Git log viewer with `git show` on click
- Keyboard shortcut editor — rebind any default shortcut

## Screenshots

Screenshots are auto-generated in CI on every push. Download the latest from the [Screenshots workflow](https://github.com/rygel/needlecast/actions/workflows/screenshots.yml) artifacts.

## Requirements

- Java 21 or later
- Windows, macOS, or Linux

## Running

```bash
mvn -pl desktop compile exec:java -Dexec.mainClass=io.github.rygel.needlecast.MainKt
```

Or build a JAR first:

```bash
mvn -pl desktop -am package -DskipTests
java -jar desktop/target/needlecast-desktop-0.6.1.jar
```

## Building from source

```bash
git clone https://github.com/rygel/needlecast.git
cd needlecast
mvn -pl desktop -am package -DskipTests
```

Run the full test suite (non-UI tests only — UI tests require Xvfb):

```bash
mvn -pl desktop test -T 4 -Dexcludes="**/*UiTest.java,**/*UiTest.kt"
```

> Full Swing/desktop UI tests require Xvfb — see [CONTRIBUTING.md](.github/CONTRIBUTING.md) for the container-based setup.

## Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+P` | Project switcher (fuzzy search across all groups) |
| `Ctrl+T` | Focus / open terminal |
| `F5` | Rescan projects |
| `Ctrl+1` | Focus project list |
| `Ctrl+2` | Focus command list |
| `Ctrl+3` | Focus console |
| `Ctrl+F` | Find in console output or editor |
| `Ctrl+H` | Replace in editor |
| `Ctrl+scroll` | Zoom editor or terminal font |

Shortcuts can be rebound in **Settings → Shortcuts**.

## Configuration

Config is stored in `~/.needlecast/config.json` and migrated automatically on version upgrades.

### Environment variables per project

Open a project's context menu → **Environment Variables**. Key/value pairs are injected into every command and terminal session for that project.

## Logging

Log output goes to `~/.needlecast/needlecast.log` (rotates at 10 MB, keeps 5 archives). Warnings and errors are also printed to stderr.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Contributing

See [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License

MIT — see [LICENSE](LICENSE).
