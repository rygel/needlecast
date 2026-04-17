# Media Player: Playback Speed & Autoplay Design

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a speed selector (0.5×–2×) and an autoplay checkbox to the media player controls, and make autoplay persist across sessions via AppConfig.

**Architecture:** All changes are confined to `MediaPlayerPanel.kt`, `AppConfig.kt`, and `ExplorerPanel.kt` (one-line constructor update). No new files needed.

**Tech Stack:** VLCJ 4.x (`player.controls().setRate(float)`), Swing (`JComboBox`, `JCheckBox`), existing `AppContext`/`AppConfig` live-settings pattern.

---

## Controls layout

The existing buttons row is:

```
[Play] [Stop] [Loop]   ──── seek ────   [Volume ▾]
```

After this change:

```
[Play] [Stop] [Loop] [Autoplay]   ──── seek ────   [Volume ▾] [1× ▾]
```

Both new controls are added to the existing `JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))` buttons panel.

---

## Playback speed

- `JComboBox<String>` with items: `"0.5×"`, `"0.75×"`, `"1×"`, `"1.25×"`, `"1.5×"`, `"1.75×"`, `"2×"`
- Default selection: `"1×"`
- On selection change: call `player.controls().setRate(speed)` where speed is the numeric value parsed from the selected item (strip `×`)
- Placed at the **right end** of the controls row (in the `right` FlowLayout panel alongside Volume)
- **Per-session only** — resets to `1×` when a new `MediaPlayerPanel` is constructed. Not persisted to `AppConfig`.

---

## Autoplay

- `JCheckBox("Autoplay")` placed in the **buttons panel**, after Loop
- Initial state read from `ctx.config.mediaAutoplay`
- On toggle: `ctx.updateConfig(ctx.config.copy(mediaAutoplay = autoplayCheck.isSelected))` — live save, same pattern as all other settings
- `MediaPlayerPanel` constructor gains `ctx: AppContext` parameter (was `file: File` only)
- At the end of `init`, after the player and listeners are fully set up: if `ctx.config.mediaAutoplay` is true, call `playFile()`
- `ExplorerPanel` line `isMediaFile(file) -> MediaPlayerPanel(file)` becomes `MediaPlayerPanel(file, ctx)`

---

## AppConfig change

Add one field to `data class AppConfig` with a default so existing saved configs deserialise without error:

```kotlin
/** Whether media files start playing automatically when opened. Default true. */
val mediaAutoplay: Boolean = true,
```

No migration step needed — Jackson uses the default for missing fields.

---

## What is NOT in scope

- Persisting the speed selection
- A Settings panel entry for autoplay (the checkbox is in the player itself)
- Keyboard shortcuts for speed change
- Speed affecting already-playing media when a new file is opened (speed resets on new open)
