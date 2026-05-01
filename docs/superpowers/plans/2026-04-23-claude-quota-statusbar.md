# Claude Code Quota Status Bar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display Claude Code usage quota (5h/7d rate limits) in Needlecast's status bar by polling the Anthropic usage API with OAuth credentials from `~/.claude/.credentials.json`.

**Architecture:** A background service (`ClaudeUsageService`) reads OAuth credentials from Claude Code's credential store, polls the Anthropic usage API every 60 seconds using `java.net.http.HttpClient`, and delivers results to the Swing `StatusBar` on the EDT. A settings toggle in `AppConfig` and `AiToolsSettingsPanel` allows disabling the feature.

**Tech Stack:** Kotlin/JVM 21, `java.net.http.HttpClient`, Jackson `ObjectMapper`, Swing (`javax.swing.Timer`), SLF4J logging.

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `ui/terminal/ClaudeUsageData.kt` | CREATE | Immutable data class holding parsed usage percentages and reset times |
| `ui/terminal/ClaudeUsageService.kt` | CREATE | Reads credentials, polls API, dispatches results to callback |
| `model/AppConfig.kt` | MODIFY (line ~617) | Add `claudeQuotaEnabled: Boolean = true` field |
| `ui/StatusBar.kt` | MODIFY | Add `quotaLabel` in CENTER position, `updateQuota()` method, color coding |
| `ui/settings/AiToolsSettingsPanel.kt` | MODIFY | Add quota toggle checkbox at top of panel |
| `ui/settings/SettingsCallbacks.kt` | MODIFY | Add `onClaudeQuotaToggled` callback |
| `ui/SettingsDialog.kt` | MODIFY (line ~76, ~594) | Pass callbacks through to `AiToolsSettingsPanel` and wire `onClaudeQuotaToggled` |
| `ui/MainWindow.kt` | MODIFY (lines ~68-72, ~183-192, ~594-603) | Instantiate `ClaudeUsageService`, wire lifecycle, pass callback |

---

### Task 1: Add `ClaudeUsageData` data class

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ClaudeUsageData.kt`

- [ ] **Step 1: Create the data class file**

```kotlin
package io.github.rygel.needlecast.ui.terminal

data class ClaudeUsageData(
    val fiveHourPercent: Double?,
    val fiveHourResetsAt: String?,
    val sevenDayPercent: Double?,
    val sevenDayResetsAt: String?,
    val sevenDaySonnetPercent: Double?,
    val sevenDayOpusPercent: Double?,
)
```

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ClaudeUsageData.kt
git commit -m "feat: add ClaudeUsageData data class for quota API response"
```

---

### Task 2: Create `ClaudeUsageService`

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ClaudeUsageService.kt`
- Reference: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ClaudeUsageData.kt` (Task 1)

- [ ] **Step 1: Create the service file**

The service reads `~/.claude/.credentials.json`, extracts the OAuth access token, and polls `https://api.anthropic.com/api/oauth/usage` every 60 seconds. HTTP calls run on a daemon thread; results are dispatched to the EDT via callback.

```kotlin
package io.github.rygel.needlecast.ui.terminal

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import javax.swing.Timer

class ClaudeUsageService(
    private val onUsage: (ClaudeUsageData?) -> Unit,
) {
    private val mapper = ObjectMapper()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "claude-usage-poller").apply { isDaemon = true }
    }
    private var httpClient: HttpClient? = null
    private var accessToken: String? = null
    private var expiresAtMs: Long = 0
    private var lastData: ClaudeUsageData? = null
    private var timer: Timer? = null

    fun start() {
        if (!loadCredentials()) {
            logger.info("No valid Claude credentials found; quota polling not started")
            return
        }
        logger.info("Claude usage polling started")
        pollOnce()
        timer = Timer(POLL_INTERVAL_MS) { pollOnce() }.apply { isRepeats = true; start() }
    }

    fun stop() {
        timer?.stop()
        timer = null
        executor.shutdownNow()
        logger.info("Claude usage polling stopped")
    }

    private fun loadCredentials(): Boolean {
        val credPath = Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
        if (!java.nio.file.Files.exists(credPath)) return false
        return try {
            val tree = mapper.readTree(credPath.toFile())
            val oauth = tree.get("claudeAiOauth") ?: return false
            val token = oauth.get("accessToken")?.asText()?.takeIf { it.isNotBlank() } ?: return false
            val expires = oauth.get("expiresAt")?.asLong() ?: 0L
            this.accessToken = token
            this.expiresAtMs = expires
            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build()
            true
        } catch (e: Exception) {
            logger.warn("Failed to read Claude credentials: {}", e.message)
            false
        }
    }

    private fun pollOnce() {
        val token = accessToken ?: return
        if (System.currentTimeMillis() > expiresAtMs) {
            logger.debug("Claude OAuth token expired; skipping poll")
            return
        }
        val client = httpClient ?: return
        executor.submit {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(USAGE_URL))
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("anthropic-beta", BETA_HEADER)
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    logger.warn("Claude usage API returned status {}: {}", response.statusCode(), response.body().take(200))
                    return@submit
                }
                val data = parseResponse(response.body())
                lastData = data
                SwingUtilities.invokeLater { onUsage(data) }
            } catch (e: Exception) {
                logger.debug("Claude usage poll failed: {}", e.message)
            }
        }
    }

    private fun parseResponse(body: String): ClaudeUsageData? {
        return try {
            val tree = mapper.readTree(body)
            if (tree.has("error")) {
                logger.warn("Claude usage API error: {}", tree.get("error"))
                return null
            }
            ClaudeUsageData(
                fiveHourPercent = tree.at("/five_hour/utilization")?.asDouble(),
                fiveHourResetsAt = tree.at("/five_hour/resets_at")?.asText(),
                sevenDayPercent = tree.at("/seven_day/utilization")?.asDouble(),
                sevenDayResetsAt = tree.at("/seven_day/resets_at")?.asText(),
                sevenDaySonnetPercent = tree.at("/seven_day_sonnet/utilization")?.asDouble(),
                sevenDayOpusPercent = tree.at("/seven_day_opus/utilization")?.asDouble(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse Claude usage response: {}", e.message)
            null
        }
    }

    companion object {
        private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
        private const val USER_AGENT = "claude-code/2.1.80"
        private const val BETA_HEADER = "oauth-2025-04-20"
        private const val POLL_INTERVAL_MS = 60_000
        private val logger = LoggerFactory.getLogger(ClaudeUsageService::class.java)
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4 -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ClaudeUsageService.kt
git commit -m "feat: add ClaudeUsageService to poll Anthropic usage API"
```

---

### Task 3: Add `claudeQuotaEnabled` to `AppConfig`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt:617`

- [ ] **Step 1: Add the field**

Insert after the `claudeHooksEnabled` field (line 617):

```kotlin
    /** Show Claude Code usage quota (5h/7d rate limits) in the status bar. Requires Claude Code credentials. */
    val claudeQuotaEnabled: Boolean = true,
```

This goes right after:

```kotlin
    val claudeHooksEnabled: Boolean = false,
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4 -q`
Expected: BUILD SUCCESS (new field has default value so no existing callers break)

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt
git commit -m "feat: add claudeQuotaEnabled setting to AppConfig"
```

---

### Task 4: Add `updateQuota()` to `StatusBar`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/StatusBar.kt`

- [ ] **Step 1: Modify StatusBar**

Replace the entire file content with:

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.ui.terminal.ClaudeUsageData
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class StatusBar : JPanel(BorderLayout()) {

    private val label = JLabel(" Ready")
    private val quotaLabel = JLabel().apply {
        isVisible = false
        border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
    }
    private val updateBadge = JLabel().apply {
        isVisible = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = BorderFactory.createEmptyBorder(0, 8, 0, 6)
    }

    init {
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY)
        add(label, BorderLayout.WEST)
        add(quotaLabel, BorderLayout.CENTER)
        add(updateBadge, BorderLayout.EAST)
    }

    fun showUpdateAvailable(version: String, onClick: () -> Unit) {
        updateBadge.icon = RemixIcons.icon("ri-arrow-up-circle-fill", 12, java.awt.Color(0x4CAF50))
        updateBadge.text = " $version available  "
        updateBadge.foreground = UIManager.getColor("Component.accentColor") ?: Color(0x00BCD4)
        updateBadge.mouseListeners.forEach { updateBadge.removeMouseListener(it) }
        updateBadge.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = onClick()
        })
        updateBadge.isVisible = true
        revalidate()
    }

    fun setStatus(msg: String) {
        label.text = " $msg"
    }

    fun setRunning(commandLabel: String) {
        label.text = " Running: $commandLabel"
    }

    fun setFinished(exitCode: Int) {
        label.text = if (exitCode == 0) " Finished successfully (exit 0)"
                     else " Finished with exit code $exitCode"
    }

    fun setReady() {
        label.text = " Ready"
    }

    fun updateQuota(data: ClaudeUsageData?) {
        if (data == null) {
            quotaLabel.isVisible = false
            return
        }

        val fiveH = data.fiveHourPercent
        val sevenD = data.sevenDayPercent

        if (fiveH == null && sevenD == null) {
            quotaLabel.isVisible = false
            return
        }

        val parts = mutableListOf<String>()
        if (fiveH != null) parts.add("5h: ${"%.0f".format(fiveH)}%")
        if (sevenD != null) parts.add("7d: ${"%.0f".format(sevenD)}%")
        quotaLabel.text = parts.joinToString(" | ")

        val worstPct = listOfNotNull(fiveH, sevenD).maxOrNull() ?: 0.0
        quotaLabel.foreground = when {
            worstPct >= 90 -> Color(0xF44336)
            worstPct >= 70 -> Color(0xFF9800)
            else -> UIManager.getColor("Label.foreground") ?: Color(0x4CAF50)
        }

        val tooltipParts = mutableListOf<String>()
        if (fiveH != null) tooltipParts.add("5-hour window: ${"%.1f".format(fiveH)}%${data.fiveHourResetsAt?.let { " (resets $it)" } ?: ""}")
        if (sevenD != null) tooltipParts.add("7-day window: ${"%.1f".format(sevenD)}%${data.sevenDayResetsAt?.let { " (resets $it)" } ?: ""}")
        if (data.sevenDaySonnetPercent != null) tooltipParts.add("7d Sonnet: ${"%.1f".format(data.sevenDaySonnetPercent)}%")
        if (data.sevenDayOpusPercent != null) tooltipParts.add("7d Opus: ${"%.1f".format(data.sevenDayOpusPercent)}%")
        quotaLabel.toolTipText = "<html>${tooltipParts.joinToString("<br>")}</html>"

        quotaLabel.isVisible = true
    }

    fun hideQuota() {
        quotaLabel.isVisible = false
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4 -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/StatusBar.kt
git commit -m "feat: add quota display label to StatusBar with color coding"
```

---

### Task 5: Add settings toggle in `AiToolsSettingsPanel`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/AiToolsSettingsPanel.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsCallbacks.kt`

- [ ] **Step 1: Add `onClaudeQuotaToggled` callback to `SettingsCallbacks`**

Add this field to the `SettingsCallbacks` data class (after `onSyntaxThemeChanged`):

```kotlin
    val onClaudeQuotaToggled: (Boolean) -> Unit = {},
```

- [ ] **Step 2: Modify `AiToolsSettingsPanel` to accept callbacks and add quota checkbox**

Change the constructor to accept `SettingsCallbacks`:

```kotlin
class AiToolsSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(BorderLayout(0, 6)) {
```

Add the quota checkbox at the top of the panel, before the CLI list. Insert this after the `border = ...` line in `init` and before `val enabledMap = ...`:

```kotlin
        val quotaToggle = JCheckBox("Show Claude quota in status bar", ctx.config.claudeQuotaEnabled).apply {
            toolTipText = "Display 5-hour and 7-day usage percentages from your Claude subscription in the status bar. Requires Claude Code credentials."
            addActionListener {
                ctx.updateConfig(ctx.config.copy(claudeQuotaEnabled = isSelected))
                callbacks.onClaudeQuotaToggled(isSelected)
            }
        }
```

Then in the layout section at the bottom of `init`, add a top panel that contains the checkbox:

Replace the existing `add(..., BorderLayout.NORTH)` line with:

```kotlin
        add(JPanel(BorderLayout()).apply {
            add(JLabel("<html>Check the AI tools shown in the project tree and AI Tools menu.<br>" +
                "Built-in tools are detected automatically; custom tools use PATH lookup.</html>").apply {
                border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            }, BorderLayout.NORTH)
            add(quotaToggle, BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
```

Also add the import for `SettingsCallbacks` at the top:

```kotlin
import io.github.rygel.needlecast.ui.settings.SettingsCallbacks
```

(This is already in the same package, so no import needed.)

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4 -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsCallbacks.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/AiToolsSettingsPanel.kt
git commit -m "feat: add Claude quota toggle in AI Tools settings"
```

---

### Task 6: Wire `AiToolsSettingsPanel` in `SettingsDialog`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SettingsDialog.kt:76`

- [ ] **Step 1: Pass callbacks to `AiToolsSettingsPanel`**

On line 76, change:

```kotlin
            add(AiToolsSettingsPanel(ctx),                      "ai-tools")
```

to:

```kotlin
            add(AiToolsSettingsPanel(ctx, callbacks),            "ai-tools")
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4 -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SettingsDialog.kt
git commit -m "feat: pass SettingsCallbacks to AiToolsSettingsPanel"
```

---

### Task 7: Wire `ClaudeUsageService` in `MainWindow`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

- [ ] **Step 1: Add import**

Add after the existing `import io.github.rygel.needlecast.ui.terminal.ClaudeHookServer` (line 15):

```kotlin
import io.github.rygel.needlecast.ui.terminal.ClaudeUsageService
```

- [ ] **Step 2: Instantiate `ClaudeUsageService`**

After the `claudeHookServer` field (around line 72), add:

```kotlin
    private var claudeUsageService: ClaudeUsageService? = null
```

- [ ] **Step 3: Start/stop the service alongside hooks**

After the hook server start/stop block (around line 192, after the `else { ... uninstallHooks() }` block), add:

```kotlin
        if (ctx.config.claudeQuotaEnabled) {
            startUsageService()
        }
```

- [ ] **Step 4: Add helper methods**

Add these methods to the `MainWindow` class (near the other lifecycle methods):

```kotlin
    private fun startUsageService() {
        val svc = ClaudeUsageService { data ->
            statusBar.updateQuota(data)
        }
        claudeUsageService = svc
        svc.start()
    }

    private fun stopUsageService() {
        claudeUsageService?.stop()
        claudeUsageService = null
        statusBar.hideQuota()
    }
```

- [ ] **Step 5: Wire `onClaudeQuotaToggled` in settings callback**

In the `SettingsCallbacks(...)` constructor in `buildMenuBar()` (around line 594-603), add after `onSyntaxThemeChanged`:

```kotlin
                        onClaudeQuotaToggled = { enabled ->
                            if (enabled) startUsageService() else stopUsageService()
                        },
```

- [ ] **Step 6: Stop service on window close**

Find the `windowClosing` handler or `disposeAll` call. Add before any existing dispose logic:

```kotlin
        claudeUsageService?.stop()
```

Look for the window listener setup. Find where `claudeHookServer?.stop()` is called and add `claudeUsageService?.stop()` alongside it.

- [ ] **Step 7: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4 -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat: wire ClaudeUsageService lifecycle in MainWindow"
```

---

### Task 8: Full build verification

- [ ] **Step 1: Run full Maven build**

Run: `mvn verify -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 2: Manual smoke test**

1. Launch Needlecast
2. Verify: if `~/.claude/.credentials.json` exists with a valid token, quota appears in status bar within ~60 seconds
3. Open Settings > AI Tools > uncheck "Show Claude quota in status bar"
4. Verify: quota label disappears immediately
5. Re-check the checkbox
6. Verify: quota reappears within ~60 seconds

---

## Self-Review Checklist

- [x] **Spec coverage**: Each section of the design spec maps to a task
  - `ClaudeUsageData` → Task 1
  - `ClaudeUsageService` → Task 2
  - `AppConfig.claudeQuotaEnabled` → Task 3
  - `StatusBar.updateQuota()` → Task 4
  - Settings toggle → Tasks 5-6
  - `MainWindow` wiring → Task 7
  - Verification → Task 8
- [x] **Placeholder scan**: No TBD/TODO/placeholder steps
- [x] **Type consistency**: `ClaudeUsageData` fields match between service (Task 2) and status bar (Task 4); `SettingsCallbacks.onClaudeQuotaToggled(Boolean)` matches usage in Tasks 5 and 7
