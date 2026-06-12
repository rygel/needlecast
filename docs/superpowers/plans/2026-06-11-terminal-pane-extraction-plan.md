# TerminalManager Decomposition — ProjectTerminalPane Extraction Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `ProjectTerminalPane` + `TerminalTabHeader` from `TerminalManager.kt` into their own file, adding test coverage for tab lifecycle.

**Architecture:** File extraction — move two classes and two top-level utilities to a new file. No interface changes. No callback pattern changes. The only coupling is `TerminalManager` constructs `ProjectTerminalPane` in `activateProject()`.

**Tech Stack:** Kotlin, JUnit 5, AssertJ, Swing (JTabbedPane)

---

### Task 1: Extract ProjectTerminalPane into its own file

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ProjectTerminalPane.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalManager.kt`

- [ ] **Step 1: Read TerminalManager.kt in full**

Read the complete file (798 lines) to understand all the classes and their boundaries:
- `TerminalManager`: lines 43-377
- `ProjectTerminalPane`: lines 383-739
- `TerminalTabHeader`: lines 742-786
- `ENCODINGS`: line 788
- `logger`: line 790
- `tryRun`: lines 792-798

- [ ] **Step 2: Create ProjectTerminalPane.kt**

Create the new file with:
- Package declaration: `io.github.rygel.needlecast.ui.terminal`
- All imports used by `ProjectTerminalPane`, `TerminalTabHeader`, `ENCODINGS`, and `tryRun`
- The `ProjectTerminalPane` class (lines 383-739) — keep `internal class`
- The `TerminalTabHeader` class (lines 742-786) — keep `private class`
- The `ENCODINGS` constant (line 788) — change visibility from `private` to `internal val`
- A new `private val logger` for the new file (since `tryRun` uses it)
- The `tryRun` function (lines 792-798) — keep as top-level `private inline fun`

Imports needed by ProjectTerminalPane:
- `io.github.rygel.needlecast.ui.RemixIcons` (for TerminalTabHeader)
- `org.slf4j.LoggerFactory`
- `java.awt.*` (BorderLayout, Color, Dimension, FlowLayout, Font, event.MouseAdapter, event.MouseEvent)
- `javax.swing.*` (BorderFactory, JButton, JComboBox, JLabel, JPanel, JTabbedPane, SwingConstants, Timer)
- `java.nio.charset.Charset`

The class `ProjectTerminalPane` already has no dependency on `TerminalManager` — it only depends on `TerminalPanel`, `AgentStatus`, and standard Swing.

- [ ] **Step 3: Remove extracted code from TerminalManager.kt**

Remove:
- Lines 380-798 (the comment before `ProjectTerminalPane`, the class itself, `TerminalTabHeader`, `ENCODINGS`, `logger`, `tryRun`)

Keep:
- `CARD_EMPTY` constant (line 30) — used only by TerminalManager
- The existing `private val logger` in TerminalManager — but since we removed the one at line 790, we need to keep a logger. Check if TerminalManager already has one or needs one. Currently the logger is at line 790 (shared). After extraction, TerminalManager needs its own logger. Add one if not present.

Actually, looking at the code, the logger at line 790 is shared between TerminalManager and ProjectTerminalPane. After extraction:
- TerminalManager needs its own logger (for any logging it does)
- ProjectTerminalPane.kt gets its own logger

Check if TerminalManager uses `logger` anywhere in lines 43-377. If not, no logger needed there.

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Run full test suite**

Run: `mvn test -pl needlecast-desktop`
Expected: 614 tests, 0 failures, 3 skipped

- [ ] **Step 6: Run ktlint**

Run: `mvn ktlint:format -pl needlecast-desktop`

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ProjectTerminalPane.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalManager.kt
git commit -m "refactor(terminal): extract ProjectTerminalPane into its own file"
```

---

### Task 2: Write tests for ProjectTerminalPane

**Files:**
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/terminal/ProjectTerminalPaneTest.kt`

- [ ] **Step 1: Check how existing terminal tests create TerminalPanel instances**

Read one of the existing UI tests to understand the test pattern:
- `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalThemeUiTest.kt` (185 lines)

Since `ProjectTerminalPane` extends `JPanel` and creates `JTabbedPane` + `TerminalPanel` instances, tests need a headful environment. Use `@EnabledIf("isHeadful")` pattern.

- [ ] **Step 2: Write test file**

```kotlin
package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.awt.GraphicsEnvironment

class ProjectTerminalPaneTest {
    companion object {
        @JvmStatic
        fun isHeadful(): Boolean = !GraphicsEnvironment.isHeadless()
    }

    private fun createPane(path: String = "/test/project"): ProjectTerminalPane {
        return ProjectTerminalPane(
            path = path,
            isDark = true,
        )
    }

    @Test
    @EnabledIf("isHeadful")
    fun `addTerminalTab increases tab count`() {
        val pane = createPane()
        val before = pane.tabCount
        pane.addTerminalTab()
        assertEquals(before + 1, pane.tabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `closeTab removes tab`() {
        val pane = createPane()
        pane.addTerminalTab()
        pane.addTerminalTab()
        val countBefore = pane.tabCount
        pane.closeTab(0)
        assertEquals(countBefore - 1, pane.tabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `closeActiveTab removes the selected tab`() {
        val pane = createPane()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.tabs.selectedIndex = 1
        val countBefore = pane.tabCount
        pane.closeActiveTab()
        assertEquals(countBefore - 1, pane.tabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `nextTab cycles tab selection`() {
        val pane = createPane()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.tabs.selectedIndex = 2
        pane.nextTab()
        assertEquals(0, pane.tabs.selectedIndex)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `prevTab cycles tab selection`() {
        val pane = createPane()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.tabs.selectedIndex = 0
        pane.prevTab()
        assertEquals(2, pane.tabs.selectedIndex)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `dispose removes all tabs`() {
        val pane = createPane()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.dispose()
        assertEquals(0, pane.tabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `status aggregation reports THINKING when any tab is thinking`() {
        val pane = createPane()
        var reportedStatus: AgentStatus? = null
        pane.onStatusChanged = { status -> reportedStatus = status }
        pane.addTerminalTab()
        pane.forceStatusOnClaudeTabs(AgentStatus.THINKING)
        assertEquals(AgentStatus.THINKING, reportedStatus)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `applyFontSize propagates to all tabs`() {
        val pane = createPane()
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.applyFontSize(16)
        // Verify no exceptions — the actual font size is internal to TerminalPanel
        // The test passes if no exception is thrown
    }
}
```

Note: `tabCount` and `tabs` may need to be exposed for testing. Check if they're accessible. `ProjectTerminalPane` has `tabs` as `private val tabs = JTabbedPane()`. Either:
1. Make `tabs` `internal` for test access
2. Add a `val tabCount: Int get() = realTabCount` public property
3. Use `componentCount` from the JPanel superclass

Check which approach works with the actual class structure and choose the least invasive option.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl needlecast-desktop -Dtest=ProjectTerminalPaneTest -DfailIfNoTests=false`
Expected: Tests pass (or are skipped on headless CI)

- [ ] **Step 4: Run full suite**

Run: `mvn test -pl needlecast-desktop`

- [ ] **Step 5: Run ktlint**

Run: `mvn ktlint:format -pl needlecast-desktop`

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/terminal/ProjectTerminalPaneTest.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ProjectTerminalPane.kt
git commit -m "test(terminal): add ProjectTerminalPaneTest with tab lifecycle tests"
```

---

### Task 3: Final verification

- [ ] **Step 1: Run ktlint**

Run: `mvn ktlint:format -pl needlecast-desktop`

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl needlecast-desktop`
Expected: 620+ tests, 0 failures (some new headful tests may be skipped on CI)

- [ ] **Step 3: Verify line counts**

Run:
```bash
wc -l needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalManager.kt needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/ProjectTerminalPane.kt
```
Expected: TerminalManager ~440 lines, ProjectTerminalPane ~420 lines

- [ ] **Step 4: Commit docs and plan**

```bash
git add docs/superpowers/specs/2026-06-11-terminal-pane-extraction-design.md docs/superpowers/plans/2026-06-11-terminal-pane-extraction-plan.md
git commit -m "docs: add Cycle 21 spec and implementation plan"
```

- [ ] **Step 5: Push and create PR**

Create feature branch `cycle-21/terminal-pane-extraction`, push, open PR into `develop`.
