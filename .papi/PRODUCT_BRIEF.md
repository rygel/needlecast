# Needlecast

> A lightweight desktop shell for developers who live in AI coding CLIs — organize projects, edit files, run builds, and manage terminals without the overhead of a full IDE.

---

## TL;DR (30 seconds)

Needlecast is a Kotlin/JVM Swing desktop app (v0.7.3) that wraps multiple AI coding CLIs — Claude, Codex, Opencode, Kilocode — into a unified project management environment. It provides an embedded JediTerm terminal, RSyntaxTextArea editor, media viewer, git integration, and auto-detection of build tools across 14+ language ecosystems. Built for solo developers juggling dozens of AI-generated projects who need something lighter than VS Code.

---

## Target Users

Solo developers and small teams who primarily interact with code through AI/vibe coding CLIs. They create many small projects rapidly, need to switch between them constantly, and want basic file editing, terminal access, and build running without spinning up a heavyweight IDE. They're on Windows, macOS, or Linux.

---

## What Problems Does This Solve?

- **Project chaos** — 100+ AI-generated projects with no organizer; Needlecast provides a tree-style sidebar with color-coded groups, fuzzy switching, and git status at a glance
- **CLI context switching** — launching and managing multiple AI coding assistants (Claude, Codex, Opencode) from one window instead of scattered terminals
- **Lightweight file viewing** — syntax-highlighted editor, image/SVG/video viewer, and markdown renderer for quick inspection without opening a full IDE
- **Build tool fragmentation** — auto-detects build tools and commands across 14+ ecosystems (Maven, Gradle, npm, Cargo, Go, .NET, etc.) with zero configuration
- **Terminal multiplexing** — embedded JediTerm with multiple tabs per project, theme-aware colors, and proper PTY support (ConPTY on Windows, native Unix PTY elsewhere)

---

## Build Sequence

<!-- PHASES:START -->

```yaml
phases:
  - id: phase-0
    slug: "setup"
    label: "Project Setup"
    description: "Project setup, scaffolding, and initial architecture"
    status: "Done"
    order: 0
  - id: phase-1
    slug: "core-stability"
    label: "Core Stability & Hardening"
    description: "Improve test coverage, fix known bugs, harden the process execution layer, and ensure reliable cross-platform terminal behavior"
    status: "Not Started"
    order: 1
  - id: phase-2
    slug: "editor-and-explorer"
    label: "Editor & Explorer Enhancements"
    description: "Improve the file explorer and editor: better search, more file type support, improved tab management, and find-in-files"
    status: "Not Started"
    order: 2
  - id: phase-3
    slug: "ai-integration"
    label: "AI CLI Integration"
    description: "Deepen integration with AI coding CLIs: better status display, usage tracking, hook server, and multi-agent workflow support"
    status: "Not Started"
    order: 3
  - id: phase-4
    slug: "web-companion"
    label: "Web Companion"
    description: "Expand the needlecast-web Javalin backend for browser-based project management and remote terminal access"
    status: "Not Started"
    order: 4
  - id: phase-5
    slug: "distribution"
    label: "Distribution & Packaging"
    description: "Cross-platform installers (jpackage, Inno Setup), auto-update via sparkle4j, code signing, and release automation"
    status: "Not Started"
    order: 5
```

<!-- PHASES:END -->

---

## Decisions Locked

*No decisions locked yet. These are added as planning cycles confirm strategic choices.*

---

## Ponytail Audit Findings (2026-06-12)

*One-shot whole-repository audit for unnecessary complexity. Does not include correctness bugs, security holes, or performance problems.*

### Dependency Savings (2 removable)

| # | Tag | Finding | File(s) |
|---|-----|---------|---------|
| 1 | `delete:` | **guava** — zero source imports. Pure classpath bloat (jediterm pulls its own transitively). | `needlecast-desktop/pom.xml` |
| 2 | `delete:` | **mockk** (test) — zero imports in any test file. JUnit + assertj already cover needs. | `needlecast-desktop/pom.xml` |
| 3 | `shrink:` | **flatlaf-extras** — 0 source imports. May provide runtime SVG icon loading via FlatLaf internals. Investigate before removing. | `needlecast-desktop/pom.xml` |
| 4 | `shrink:` | **jediterm-typeahead** — 0 source imports. Likely transitive runtime dep of jediterm-pty. Investigate before removing. | `needlecast-desktop/pom.xml` |

### Abstraction Savings (2 collapsible)

| # | Tag | Finding | File(s) |
|---|-----|---------|---------|
| 5 | `yagni:` | **CommandRunner** interface — 1 prod impl (`ProcessCommandRunner`), zero test fakes, never swapped. Inline the concrete class. | `process/CommandRunner.kt` |
| 6 | `yagni:` | **ConfigStore** interface — 1 prod impl (`JsonConfigStore`) + 2 tooling-only impls (in `tools/`, flagged for deletion below). Collapse after tools/ removal. | `config/ConfigStore.kt` |

### Code-Level Savings (3 findings)

| # | Tag | Finding | File(s) |
|---|-----|---------|---------|
| 7 | `shrink:` | **Scanner `cmd()` duplication** — identical `IS_WINDOWS` ternary copy-pasted across ~15 scanner files (CMake, Zig, Swift, Elixir, Ruby, Go, PHP, Rust, Dart, .NET, SBT, APM). Extract to `ProjectScanner` companion or shared `ScannerUtils`. | `scanner/*.kt` (~15 files) |
| 8 | `yagni:` | **Click-trace infrastructure** — `clickTraceForced`, `isClickTraceEnabled()`, `clickSeq`, `lastClickTimeNs` are diagnostic scaffolding guarded by system properties. ~30 lines. Remove if the bug is resolved. | `ui/ProjectTreePanel.kt:60-65` |
| 9 | `delete:` | **100+ `println`/`System.err.println`** — all in `tools/ScreenshotTour.kt`, flagged for deletion below. | `tools/ScreenshotTour.kt` |

### Structural Savings (11 findings)

| # | Tag | Finding | File(s) | Lines |
|---|-----|---------|---------|-------|
| 10 | `delete:` | **tools/ScreenshotTour.kt** — 126 KB debug scaffolding, largest file in the repo, 100+ println calls. | `tools/ScreenshotTour.kt` | 2,768 |
| 11 | `delete:` | **tools/ProjectTreeDebug.kt** — standalone debug harness for tree layout. | `tools/ProjectTreeDebug.kt` | 145 |
| 12 | `delete:` | **tools/CdsTraining.kt** — CI training entrypoint; belongs in `ci/` or `build/` script, not main source. | `tools/CdsTraining.kt` | 32 |
| 13 | `delete:` | **ui/DirectoryPanel.kt** — superseded by `ProjectTreePanel`, zero external references. | `ui/DirectoryPanel.kt` | 523 |
| 14 | `delete:` | **ui/GroupPanel.kt** — only referenced by dead `DirectoryPanel`. | `ui/GroupPanel.kt` | 265 |
| 15 | `delete:` | **ui/DragAndDrop.kt** — `DirectoryDragHandler`/`DirectoryDropHandler` only used by dead `DirectoryPanel`/`GroupPanel`. | `ui/DragAndDrop.kt` | 66 |
| 16 | `delete:` | **ui/renderers/CompactProjectDirectoryRenderer.kt** — only imported by dead `DirectoryPanel`. | `ui/renderers/CompactProjectDirectoryRenderer.kt` | 154 |
| 17 | `yagni:` | **model/ProjectGroup + service/ProjectService** — legacy migration types only imported by dead `DirectoryPanel`. Remove with dead UI cluster. | `model/AppConfig.kt`, `service/ProjectService.kt` | ~55 |
| 18 | `delete:` | **archive/needlecast-web/** — entire dead web module with compiled `target/classes/`. | `archive/needlecast-web/` | — |
| 19 | `delete:` | **docs/TODO.md** — all 27 items checked `[x]`. Archive or remove. | `docs/TODO.md` | — |
| 20 | `delete:` | **docs/ARCHITECTURE.md** — references deleted/renamed classes (`DirectoryPanel`, `FileExplorerPanel`), doesn't reflect current package structure (`ui/explorer/`, `ui/diff/`, `ui/settings/`, `ui/logviewer/`, `ui/components/`). Rewrite or delete. | `docs/ARCHITECTURE.md` | — |

### Bottom Line

- **~4,200+ lines removable** (tools/ + dead UI cluster + archive)
- **2 dependencies removable** (guava, mockk) with zero code changes
- **Biggest single win:** Delete `tools/ScreenshotTour.kt` — 2,768 lines of debug scaffolding
- **Architecture cleanup:** Extract shared scanner `cmd()` helper, collapse `CommandRunner` interface