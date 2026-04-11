# Settings Dialog Rework — Design Spec

## Goal

Replace the 7-tab `SettingsDialog` with a vertical-sidebar layout: category list on the left, content panel on the right. Split the 1,095-line monolith into 9 focused panel classes plus shared utilities.

## Architecture

`SettingsDialog.kt` becomes a ~150-line shell that owns the sidebar and `CardLayout` content area. Each of the 9 settings categories lives in its own file under `ui/settings/`. Shared low-level helpers (`monoFont()`, `availableFontFamilies()`, `runCommandStreaming()`, etc.) move to `ui/settings/SettingsUtils.kt`.

The 8 reactive callback parameters on the current constructor are grouped into a single `SettingsCallbacks` data class (all fields default to no-ops), giving a clean four-parameter constructor:

```kotlin
class SettingsDialog(
    owner: JFrame,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JDialog(...)
```

```kotlin
data class SettingsCallbacks(
    val onShortcutsChanged: () -> Unit = {},
    val onLayoutChanged: () -> Unit = {},
    val onTerminalColorsChanged: (fg: Color?, bg: Color?) -> Unit = { _, _ -> },
    val onFontSizeChanged: (Int) -> Unit = {},
    val onUiFontChanged: (family: String?, size: Int?) -> Unit = { _, _ -> },
    val onEditorFontChanged: (family: String?, size: Int) -> Unit = { _, _ -> },
    val onTerminalFontChanged: (family: String?) -> Unit = {},
    val onSyntaxThemeChanged: () -> Unit = {},
)
```

## Sidebar

The left panel is a `JList<SidebarEntry>` (sealed class: `Header` / `Category`) with a custom cell renderer. Headers are rendered in small-caps bold grey and are non-selectable — clicking one re-selects the previous valid category. Categories render as plain-text labels with selection highlight. Fixed sidebar width: 160 px.

Sidebar entries in order:

```
Header("GENERAL")
  Category("appearance",  "Appearance")
  Category("layout",      "Layout")
  Category("terminal",    "Terminal")
Header("INTEGRATIONS")
  Category("editors",     "External Editors")
  Category("ai-tools",    "AI Tools")
  Category("renovate",    "Renovate")
Header("ADVANCED")
  Category("apm",         "APM")
  Category("shortcuts",   "Shortcuts")
  Category("language",    "Language")
```

Content is swapped via `CardLayout` keyed on `Category.key`. Default selection on open: `"appearance"`.

## Dialog size

- Preferred: 760 × 560 (up from 600 × 500)
- Minimum: 640 × 460

## File structure

```
needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/
  SettingsDialog.kt                           — sidebar shell (modified, ~150 lines)
  settings/
    SettingsCallbacks.kt                      — data class (new)
    SettingsUtils.kt                          — shared helpers (new)
    AppearanceSettingsPanel.kt                — new
    LayoutSettingsPanel.kt                    — new
    TerminalSettingsPanel.kt                  — new
    ExternalEditorsSettingsPanel.kt           — new
    AiToolsSettingsPanel.kt                   — new
    RenovateSettingsPanel.kt                  — new
    ApmSettingsPanel.kt                       — new
    ShortcutsSettingsPanel.kt                 — new
    LanguageSettingsPanel.kt                  — new
```

## Panel responsibilities

### AppearanceSettingsPanel
Receives: `ctx`, `onUiFontChanged`, `onEditorFontChanged`, `onSyntaxThemeChanged`

Content:
- **UI font** — family combo (all system fonts + "System default"), size spinner, Reset button
- **Editor font** — family combo (monospace only + "Auto"), size spinner, Reset button
- **Syntax theme** — combo box (auto/monokai/dark/druid/idea/eclipse/default/default-alt/vs)

### LayoutSettingsPanel
Receives: `ctx`, `onLayoutChanged`

Content:
- **Layout** section header
  - "Show panel tabs at the top" checkbox (`tabsOnTop`)
  - "Highlight active docking panel border [alpha]" checkbox (`dockingActiveHighlight`)
- **Diagnostics** section header
  - "Enable project tree click tracing" checkbox
  - "Enable EDT stall monitor" checkbox
  - Note: "Logs go to ~/.needlecast/needlecast.log. Enable only while diagnosing lag."

### TerminalSettingsPanel
Receives: `ctx`, `onFontSizeChanged`, `onTerminalColorsChanged`, `onTerminalFontChanged`

Content:
- **Terminal font** — family combo (monospace only + "Auto"), size spinner (8–36, step 1) — consolidates the split from the old layout where family was in "Fonts" and size was in "Terminal"
- **Default shell** — combo (OS default + detected shells + "Manual entry…"), manual text field (shown only for manual), Apply button, note: "Takes effect on next terminal activation."
- **Terminal colors** section — foreground swatch, background swatch, Reset button, note; color chooser on click
- Shell detection runs in background `SwingWorker`; worker is cancelled on `windowClosed`

### ExternalEditorsSettingsPanel
Receives: `ctx`

Content: identical to current `buildEditorsTab()` — `JList<ExternalEditor>` with add/remove toolbar.

### AiToolsSettingsPanel
Receives: `ctx`

Content: identical to current `buildAiToolsTab()` — checkbox list of built-in and custom CLIs, add/remove custom toolbar.

### RenovateSettingsPanel
Receives: `ctx`, `sendToTerminal`

Content: identical to current `buildRenovateTab()` — status check, package-manager install buttons, streamed output area.

### ApmSettingsPanel
Receives: `ctx`, `sendToTerminal`

Content: identical to current `buildApmTab()` — status check, install buttons, streamed output area.

### ShortcutsSettingsPanel
Receives: `ctx`, `onShortcutsChanged`

Content: identical to current `buildShortcutsTab()` — key-recording fields grid, Save button.
`defaultShortcuts` and `actionLabels` move from `SettingsDialog.companion` to `ShortcutsSettingsPanel.companion` (or top-level in the file).

### LanguageSettingsPanel
Receives: `ctx`

Content: identical to current `buildLanguageTab()` — language combo, Apply button.

## SettingsUtils.kt

Top-level functions (package-private visibility, `internal` if needed):

```kotlin
fun monoFont(): String
fun uiBaseFont(): Font
fun availableFontFamilies(): List<String>
fun availableMonospaceFamilies(): List<String>
fun isMonospaced(name: String): Boolean
fun buildOutputArea(): JTextArea
fun runCommandStreaming(command: String, outputArea: JTextArea, env: Map<String,String> = emptyMap(), onFinished: () -> Unit)
```

`runCommandStreaming` needs access to a parent component for error dialogs — panels pass `this` as the owner.

## MainWindow call-site change

`SettingsDialog(owner, ctx, sendToTerminal, SettingsCallbacks(...))` replaces the current 10-argument call. The named arguments in `SettingsCallbacks` make the mapping explicit.

## What is NOT in scope

- Adding a FlatLaf theme switcher (unknown if supported in current setup)
- Changing any settings logic or persistence — this is a pure structural refactor
- Removing any existing setting
