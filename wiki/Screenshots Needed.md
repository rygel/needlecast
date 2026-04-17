# Screenshots Needed — User Manual

Organized by manual section. Each entry describes the exact UI state to capture.

**Automation status:** 71 of 88 screenshots are captured automatically by `ScreenshotTour.kt` and committed to `docs/screenshots/` on every push to `develop`. 17 are skipped; see the reason column and the [Skipped screenshots](#skipped-screenshots) section at the bottom.

| Status | Meaning |
|---|---|
| ✅ Automated | Captured by `ScreenshotTour.kt` |
| ⏭ Skipped | Not automatable — see reason |

---

## 1. Overview & First Impressions

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 1.1 | `overview-full-window.png` | Complete main window with all default panels visible, a real project selected, terminal active, commands populated | Use dark theme (Dark Purple); show a realistic project state | ✅ Automated |
| 1.2 | `overview-light-theme.png` | Same layout with a light theme active | For theme comparison | ✅ Automated |
| 1.3 | `overview-empty-first-launch.png` | Window on first launch — empty project tree, no active project | Shows the blank-slate starting point | ✅ Automated |

---

## 2. Project Management

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 2.1 | `project-tree-overview.png` | Project Tree with 2–3 groups, each containing 2–3 projects; show color stripes, git branch badges, build tool badges, one active (green dot), one dirty (amber branch) | Key anatomy shot | ✅ Automated |
| 2.2 | `project-tree-row-anatomy.png` | Single project row with all elements labeled: color stripe, active dot, LED, project name, git badge, build tool badges, tags | Annotated diagram | ⏭ Skipped — requires post-processing to add callout labels; raw capture alone is not useful |
| 2.3 | `project-tree-context-menu-folder.png` | Right-click context menu on a folder | All items visible including "Advanced" submenu | ✅ Automated |
| 2.4 | `project-tree-context-menu-project.png` | Right-click context menu on a project | Show "▶ Activate Terminal", Tags, Shell Settings, Environment, Set Color, etc. | ✅ Automated |
| 2.5 | `project-tree-filter.png` | Filter field with text typed; some projects hidden | Shows live filtering behavior | ✅ Automated |
| 2.6 | `project-tree-missing-directory.png` | A project row with red text and ⚠ icon (missing directory) | | ✅ Automated |
| 2.7 | `project-tree-led-thinking.png` | Project row with blinking (or lit) THINKING LED | Capture mid-blink or use the amber state | ✅ Automated |
| 2.8 | `project-switcher-dialog.png` | Ctrl+P project switcher open, with a search term typed and results showing | Show breadcrumb paths in results | ✅ Automated |
| 2.9 | `dialog-shell-settings.png` | Shell Settings dialog open (Shell + Startup fields) | | ✅ Automated |
| 2.10 | `dialog-env-editor.png` | Environment Variables dialog with 3–4 rows of key/value pairs | Show + and − buttons | ✅ Automated |
| 2.11 | `dialog-tags.png` | Tags input dialog | | ✅ Automated |
| 2.12 | `dialog-color-picker.png` | Color picker open from "Set Color…" | | ✅ Automated |
| 2.13 | `project-tree-drag-drop.png` | Mid-drag of a project over a folder (blue drop indicator visible) | | ⏭ Skipped — mid-drag state is not reliably capturable in a headless Xvfb environment |

---

## 3. Build Tool Detection

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 3.1 | `commands-panel-maven.png` | Commands panel for a Maven project — full list of detected commands | | ✅ Automated |
| 3.2 | `commands-panel-gradle.png` | Commands panel for a Gradle project | | ✅ Automated |
| 3.3 | `commands-panel-npm.png` | Commands panel for an npm project with several scripts | | ✅ Automated |
| 3.4 | `commands-panel-multitools.png` | Commands panel for a project with multiple build tools detected simultaneously (e.g. Maven + Docker) | Shows multiple build badges in tree | ✅ Automated |

---

## 4. Command Execution

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 4.1 | `commands-panel-anatomy.png` | Commands panel with all controls labeled: Run, Cancel, Queue buttons; History toggle; Queue toggle; README preview at bottom | | ✅ Automated |
| 4.2 | `commands-running.png` | Command currently running — Run button disabled, Cancel button active, status bar showing "Running: mvn test" | | ⏭ Skipped — requires a command to be actively running with precise capture timing; not reliable in CI |
| 4.3 | `commands-finished-success.png` | Status bar showing "Finished successfully (exit 0)" in green | | ⏭ Skipped — requires command execution and precise post-completion timing |
| 4.4 | `commands-finished-error.png` | Status bar showing "Finished with exit code 1" | | ⏭ Skipped — requires command execution and precise post-completion timing |
| 4.5 | `commands-history.png` | History panel open below command list — show green exit 0 and red exit 1 entries with timestamps | | ✅ Automated |
| 4.6 | `commands-queue.png` | Queue panel open with 3 commands queued | | ✅ Automated |
| 4.7 | `commands-readme-preview.png` | README preview at the bottom of the Commands panel | | ✅ Automated |
| 4.8 | `console-output.png` | Output Console with command output, header line visible | | ✅ Automated |
| 4.9 | `console-find-bar.png` | Console with Ctrl+F find bar open, a match highlighted in orange, status showing "3 / 12" | | ✅ Automated |
| 4.10 | `desktop-notification.png` | OS desktop notification after command completion | Capture if possible | ⏭ Skipped — OS-level desktop notifications are not visible inside the Xvfb virtual display |

---

## 5. Terminal

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 5.1 | `terminal-active.png` | Terminal with an active shell session, prompt visible, some output | | ✅ Automated |
| 5.2 | `terminal-multiple-tabs.png` | Terminal with 3 tabs: "Terminal 1", "Terminal 2", "Terminal 3" — show + button and ✕ on tabs | | ✅ Automated |
| 5.3 | `terminal-placeholder.png` | Terminal placeholder card ("Select a project…" message) before activation | | ✅ Automated |
| 5.4 | `terminal-shell-picker.png` | Shell picker popup open from right-clicking the placeholder — show AI CLIs at top, then system shells | | ⏭ Skipped — right-clicking the placeholder to trigger the picker is too brittle in headless Xvfb |
| 5.5 | `terminal-status-thinking.png` | Terminal running Claude Code with LED showing THINKING state in tree | | ⏭ Skipped — requires live Claude Code integration; not available in CI |
| 5.6 | `terminal-status-waiting.png` | Terminal waiting for input — WAITING/IDLE LED state | | ⏭ Skipped — requires live Claude Code integration; not available in CI |
| 5.7 | `terminal-font-zoom.png` | Terminal at a noticeably larger font size after Ctrl+Scroll | | ✅ Automated |

---

## 6. Code Editor

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 6.1 | `editor-overview.png` | Editor with a Kotlin file open — syntax highlighting, line numbers, code folding markers in gutter, multiple tabs | | ✅ Automated |
| 6.2 | `editor-multiple-tabs.png` | Three editor tabs open; one showing `*` (unsaved changes) | | ✅ Automated |
| 6.3 | `editor-find-bar.png` | Find bar open at bottom of editor — match highlighted, status "✓", case/word/regex checkboxes visible | | ✅ Automated |
| 6.4 | `editor-find-replace-bar.png` | Find & Replace bar open with both fields visible | | ✅ Automated |
| 6.5 | `editor-toolbar.png` | Close-up of the editor toolbar: Save button, "Open with ▼" dropdown | | ✅ Automated |
| 6.6 | `editor-open-with-menu.png` | "Open with" dropdown open showing VS Code, Zed, IntelliJ | | ✅ Automated |
| 6.7 | `editor-large-file.png` | Editor showing the "[File too large to display…]" placeholder | | ✅ Automated |
| 6.8 | `editor-syntax-themes.png` | Side-by-side or sequential shots of Monokai vs Eclipse syntax themes on the same file | | ✅ Automated |
| 6.9 | `editor-unsaved-dialog.png` | "Save / Discard / Cancel" dialog when closing a modified tab | | ✅ Automated |
| 6.10 | `editor-tab-context-menu.png` | Right-click context menu on an editor tab | | ✅ Automated |

---

## 7. File Explorer

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 7.1 | `explorer-overview.png` | File Explorer panel with address bar, toolbar buttons, and file table populated | | ✅ Automated |
| 7.2 | `explorer-context-menu-file.png` | Right-click context menu on a file | Show Open in Editor, Open with, Rename, Delete, Copy Path | ✅ Automated |
| 7.3 | `explorer-context-menu-folder.png` | Right-click context menu on a folder | Show Open, New File, New Folder, Rename, Delete, Copy Path | ✅ Automated |
| 7.4 | `explorer-hidden-files-on.png` | Explorer with hidden files visible (button green, dotfiles showing) | | ✅ Automated |
| 7.5 | `explorer-hidden-files-off.png` | Same directory with hidden files hidden | | ✅ Automated |
| 7.6 | `explorer-address-bar-editing.png` | Address bar focused with a path being typed | | ✅ Automated |

---

## 8. Log Viewer

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 8.1 | `logviewer-overview.png` | Log Viewer with a real log file loaded — ERROR (red), WARN (orange), INFO, DEBUG (gray) entries visible | | ✅ Automated |
| 8.2 | `logviewer-level-filters.png` | Level filter buttons close-up — some toggled off (e.g. DEBUG and TRACE grayed) | | ✅ Automated |
| 8.3 | `logviewer-stack-trace.png` | A log entry with a Java stack trace expanded below it | | ✅ Automated |
| 8.4 | `logviewer-search.png` | Search bar open inside the log viewer, match highlighted in orange | | ✅ Automated |
| 8.5 | `logviewer-follow-button.png` | Close-up of the Follow (↓) button in both on and off states | | ✅ Automated |

---

## 9. Dependency Updates (Renovate)

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 9.1 | `renovate-not-installed.png` | Renovate panel showing "✗ Not installed" state | | ✅ Automated |
| 9.2 | `renovate-scanning.png` | Panel in progress — "Scanning…" state | | ⏭ Skipped — requires Renovate to be installed in the CI container and actively scanning |
| 9.3 | `renovate-results.png` | Results table with a mix of major (red), minor (orange), and patch (green) updates; checkboxes; All/None/Patch only buttons | Key shot | ⏭ Skipped — requires a real Renovate scan to populate the results table |
| 9.4 | `renovate-major-warning.png` | Confirmation dialog before applying major updates | | ⏭ Skipped — requires scan results to be present before the dialog can be triggered |
| 9.5 | `renovate-logs.png` | "Show Logs" toggled on — raw Renovate output visible below the table | | ⏭ Skipped — requires a real Renovate scan to produce log output |

---

## 10. Search (Find in Files)

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 10.1 | `search-overview.png` | Search panel with a query entered and results populated | Show file paths, line numbers, preview snippets | ✅ Automated |
| 10.2 | `search-options.png` | Search options row close-up — Match case, Whole word, Regex checkboxes; Include/Exclude fields; Limit MB | | ✅ Automated |
| 10.3 | `search-result-count.png` | Status line showing "42 match(es) in 8 file(s) (0.12 s)" | | ✅ Automated |
| 10.4 | `search-editor-jump.png` | Editor open at exact line/column after double-clicking a search result — match visible | | ✅ Automated |

---

## 11. Git Integration

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 11.1 | `git-branch-badges.png` | Project Tree close-up showing clean branch (gray) and dirty branch (amber with asterisk) | | ✅ Automated |
| 11.2 | `gitlog-overview.png` | Git Log panel with commit list on top and full diff in bottom pane | | ✅ Automated |
| 11.3 | `gitlog-commit-selected.png` | A commit selected, diff showing added/removed lines | | ✅ Automated |

---

## 12. Prompt Library

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 12.1 | `prompt-input-panel.png` | Prompt Input panel at the bottom of the window — category/template dropdowns, body text area, Paste to Terminal button | | ✅ Automated |
| 12.2 | `prompt-library-dialog.png` | Full Prompt Library dialog — left tree with categories, right form with name/category/body fields, search bar active | | ✅ Automated |
| 12.3 | `prompt-variable-dialog.png` | Variable Resolution Dialog open — "Fill in Placeholders" with two text fields for `{error}` and `{target}` | | ✅ Automated |
| 12.4 | `command-library-dialog.png` | Command Library dialog with a git command selected and command text visible | | ✅ Automated |
| 12.5 | `ai-tools-menu.png` | AI Tools menu open — showing found CLIs in bold, missing CLIs grayed out | | ✅ Automated |

---

## 13. Docs Panel

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 13.1 | `docs-panel-rendered.png` | Docs Panel showing a README.md rendered as HTML with headings, code blocks, bullet lists | | ✅ Automated |
| 13.2 | `docs-panel-raw.png` | Same file in raw mode with Markdown syntax highlighted | | ✅ Automated |

---

## 14. Settings

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 14.1 | `settings-editors-tab.png` | Editors tab with VS Code, Zed, IntelliJ in the list | | ✅ Automated |
| 14.2 | `settings-aitools-tab.png` | AI Tools tab with checkboxes for built-in CLIs | | ✅ Automated |
| 14.3 | `settings-renovate-tab.png` | Renovate tab showing installed version + install buttons | | ✅ Automated |
| 14.4 | `settings-apm-tab.png` | APM tab showing status and install options | | ✅ Automated |
| 14.5 | `settings-shortcuts-tab.png` | Shortcuts tab with full list of actions and key bindings | | ✅ Automated |
| 14.6 | `settings-language-tab.png` | Language tab with dropdown | | ✅ Automated |
| 14.7 | `settings-layout-tab.png` | Layout & Terminal tab — fonts section, terminal section, syntax theme dropdown all visible | | ✅ Automated |
| 14.8 | `settings-color-picker.png` | Terminal color swatch picker open | | ✅ Automated |
| 14.9 | `settings-shortcut-recording.png` | A shortcut field active and recording a new key combination | | ⏭ Skipped — requires precise keyboard event timing that is not reliable in headless CI |

---

## 15. Themes

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 15.1 | `theme-dark-purple.png` | Default Dark Purple theme — full window | | ✅ Automated |
| 15.2 | `theme-catppuccin-mocha.png` | Catppuccin Mocha | | ✅ Automated |
| 15.3 | `theme-nord.png` | Nord | | ✅ Automated |
| 15.4 | `theme-github-light.png` | GitHub Light — full window | | ✅ Automated |
| 15.5 | `theme-menu.png` | View menu open with Dark Themes and Light Themes submenus visible | | ✅ Automated |

---

## 16. Media Viewers

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 16.1 | `viewer-image.png` | Image Viewer with an image loaded, zoom percentage shown in status | | ✅ Automated |
| 16.2 | `viewer-svg.png` | SVG Viewer with a vector graphic | | ✅ Automated |
| 16.3 | `viewer-media-player.png` | Media Player with an audio or video file loaded — all controls visible (play, stop, loop, volume, seek) | | ⏭ Skipped — requires codec support and a real media file; not available in the CI container |

---

## 17. UI Layout & Docking

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 17.1 | `docking-drag-in-progress.png` | A panel mid-drag with blue drop-zone arrows visible | Hard to capture; may need to stage | ⏭ Skipped — mid-drag state is not reliably capturable in headless Xvfb; needs manual capture |
| 17.2 | `docking-floating-panel.png` | A panel detached as a floating window | | ⏭ Skipped — floating window management is unreliable in headless Xvfb |
| 17.3 | `docking-tabs-bottom.png` | Panel with tabs at the bottom (tabsOnTop = false) | | ✅ Automated |
| 17.4 | `panels-menu.png` | Panels menu open with checkboxes for all 12 panels | | ✅ Automated |
| 17.5 | `status-bar-update-badge.png` | Status bar showing "⬆ 1.2.3 available" update badge (cyan) | | ✅ Automated |

---

## 18. Dialogs & Workflows

| # | Filename | What to show | Notes | Status |
|---|---|---|---|---|
| 18.1 | `about-dialog.png` | About Needlecast dialog — icon, version, author, link, MIT license | | ✅ Automated |
| 18.2 | `import-config-dialog.png` | File chooser open for Import Config | | ✅ Automated |
| 18.3 | `add-project-dialog.png` | Directory chooser open for Add Project | | ✅ Automated |
| 18.4 | `delete-confirmation.png` | Delete confirmation dialog for a file or project | | ✅ Automated |

---

## Summary

**Total screenshots: 88 — 71 automated, 17 skipped**

| Section | Total | Automated | Skipped |
|---|---|---|---|
| 1. Overview | 3 | 3 | 0 |
| 2. Project Management | 13 | 11 | 2 |
| 3. Build Tool Detection | 4 | 4 | 0 |
| 4. Command Execution | 10 | 7 | 3 |
| 5. Terminal | 7 | 5 | 2 (+ 1 skipped) |
| 6. Code Editor | 10 | 10 | 0 |
| 7. File Explorer | 6 | 6 | 0 |
| 8. Log Viewer | 5 | 5 | 0 |
| 9. Dependency Updates | 5 | 1 | 4 |
| 10. Search | 4 | 4 | 0 |
| 11. Git Integration | 3 | 3 | 0 |
| 12. Prompt Library | 5 | 5 | 0 |
| 13. Docs Panel | 2 | 2 | 0 |
| 14. Settings | 9 | 8 | 1 |
| 15. Themes | 5 | 5 | 0 |
| 16. Media Viewers | 3 | 2 | 1 |
| 17. UI Layout & Docking | 5 | 3 | 2 |
| 18. Dialogs & Workflows | 4 | 4 | 0 |
| **Total** | **88** | **71** | **17** |

---

## Skipped screenshots

These 17 screenshots cannot be captured automatically by `ScreenshotTour.kt` and must be captured manually or require a change to CI infrastructure before they can be automated.

| # | Filename | Reason | How to unblock |
|---|---|---|---|
| 2.2 | `project-tree-row-anatomy.png` | Requires post-processing to add callout labels/arrows | Capture `project-tree-overview.png` and annotate in an image editor |
| 2.13 | `project-tree-drag-drop.png` | Mid-drag state is not reliably capturable in headless Xvfb | Capture manually on a dev machine with a screen recorder |
| 4.2 | `commands-running.png` | Requires a command to be actively running; timing of capture is not reliable in CI | Add a long-running no-op command to the demo and capture during execution |
| 4.3 | `commands-finished-success.png` | Requires command execution and post-completion state | Run a fast command in the demo and capture the result label |
| 4.4 | `commands-finished-error.png` | Requires command execution with a non-zero exit code | Run `exit 1` in the demo and capture |
| 4.10 | `desktop-notification.png` | OS-level notifications are not visible inside the Xvfb virtual display | Capture manually on macOS or Windows |
| 5.4 | `terminal-shell-picker.png` | Right-clicking the terminal placeholder requires locating a specific component in the Swing tree | Implement targeted right-click via Robot once the component ID is known |
| 5.5 | `terminal-status-thinking.png` | Requires a live Claude Code process whose status is being polled | Capture manually while Claude Code is running |
| 5.6 | `terminal-status-waiting.png` | Requires a live Claude Code process in the waiting state | Capture manually while Claude Code is running |
| 9.2 | `renovate-scanning.png` | Requires Renovate to be installed in the CI container | Install Renovate in `Dockerfile.screenshots` and add a scan step |
| 9.3 | `renovate-results.png` | Requires a real Renovate scan to populate the results table | Same as 9.2 |
| 9.4 | `renovate-major-warning.png` | Requires scan results to trigger the confirmation dialog | Same as 9.2 |
| 9.5 | `renovate-logs.png` | Requires raw Renovate output from a real scan | Same as 9.2 |
| 14.9 | `settings-shortcut-recording.png` | The shortcut field starts recording on focus but the active recording state is hard to capture reliably in CI | Capture manually; or synthesize the active-recording appearance by reflection |
| 16.3 | `viewer-media-player.png` | Requires a media file and codec support not present in the CI container | Add a small test audio file and install a codec in `Dockerfile.screenshots` |
| 17.1 | `docking-drag-in-progress.png` | Mid-drag drop-zone arrows are visible only while the mouse button is held; not reliably capturable headlessly | Capture manually on a dev machine |
| 17.2 | `docking-floating-panel.png` | Detaching a panel to a floating window requires a docking framework interaction that is unreliable in Xvfb | Capture manually; or invoke `Docking.undock()` via reflection and capture the resulting floating window |

---

## Capture Notes

- **Resolution:** 1440 × 900 minimum; 2x (Retina/HiDPI) preferred
- **Theme for most shots:** Dark Purple (the default); call out explicitly when another theme is needed
- **Window size:** Maximize or use a consistent 1400 × 880 window for comparability
- **Real data:** Use realistic project names and actual code in editor shots — avoid placeholder "Lorem ipsum" content
- **Cursor:** Hide the mouse cursor unless it is needed to show a hover state or drag
- **Popups and menus:** Capture with the menu/popup fully open before it auto-dismisses
- **Annotations:** For anatomy shots (2.2, 4.1), add callout arrows/labels in post-processing rather than burning them into the raw capture
