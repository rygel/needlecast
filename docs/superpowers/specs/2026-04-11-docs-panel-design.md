# Docs Panel — Design Spec

**Date:** 2026-04-11  
**Status:** Approved

---

## Overview

A dockable **Docs** panel that discovers all Markdown files in the active project and lets the user browse them in either a rendered HTML view or a syntax-highlighted raw view. The panel updates automatically when the active project changes.

---

## User-Facing Behaviour

- When a project is active, the panel scans its directory tree for `*.md` files.
- Files are listed in a left-hand pane: `README.md` (any case) pinned first, remainder sorted alphabetically by relative path.
- Clicking a file loads its content into the right-hand pane.
- A **Rendered / Raw** toggle in the toolbar switches between:
  - **Rendered** — markdown converted to HTML and displayed in a `JEditorPane` with inline CSS that respects the app's dark/light theme.
  - **Raw** — read-only `RSyntaxTextArea` with `SYNTAX_STYLE_MARKDOWN` highlighting.
- A **Refresh** button re-scans the project directory and reloads the current file.
- If no project is active, the panel shows a "No project selected" placeholder.
- If the project has no `.md` files, the panel shows "No Markdown files found".

---

## Architecture

### New file

`needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocsPanel.kt`

Single `JPanel(BorderLayout())` class. No external collaborators beyond `AppContext` (for theme state).

### Layout

```
┌─────────────────────────────────────────────────┐
│ [⟳ Refresh]               [● Rendered] [○ Raw]  │
├──────────────┬──────────────────────────────────┤
│ README.md    │                                  │
│ docs/        │   <right pane via CardLayout>    │
│   GUIDE.md   │   - JEditorPane (rendered)       │
│   ARCH.md    │   - RSyntaxTextArea (raw)        │
│ CHANGELOG.md │                                  │
└──────────────┴──────────────────────────────────┘
```

- Left: `JList<String>` in a `JScrollPane`. Entries are relative paths from the project root.
- Right: `CardLayout` container holding two cards — `"rendered"` (`JEditorPane` in a `JScrollPane`) and `"raw"` (`RSyntaxTextArea` in a `RTextScrollPane`).
- The two components share the same content area; toggling swaps the visible card.

### Data flow

1. `loadProject(path: String?)` is called by `MainWindow` on project change.
2. Panel walks the project tree for `*.md` files (max depth: unbounded, but skips `.git/`, `target/`, `node_modules/`, `build/`, `.gradle/`).
3. File list is sorted: `README.md` variants first, then alphabetical.
4. On file selection, the file is read from disk.
   - If in rendered mode: `commonmark-java` converts to HTML → injected into `JEditorPane`.
   - If in raw mode: text is set on `RSyntaxTextArea`.
5. Conversion result is cached per file path + last-modified timestamp. Switching between rendered/raw for the same file does not re-read disk if the cache is warm.
6. Toggle re-renders the current file if switching to a view that hasn't been populated yet.
7. Refresh clears the cache, re-scans, and reloads the current file.

### Theme integration

`JEditorPane` content is wrapped in an HTML template with inline CSS:

```html
<html><head><style>
  body { background: #1e1e1e; color: #d4d4d4; font-family: sans-serif; ... }
  code { background: #2d2d2d; }
  pre  { background: #2d2d2d; padding: 8px; }
  a    { color: #4fc3f7; }
</style></head><body>…</body></html>
```

Colours are read from `UIManager` at render time (`"Panel.background"`, `"Label.foreground"`, `"TextArea.background"`) so they match whichever FlatLaf theme is active.

---

## Dependencies

Added to `needlecast-desktop/pom.xml`:

```xml
<dependency>
  <groupId>org.commonmark</groupId>
  <artifactId>commonmark</artifactId>
  <version>0.22.0</version>
</dependency>
<dependency>
  <groupId>org.commonmark</groupId>
  <artifactId>commonmark-ext-gfm-tables</artifactId>
  <version>0.22.0</version>
</dependency>
<dependency>
  <groupId>org.commonmark</groupId>
  <artifactId>commonmark-ext-gfm-strikethrough</artifactId>
  <version>0.22.0</version>
</dependency>
```

---

## MainWindow integration

`MainWindow.kt` changes (minimal, same pattern as every other panel):

1. Instantiate: `private val docsPanel = DocsPanel(ctx)`
2. Wrap: `private val docsDockable = DockablePanel(docsPanel, "docs", "Docs")`
3. Register: `Docking.registerDockable(docsDockable)` alongside the others.
4. Add to `allDockables` list.
5. Default layout: docked into the same tab group as `searchDockable` / `logViewerDockable`.
6. Call `docsPanel.loadProject(path)` from the `onProjectSelected` callback.

---

## Error handling

| Situation | Behaviour |
|---|---|
| No project selected | Placeholder label: "No project selected" |
| Project has no `.md` files | Placeholder label: "No Markdown files found in this project" |
| File read error | Error message shown in content area: "Could not read file: `<path>`" |
| Markdown parse error | Falls back to raw text display with a warning label |

---

## Out of scope

- Editing markdown files (read-only panel)
- Search within docs (covered by the existing Search panel)
- Watching for file changes on disk (Refresh button is sufficient)
- Rendering remote/GitHub-hosted markdown (project files only)
