# Privacy Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a privacy mode that lets users mark projects as private and toggle a global switch to redact all private project names/paths with `••••••` for screenshots.

**Architecture:** Add a `private` boolean to `ProjectDirectory` and a `privacyModeEnabled` boolean to `AppConfig`. A helper function on `ProjectDirectory` decides whether to return the real label or the placeholder. All UI renderers call through this helper instead of `label()` directly. A context menu toggle marks individual projects; a toolbar button + View menu item control the global switch.

**Tech Stack:** Kotlin, Swing, Jackson (config), JUnit 5 + MockK (tests)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `model/AppConfig.kt` | Modify | Add `private` field to `ProjectDirectory`, `privacyModeEnabled` to `AppConfig`, add `redactedLabel()` method |
| `ui/ProjectTreePanel.kt` | Modify | Context menu "Mark as Private" toggle, tree header privacy button, redact cell renderer, redact tooltip, redact dialog titles |
| `ui/MainWindow.kt` | Modify | "Privacy Mode" checkbox in View menu |
| `ui/ProjectSwitcherDialog.kt` | Modify | Redact name/subtitle in switcher entries |
| `ui/explorer/ExplorerPanel.kt` | Modify | Redact address bar path |
| `test/.../PrivacyModeTest.kt` | Create | Unit tests for redaction logic |
| `test/.../ProjectSwitcherFilterTest.kt` | Modify | Add tests for privacy-aware filtering |

---

### Task 1: Data model — add `private` field and `redactedLabel()` helper

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt:638-663` (ProjectDirectory)
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt:547-622` (AppConfig)
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/model/PrivacyModeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.rygel.needlecast.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PrivacyModeTest {

    @Test
    fun `label returns display name when not private and privacy mode off`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project")
        assertEquals("My Project", dir.label(privacyModeEnabled = false))
    }

    @Test
    fun `label returns bullet placeholder when private and privacy mode on`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project", private = true)
        assertEquals("••••••", dir.label(privacyModeEnabled = true))
    }

    @Test
    fun `label returns real name when private but privacy mode off`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project", private = true)
        assertEquals("My Project", dir.label(privacyModeEnabled = false))
    }

    @Test
    fun `label returns real name when privacy mode on but project not private`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", displayName = "My Project", private = false)
        assertEquals("My Project", dir.label(privacyModeEnabled = true))
    }

    @Test
    fun `path returns real path when not redacted`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", private = true)
        assertEquals("/home/user/secret-project", dir.path)
    }

    @Test
    fun `redactedPath returns bullets when private and privacy mode on`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", private = true)
        assertEquals("••••••", dir.redactedPath(privacyModeEnabled = true))
    }

    @Test
    fun `redactedPath returns real path when privacy mode off`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", private = true)
        assertEquals("/home/user/secret-project", dir.redactedPath(privacyModeEnabled = false))
    }

    @Test
    fun `redactedPath returns real path when not private`() {
        val dir = ProjectDirectory(path = "/home/user/secret-project", private = false)
        assertEquals("/home/user/secret-project", dir.redactedPath(privacyModeEnabled = true))
    }

    @Test
    fun `label falls back to path segment when no displayName`() {
        val dir = ProjectDirectory(path = "/home/user/my-project", private = false)
        assertEquals("my-project", dir.label(privacyModeEnabled = false))
    }

    @Test
    fun `privacyModeEnabled defaults to false in AppConfig`() {
        val config = AppConfig()
        assertFalse(config.privacyModeEnabled)
    }

    @Test
    fun `private defaults to false in ProjectDirectory`() {
        val dir = ProjectDirectory(path = "/test")
        assertFalse(dir.private)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl needlecast-desktop -Dtest=PrivacyModeTest -T 4`
Expected: FAIL — `ProjectDirectory` does not have `private` field, no `label(Boolean)` overload, no `redactedPath`

- [ ] **Step 3: Add `private` field to `ProjectDirectory`**

In `model/AppConfig.kt`, modify `ProjectDirectory` (around line 638) — add the `private` field and the two privacy-aware methods:

```kotlin
data class ProjectDirectory(
    val path: String,
    val displayName: String? = null,
    /** Optional hex color string (e.g. "#FF5722") shown as a left-edge stripe in the project list. */
    val color: String? = null,
    /** Per-project environment variable overrides injected into commands and terminals. */
    val env: Map<String, String> = emptyMap(),
    /**
     * Custom shell executable for this project's terminal (e.g. "zsh", "bash", "powershell").
     * Null means use the OS default (cmd.exe on Windows, /bin/bash on Unix).
     */
    val shellExecutable: String? = null,
    /**
     * Command sent to the shell's stdin immediately after it starts (e.g. "conda activate ml").
     * Null means no startup command.
     */
    val startupCommand: String? = null,
    /**
     * Additional directories to scan for shell and language scripts,
     * stored relative to [path] when possible.
     */
    val extraScanDirs: List<String> = emptyList(),
    val skillTargetDir: String? = null,
    /** When true and privacy mode is enabled, this project's name and path are hidden. */
    val private: Boolean = false,
) {
    fun label(): String = displayName ?: path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

    fun label(privacyModeEnabled: Boolean): String =
        if (private && privacyModeEnabled) "••••••" else label()

    fun redactedPath(privacyModeEnabled: Boolean): String =
        if (private && privacyModeEnabled) "••••••" else path
}
```

- [ ] **Step 4: Add `privacyModeEnabled` to `AppConfig`**

In `model/AppConfig.kt`, add the field to the `AppConfig` data class (after `mediaAutoplay` near line 621):

```kotlin
    /** Whether media files start playing automatically when opened in the Explorer. Default true. */
    val mediaAutoplay: Boolean = true,
    /** When true, projects marked as private show •••••• instead of names/paths. */
    val privacyModeEnabled: Boolean = false,
)
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl needlecast-desktop -Dtest=PrivacyModeTest -T 4`
Expected: PASS (all 11 tests)

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/model/PrivacyModeTest.kt
git commit -m "feat: add privacy flag to ProjectDirectory and toggle to AppConfig"
```

---

### Task 2: Project tree — context menu "Mark as Private" toggle

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt:1035-1074` (context menu area)

- [ ] **Step 1: Add "Mark as Private" checkbox to the project context menu**

In `ProjectTreePanel.kt`, inside `showProjectContextMenu()` — after the Tags submenu block (line 1050) and before "Shell Settings" (line 1051), add a "Mark as Private" checkbox menu item:

```kotlin
                menu.add(JCheckBoxMenuItem("Private", entry.directory.private).apply {
                    toolTipText = "Hide project name and path when Privacy Mode is on"
                    addActionListener {
                        val cur = node.userObject as? ProjectTreeEntry.Project ?: return@addActionListener
                        node.userObject = cur.copy(directory = cur.directory.copy(private = isSelected))
                        treeModel.nodeChanged(node); persist(); tree.repaint()
                    }
                })
```

The insertion point is between the Tags submenu closing (line 1050 `})`) and the Shell Settings line (line 1051 `menu.add(JMenuItem("Shell Settings…`)).

- [ ] **Step 2: Verify manually or via existing UI tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: All existing tests pass (no test changes in this step, pure UI addition)

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git commit -m "feat: add 'Private' toggle to project context menu"
```

---

### Task 3: Project tree — privacy toggle button in header and redaction in cell renderer

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt:85-89` (tooltip)
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt:192-228` (header buttons)
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt:1246` (cell renderer name)
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt:820,832,877` (dialog titles)

- [ ] **Step 1: Add privacy toggle button to the tree header bar**

In the `init` block, after `rescanBtn` (line 208) and before `filterField` (line 210), add:

```kotlin
        val privacyBtn = JButton("\uD83D\uDC41").apply {
            toolTipText = "Privacy Mode — hide private project names and paths"
            isFocusPainted = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            isOpaque = false
            val updateIcon = {
                text = if (ctx.config.privacyModeEnabled) "\uD83D\uDC41\u200D\uD83D\uDD75" else "\uD83D\uDC41"
                toolTipText = if (ctx.config.privacyModeEnabled) "Privacy Mode ON — click to show names" else "Privacy Mode — hide private project names and paths"
            }
            updateIcon()
            addActionListener {
                ctx.updateConfig(ctx.config.copy(privacyModeEnabled = !ctx.config.privacyModeEnabled))
                updateIcon()
                tree.repaint()
            }
        }
```

Then add `privacyBtn` to the `btnPanel` (line 224):
```kotlin
            val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(privacyBtn); add(addFolderBtn); add(addProjectBtn); add(rescanBtn)
            }
```

- [ ] **Step 2: Redact tooltip**

In the anonymous `JTree` subclass (line 85-88), change the tooltip to use `redactedPath`:

```kotlin
        override fun getToolTipText(e: java.awt.event.MouseEvent): String? {
            val path = getPathForLocation(e.x, e.y) ?: return null
            val entry = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject
            return (entry as? ProjectTreeEntry.Project)?.directory?.redactedPath(ctx.config.privacyModeEnabled)
        }
```

- [ ] **Step 3: Redact cell renderer name**

In `ProjectTreeCellRenderer.getTreeCellRendererComponent()`, line 1246, change:

From:
```kotlin
                    nameLabel.text = entry.directory.label()
```

To:
```kotlin
                    nameLabel.text = entry.directory.label(ctx.config.privacyModeEnabled)
```

- [ ] **Step 4: Redact dialog titles**

At line 820 (Shell Settings dialog title), change:

From:
```kotlin
        if (JOptionPane.showConfirmDialog(owner, form, "Shell Settings \u2014 ${dir.label()}",
```

To:
```kotlin
        if (JOptionPane.showConfirmDialog(owner, form, "Shell Settings \u2014 ${dir.label(ctx.config.privacyModeEnabled)}",
```

At line 832 (Env editor dialog), change:

From:
```kotlin
        EnvEditorDialog(owner, entry.directory.label(), entry.directory.env) { newEnv ->
```

To:
```kotlin
        EnvEditorDialog(owner, entry.directory.label(ctx.config.privacyModeEnabled), entry.directory.env) { newEnv ->
```

At line 877 (Script Directories dialog title), change:

From:
```kotlin
        if (JOptionPane.showConfirmDialog(owner, form, "Script Directories \u2014 ${dir.label()}",
```

To:
```kotlin
        if (JOptionPane.showConfirmDialog(owner, form, "Script Directories \u2014 ${dir.label(ctx.config.privacyModeEnabled)}",
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: All existing tests pass

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt
git commit -m "feat: add privacy toggle button and redact names/paths in tree"
```

---

### Task 4: View menu — "Privacy Mode" checkbox

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt:887-889` (View menu, after showExplorerCb)

- [ ] **Step 1: Add Privacy Mode checkbox to View menu**

In `buildViewMenu()`, after the `add(showExplorerCb)` line (line 888) and before the `addSeparator()` (line 889), add:

```kotlin
            add(JCheckBoxMenuItem("Privacy Mode", ctx.config.privacyModeEnabled).apply {
                toolTipText = "Hide private project names and paths for screenshots"
                addActionListener {
                    ctx.updateConfig(ctx.config.copy(privacyModeEnabled = isSelected))
                }
            })
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: All existing tests pass

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat: add Privacy Mode checkbox to View menu"
```

---

### Task 5: Project switcher — redact private project names and paths

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectSwitcherDialog.kt:41-44` (Entry data class)
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectSwitcherDialog.kt:46` (collectProjects call)
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectSwitcherDialog.kt:199-210` (EntryCellRenderer)

- [ ] **Step 1: Pass privacyModeEnabled to Entry and use redacted values**

Change the `Entry` data class (line 41-44) to accept `privacyModeEnabled` and use the redacted values:

```kotlin
    data class Entry(val dir: ProjectDirectory, val folderPath: String, private val privacyModeEnabled: Boolean) {
        val label: String    get() = dir.label(privacyModeEnabled)
        val subtitle: String get() {
            val path = dir.redactedPath(privacyModeEnabled)
            return if (folderPath.isEmpty()) path else "$folderPath  •  $path"
        }
    }
```

Update the `allEntries` construction (line 46) — the dialog needs `ctx.config.privacyModeEnabled`:

```kotlin
    private val allEntries: List<Entry> = collectProjects(loadProjectTree(), "", ctx.config.privacyModeEnabled)
```

Update `collectProjects` at the bottom of the file (line 215-224):

```kotlin
private fun collectProjects(entries: List<ProjectTreeEntry>, folderPath: String, privacyModeEnabled: Boolean): List<ProjectSwitcherDialog.Entry> =
    entries.flatMap { entry ->
        when (entry) {
            is ProjectTreeEntry.Project -> listOf(ProjectSwitcherDialog.Entry(entry.directory, folderPath, privacyModeEnabled))
            is ProjectTreeEntry.Folder  -> {
                val path = if (folderPath.isEmpty()) entry.name else "$folderPath / ${entry.name}"
                collectProjects(entry.children, path, privacyModeEnabled)
            }
        }
    }
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: `ProjectSwitcherFilterTest` will fail because `filterEntries` uses `Entry` which now requires `privacyModeEnabled`

- [ ] **Step 3: Fix `filterEntries` test helper**

The `filterEntries` function at line 227-234 filters on `it.label` and `it.dir.path`, which already go through the privacy-aware methods. The `Entry` constructor now requires 3 params. Update the test's `entry()` helper:

In `ProjectSwitcherFilterTest.kt`, change the helper (line 9-13):

```kotlin
    private fun entry(name: String, path: String, folderPath: String = "Group") =
        ProjectSwitcherDialog.Entry(
            dir        = ProjectDirectory(path = path, displayName = name),
            folderPath = folderPath,
            privacyModeEnabled = false,
        )
```

- [ ] **Step 4: Add privacy filter tests**

Append to `ProjectSwitcherFilterTest.kt`:

```kotlin
    @Test
    fun `private project name is redacted when privacy mode on`() {
        val e = ProjectSwitcherDialog.Entry(
            dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
            folderPath = "Work",
            privacyModeEnabled = true,
        )
        assertEquals("••••••", e.label)
        assertEquals("Work  •  ••••••", e.subtitle)
    }

    @Test
    fun `private project name is visible when privacy mode off`() {
        val e = ProjectSwitcherDialog.Entry(
            dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
            folderPath = "Work",
            privacyModeEnabled = false,
        )
        assertEquals("Secret Project", e.label)
        assertEquals("Work  •  /workspace/secret", e.subtitle)
    }

    @Test
    fun `filter matches on redacted label when privacy mode on`() {
        val entries = listOf(
            ProjectSwitcherDialog.Entry(
                dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
                folderPath = "Work",
                privacyModeEnabled = true,
            ),
        )
        val result = filterEntries(entries, "••••••")
        assertEquals(1, result.size)
    }

    @Test
    fun `filter matches on real path even when privacy mode on`() {
        val entries = listOf(
            ProjectSwitcherDialog.Entry(
                dir = ProjectDirectory(path = "/workspace/secret", displayName = "Secret Project", private = true),
                folderPath = "Work",
                privacyModeEnabled = true,
            ),
        )
        val result = filterEntries(entries, "secret")
        assertEquals(1, result.size)
    }
```

Note: The `filterEntries` function at line 233 uses `it.dir.path` which returns the real path (not redacted). This is intentional — filtering should work on real data even in privacy mode so the user can still find projects by typing. If the test for "filter matches on real path" fails, the `filterEntries` function already accesses `it.dir.path` directly which is the raw path, so it should pass.

- [ ] **Step 5: Run tests**

Run: `mvn test -pl needlecast-desktop -Dtest=ProjectSwitcherFilterTest -T 4`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectSwitcherDialog.kt needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ProjectSwitcherFilterTest.kt
git commit -m "feat: redact private project names in switcher dialog"
```

---

### Task 6: Explorer panel — redact address bar path

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt:248` (addressField)

- [ ] **Step 1: Redact the address bar path for private projects**

In `ExplorerPanel.kt`, the `navigateTo()` method sets `addressField.text = dir.absolutePath` (line 248). The explorer doesn't directly know which project a directory belongs to, but `projectRootPath` (line 67) tracks the current project root. We need to check if the current project is private.

Add a helper method to `ExplorerPanel`:

```kotlin
    private fun isCurrentProjectPrivate(): Boolean {
        val root = projectRootPath ?: return false
        return ctx.config.projectTree
            .filterIsInstance<ProjectTreeEntry.Project>()
            .any { it.directory.path == root && it.directory.private }
    }
```

Also add the imports at the top of the file:
```kotlin
import io.github.rygel.needlecast.model.ProjectTreeEntry
```

Then change the `navigateTo()` method (line 248):

From:
```kotlin
        addressField.text = dir.absolutePath
```

To:
```kotlin
        addressField.text = if (ctx.config.privacyModeEnabled && isCurrentProjectPrivate()) "••••••" else dir.absolutePath
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: All existing tests pass

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt
git commit -m "feat: redact explorer address bar for private projects"
```

---

### Task 7: Full build verification

**Files:** None (verification only)

- [ ] **Step 1: Run full test suite**

Run: `mvn verify -pl needlecast-desktop -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests including project tree tests**

Run: `mvn test -pl needlecast-desktop -T 4`
Expected: All tests pass

- [ ] **Step 3: Final commit (if any remaining fixes needed)**

Only if fixes were required during verification.
