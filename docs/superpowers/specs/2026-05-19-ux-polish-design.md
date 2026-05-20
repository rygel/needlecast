# UX Polish: Editor, Tooltips, Git Sync, Empty States & Tour

**Date**: 2026-05-19
**Status**: Approved
**Approach**: C — Full first-run tour + contextual hints + all targeted fixes

## Overview

Five UX issues that make needlecast feel unfinished for new users:

1. Editor background too light in the default dark theme
2. Colored indicators lack tooltip explanations
3. Git does not sync automatically
4. "No project selected" placeholders are unhelpful
5. No onboarding guidance

---

## 1. Editor Background

### Default theme tweak

Change the `dark-purple` theme's editor background to `#1E1E2E` (deep purple-dark, matching Catppuccin Mocha's base). This becomes the new default for all dark themes that don't explicitly override the editor background.

### Color picker in Settings

Add "Editor background" and "Editor foreground" color picker rows in the existing **Layout & Terminal** settings panel (`AiToolsSettingsPanel.kt`).

- Stored as `editorBackground: String?` and `editorForeground: String?` in `AppConfig` (nullable hex color strings — `null` means "use theme default")
- Applied in `EditorPanel.applyTheme()` after the RSyntaxTextArea theme loads — overrides `textArea.background`, `textArea.foreground`, and `gutter.background`
- When the user clears a custom color, it reverts to the theme default

### Scope

Only affects the code editor (RSyntaxTextArea). Terminal, diff viewer, and console keep their existing color handling.

---

## 2. Highlight Tooltips & Dynamic Help Popups

### Diff viewer legend

Add a collapsible legend bar at the top of `DiffViewerPanel`:

- Green swatch + "Added" label
- Red swatch + "Removed" label
- Yellow swatch + "Search match" label

Collapsed by default after first viewing. Collapse state remembered in `AppConfig.diffLegendDismissed: Boolean`.

### Tooltips on all colored indicators

| Indicator | Tooltip text |
|-----------|-------------|
| Diff gutter green stripe | "Added line" |
| Diff gutter red stripe | "Removed line" |
| Console search highlight (gold) | "Search match N of M" |
| Log viewer search highlight (gold) | "Search match N of M" |
| Diff search highlight (yellow) | "Search match N of M" |
| Project tree branch label | Full branch path + ahead/behind count |
| Project tree dirty dot | "Uncommitted changes" |
| Agent status LED (pulsing) | "Agent active" / "Agent waiting" |

### Dynamic help popups

Small non-modal balloon popups that appear contextually on first use:

- **Diff viewer first open**: "Green = added, Red = removed. Click a commit to see its diff."
- **Git panel first open**: "Fetch/Pull/Push sync with remote. Changes are fetched automatically when you select a project."
- **First search used** (in any panel): "Press Enter or Shift+Enter to navigate matches."

Each popup is shown once. Tracked in `AppConfig.shownHints: Set<String>`. Toggle "Show help popups" in Settings to re-enable all popups at once.

---

## 3. Git Smart Auto-Fetch

### Event-driven fetch

A `GitAutoSync` service (instantiated in `AppContext`) triggers `git fetch` via existing `GitService.fetchStreaming()` on:

1. **Project activated** — when the user double-clicks a project in the tree
2. **GitLogPanel opened** — when the user switches to the Git tab
3. **App window gains focus** — fetch the active project if >5 minutes since last fetch

### Implementation

`GitAutoSync` holds a `Map<String, Instant>` tracking per-project last-fetch timestamps. Before each fetch, it checks whether the configured interval has elapsed. The fetch runs on a daemon thread, streams output to the GitLogPanel's output view, and refreshes the project tree's branch/dirty indicators on completion.

### Scope

**Fetch only** — no auto-pull, no auto-push, no auto-commit. Pull/push remain user-initiated to avoid merge conflicts.

### Config

- `gitAutoFetch: Boolean = true` — toggle in Settings → Git
- `gitAutoFetchIntervalMinutes: Int = 5` — minimum interval between fetches for the same project

---

## 4. Contextual Empty States & CWD Auto-Detect

### ContextualHintPanel component

A reusable Swing panel that replaces all current `"No project selected"` JLabels. Each instance contains:

- Icon (from `RemixIcons`)
- Headline (e.g. "No project selected")
- Actionable description (e.g. "Double-click a project in the tree to open a terminal, or press Ctrl+P to search.")
- Optional action button (e.g. "Add current directory")
- Dismiss button (×) that hides the hint and records the dismissal in `AppConfig.dismissedHints: Set<String>`

### Panels updated

- `DocsPanel` — hint: "Select a project to browse its documentation."
- `RenovatePanel` — hint: "Select a project to view dependency updates."
- `SkillsPanel` — hint: "Select a project to deploy skills."
- `DocViewerPanel` — hint: "Select a project to view generated docs."
- `TerminalManager` placeholder — hint: "Double-click a project to open a terminal, or right-click for shell options."

### CWD auto-detect

On startup (`MainWindow` init), check if `System.getProperty("user.dir")` contains a `.git/` directory:

1. Auto-add the directory as a `ProjectDirectory` in a default group (if not already configured)
2. Show a non-modal banner at the top of the main window: "Detected project at `/path`. [Select it] [Dismiss]"
3. "Select it" activates the project (same as double-clicking in tree)
4. Banner auto-dismisses after 10 seconds if ignored

### Config

- `showContextualHints: Boolean = true` — toggle in Settings → Layout & Terminal
- When off, all `ContextualHintPanel`s collapse to simple text labels (current behavior)
- Individual hint dismissals tracked in `dismissedHints: Set<String>` separately from the global toggle

---

## 5. First-Run Tour

### TourOverlay component

A semi-transparent overlay that highlights a specific component with a tooltip-style bubble:

- Dark scrim with a transparent "hole" cut around the target component
- Bubble shows title, description, step indicator ("2 of 7"), Next and Skip buttons
- Positioned using `SwingUtilities.convertRectangle()` relative to the window
- Non-blocking — user can interact with the app underneath

### Tour steps (first launch only)

1. **Project tree** — "Your projects appear here. Double-click to open a terminal and file explorer."
2. **Project switcher** (Ctrl+P) — "Quickly switch between projects with Ctrl+P."
3. **Explorer panel** — "Browse and edit files. Syntax highlighting works for 20+ languages."
4. **Terminal** — "Each project gets its own terminal. Agent status is shown with a pulsing dot."
5. **Git panel** — "View commit history, diffs, and sync with remote. Fetches happen automatically."
6. **Commands panel** — "Build commands are auto-detected. Click to run."
7. **Settings** — "Customize themes, editor colors, and behavior here."

### Edge cases

- If the tour target panel is in an unselected tab, the tour selects that tab first
- If the window is resized during the tour, positions recalculate
- Tour steps that reference panels not in the current layout are skipped

### Config

- `tourCompleted: Boolean = false` — set to `true` on tour finish or skip
- "Restart tour" button added to Settings → Layout & Terminal
- Tour triggers only when `~/.needlecast/config.json` does not exist on startup

---

## Files Modified

| File | Change |
|------|--------|
| `model/AppConfig.kt` | Add `editorBackground`, `editorForeground`, `gitAutoFetch`, `gitAutoFetchIntervalMinutes`, `showContextualHints`, `dismissedHints`, `shownHints`, `diffLegendDismissed`, `tourCompleted` |
| `config/ConfigMigrator.kt` | Migration for new fields with defaults |
| `ui/MainWindow.kt` | CWD detect banner, tour trigger on first run |
| `ui/ProjectTreePanel.kt` | Tooltips on branch, dirty dot, agent LED |
| `ui/StatusBar.kt` | (Already has tooltips — no change needed) |
| `ui/explorer/EditorPanel.kt` | Apply custom background/foreground from config |
| `ui/terminal/TerminalManager.kt` | Replace placeholder with `ContextualHintPanel` |
| `ui/ConsolePanel.kt` | Search match tooltips |
| `ui/GitLogPanel.kt` | Trigger auto-fetch on open |
| `ui/diff/DiffViewerPanel.kt` | Legend bar, gutter tooltips |
| `ui/diff/DiffSearchBar.kt` | Search match tooltips |
| `ui/logviewer/LogViewerPanel.kt` | Search match tooltips |
| `ui/DocsPanel.kt` | Replace placeholder with `ContextualHintPanel` |
| `ui/RenovatePanel.kt` | Replace placeholder with `ContextualHintPanel` |
| `ui/SkillsPanel.kt` | Replace placeholder with `ContextualHintPanel` |
| `ui/DocViewerPanel.kt` | Replace placeholder with `ContextualHintPanel` |
| `ui/settings/AiToolsSettingsPanel.kt` | Color pickers, toggles, restart tour button |
| `AppContext.kt` | Instantiate `GitAutoSync` |
| `ThemeRegistry.kt` | Darker default editor background |

## New Files

| File | Purpose |
|------|---------|
| `ui/components/ContextualHintPanel.kt` | Reusable hint panel with icon, headline, description, action, dismiss |
| `ui/components/TourOverlay.kt` | Semi-transparent overlay with step bubbles |
| `ui/components/BannerNotification.kt` | Non-modal top-of-window banner for CWD detect |
| `git/GitAutoSync.kt` | Event-driven auto-fetch service |
| `ui/components/DynamicHelpPopup.kt` | One-time contextual balloon popup |

## Architecture Notes

- All new components follow existing Swing patterns (no new frameworks)
- `GitAutoSync` depends on existing `GitService` interface — no changes to `ProcessGitService`
- `ContextualHintPanel` is self-contained — accepts icon, headline, description, optional action, hint ID for dismissal tracking
- Tour overlay uses pure Swing painting — no external dependencies
- All new config fields have sensible defaults so existing configs migrate cleanly
