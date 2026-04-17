# Needlecast Wiki

> **Needlecast** is a polyglot project launcher and workspace manager for developers who work across multiple projects and technology stacks.

It provides a single window for launching builds, tailing logs, searching code, managing dependencies, inspecting git history, and driving an embedded terminal — with zero per-project configuration for 14+ build ecosystems.

---

## Quick Navigation

### Getting Started
- [[Getting Started]] — Prerequisites, first launch, adding a project
- [[UI Overview]] — Panel layout, menu bar, docking, status bar

### Core Features
- [[Project Management]] — Groups, folders, color stripes, tags, per-project shell/env, fuzzy switcher, agent LED
- [[Build Tool Detection]] — Auto-detected commands for 14+ ecosystems
- [[Command Execution]] — Running commands, queue, history, console, notifications
- [[Terminal]] — PTY terminal, multi-tabs, shell picker, Claude Code hooks, AI status detection
- [[Code Editor]] — Syntax highlighting, find/replace, atomic save, large-file handling
- [[File Explorer]] — Table-based file browser, context menus, drag-and-drop
- [[Log Viewer]] — Live tailing, log format parsing, level filters, incremental search
- [[Dependency Updates]] — Renovate-powered scanning and patching
- [[Search]] — Find in files with ripgrep, 10k results, exact line/column navigation
- [[Git Integration]] — Branch/dirty badge in tree, 40-commit log with full diffs
- [[Prompt Library]] — 27 AI prompts with full body text, 34 command templates, placeholder substitution, 17 AI CLIs
- [[Docs Panel]] — CommonMark Markdown browser with rendered HTML and raw-source mode
- [[APM Integration]] — Agent Package Manager scanner and settings tab

### Reference
- [[Settings]] — All 7 settings tabs documented, all 27 themes listed
- [[Keyboard Shortcuts]] — Default bindings by panel
- [[Media Viewers]] — Image (5%–3200% zoom), SVG, and VLC-based media player
- [[Configuration File]] — Complete `config.json` field reference and backup behaviour
- [[Update Checker]] — Automatic and manual update checking via GitHub releases

---

## Feature Quick-Reference

| Scenario | Where to look |
|---|---|
| Jump between dozens of projects instantly | [[Project Management#Fuzzy Project Switcher\|Ctrl+P fuzzy switcher]] |
| Run a build without opening a terminal | [[Command Execution]] |
| Chain clean → build → test → package | [[Command Execution#Command Queue\|Command queue]] |
| See which AI agent is thinking | [[Project Management#Agent Status LED\|Agent LED in Project Tree]] |
| Tail a log file live | [[Log Viewer]] |
| Search a word across the whole codebase | [[Search]] |
| See what the last 40 commits changed | [[Git Integration]] |
| Safely bump all patch dependencies | [[Dependency Updates]] |
| Send a canned prompt to Claude | [[Prompt Library]] |
| Repair a project with a moved directory | [[Project Management#Missing Project Directories\|Drag-and-drop repair]] |
| Override the shell for one project | [[Project Management#Shell Settings\|Per-project Shell Settings]] |

---

## Configuration

All settings are stored in `~/.needlecast/config.json`. Up to 5 rotated backups are kept. The docking layout is in `~/.needlecast/docking-layout.xml`. See [[Settings]] for a full reference.
