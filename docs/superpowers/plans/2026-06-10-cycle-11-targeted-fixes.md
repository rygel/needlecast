# Cycle 11: Targeted Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four UX gaps: command list build-tool badges, command override reset, explorer context menu open-in-finder, and update error UX (already done).

**Architecture:** Three independent patches touching CommandPanel, ExplorerPanel. No cross-cutting changes. Each task is self-contained.

**Tech Stack:** Kotlin, Swing (JList renderer, JPopupMenu), existing `BuildTool` enum with tag labels/colors.

---

## Task 1: Build Tool Badges in Command List

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt:289-294,301-344,589-619`
- Test: Visual verification

### Part A: Fix hardcoded BuildTool.MAVEN in executeCommand

- [ ] **Step 1: Add buildTool parameter to executeCommand**

In `CommandPanel.kt`, change `executeCommand` signature at line 301 to accept `buildTool: BuildTool = BuildTool.MAVEN`:

```kotlin
private fun executeCommand(
    label: String,
    argv: List<String>,
    workingDir: String,
    buildTool: BuildTool = BuildTool.MAVEN,
) {
```

Then at line 341, replace `BuildTool.MAVEN` with the parameter:

```kotlin
val descriptor = CommandDescriptor(label, buildTool, argv, workingDir, currentProjectEnv)
```

- [ ] **Step 2: Pass buildTool from runSelected**

At line 293, change:

```kotlin
executeCommand(cmd.label, cmd.argv, cmd.workingDirectory, cmd.buildTool)
```

The other call sites (`rerunHistoryEntry` at 298, `drainQueue` at 385) don't have buildTool available so they'll use the default `BuildTool.MAVEN` — acceptable since history/queue don't track build tool.

- [ ] **Step 3: Compile and verify**

Run: `mvn compile -pl needlecast-desktop -q`

### Part B: Add build tool badge to CommandCellRenderer

- [ ] **Step 4: Update CommandCellRenderer to show badge**

Replace the `CommandCellRenderer` class (lines 589-619) with a renderer that prepends a colored badge:

```kotlin
private class CommandCellRenderer : ListCellRenderer<CommandDescriptor> {
    private val panel =
        JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            isOpaque = true
        }
    private val badgeLabel =
        JLabel().apply {
            font = font.deriveFont(Font.BOLD, 10f)
            border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
            isOpaque = true
        }
    private val nameLabel = JLabel()

    init {
        panel.add(badgeLabel, BorderLayout.WEST)
        panel.add(nameLabel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out CommandDescriptor>,
        value: CommandDescriptor?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val tool = value?.buildTool
        if (tool != null) {
            badgeLabel.text = tool.tagLabel
            val bgColor =
                try {
                    java.awt.Color.decode(tool.tagColor)
                } catch (_: Exception) {
                    java.awt.Color.GRAY
                }
            badgeLabel.background = bgColor
            badgeLabel.foreground = java.awt.Color.WHITE
            badgeLabel.isVisible = true
        } else {
            badgeLabel.isVisible = false
        }
        nameLabel.text = (value?.label ?: "").toHtmlLabel()
        nameLabel.toolTipText =
            if (value?.isSupported == true) {
                value.argv.joinToString(" ")
            } else {
                "This run configuration type is not directly executable"
            }
        nameLabel.foreground =
            when {
                isSelected -> list.selectionForeground
                value?.isSupported == false -> java.awt.Color.GRAY
                else -> list.foreground
            }
        panel.background = if (isSelected) list.selectionBackground else list.background
        nameLabel.background = panel.background
        nameLabel.isOpaque = false
        return panel
    }
}
```

This renders a small opaque badge (e.g. "mvn" in green, "npm" in red) to the left of the command label.

- [ ] **Step 5: Compile and verify**

Run: `mvn compile -pl needlecast-desktop -q`

- [ ] **Step 6: Run ktlint**

Run: `mvn ktlint:check -pl needlecast-desktop -q`

Fix any formatting issues, then:

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt
git commit -m "feat(commands): show build tool badges in command list

Render colored badges (mvn, npm, gradle, etc.) next to command labels
in the command panel. Also fix hardcoded BuildTool.MAVEN in
executeCommand — now passes through the actual buildTool from the
selected command descriptor."
```

---

## Task 2: Reset Command Override to Default

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt:388-450`

- [ ] **Step 1: Add helper to detect if a command has an active override**

In `CommandPanel`, add a private helper after `editSelectedCommand()` (after line 450):

```kotlin
private fun findActiveOverride(cmd: CommandDescriptor): CommandOverride? {
    val workDir = currentProjectPath ?: return null
    val overrides = ctx.config.commandOverrides[workDir] ?: return null
    return overrides.firstOrNull { it.argv == cmd.argv }
        ?: overrides.firstOrNull { it.originalArgv == cmd.argv }
}
```

This checks if the command's argv matches either the overridden argv (current state) or the original argv (if somehow double-matched).

- [ ] **Step 2: Add "Reset to Default" to context menu**

In `showCommandContextMenu`, after the "Edit…" menu item (after line 413), add a separator and reset item:

```kotlin
val activeOverride = cmd?.let { findActiveOverride(it) }
if (activeOverride != null) {
    menu.addSeparator()
    menu.add(
        JMenuItem("Reset to Default").apply {
            icon = RemixIcons.icon("ri-arrow-go-back-line", 12)
            addActionListener { resetSelectedCommand(activeOverride) }
        },
    )
}
```

- [ ] **Step 3: Implement resetSelectedCommand**

Add after the new `findActiveOverride` helper:

```kotlin
private fun resetSelectedCommand(override: CommandOverride) {
    val idx = commandList.selectedIndex.takeIf { it >= 0 } ?: return
    val workDir = currentProjectPath ?: return
    val restored =
        CommandDescriptor(
            label = override.originalArgv.joinToString(" "),
            buildTool = commandModel.getElementAt(idx).buildTool,
            argv = override.originalArgv,
            workingDirectory = commandModel.getElementAt(idx).workingDirectory,
        )
    commandModel.set(idx, restored)
    val remaining =
        ctx.config.commandOverrides[workDir]
            ?.filterNot { it.originalArgv == override.originalArgv }
            ?: emptyList()
    val newOverrides =
        if (remaining.isEmpty()) {
            ctx.config.commandOverrides - workDir
        } else {
            ctx.config.commandOverrides + (workDir to remaining)
        }
    ctx.updateConfig(ctx.config.copy(commandOverrides = newOverrides))
}
```

The label for the restored command uses the original argv joined with spaces (since we don't store the original label separately in `CommandOverride`). The `buildTool` and `workingDirectory` are preserved from the current entry.

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -pl needlecast-desktop -q`

- [ ] **Step 5: Run ktlint**

Run: `mvn ktlint:check -pl needlecast-desktop -q`

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/CommandPanel.kt
git commit -m "feat(commands): add Reset to Default for overridden commands

Right-click context menu now shows 'Reset to Default' when a command
has been edited. Removes the stored CommandOverride from config and
restores the original argv from the override's originalArgv field."
```

---

## Task 3: Explorer Right-Click Open in Finder/Explorer

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt:607-661,371-384`

### Part A: Add "Open in Explorer" for directories

- [ ] **Step 1: Add context menu item for directories**

In `showContextMenu`, in the `FileEntry.Dir` branch (after line 627, before the closing brace at 628), add before the final `copyPathItem`:

```kotlin
menu.addSeparator()
menu.add(
    JMenuItem(
        when {
            IS_MAC -> "Open in Finder"
            IS_WINDOWS -> "Open in Explorer"
            else -> "Open in File Manager"
        },
    ).apply {
        icon = RemixIcons.icon("ri-folder-open-line", 12)
        addActionListener { openInFileManager(entry.file) }
    },
)
```

This reuses the existing `openInFileManager(dir: File)` method which already handles all platforms.

### Part B: Add "Reveal in Explorer" for files

- [ ] **Step 2: Add revealInFileManager method**

Add after `openInFileManager` (after line 384):

```kotlin
private fun revealInFileManager(file: File) {
    try {
        when {
            IS_WINDOWS -> ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
            IS_MAC -> ProcessBuilder("open", "-R", file.absolutePath).start()
            else -> openInFileManager(file.parentFile ?: return)
        }
    } catch (_: Exception) {
    }
}
```

- [ ] **Step 3: Add context menu item for files**

In `showContextMenu`, in the `FileEntry.RegularFile` branch (after line 651, before the closing brace at 652), add before the final `copyPathItem`:

```kotlin
menu.addSeparator()
menu.add(
    JMenuItem(
        when {
            IS_MAC -> "Reveal in Finder"
            IS_WINDOWS -> "Reveal in Explorer"
            else -> "Open Containing Folder"
        },
    ).apply {
        addActionListener { revealInFileManager(entry.file) }
    },
)
```

- [ ] **Step 4: Compile and verify**

Run: `mvn compile -pl needlecast-desktop -q`

- [ ] **Step 5: Run ktlint**

Run: `mvn ktlint:check -pl needlecast-desktop -q`

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/explorer/ExplorerPanel.kt
git commit -m "feat(explorer): add Open in Finder/Explorer to context menu

Directories get 'Open in Explorer/Finder' which opens the directory in
the OS file manager. Files get 'Reveal in Explorer/Finder' which
selects the file in the OS file manager (explorer /select on Windows,
open -R on macOS, opens parent on Linux)."
```

---

## Task 4: Bump Version and Release

- [ ] **Step 1: Bump version to 0.8.3**

Update `pom.xml` and `needlecast-desktop/pom.xml` from `0.8.3-beta.1` to `0.8.3`.

- [ ] **Step 2: Commit and push**

```bash
git add pom.xml needlecast-desktop/pom.xml
git commit -m "chore: bump version to 0.8.3"
git push origin develop
```

- [ ] **Step 3: Merge to main and trigger release**

```bash
git checkout main
git merge develop --no-edit
git push origin main
gh workflow run auto-release.yml --ref main -f version="0.8.3"
```

- [ ] **Step 4: Verify release assets**

Check that the release has all 9 assets and the appcast.xml is updated.

---

## Already Done

- **Update Error UX** — Implemented in commit `583721a` as `UpdateCheckErrors.kt` with classification, friendly messages, and tests. Integrated into `MainWindow.kt`.
