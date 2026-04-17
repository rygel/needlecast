# Code Editor

## Overview

Needlecast includes an embedded code editor based on **RSyntaxTextArea**, supporting syntax highlighting for 20+ languages. It handles files up to **2 MB**; larger files show a placeholder and cannot be edited. The editor is intended for quick edits and code inspection — not as a full IDE replacement.

---

## Opening Files

- **Single-click** a file in the [[File Explorer]] → opens in a new editor tab
- **Double-click** a [[Search]] result → opens at the exact line and column
- After a [[Search]], the caret is positioned and the view scrolled to the match

---

## Multi-Tab Interface

Each open file gets a tab. The tab shows the filename; modified files show `* filename`.

### Tab Context Menu (right-click a tab)

| Item | Action |
|---|---|
| **Close** | Close this tab |
| **Close All to the Left** | Close all tabs left of this one |
| **Close All to the Right** | Close all tabs right of this one |
| **Close All** | Close every open tab |

Closing a modified tab opens a **Save / Discard / Cancel** confirmation dialog. When the application exits, all open editors with unsaved changes are checked — you get the same dialog for each.

---

## Toolbar

The editor toolbar has two controls:

| Control | Action |
|---|---|
| **Save** | Saves the current file |
| **Open with ▼** | Dropdown listing configured external editors (VS Code, Zed, IntelliJ IDEA, and any [[Settings#External Editors|custom editors]]). Launches the selected editor with the file path. |

> [!note]
> The external editor launch passes only the **file path** — line number is not passed. There is no right-click context menu in the editor area.

---

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+S` / `Cmd+S` | Save file |
| `Ctrl+F` / `Cmd+F` | Open find bar |
| `Ctrl+H` / `Cmd+H` | Open find & replace bar |
| `Escape` | Close find bar |
| `Enter` | Next match (find bar open) |
| `Shift+Enter` | Previous match (find bar open) |
| `Ctrl+Scroll` | Zoom font size (6–72 pt) |

Standard RSyntaxTextArea shortcuts also apply: `Ctrl+Z` undo, `Ctrl+Y` / `Ctrl+Shift+Z` redo, `Ctrl+A` select all.

---

## Find Bar

Press `Ctrl+F` to open the inline find bar at the bottom of the editor.

| Element | Detail |
|---|---|
| Search field | Auto-fills with selected text when the bar opens |
| ▲ / ▼ buttons | Previous / next match |
| **Aa** checkbox | Match case |
| **\b** checkbox | Whole word (disabled when Regex is on) |
| **.\*** checkbox | Regular expression (disables Whole word) |
| Status indicator | ✓ (green) = found, "Not found" (red) |

## Find & Replace Bar

Press `Ctrl+H` to open the combined find + replace bar.

| Element | Detail |
|---|---|
| **Replace** button | Replaces the current match |
| **Replace All** button | Replaces all matches; status shows replacement count |

Same search options (case, whole word, regex) apply.

---

## File Saving

Files are saved with an **atomic write**:

1. Content is written to `<filename>.tmp` in the same directory
2. `Files.move(..., StandardCopyOption.ATOMIC_MOVE)` replaces the original
3. Encoding is always **UTF-8**

If the write fails, an error dialog is shown and the original file is untouched.

---

## Encoding Detection (Reading)

Files are read by trying these charsets in order:

1. **UTF-8** — tried first
2. **OS native encoding** — e.g. `windows-1252` on Windows, `cp1252`
3. **ISO-8859-1** — always succeeds (never throws)

Writes are always UTF-8 regardless of the charset used to read.

---

## Large File Limit

Files larger than **2 MB** display a placeholder message:

```
[File too large to display (XXX KB > 2 MB limit)]
```

The file cannot be edited. Open it in an external editor instead.

---

## Syntax Highlighting

Language is auto-detected from the file extension:

| Extension(s) | Language |
|---|---|
| `.kt`, `.kts` | Kotlin |
| `.java` | Java |
| `.xml`, `.pom` | XML |
| `.html`, `.htm` | HTML |
| `.json` | JSON |
| `.js`, `.mjs` | JavaScript |
| `.ts` | TypeScript |
| `.css` | CSS |
| `.py` | Python |
| `.sh`, `.bash` | Shell script |
| `.bat`, `.cmd` | Windows Batch |
| `.sql` | SQL |
| `.yaml`, `.yml` | YAML |
| `.properties` | Properties |
| `.cs` | C# |
| `.c`, `.h` | C |
| `.cpp`, `.cxx`, `.hpp` | C++ |
| `.go` | Go |
| `.rs` | Rust |
| `.rb` | Ruby |
| `.md` | Markdown |
| `.gradle` | Groovy |
| Other | No highlighting |

---

## Editor Features

| Feature | State |
|---|---|
| **Code folding** | Enabled (click gutter markers to fold/unfold) |
| **Line numbers** | Always shown in gutter |
| **Current line highlight** | On |
| **Bracket matching** | On |
| **Tab size** | 4 spaces (fixed) |
| **Auto-indent** | Provided by RSyntaxTextArea |
| **Auto-close brackets** | Provided by RSyntaxTextArea |
| **Word wrap** | Off — not exposed in UI |
| **Undo/redo** | RSyntaxTextArea default (large history) |

---

## Font

Configured in **[[Settings]] → Layout & Terminal → Editor font**.

**Font size range:** 6–72 pt (default 12 pt). `Ctrl+Scroll` adjusts temporarily; the size is persisted.

**Platform default font priority** (applied when family is "Auto"):

| Platform | Priority |
|---|---|
| **Windows** | Cascadia Mono → Cascadia Code → JetBrains Mono → Fira Code → Consolas → Lucida Console |
| **macOS** | SF Mono → Menlo → JetBrains Mono → Fira Code → Monaco → Courier New |
| **Linux** | JetBrains Mono → Fira Code → DejaVu Sans Mono → Liberation Mono → Noto Mono → Ubuntu Mono |
| Fallback | `Font.MONOSPACED` |

---

## Syntax Theme

Configured in **[[Settings]] → Syntax Theme**:

| Key | Description |
|---|---|
| `auto` | Follows app dark/light mode |
| `monokai` | Monokai (dark) |
| `dark` | Generic dark |
| `druid` | Druid (dark) |
| `idea` | IntelliJ IDEA (light) |
| `eclipse` | Eclipse (light) |
| `default` | Default (light) |
| `default-alt` | Default Alt (light) |
| `vs` | Visual Studio (light) |

---

## Related

- [[File Explorer]] — browsing and opening files
- [[Search]] — finding files and jumping to match locations
- [[Settings]] — font family/size, syntax theme, external editors
- [[Media Viewers]] — non-text file handling (images, SVG, audio/video)
