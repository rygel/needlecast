# Media Player: Playback Speed & Autoplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a playback speed selector (0.5×–2×) and an Autoplay checkbox to the media player controls, with autoplay persisted to AppConfig.

**Architecture:** Three files change. `AppConfig` gains one boolean field. `MediaPlayerPanel` gains a `ctx: AppContext` constructor parameter, a `JComboBox` for speed, and a `JCheckBox` for autoplay. `ExplorerPanel` passes `ctx` to the updated constructor. No new files.

**Tech Stack:** VLCJ 4.x (`player.controls().setRate(float)`), Swing (`JComboBox`, `JCheckBox`), Jackson (AppConfig JSON serialisation with default values).

---

## Files

| File | Change |
|---|---|
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt` | Add `val mediaAutoplay: Boolean = true` |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/MediaPlayerPanel.kt` | Add `ctx` param, speed combobox, autoplay checkbox, autoplay-on-open logic |
| `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt` | Update `MediaPlayerPanel(file)` → `MediaPlayerPanel(file, ctx)` |
| `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigRoundTripTest.kt` | Add two tests for `mediaAutoplay` |

---

### Task 1: Add `mediaAutoplay` to AppConfig with tests

**Files:**
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigRoundTripTest.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt:619-622`

- [ ] **Step 1: Write two failing tests in `ConfigRoundTripTest.kt`**

Add these two tests at the end of the class (before the closing `}`):

```kotlin
@Test
fun `mediaAutoplay persists across save and load`(@TempDir dir: Path) {
    val store = JsonConfigStore(dir.resolve("config.json"))
    store.save(AppConfig(mediaAutoplay = true))
    assertTrue(store.load().mediaAutoplay, "mediaAutoplay=true should survive a round-trip")
    store.save(AppConfig(mediaAutoplay = false))
    assertFalse(store.load().mediaAutoplay, "mediaAutoplay=false should survive a round-trip")
}

@Test
fun `mediaAutoplay defaults to true`(@TempDir dir: Path) {
    val store = JsonConfigStore(dir.resolve("config.json"))
    store.save(AppConfig())
    assertTrue(store.load().mediaAutoplay, "mediaAutoplay should default to true")
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
mvn test -pl needlecast-desktop -Dtest=ConfigRoundTripTest -T 4
```

Expected: compilation error — `mediaAutoplay` does not exist on `AppConfig` yet.

- [ ] **Step 3: Add the field to `AppConfig`**

In `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`, insert after the `commandOverrides` line and before the closing `)`:

```kotlin
    /** Per-project command overrides. Outer key = working directory path. */
    val commandOverrides: Map<String, List<CommandOverride>> = emptyMap(),
    /** Whether media files start playing automatically when opened in the Explorer. Default true. */
    val mediaAutoplay: Boolean = true,
)
```

- [ ] **Step 4: Run tests to confirm they pass**

```
mvn test -pl needlecast-desktop -Dtest=ConfigRoundTripTest -T 4
```

Expected: `Tests run: 16, Failures: 0, Errors: 0` (two new tests added to the existing 14).

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigRoundTripTest.kt
git commit -m "feat: add mediaAutoplay field to AppConfig"
```

---

### Task 2: Add speed combobox and autoplay checkbox to MediaPlayerPanel

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/MediaPlayerPanel.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

No unit tests possible for this task — VLCJ requires a live VLC installation and a display. Verify manually by running the app.

- [ ] **Step 1: Update the `MediaPlayerPanel` class signature and add new fields**

Replace the class header and field declarations at the top of `MediaPlayerPanel.kt`. The file currently starts with:

```kotlin
class MediaPlayerPanel(private val file: File) : JPanel(BorderLayout()) {
```

Change to:

```kotlin
class MediaPlayerPanel(
    private val file: File,
    private val ctx: io.github.rygel.needlecast.AppContext,
) : JPanel(BorderLayout()) {
```

Also add these two new field declarations after the existing `private val loopCheck` line:

```kotlin
    private val loopCheck = javax.swing.JCheckBox("Loop")
    private val autoplayCheck = javax.swing.JCheckBox("Autoplay")
    private val speedCombo = javax.swing.JComboBox(arrayOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×")).apply {
        selectedItem = "1×"
        maximumSize = java.awt.Dimension(70, preferredSize.height)
    }
```

- [ ] **Step 2: Wire the autoplay checkbox initial state from config**

The `autoplayCheck` needs its initial state set from `ctx.config.mediaAutoplay`. In `init`, find the line:

```kotlin
            player.audio().setVolume(volumeSlider.value)
```

Immediately before it, add:

```kotlin
            autoplayCheck.isSelected = ctx.config.mediaAutoplay
```

- [ ] **Step 3: Add autoplay and speed controls to the buttons panel**

Find the buttons panel in `init`:

```kotlin
                val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    add(playButton)
                    add(stopButton)
                    add(loopCheck)
                }
```

Replace with:

```kotlin
                val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    add(playButton)
                    add(stopButton)
                    add(loopCheck)
                    add(autoplayCheck)
                }
```

Find the right panel:

```kotlin
                val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    add(JLabel("Volume"))
                    add(volumeSlider)
                }
```

Replace with:

```kotlin
                val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    add(JLabel("Volume"))
                    add(volumeSlider)
                    add(speedCombo)
                }
```

- [ ] **Step 4: Wire autoplay checkbox to AppConfig**

After the existing `volumeSlider.addChangeListener { ... }` block, add:

```kotlin
            autoplayCheck.addActionListener {
                ctx.updateConfig(ctx.config.copy(mediaAutoplay = autoplayCheck.isSelected))
            }
```

- [ ] **Step 5: Wire the speed combobox to VLCJ**

After the `autoplayCheck.addActionListener` block, add:

```kotlin
            speedCombo.addActionListener {
                val item = (speedCombo.selectedItem as? String) ?: "1×"
                val rate = item.removeSuffix("×").trim().toFloatOrNull() ?: 1.0f
                player.controls().setRate(rate)
            }
```

- [ ] **Step 6: Call `playFile()` automatically if autoplay is enabled**

Find the end of the `init` block, just before the closing `}` of `if (!ensureVlcAvailable()) { ... } else { ... }`. Currently the last lines in the `else` branch are:

```kotlin
            updateTimer = Timer(250) { refreshTime() }.apply { isRepeats = true; start() }
            statusLabel.text = "Ready"
        }
    }
```

Replace with:

```kotlin
            updateTimer = Timer(250) { refreshTime() }.apply { isRepeats = true; start() }
            statusLabel.text = "Ready"
            if (ctx.config.mediaAutoplay) {
                javax.swing.SwingUtilities.invokeLater { playFile() }
            }
        }
    }
```

- [ ] **Step 7: Update `ExplorerPanel` to pass `ctx`**

In `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`, find:

```kotlin
            isMediaFile(file)  -> MediaPlayerPanel(file)
```

Replace with:

```kotlin
            isMediaFile(file)  -> MediaPlayerPanel(file, ctx)
```

- [ ] **Step 8: Build to confirm no compile errors**

```
mvn compile -pl needlecast-desktop -am -T 4
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 9: Run full test suite**

```
mvn test -pl needlecast-desktop -T 4
```

Expected: `Tests run: 249, Failures: 0, Errors: 0` (two new tests from Task 1, rest unchanged).

- [ ] **Step 10: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/MediaPlayerPanel.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt
git commit -m "feat: add playback speed selector and autoplay checkbox to media player"
```
