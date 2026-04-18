# Getting Started

## Prerequisites

Needlecast is a JVM desktop application.

**Required:**
- **Java 21+** on your `PATH`

**Recommended:**
- **ripgrep (`rg`)** — significantly faster [[Search]]
- **VLC 3.x** — required for the [[Media Viewers#Media Player|media player]]

**Optional:**
- **Renovate** — for [[Dependency Updates]] (installable from inside Settings)
- **APM** — Microsoft Agent Package Manager for managing AI skills

---

## First Launch

On first launch Needlecast creates `~/.needlecast/` containing:

| File | Purpose |
|---|---|
| `config.json` | All settings, projects, shortcuts, templates |
| `docking-layout.xml` | Panel positions and sizes |
| `needlecast.log` | Application log (10 MB rotation, 5 archives) |

The window opens with an empty Project Tree and the default panel layout. See [[UI Overview]] for a map of every panel.

---

## Adding Your First Project

**Method 1 — Right-click in the Project Tree:**
1. Right-click in the empty Project Tree → **New Folder…**
2. Give the group a name (e.g. *Work*, *Personal*)
3. Right-click the group → **Add Project…**
4. Browse to the root directory of your project → confirm

**Method 2 — Drag and drop:**
Drag any folder from your OS file manager and drop it onto the Project Tree. Needlecast adds it to the nearest group.

After adding, the [[Build Tool Detection|build scanner]] runs immediately and populates the Commands panel with detected build targets. No configuration is required.

---

## Selecting a Project

Click a project name in the tree to make it **active**. All panels update:

- **Commands** — shows detected build commands
- **Terminal** — switches working directory
- **Log Viewer** — discovers log files
- **Git Log** — shows commit history
- **Search** — scopes searches to this project
- **Renovate** — scans this project's dependencies

---

## Running Your First Command

1. Select a project.
2. In the Commands panel, **double-click** a command (e.g. `mvn test`).
3. Output streams into the Output Console below the terminal.
4. When it finishes, the status bar shows the exit code.

---

## Opening the Terminal

Press `Ctrl+T` or double-click a project in the tree. The terminal opens in the project's directory with any configured [[Project Management#Per-Project Settings|env vars and startup command]] applied.

---

## Configuring Appearance

- **Theme:** View → Dark Themes or Light Themes — 27 built-in themes
- **System (auto):** View → System (auto) — follows OS dark/light mode
- **Fonts:** [[Settings]] → Layout & Terminal → Fonts
- **Syntax highlighting:** [[Settings]] → Syntax Theme

---

## Next Steps

| Goal | Article |
|---|---|
| Organize projects into groups | [[Project Management]] |
| Understand detected commands | [[Build Tool Detection]] |
| Chain commands in sequence | [[Command Execution#Command Queue]] |
| Configure per-project shell and env | [[Project Management#Per-Project Settings]] |
| Find code across the project | [[Search]] |
| Watch logs as a service runs | [[Log Viewer]] |
| Bump outdated dependencies | [[Dependency Updates]] |
| Send a prompt to Claude in the terminal | [[Prompt Library]] |
| Change themes, fonts, shortcuts | [[Settings]] |
