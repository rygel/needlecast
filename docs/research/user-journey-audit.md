# User Journey Audit

> Date: 2026-06-08
> Phase: Phase 2 Entry — User-Facing Polish
> Target persona: Developer using AI coding CLIs

## Overview

Audit of all user-facing panels in Needlecast, evaluating from the perspective of a developer working with AI coding CLIs (Claude Code, etc.). Findings are rated by severity and include concrete improvement suggestions.

## Prioritised Backlog

### P1 — Must fix before Phase 2 polish

1. **P1: File table sorts lexicographically** — ExplorerPanel `getColumnClass` returns `String::class.java` for all columns, so "10 KB" sorts before "2 KB". Needs typed column classes or a custom comparator.
2. **P1: Git log loading state** — `loadProject` clears the log list without showing a loading state. User sees a blank panel with no feedback.
3. **P1: Settings buttons are redundant** — Close and Apply both just `dispose()`. Confusing UX that violates standard dialog expectations. Remove Apply, rename Close → Done, or differentiate behavior.

### P2 — Important polish

4. **P2: No empty state in project tree** — When no projects are added, user sees a blank JTree area. Needs a "Add a project to get started" placeholder with a call-to-action button.
5. **P2: Commit message field is single-line** — `JTextField` instead of `JTextArea`. Git commit conventions expect a subject line + body. Switch to `JTextArea` with Ctrl+Enter to commit.
6. **P2: No file search in explorer** — Project tree has a filter field. Explorer has none. Users navigating large directories need a quick-filter. Add a filter field above the file table.
7. **P2: Tab close buttons are too small** — `ri-close-line` at 12px on high-DPI displays. Increase to 14-16px.
8. **P2: Diff panel has no file navigation** — No Previous/Next file buttons in multi-file diffs. Add arrow buttons or keyboard shortcuts.
9. **P2: No "Restore Defaults" in settings** — No way to reset all settings to factory defaults. Add a "Restore Defaults" button to the settings dialog footer.
10. **P2: Skills panel search doesn't debounce** — Triggers on every keystroke. Add a 100-200ms debounce timer (follow ProjectTreePanel pattern).

### P3 — Nice to have

11. **P3: No manual terminal restart button** — Autorestart exists but no manual restart UI. Add a "Restart" button to terminal toolbar.
12. **P3: No diff word-level highlighting** — Only line-level diff markers. Implementing inline word diff would significantly improve code review UX.
13. **P3: No git branch selector** — GitLogPanel doesn't show or switch branches. Add branch display to toolbar and branch-switching UI.
14. **P3: No "Select encoding" in editor** — Charset auto-detection is good but unoverridable. Add encoding selector to editor status bar or "Open with encoding" menu.
15. **P3: No skill categories/folders** — Flat list becomes unwieldy with many skills. Add grouping or folder support.

---

## Panel-by-Panel Findings

### 1. ProjectTreePanel

**Purpose:** Primary navigation hub — hierarchical project tree with agent status, git info, and CRUD operations.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 1.1 | **No empty state** | P2 | Add "Add a project to get started" placeholder with a add-project button when tree is empty |
| 1.2 | **BlinkOn state never reset** | P3 | `blinkOn` is never set to `false` when `blinkTimer.stop()` is called — could leave blinking indicator stuck |
| 1.3 | **Row height = 0 causes jitter** | P3 | Variable row heights cause layout/scroll jitter on large trees. Consider a fixed height with fallback |
| 1.4 | **No keyboard context menu** | P3 | No Enter-key handler for context menu or activation on selected node |

**Well done:** Rich tooltip system, async scanning pipeline, privacy mode, drag-and-drop, git status caching, comprehensive context menus.

---

### 2. TerminalPanel

**Purpose:** Embedded PTY terminal using JediTerm. Auto-starts shell per project, detects agent status.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 2.1 | **No manual restart button** | P3 | Add "Restart" button to terminal toolbar for manual session restart |
| 2.2 | **Spinner/silence heuristics imperfect** | P3 | Output without a spinner character can trigger both silence timer restart AND THINKING transition — can race |
| 2.3 | **Daemon thread has no name** | P3 | Name the startShell thread for easier debugging in thread dumps |
| 2.4 | **No copy shortcut integration** | P3 | Custom paste dispatcher exists but no explicit copy handling (JediTerm's selection copy is implicit) |

**Well done:** Agent status heuristics, smart paste handling, input method support, comprehensive font/theme control, reflective color synchronization, Claude session detection, graceful EOF auto-restart.

---

### 3. ExplorerPanel

**Purpose:** File browser with tabbed document viewer. Navigates filesystem, opens files in editors/viewers.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 3.1 | **File table sorts lexicographically** | **P1** | Implement `getColumnClass()` to return `Integer.class` for Size, `Date.class` for Modified, or provide custom `Comparator` |
| 3.2 | **No file search/filter** | P2 | Add a filter text field above the file table (pattern match on filename) |
| 3.3 | **Tab close button too small** | P2 | Increase `ri-close-line` from 12px to 14-16px for high-DPI usability |
| 3.4 | **No file preview panel** | P3 | Add a preview pane (VS Code-style) that shows file content without opening a tab |
| 3.5 | **dateFmt is not thread-safe** | P3 | Replace shared `SimpleDateFormat` with `ThreadLocal` or `DateTimeFormatter` (safe across SwingWorker invocations) |
| 3.6 | **No "New File" root entry** | P3 | Add a button/toolbar action for creating new files outside a directory context |

**Well done:** Sort indicators, per-project sort persistence, rich file-typing routing, thorough drag-and-drop, unsaved changes check, privacy mode, tab context menus, keyboard shortcuts, honest alpha warnings.

---

### 4. EditorPanel

**Purpose:** Syntax-highlighting code editor via RSyntaxTextArea. Supports 20+ languages, find/replace, external editor integration.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 4.1 | **No "Select encoding"** | P3 | Add a combo box or menu for overriding charset auto-detection |
| 4.2 | **No auto-save** | P3 | Add configurable auto-save (e.g., every 30s or on focus loss) |
| 4.3 | **Hardcoded theme resource paths** | P3 | `"monokai.xml"` / `"idea.xml"` strings have no fallback — if resource is missing, theme silently fails |
| 4.4 | **No minimap or scrollbar preview** | P3 | RTextScrollPane doesn't show a minimap — nice-to-have for large file navigation |

**Well done:** Charset detection, chunked text loading for large files, 2 MB file size limit with user feedback, atomic save via `.tmp` + `ATOMIC_MOVE`, cross-platform monospace font detection, unsaved changes dialog, Ctrl+S/F/H, external editor dropdown, RSTA theme + FlatLaf override.

---

### 5. SettingsDialog

**Purpose:** Modal settings dialog with sidebar navigation and card-based panels.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 5.1 | **Close and Apply buttons are redundant** | **P1** | Since settings are live, Close and Apply both `dispose()`. Remove Apply, rename Close → Done, or add Cancel to revert unsaved changes |
| 5.2 | **No "Restore Defaults"** | P2 | Add a "Restore Defaults" button in dialog footer or per-panel reset |
| 5.3 | **Sidebar width is fixed 160px** | P3 | Make sidebar width resizable via a divider or increase default to 180-200px for readability |
| 5.4 | **Sidebar uses hardcoded uppercase headers** | P3 | "GENERAL", "INTEGRATIONS", "ADVANCED" — consider styled headers instead of hardcoded uppercase |

**Well done:** Live settings application, clean sidebar/list with non-selectable headers, CardLayout for panel switching, separated panel classes, SettingsCallbacks coordination.

---

### 6. MainWindow

**Purpose:** Application window orchestrating all panels. Manages lifecycle, shortcuts, updates, tour, and shutdown.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 6.1 | **System.exit in finally** | P2 | `System.exit(0)` in `windowClosing` finally block prevents cleanup if dispose throws. Use graceful degradation |
| 6.2 | **No recent files/projects** | P3 | Add a "Recent Projects" list to File menu for quick access |
| 6.3 | **CWD auto-detection is surprising** | P3 | Silently adds CWD to project tree without confirmation. Consider a dialog: "Detected git repo at /path. Add to tree?" |
| 6.4 | **No command palette** | P3 | Ctrl+Shift+P-style command palette would accelerate navigation for keyboard-oriented users |

**Well done:** Panel coordination architecture, EDT stall monitoring, config import/export, Sparkle4j update checking, first-run tour, CWD detection, window geometry persistence, keyboard shortcut rebinding, graceful shutdown.

---

### 7. SkillsPanel

**Purpose:** Manage reusable prompt/instruction snippets deployed to project directories.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 7.1 | **Search filter doesn't debounce** | P2 | Add 100-200ms debounce timer (follow ProjectTreePanel pattern) |
| 7.2 | **No batch deploy** | P3 | Skills deploy one at a time — add "Deploy All" button |
| 7.3 | **No skill categories** | P3 | Flat list becomes unwieldy. Add folder/category grouping |
| 7.4 | **No deploy confirmation** | P3 | Deploy/Undeploy is single-click with no undo — add confirmation dialog |

**Well done:** Deployment status visual feedback, context-sensitive button states, clean SkillEntry model, edit dialog, project tree update helpers.

---

### 8. DocViewerPanel

**Purpose:** Discover and open project documentation (Doxygen, Javadoc, Rustdoc) in browser.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 8.1 | **No in-app preview** | P3 | Only opens in system browser — consider embedded WebView |
| 8.2 | **No custom doc paths** | P3 | Only `DocRegistry.targetsFor()` entries are shown — no way to add custom documentation paths |
| 8.3 | **Selection flicker on headers** | P3 | Selecting a header/placeholder row triggers visual flicker before being cleared |

**Well done:** Availability detection (checks file exists), unavailable entries shown with generation command tooltip, category headers with fill/hollow indicators, empty states ("No project selected", "No documentation targets"), thread-safe reload, dismissible hints.

---

### 9. GitLogPanel

**Purpose:** Git commit history, staging, and remote operations (fetch/push/pull).

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 9.1 | **No loading state** | **P1** | `loadProject` clears the list but shows no spinner or "Loading..." text. Use a placeholder |
| 9.2 | **Commit message is single-line** | P2 | `JTextField` is unsuitable for commit messages with body. Use `JTextArea` with Ctrl+Enter to submit |
| 9.3 | **No branch display/switch** | P3 | No current branch indicator or branch switcher in toolbar |
| 9.4 | **No refresh button** | P3 | Log only loads on project selection — no manual refresh |
| 9.5 | **Full hash shown in log** | P3 | 40-char full hash is clipped — use 7-char abbreviated hash |
| 9.6 | **No git stash support** | P3 | Stash/pop not available |
| 9.7 | **Commit message has no amend option** | P3 | No "Amend previous commit" checkbox |

**Well done:** Three-view card layout, streaming output, exit code display, default "all files checked", commit message validation (red border), button disabling during operations, color-coded file status, one-time help popup.

---

### 10. DiffContentPanel

**Purpose:** Side-by-side and unified git diff display.

| # | Finding | Severity | Suggestion |
|---|---------|----------|------------|
| 10.1 | **No file navigation** | P2 | No Previous/Next file buttons for multi-file diffs — add arrow buttons or keyboard shortcuts (n/p) |
| 10.2 | **No word-level diff** | P3 | Only line-level markers. Adding inline word diff highlights (like GitHub's) would significantly improve code review |
| 10.3 | **No diff statistics** | P3 | Adds/removals count not shown in panel (though `DiffStats` exists in model) |
| 10.4 | **Divider size is 2px** | P3 | Very thin — hard to grab with mouse. Increase to 5-6px |

**Well done:** Side-by-side and unified toggle, line number gutters, synchronized scrolling, binary file handling, empty states, hunk position navigation.

---

## Cross-Cutting Recommendations

1. **Loading states everywhere** — Every panel that loads async data should show a placeholder or loading indicator. Currently only a few do.
2. **Keyboard navigation audit** — Most panels rely on mouse. Add keyboard equivalents for all primary actions.
3. **Consistent confirm/cancel patterns** — Standardize on one dialog pattern across the app (currently uses OK_CANCEL, YES_NO, and custom buttons interchangeably).
4. **Accessibility baseline** — At minimum, set `AccessibleContext` properties on primary components.
5. **i18n groundwork** — Some panels use `ctx.i18n`, most use hardcoded strings. Not urgent but worth noting before the codebase grows further.
