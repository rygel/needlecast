# Cycle 11: Targeted Fixes — Design Spec

**Date:** 2026-06-10
**Cycle:** 11
**Approach:** Targeted fixes (four independent tasks)

## Scope

Four small, independent fixes addressing open items from #138 and recent UX issues.

---

## 1. Build Tool Tags in Command List

### Problem

When a project has multiple build systems (e.g. Maven + npm), the command list is a flat list with no visual distinction between tools. The `BuildTool` enum already has `tagLabel` and `tagColor` fields, and the project tree renders colored badges for them, but `CommandCellRenderer` ignores `buildTool` entirely.

### Fix

**CommandCellRenderer** (`CommandPanel.kt`, lines 589-619): Render a small colored badge before the command label, using `value.buildTool.tagLabel` and `value.buildTool.tagColor`. Match the badge style used in `ProjectTreeCellRenderer` — a small rounded rectangle with white text on a colored background.

The renderer currently produces a single `JLabel` with HTML text. Add the badge as an inline HTML element or switch to a custom panel layout. The HTML approach is simpler and consistent with the existing renderer pattern:

```html
<span style="background:#2E7D32;color:white;border-radius:3px;padding:1px 4px">mvn</span> clean install
```

**Bug fix in executeCommand()** (`CommandPanel.kt`, line 341): Replace hardcoded `BuildTool.MAVEN` with the actual `buildTool` from the selected command descriptor.

### Files

- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt`

### Testing

- Visual: open a project with Maven + npm, verify colored badges appear next to each command
- Unit: verify the HTML badge output for a few BuildTool values

---

## 2. Reset Command Override to Default

### Problem

Users can edit commands via right-click > Edit (already fully implemented). Once edited, the override persists in `AppConfig.commandOverrides`. There is no way to revert to the scanner-generated default without manually editing config.json.

### Fix

Add a **"Reset to Default"** menu item to the command right-click context menu in `CommandPanel`.

**Visibility:** Only shown when the selected command has an active override. Detection: check `ctx.config.commandOverrides[projectPath]` for an entry whose `originalArgv` matches the command's current argv (or if the command was already overridden, check the chain via `resolveOriginalArgv`).

**Behavior on click:**
1. Find the matching `CommandOverride` for this command
2. Remove it from the project's override list in config
3. Reconstruct the original descriptor from the stored `originalArgv` (avoids a potentially expensive full rescan — the original label and argv are known from the override)
4. Update the list model entry with the original descriptor

The existing `editSelectedCommand()` method already has logic to resolve the true original argv from the override chain. The reset path uses the same lookup to find and remove the override, then creates a `CommandDescriptor` with `originalArgv` and the original label.

### Files

- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt` (context menu section, lines ~409-450)

### Testing

- Edit a command, verify "Reset to Default" appears in context menu
- Click it, verify the command reverts to scanner default
- Verify the override is removed from config (no growing override list)
- Verify rescanning the project doesn't re-add stale overrides

---

## 3. Explorer Right-Click Context Menu

### Problem

The Explorer toolbar has an "Open in Explorer/Finder" button, but right-clicking a directory or file in the file table shows no such option.

### Fix

Add entries to the right-click context menu in `ExplorerPanel.showContextMenu()`:

**For directories** (`FileEntry.Dir`, around line 618): Add "Open in Explorer" / "Open in Finder" menu item that calls `openInFileManager(entry.file)`. The method already exists and handles all platforms.

**For files** (`FileEntry.RegularFile`, around line 630): Add "Reveal in Explorer" / "Reveal in Finder" menu item. This requires a new `revealInFileManager(file: File)` method with platform-specific behavior:

- **Windows:** `ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()`
- **macOS:** `ProcessBuilder("open", "-R", file.absolutePath).start()`
- **Linux:** Fall back to `Desktop.getDesktop().open(file.parentFile)` (no standard "reveal" support)

Menu item labels use the same platform-conditional pattern already in the toolbar button tooltip.

### Files

- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt`

### Testing

- Right-click a directory in the explorer, verify "Open in Explorer" appears and works
- Right-click a file, verify "Reveal in Explorer" appears and selects the file in the OS file manager
- Verify platform detection (IS_MAC / IS_WINDOWS / else) for menu labels

---

## 4. Better Update Check Error Messages

### Problem

When update checks fail, the manual check dialog shows the raw Java exception: `"Could not check for updates: java.net.ConnectException"`. This is meaningless to users. The periodic check silently fails with only a log entry.

### Fix

The `classifyUpdateError()` method in `MainWindow.kt` already categorizes errors into: `tls_handshake`, `tls_ssl`, `dns_unresolved_host`, `network_timeout`, `network_connect_refused`, `tls_cert_path`, `tls_certificate`, `unknown`. Add a `friendlyUpdateMessage(category: String): String` function that maps these to user-facing messages:

| Category | Message |
|----------|---------|
| `tls_handshake`, `tls_ssl`, `tls_cert_path`, `tls_certificate` | "Secure connection to the update server failed. This may be caused by a proxy, firewall, or security software." |
| `dns_unresolved_host` | "Could not reach the update server. Check your internet connection." |
| `network_timeout` | "Update check timed out. The server may be slow or unreachable." |
| `network_connect_refused` | "The update server refused the connection. Try again later." |
| `unknown` | Keep raw exception message as fallback |

**In `checkForUpdatesManual()`** (line 697): Replace `"Could not check for updates: ${e.message}"` with `"Could not check for updates.\n${friendlyUpdateMessage(classifyUpdateError(rootCause(e)))}"`.

**In `checkForUpdates()`** (periodic, line 640): Log the friendly message alongside the technical details already logged.

### Files

- `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

### Testing

- Disconnect from network, trigger manual update check, verify friendly message
- Simulate timeout (low connect timeout), verify timeout message
- Normal failure: verify raw message shown for truly unknown errors

---

## Out of Scope

- Settings panel for managing command overrides (view/delete across projects)
- Sorting/grouping commands by build tool
- CI screenshot automation script
- MainWindow decomposition
