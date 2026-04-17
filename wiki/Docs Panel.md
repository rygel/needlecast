# Docs Panel

## Overview

The **Docs Panel** is a dockable panel that functions as a Markdown file browser with rendered HTML output. It lets you read documentation (README files, wiki pages, architecture docs) directly inside Needlecast without switching to a browser.

---

## What It Does

- Browses Markdown files (`.md`) in the active project
- Renders Markdown to HTML using the **CommonMark** library
- Caches rendered HTML for performance
- Falls back to a **raw mode** with syntax highlighting for viewing Markdown source

---

## Layout

| Element | Description |
|---|---|
| **File browser** (left pane) | Tree or list of `.md` files discovered in the project |
| **Rendered view** (right pane) | HTML rendering of the selected Markdown file |
| **Raw toggle** | Switch between rendered HTML and highlighted Markdown source |

---

## Accessing the Panel

Toggle visibility via **Panels → Docs** in the menu bar.

---

## Rendering

Markdown is converted to HTML using **CommonMark**, the standard CommonMark spec implementation. The rendered HTML is:
- Cached per file (cache invalidated when file changes)
- Displayed in a `JEditorPane` configured for `text/html`
- Styled to match the active application theme

**Raw mode** shows the Markdown source with syntax highlighting instead of rendered output — useful when you want to copy fenced code blocks or inspect the raw text.

---

## Notes

> [!note]
> The Docs Panel is distinct from the **README preview** in the Commands panel, which shows only the first 20 lines of a README as plain text. The Docs Panel renders the full file as HTML and lets you browse all `.md` files in the project.

---

## Related

- [[Command Execution#README Preview|README preview in Commands panel]] — plain-text 20-line preview
- [[Code Editor]] — for editing Markdown files
- [[File Explorer]] — for browsing all file types
