# Claude Code Quota Display in Needlecast Status Bar

**Date:** 2026-04-23
**Status:** Approved

## Summary

Display Claude Code usage quota (5-hour and 7-day rate limits) in Needlecast's status bar by reading OAuth credentials from `~/.claude/.credentials.json` and polling the Anthropic usage API.

## Motivation

Claude Code users on Pro/Max subscriptions have rate limits (5h and 7d windows). Needlecast already integrates with Claude Code via lifecycle hooks. Showing quota directly in the status bar gives users real-time awareness of their usage without leaving the IDE or running `/cost`.

## Architecture

### Files

| File | Action | Purpose |
|------|--------|---------|
| `ui/terminal/ClaudeUsageData.kt` | NEW | Data class for API response |
| `ui/terminal/ClaudeUsageService.kt` | NEW | Reads credentials, polls API, exposes usage data |
| `ui/StatusBar.kt` | MODIFY | Add quota label between status text and update badge |
| `ui/MainWindow.kt` | MODIFY | Wire service to status bar, manage lifecycle |
| `model/AppConfig.kt` | MODIFY | Add `claudeQuotaEnabled` field |
| `ui/settings/AiToolsSettingsPanel.kt` | MODIFY | Add quota display toggle checkbox |

### Data Flow

```
~/.claude/.credentials.json
    -> ClaudeUsageService (reads claudeAiOauth.accessToken)
    -> java.net.http.HttpClient GET https://api.anthropic.com/api/oauth/usage
    -> parses JSON into ClaudeUsageData
    -> SwingUtilities.invokeLater -> StatusBar.updateQuota(data)
    -> polls every 60 seconds via javax.swing.Timer
```

## ClaudeUsageData

```kotlin
data class ClaudeUsageData(
    val fiveHourPercent: Double?,
    val fiveHourResetsAt: Long?,
    val sevenDayPercent: Double?,
    val sevenDayResetsAt: Long?,
    val sevenDaySonnetPercent: Double?,
    val sevenDayOpusPercent: Double?,
)
```

## ClaudeUsageService

### Responsibilities

1. **Credential discovery**: Read `~/.claude/.credentials.json`, extract `claudeAiOauth.accessToken` and `claudeAiOauth.expiresAt`
2. **Token validity**: Skip polling if token is expired (Claude Code manages its own token refresh)
3. **API polling**: HTTP GET to `https://api.anthropic.com/api/oauth/usage` with headers:
   - `Authorization: Bearer <accessToken>`
   - `anthropic-beta: oauth-2025-04-20`
   - `User-Agent: claude-code/2.1.80`
   - `Accept: application/json`
4. **Response parsing**: Parse JSON response fields into `ClaudeUsageData`
5. **EDT dispatch**: Deliver results on the Event Dispatch Thread

### Implementation Details

- Uses `java.net.http.HttpClient` (JDK 11+, project targets JVM 21)
- Uses Jackson `ObjectMapper` for JSON parsing (already on classpath)
- 60-second `javax.swing.Timer` for polling interval
- HTTP calls executed on a daemon thread via `Executors.newSingleThreadExecutor`
- Polling paused when window is not visible
- Auto-starts when credentials file exists with valid token

### Credential File Format

```json
{
  "claudeAiOauth": {
    "accessToken": "sk-ant-oat01-...",
    "refreshToken": "sk-ant-ort01-...",
    "expiresAt": 1776973880786,
    "scopes": ["user:inference", ...],
    "subscriptionType": "max",
    "rateLimitTier": "default_claude_max_20x"
  }
}
```

### API Response Format

```json
{
  "five_hour": { "utilization": 23.5, "resets_at": "2026-04-23T15:00:00Z" },
  "seven_day": { "utilization": 41.2, "resets_at": "2026-04-30T10:00:00Z" },
  "seven_day_sonnet": { "utilization": 35.0 },
  "seven_day_opus": { "utilization": 55.0 },
  "extra_usage": { "is_enabled": false }
}
```

## StatusBar Changes

### Layout

```
[  Ready  ]                    [ 5h: 23% | 7d: 41% ]  [ v0.7.4 available ]
  WEST                           CENTER                  EAST
  (status label)                 (quota label)           (update badge)
```

### Quota Label Behavior

- **Visible**: When `ClaudeUsageService` has valid data
- **Hidden**: When credentials missing, token expired, or API unreachable
- **Format**: `5h: XX% | 7d: XX%`
- **Color coding**:
  - Green: < 70%
  - Yellow (orange): 70-89%
  - Red: >= 90%
- **Tooltip**: Full breakdown including Sonnet/Opus percentages and reset times

## Settings

### AppConfig

Add a new field to `AppConfig` (after `claudeHooksEnabled`):

```kotlin
/** Show Claude Code usage quota (5h/7d rate limits) in the status bar.
 *  Default true (auto-detected when credentials exist). */
val claudeQuotaEnabled: Boolean = true,
```

### AI Tools Settings Panel

Add a checkbox in the `AiToolsSettingsPanel` for toggling quota display. Placed at the top of the panel, before the CLI list, since it is a Claude-specific feature toggle (similar to how `claudeHooksEnabled` works):

- **Label**: "Show Claude quota in status bar"
- **Tooltip**: "Display 5-hour and 7-day usage percentages from your Claude subscription in the status bar. Requires Claude Code credentials."
- **Default**: Checked (enabled)
- **Behavior**: When unchecked, `ClaudeUsageService` stops polling and the quota label is hidden immediately. When re-checked, polling resumes.

The checkbox updates `ctx.config.claudeQuotaEnabled` via `ctx.updateConfig()` and fires a callback so `MainWindow` can start/stop the `ClaudeUsageService`.

### SettingsCallbacks

Add a new callback to `SettingsCallbacks`:

```kotlin
val onClaudeQuotaToggled: (enabled: Boolean) -> Unit = {},
```

## MainWindow Wiring

- Instantiate `ClaudeUsageService` alongside `ClaudeHookServer`
- Only start if `ctx.config.claudeQuotaEnabled` is true
- Pass usage callback to `statusBar.updateQuota()`
- Wire `onClaudeQuotaToggled` callback to start/stop the service
- Start service in `init` block (after UI is set up)
- Stop service on window close

## Error Handling

| Condition | Behavior |
|-----------|----------|
| No credentials file | Quota label hidden, no polling |
| `claudeQuotaEnabled` is false | Quota label hidden, no polling |
| Token expired | Skip poll cycle, show "--" |
| Network error | Keep last known data, retry next cycle |
| Malformed response | Log warning (SLF4J), keep last known data |
| API returns error JSON | Log warning, keep last known data |
| Window not visible | Pause polling timer |

## Dependencies

No new Maven dependencies required:
- `java.net.http.HttpClient` — bundled in JDK 21
- Jackson `ObjectMapper` — already on classpath
- `javax.swing.Timer` — standard Swing

## Testing

- Unit test `ClaudeUsageService` credential parsing with mock JSON
- Unit test `ClaudeUsageData` construction from API response JSON
- Manual verification: run Needlecast with valid Claude credentials, confirm quota appears in status bar
