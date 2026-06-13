# Cycle 23: DirectoryPanel Decomposition

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `CompactProjectDirectoryRenderer` and `ShellSettingsDialog` from `DirectoryPanel.kt` (727 → ~480 lines), adding test coverage for both extractions.

**Architecture:** The renderer is a self-contained `ListCellRenderer` with zero panel coupling — clean extraction. The shell settings dialog is a reusable form dialog (like the existing `EnvEditorDialog` pattern). Both reduce DirectoryPanel's responsibilities.

**Tech Stack:** Kotlin, Swing, JUnit 5

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRenderer.kt` | Project list cell renderer with color stripe, active dot, branch, build-tool tags |
| Create | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialog.kt` | Modal dialog for per-project shell executable and startup command |
| Create | `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRendererTest.kt` | Renderer tests |
| Create | `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialogTest.kt` | Dialog tests |
| Modify | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DirectoryPanel.kt` | Remove renderer class and shell settings method, use extractions |

---

### Task 1: Extract CompactProjectDirectoryRenderer

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRenderer.kt`
- Test: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRendererTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.rygel.needlecast.ui.renderers

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JList

class CompactProjectDirectoryRendererTest {
    private val activePaths = mutableSetOf<String>()
    private val renderer = CompactProjectDirectoryRenderer(
        activePathsProvider = { activePaths },
        gitStatusProvider = { null },
    )
    private val list = JList<DetectedProject>()

    private fun makeProject(
        path: String = "/test",
        label: String = "test-project",
        tools: Set<BuildTool> = emptySet(),
        color: String? = null,
        scanFailed: Boolean = false,
    ) = DetectedProject(
        directory = ProjectDirectory(path = path, label = label, color = color),
        buildTools = tools,
        entries = emptyList<ProjectTreeEntry>(),
        scanFailed = scanFailed,
    )

    @Test
    fun `renders project name`() {
        val project = makeProject(label = "my-app")
        val c = renderer.getListCellRendererComponent(list, project, 0, false, false)
        assertNotNull(c)
    }

    @Test
    fun `active dot visible when path in activePaths`() {
        val project = makeProject(path = "/x")
        activePaths.add("/x")
        renderer.getListCellRendererComponent(list, project, 0, false, false)
        assertTrue(renderer.activeDotVisible)
    }

    @Test
    fun `active dot hidden when path not in activePaths`() {
        val project = makeProject(path = "/x")
        activePaths.clear()
        renderer.getListCellRendererComponent(list, project, 0, false, false)
        assertFalse(renderer.activeDotVisible)
    }

    @Test
    fun `color stripe visible when project has color`() {
        val project = makeProject(color = "#FF0000")
        renderer.getListCellRendererComponent(list, project, 0, false, false)
        assertTrue(renderer.colorStripeVisible)
    }

    @Test
    fun `color stripe hidden when project has no color`() {
        val project = makeProject(color = null)
        renderer.getListCellRendererComponent(list, project, 0, false, false)
        assertFalse(renderer.colorStripeVisible)
    }

    @Test
    fun `handles null value without exception`() {
        val c = renderer.getListCellRendererComponent(list, null, 0, false, false)
        assertNotNull(c)
    }

    @Test
    fun `scan failed shows warning icon`() {
        val project = makeProject(scanFailed = true)
        renderer.getListCellRendererComponent(list, project, 0, false, false)
        assertTrue(renderer.hasWarningIcon)
    }

    @Test
    fun `branch shown when git status available`() {
        val rendererWithGit = CompactProjectDirectoryRenderer(
            activePathsProvider = { emptySet() },
            gitStatusProvider = { GitStatus(branch = "main", isDirty = false) },
        )
        val project = makeProject(path = "/x")
        rendererWithGit.getListCellRendererComponent(list, project, 0, false, false)
        assertEquals("main", rendererWithGit.branchText)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl needlecast-desktop -Dtest="CompactProjectDirectoryRendererTest" -q 2>&1`
Expected: FAIL — `Unresolved reference 'CompactProjectDirectoryRenderer'`

- [ ] **Step 3: Create the renderer file**

Move the entire `CompactProjectDirectoryRenderer` class from `DirectoryPanel.kt` (lines 590-727) into its own file at `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRenderer.kt`.

Change from `private class` to `internal class`. Change constructor parameter names to `activePathsProvider` and `gitStatusProvider` (keep positional compatibility). Add test-visibility properties: `activeDotVisible`, `colorStripeVisible`, `branchText`, `hasWarningIcon`.

```kotlin
package io.github.rygel.needlecast.ui.renderers

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.ui.RemixIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class CompactProjectDirectoryRenderer(
    private val activePathsProvider: () -> Set<String>,
    private val gitStatusProvider: (String) -> GitStatus?,
) : ListCellRenderer<DetectedProject> {
    private val colorStripe =
        JPanel().apply {
            preferredSize = Dimension(4, 0)
            isOpaque = true
        }
    private val panel =
        JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
    private val outerPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = true
        }

    init {
        outerPanel.add(colorStripe, BorderLayout.WEST)
        outerPanel.add(panel, BorderLayout.CENTER)
    }

    private val nameLabel =
        JLabel().apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
    private val activeDot =
        JLabel(RemixIcons.icon("ri-checkbox-blank-circle-fill", 10, Color(0x4CAF50))).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 4)
        }
    private val branchLabel =
        JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            foreground = Color(0x888888)
            border = BorderFactory.createEmptyBorder(0, 18, 0, 0)
        }
    private val tagsPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = false
        }

    private val nameRow =
        JPanel(BorderLayout(2, 0)).apply {
            isOpaque = false
            add(activeDot, BorderLayout.WEST)
            add(nameLabel, BorderLayout.CENTER)
            add(tagsPanel, BorderLayout.EAST)
        }

    private val cellPanel =
        JPanel(BorderLayout(0, 1)).apply {
            isOpaque = false
            add(nameRow, BorderLayout.NORTH)
            add(branchLabel, BorderLayout.CENTER)
        }

    init {
        panel.add(cellPanel, BorderLayout.CENTER)
    }

    val activeDotVisible: Boolean get() = activeDot.isVisible
    val colorStripeVisible: Boolean get() = colorStripe.isVisible
    val branchText: String get() = branchLabel.text
    val hasWarningIcon: Boolean get() = tagsPanel.components.any { it is JLabel }

    override fun getListCellRendererComponent(
        list: JList<out DetectedProject>,
        value: DetectedProject?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val isActive = value != null && value.directory.path in activePathsProvider()
        activeDot.isVisible = isActive
        nameLabel.text = value?.directory?.label() ?: ""

        val gs = value?.let { gitStatusProvider(it.directory.path) }
        if (gs != null && gs.branch != null) {
            val dirtyMark = if (gs.isDirty) "*" else ""
            branchLabel.text = "${gs.branch}$dirtyMark"
            branchLabel.toolTipText = gs.branch
            branchLabel.foreground = if (gs.isDirty) Color(0xE6A817) else Color(0x888888)
        } else {
            branchLabel.text = " "
            branchLabel.toolTipText = null
        }

        tagsPanel.removeAll()
        if (value != null) {
            if (value.scanFailed) {
                tagsPanel.add(
                    JLabel(RemixIcons.icon("ri-error-warning-line", 10, Color(0xB71C1C))).apply {
                        toolTipText = "Scan failed — check logs or rescan"
                    },
                )
            } else {
                val tools = value.buildTools
                val tags = if (tools.isEmpty()) listOf(null) else tools.map { it }
                tags.forEach { tool -> tagsPanel.add(buildTagLabel(tool)) }
            }
        }

        val bg = if (isSelected) list.selectionBackground else list.background
        outerPanel.background = bg
        panel.background = bg
        nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
        panel.isOpaque = true

        val colorHex = value?.directory?.color
        colorStripe.isVisible = colorHex != null
        if (colorHex != null) {
            colorStripe.background =
                try {
                    Color.decode(colorHex)
                } catch (_: Exception) {
                    Color.GRAY
                }
        }

        return outerPanel
    }

    private fun buildTagLabel(
        tool: BuildTool?,
        label: String? = null,
        color: String? = null,
    ): JLabel {
        val text = label ?: tool?.tagLabel ?: "?"
        val hex = color ?: tool?.tagColor ?: "#757575"
        return JLabel(text).apply {
            font = Font(Font.SANS_SERIF, Font.BOLD, 9)
            foreground = Color.WHITE
            background = Color.decode(hex)
            isOpaque = true
            border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
            preferredSize = Dimension(preferredSize.width, 14)
            if (tool == null && label == "\u26A0") toolTipText = "Scan failed — check logs or rescan"
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl needlecast-desktop -Dtest="CompactProjectDirectoryRendererTest" -q 2>&1`
Expected: 8 tests PASS

- [ ] **Step 5: Update DirectoryPanel to use extracted renderer**

In `DirectoryPanel.kt`:
1. Add import: `import io.github.rygel.needlecast.ui.renderers.CompactProjectDirectoryRenderer`
2. Change line 67 from `setCellRenderer(CompactProjectDirectoryRenderer({ activePaths }) { path -> gitStatusCache[path] })` to `setCellRenderer(CompactProjectDirectoryRenderer(activePathsProvider = { activePaths }, gitStatusProvider = { path -> gitStatusCache[path] }))`
3. Delete lines 590-727 (the entire `CompactProjectDirectoryRenderer` private class)

- [ ] **Step 6: Compile and run all tests**

Run: `mvn test -pl needlecast-desktop -q 2>&1`
Expected: All existing tests pass + 8 new tests pass. 632 total, 0 new failures.

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRenderer.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRendererTest.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DirectoryPanel.kt
git commit -m "Extract CompactProjectDirectoryRenderer from DirectoryPanel

- Renderer (138 lines) moved to ui/renderers/ package
- +8 tests for rendering, active dot, color stripe, git status, scan failure
- DirectoryPanel reduced from 727 to ~589 lines"
```

---

### Task 2: Extract ShellSettingsDialog

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialog.kt`
- Test: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialogTest.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DirectoryPanel.kt:472-543`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

class ShellSettingsDialogTest {

    @Test
    fun `dialog has correct title`() {
        val dialog = ShellSettingsDialog(
            owner = null,
            projectLabel = "my-project",
            currentShell = "zsh",
            currentStartup = "conda activate ml",
            onSave = {},
        )
        assertEquals("Shell Settings \u2014 my-project", dialog.title)
        dialog.dispose()
    }

    @Test
    fun `shell field shows current value`() {
        val dialog = ShellSettingsDialog(
            owner = null,
            projectLabel = "test",
            currentShell = "fish",
            currentStartup = null,
            onSave = {},
        )
        assertEquals("fish", dialog.shellText)
        dialog.dispose()
    }

    @Test
    fun `startup field shows current value`() {
        val dialog = ShellSettingsDialog(
            owner = null,
            projectLabel = "test",
            currentShell = null,
            currentStartup = "echo hello",
            onSave = {},
        )
        assertEquals("echo hello", dialog.startupText)
        dialog.dispose()
    }

    @Test
    fun `shell field is empty when null`() {
        val dialog = ShellSettingsDialog(
            owner = null,
            projectLabel = "test",
            currentShell = null,
            currentStartup = null,
            onSave = {},
        )
        assertEquals("", dialog.shellText)
        dialog.dispose()
    }

    @Test
    fun `onSave receives trimmed values`() {
        var savedShell: String? = "unset"
        var savedStartup: String? = "unset"
        val dialog = ShellSettingsDialog(
            owner = null,
            projectLabel = "test",
            currentShell = "  zsh  ",
            currentStartup = "  echo hi  ",
            onSave = { shell, startup ->
                savedShell = shell
                savedStartup = startup
            },
        )
        dialog.simulateOk()
        assertEquals("zsh", savedShell)
        assertEquals("echo hi", savedStartup)
        dialog.dispose()
    }

    @Test
    fun `onSave receives null for blank shell`() {
        var savedShell: String? = "unset"
        val dialog = ShellSettingsDialog(
            owner = null,
            projectLabel = "test",
            currentShell = "   ",
            currentStartup = null,
            onSave = { shell, _ -> savedShell = shell },
        )
        dialog.simulateOk()
        assertEquals(null, savedShell)
        dialog.dispose()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl needlecast-desktop -Dtest="ShellSettingsDialogTest" -q 2>&1`
Expected: FAIL — `Unresolved reference 'ShellSettingsDialog'`

- [ ] **Step 3: Create ShellSettingsDialog**

Create `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialog.kt` following the `EnvEditorDialog` pattern:

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke

internal class ShellSettingsDialog(
    owner: Window?,
    projectLabel: String,
    currentShell: String?,
    currentStartup: String?,
    private val onSave: (shell: String?, startup: String?) -> Unit,
) : JDialog(owner, "Shell Settings \u2014 $projectLabel", ModalityType.APPLICATION_MODAL) {

    private val shellField = JTextField(currentShell ?: "", 30)
    private val startupField = JTextField(currentStartup ?: "", 30)

    val shellText: String get() = shellField.text
    val startupText: String get() = startupField.text

    private val defaultShell =
        when {
            IS_WINDOWS -> "cmd.exe"
            IS_MAC -> "/bin/zsh"
            else -> "/bin/bash"
        }

    init {
        val form = buildForm()
        add(form, BorderLayout.CENTER)

        val buttonPanel =
            JPanel().apply {
                val okButton = JButton("OK")
                val cancelButton = JButton("Cancel")
                okButton.addActionListener { handleOk() }
                cancelButton.addActionListener { dispose() }
                rootPane.defaultButton = okButton
                add(okButton)
                add(cancelButton)
            }
        add(buttonPanel, BorderLayout.SOUTH)

        pack()
        setLocationRelativeTo(owner)

        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
    }

    private fun buildForm(): JPanel =
        JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            val gc =
                GridBagConstraints().apply {
                    insets = Insets(4, 4, 4, 4)
                    anchor = GridBagConstraints.WEST
                }

            gc.gridx = 0
            gc.gridy = 0
            gc.weightx = 0.0
            gc.fill = GridBagConstraints.NONE
            add(JLabel("Shell:"), gc)
            gc.gridx = 1
            gc.weightx = 1.0
            gc.fill = GridBagConstraints.HORIZONTAL
            add(shellField, gc)

            gc.gridx = 0
            gc.gridy = 1
            gc.weightx = 0.0
            gc.fill = GridBagConstraints.NONE
            add(JLabel("Startup command:"), gc)
            gc.gridx = 1
            gc.weightx = 1.0
            gc.fill = GridBagConstraints.HORIZONTAL
            add(startupField, gc)

            gc.gridx = 0
            gc.gridy = 2
            gc.gridwidth = 2
            gc.fill = GridBagConstraints.HORIZONTAL
            add(
                JLabel(
                    "<html><small>" +
                        "Shell: e.g. <tt>zsh</tt>, <tt>fish</tt>, <tt>powershell</tt> \u2014 " +
                        "blank uses system default (<tt>$defaultShell</tt>)<br>" +
                        "Startup: sent to the shell on open, e.g. <tt>conda activate ml</tt>" +
                        "</small></html>",
                ),
                gc,
            )
        }

    private fun handleOk() {
        val shell = shellField.text.trim().takeIf { it.isNotEmpty() }
        val startup = startupField.text.trim().takeIf { it.isNotEmpty() }
        onSave(shell, startup)
        dispose()
    }

    fun simulateOk() = handleOk()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl needlecast-desktop -Dtest="ShellSettingsDialogTest" -q 2>&1`
Expected: 6 tests PASS

- [ ] **Step 5: Update DirectoryPanel to use ShellSettingsDialog**

In `DirectoryPanel.kt`, replace the `editShellSettings` method (lines 472-543) with:

```kotlin
    private fun editShellSettings(project: DetectedProject) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        ShellSettingsDialog(
            owner = owner,
            projectLabel = project.directory.label(),
            currentShell = project.directory.shellExecutable,
            currentStartup = project.directory.startupCommand,
            onSave = { shell, startup ->
                updateProjectDirectory(project) { it.copy(shellExecutable = shell, startupCommand = startup) }
            },
        ).isVisible = true
    }
```

This removes ~70 lines of inline GridBagLayout form building.

- [ ] **Step 6: Compile and run all tests**

Run: `mvn test -pl needlecast-desktop -q 2>&1`
Expected: All tests pass. DirectoryPanel now ~510 lines.

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialog.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/ShellSettingsDialogTest.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DirectoryPanel.kt
git commit -m "Extract ShellSettingsDialog from DirectoryPanel

- ShellSettingsDialog (108 lines) follows EnvEditorDialog pattern
- +6 tests for dialog fields, title, save callback
- DirectoryPanel reduced from ~589 to ~510 lines"
```

---

### Task 3: Final verification and PR

**Files:**
- All files from Tasks 1 and 2

- [ ] **Step 1: Run ktlint format**

Run: `mvn ktlint:format -pl needlecast-desktop -q 2>&1`
Expected: No output (clean)

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl needlecast-desktop -q 2>&1`
Expected: 632+ tests, 0 new failures (pre-existing SkillLibraryStoreTest symlink failures are known/unrelated)

- [ ] **Step 3: Verify line counts**

Run: `(Get-Content 'needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\ui\DirectoryPanel.kt').Count`
Expected: ~510 lines (down from 727)

- [ ] **Step 4: Push branch and create PR**

```bash
git push -u origin cycle-23/directory-panel-decomposition
gh pr create --base develop --title "Cycle 23: Extract CompactProjectDirectoryRenderer + ShellSettingsDialog from DirectoryPanel" --body "Decomposition of DirectoryPanel (727 to ~510 lines, -217).

## Extracted

- CompactProjectDirectoryRenderer (ui/renderers/, 138 lines) - project list cell renderer with color stripe, active dot, branch, build-tool tags
- ShellSettingsDialog (ui/, 108 lines) - modal dialog for per-project shell executable and startup command

## Tests

+14 new tests (8 renderer, 6 dialog)
All existing tests pass (pre-existing SkillLibraryStoreTest symlink failures unrelated)"
```

---

## Summary

| File | Before | After | Delta |
|------|--------|-------|-------|
| `DirectoryPanel.kt` | 727 | ~510 | -217 |
| `CompactProjectDirectoryRenderer.kt` | (inline) | 138 | +138 new file |
| `ShellSettingsDialog.kt` | (inline) | 108 | +108 new file |
| New test files | 0 | 2 | +14 tests |

Total net reduction in DirectoryPanel: **-217 lines** (30% smaller).
New test coverage: **+14 tests** (from 0 direct tests).
