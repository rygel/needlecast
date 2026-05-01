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