# Doc Viewer Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dockable Doc Viewer panel that discovers generated documentation (Javadoc, rustdoc, Sphinx, etc.) for the active project and opens it in the system browser.

**Architecture:** Four new files plus one MainWindow edit. `DocCategory` and `DocTarget` are pure model types. `DocRegistry` is a stateless object mapping build tools to their known doc output paths — pure data, no I/O. `DocViewerPanel` does all I/O (disk checks, browser launch) and calls `DocRegistry` to get the candidate list when a project is loaded.

**Tech Stack:** Kotlin, Swing (JList + custom ListCellRenderer), JUnit 5, `java.awt.Desktop.browse()` for browser launch, ModernDocking for panel registration.

---

## File Map

| Action | Path |
|--------|------|
| Create | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/DocCategory.kt` |
| Create | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/DocTarget.kt` |
| Create | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/service/DocRegistry.kt` |
| Create | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocViewerPanel.kt` |
| Modify | `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt` |
| Create | `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/service/DocRegistryTest.kt` |

---

## Task 1: DocCategory enum and DocTarget data class

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/DocCategory.kt`
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/DocTarget.kt`

- [ ] **Step 1: Create DocCategory.kt**

```kotlin
package io.github.rygel.needlecast.model

enum class DocCategory(val displayName: String) {
    API_DOCS("API Docs"),
    TEST_REPORTS("Test Reports"),
    COVERAGE("Coverage"),
    SITE("Site"),
}
```

- [ ] **Step 2: Create DocTarget.kt**

```kotlin
package io.github.rygel.needlecast.model

data class DocTarget(
    val label: String,
    val relativePath: String,
    val buildTool: BuildTool,
    val category: DocCategory,
    val hint: String,
)
```

- [ ] **Step 3: Compile to verify**

```bash
mvn compile -pl needlecast-desktop -am -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/DocCategory.kt \
        needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/DocTarget.kt
git commit -m "feat: add DocCategory and DocTarget model types"
```

---

## Task 2: DocRegistry with unit tests

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/service/DocRegistry.kt`
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/service/DocRegistryTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DocCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocRegistryTest {

    @Test
    fun `targetsFor empty set returns empty list`() {
        val result = DocRegistry.targetsFor(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `targetsFor maven returns exactly 5 entries`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MAVEN))
        assertEquals(5, result.size)
    }

    @Test
    fun `maven api docs paths are correct`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MAVEN))
        val apiDocs = result.filter { it.category == DocCategory.API_DOCS }
        assertTrue(apiDocs.any { it.relativePath == "target/site/apidocs/index.html" })
        assertTrue(apiDocs.any { it.relativePath == "target/site/testapidocs/index.html" })
    }

    @Test
    fun `maven has one coverage and one test-reports and one site entry`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MAVEN))
        assertEquals(1, result.count { it.category == DocCategory.COVERAGE })
        assertEquals(1, result.count { it.category == DocCategory.TEST_REPORTS })
        assertEquals(1, result.count { it.category == DocCategory.SITE })
    }

    @Test
    fun `gradle returns exactly 6 entries`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.GRADLE))
        assertEquals(6, result.size)
    }

    @Test
    fun `cargo returns exactly 1 entry pointing to target dot doc`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.CARGO))
        assertEquals(1, result.size)
        assertEquals("target/doc/index.html", result[0].relativePath)
        assertEquals(DocCategory.API_DOCS, result[0].category)
    }

    @Test
    fun `mix returns exactly 1 ExDoc entry`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MIX))
        assertEquals(1, result.size)
        assertEquals("doc/index.html", result[0].relativePath)
        assertEquals("ExDoc", result[0].label)
    }

    @Test
    fun `each documented build tool has at least one target`() {
        val documentedTools = setOf(
            BuildTool.MAVEN, BuildTool.GRADLE, BuildTool.NPM, BuildTool.CARGO,
            BuildTool.UV, BuildTool.POETRY, BuildTool.PIP,
            BuildTool.MIX, BuildTool.SBT, BuildTool.BUNDLER, BuildTool.COMPOSER,
            BuildTool.PUB, BuildTool.FLUTTER, BuildTool.SPM,
            BuildTool.CMAKE, BuildTool.MAKE, BuildTool.DOTNET,
        )
        for (tool in documentedTools) {
            val result = DocRegistry.targetsFor(setOf(tool))
            assertTrue(result.isNotEmpty(), "$tool should have at least one doc target")
        }
    }

    @Test
    fun `undocumented build tools return empty list`() {
        for (tool in setOf(BuildTool.GO, BuildTool.INTELLIJ_RUN, BuildTool.APM, BuildTool.ZIG)) {
            val result = DocRegistry.targetsFor(setOf(tool))
            assertTrue(result.isEmpty(), "$tool should have no doc targets")
        }
    }

    @Test
    fun `multiple build tools returns union of targets`() {
        val maven  = DocRegistry.targetsFor(setOf(BuildTool.MAVEN)).size
        val gradle = DocRegistry.targetsFor(setOf(BuildTool.GRADLE)).size
        val both   = DocRegistry.targetsFor(setOf(BuildTool.MAVEN, BuildTool.GRADLE)).size
        assertEquals(maven + gradle, both)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -pl needlecast-desktop -Dtest=DocRegistryTest -q 2>&1 | tail -10
```

Expected: compilation error — `DocRegistry` does not exist yet.

- [ ] **Step 3: Create DocRegistry.kt**

```kotlin
package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DocCategory
import io.github.rygel.needlecast.model.DocTarget

object DocRegistry {

    private val all: List<DocTarget> = listOf(
        // ── Maven ────────────────────────────────────────────────────────────
        DocTarget("Javadoc",        "target/site/apidocs/index.html",     BuildTool.MAVEN, DocCategory.API_DOCS,      "mvn javadoc:javadoc"),
        DocTarget("Test Javadoc",   "target/site/testapidocs/index.html", BuildTool.MAVEN, DocCategory.API_DOCS,      "mvn javadoc:test-javadoc"),
        DocTarget("Surefire Report","target/site/surefire-report.html",   BuildTool.MAVEN, DocCategory.TEST_REPORTS,  "mvn surefire-report:report"),
        DocTarget("JaCoCo Coverage","target/site/jacoco/index.html",      BuildTool.MAVEN, DocCategory.COVERAGE,      "mvn jacoco:report"),
        DocTarget("Maven Site",     "target/site/index.html",             BuildTool.MAVEN, DocCategory.SITE,          "mvn site"),

        // ── Gradle ───────────────────────────────────────────────────────────
        DocTarget("Javadoc",        "build/docs/javadoc/index.html",                  BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew javadoc"),
        DocTarget("Groovydoc",      "build/docs/groovydoc/index.html",                BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew groovydoc"),
        DocTarget("Dokka HTML",     "build/docs/dokka/html/index.html",               BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew dokkaHtml"),
        DocTarget("Dokka Javadoc",  "build/docs/dokka/javadoc/index.html",            BuildTool.GRADLE, DocCategory.API_DOCS,     "./gradlew dokkaJavadoc"),
        DocTarget("JaCoCo Coverage","build/reports/jacoco/test/html/index.html",      BuildTool.GRADLE, DocCategory.COVERAGE,     "./gradlew jacocoTestReport"),
        DocTarget("Test Results",   "build/reports/tests/test/index.html",            BuildTool.GRADLE, DocCategory.TEST_REPORTS, "./gradlew test"),

        // ── npm / Node ───────────────────────────────────────────────────────
        DocTarget("TypeDoc",          "docs/index.html",          BuildTool.NPM, DocCategory.API_DOCS, "npx typedoc"),
        DocTarget("JSDoc",            "out/index.html",           BuildTool.NPM, DocCategory.API_DOCS, "npx jsdoc"),
        DocTarget("JSDoc (alt)",      "jsdoc/index.html",         BuildTool.NPM, DocCategory.API_DOCS, "npx jsdoc"),
        DocTarget("documentation.js", "documentation/index.html", BuildTool.NPM, DocCategory.API_DOCS, "npx documentation build"),

        // ── Rust ─────────────────────────────────────────────────────────────
        DocTarget("rustdoc", "target/doc/index.html", BuildTool.CARGO, DocCategory.API_DOCS, "cargo doc"),

        // ── Python (UV / Poetry / pip) ───────────────────────────────────────
        DocTarget("Sphinx", "docs/_build/html/index.html", BuildTool.UV,     DocCategory.API_DOCS, "make -C docs html"),
        DocTarget("MkDocs", "site/index.html",             BuildTool.UV,     DocCategory.SITE,     "mkdocs build"),
        DocTarget("pdoc",   "html/index.html",             BuildTool.UV,     DocCategory.API_DOCS, "pdoc --html ."),
        DocTarget("Sphinx", "docs/_build/html/index.html", BuildTool.POETRY, DocCategory.API_DOCS, "make -C docs html"),
        DocTarget("MkDocs", "site/index.html",             BuildTool.POETRY, DocCategory.SITE,     "mkdocs build"),
        DocTarget("pdoc",   "html/index.html",             BuildTool.POETRY, DocCategory.API_DOCS, "pdoc --html ."),
        DocTarget("Sphinx", "docs/_build/html/index.html", BuildTool.PIP,    DocCategory.API_DOCS, "make -C docs html"),
        DocTarget("MkDocs", "site/index.html",             BuildTool.PIP,    DocCategory.SITE,     "mkdocs build"),
        DocTarget("pdoc",   "html/index.html",             BuildTool.PIP,    DocCategory.API_DOCS, "pdoc --html ."),

        // ── Elixir ───────────────────────────────────────────────────────────
        DocTarget("ExDoc", "doc/index.html", BuildTool.MIX, DocCategory.API_DOCS, "mix docs"),

        // ── Scala ────────────────────────────────────────────────────────────
        DocTarget("Scaladoc (2.x)", "target/scala-2.13/api/index.html", BuildTool.SBT, DocCategory.API_DOCS, "sbt doc"),
        DocTarget("Scaladoc (3.x)", "target/scala-3/api/index.html",    BuildTool.SBT, DocCategory.API_DOCS, "sbt doc"),

        // ── Ruby ─────────────────────────────────────────────────────────────
        DocTarget("YARD", "doc/index.html",   BuildTool.BUNDLER, DocCategory.API_DOCS, "yard doc"),
        DocTarget("RDoc", "rdoc/index.html",  BuildTool.BUNDLER, DocCategory.API_DOCS, "rdoc"),

        // ── PHP ──────────────────────────────────────────────────────────────
        DocTarget("phpDocumentor", "docs/api/index.html", BuildTool.COMPOSER, DocCategory.API_DOCS, "phpdoc"),

        // ── Dart / Flutter ───────────────────────────────────────────────────
        DocTarget("dartdoc", "doc/api/index.html", BuildTool.PUB,     DocCategory.API_DOCS, "dart doc"),
        DocTarget("dartdoc", "doc/api/index.html", BuildTool.FLUTTER, DocCategory.API_DOCS, "dart doc"),

        // ── Swift ────────────────────────────────────────────────────────────
        DocTarget("DocC", "docs/index.html", BuildTool.SPM, DocCategory.API_DOCS, "swift package generate-documentation"),

        // ── C / C++ ──────────────────────────────────────────────────────────
        DocTarget("Doxygen",         "docs/html/index.html",       BuildTool.CMAKE, DocCategory.API_DOCS, "doxygen"),
        DocTarget("Doxygen (build/)", "build/docs/html/index.html", BuildTool.CMAKE, DocCategory.API_DOCS, "doxygen"),
        DocTarget("Doxygen",         "docs/html/index.html",       BuildTool.MAKE,  DocCategory.API_DOCS, "doxygen"),
        DocTarget("Doxygen (build/)", "build/docs/html/index.html", BuildTool.MAKE,  DocCategory.API_DOCS, "doxygen"),

        // ── .NET ─────────────────────────────────────────────────────────────
        DocTarget("DocFX", "_site/index.html", BuildTool.DOTNET, DocCategory.SITE, "docfx build"),
    )

    fun targetsFor(buildTools: Set<BuildTool>): List<DocTarget> =
        all.filter { it.buildTool in buildTools }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -pl needlecast-desktop -Dtest=DocRegistryTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` and `Tests run: 10, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/service/DocRegistry.kt \
        needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/service/DocRegistryTest.kt
git commit -m "feat: add DocRegistry with targets for 17 build tools"
```

---

## Task 3: DocViewerPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocViewerPanel.kt`

No unit test for this task — the logic lives in `DocRegistry` (already tested). The panel is pure Swing wiring.

- [ ] **Step 1: Create DocViewerPanel.kt**

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.DocCategory
import io.github.rygel.needlecast.model.DocTarget
import io.github.rygel.needlecast.service.DocRegistry
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * Dockable panel that discovers generated documentation for the active project
 * and opens it in the system browser.
 *
 * Entries are grouped by [DocCategory]. Available doc sets (output files exist
 * on disk) are shown normally; unavailable ones are greyed out with a tooltip
 * showing the command needed to generate them.
 *
 * Double-click or "Open in Browser" launches [Desktop.browse].
 */
class DocViewerPanel : JPanel(BorderLayout()) {

    // ── Row model ─────────────────────────────────────────────────────────────

    private sealed class DocRow {
        data class Header(val category: DocCategory) : DocRow()
        data class Entry(val target: DocTarget, val available: Boolean) : DocRow()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private val listModel     = DefaultListModel<DocRow>()
    private val list          = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(DocRowRenderer())
    }
    private val openButton    = JButton("Open in Browser").apply { isEnabled = false }
    private val refreshButton = JButton("\u21BB  Refresh")    // ↻

    private var currentProject: DetectedProject? = null

    // ─────────────────────────────────────────────────────────────────────────

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            isOpaque = false
            add(refreshButton)
        }
        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            isOpaque = false
            add(openButton)
        }

        add(toolbar,           BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)
        add(buttonBar,         BorderLayout.SOUTH)

        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = list.selectedValue
                openButton.isEnabled = row is DocRow.Entry && row.available
            }
        }

        openButton.addActionListener    { openSelected() }
        refreshButton.addActionListener { reload() }

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) openSelected()
            }
        })
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadProject(project: DetectedProject?) {
        currentProject = project
        reload()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun reload() {
        listModel.clear()
        openButton.isEnabled = false

        val project = currentProject ?: return
        val projectDir = File(project.directory.path)
        val targets = DocRegistry.targetsFor(project.buildTools)

        for (category in DocCategory.entries) {
            val inCategory = targets.filter { it.category == category }
            if (inCategory.isEmpty()) continue

            val rows = inCategory
                .map { target -> DocRow.Entry(target, File(projectDir, target.relativePath).exists()) }
                .sortedWith(compareBy({ !it.available }, { it.target.label }))

            listModel.addElement(DocRow.Header(category))
            rows.forEach { listModel.addElement(it) }
        }
    }

    private fun openSelected() {
        val row = list.selectedValue as? DocRow.Entry ?: return
        if (!row.available) return
        val file = File(currentProject?.directory?.path ?: return, row.target.relativePath)
        try {
            Desktop.getDesktop().browse(file.toURI())
        } catch (ex: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Could not open browser:\n${ex.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private inner class DocRowRenderer : ListCellRenderer<DocRow> {

        private val headerLabel = JLabel().apply {
            border = BorderFactory.createEmptyBorder(6, 6, 2, 6)
            font = font.deriveFont(Font.BOLD, 11f)
            isOpaque = true
        }

        private val entryPanel = JPanel(BorderLayout(6, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 18, 2, 6)
            isOpaque = true
        }
        private val entryLabel = JLabel()
        private val hintLabel  = JLabel().apply {
            font = font.deriveFont(Font.ITALIC, 10f)
            foreground = Color.GRAY
        }

        init {
            entryPanel.add(entryLabel, BorderLayout.CENTER)
            entryPanel.add(hintLabel,  BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out DocRow>, value: DocRow?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            return when (val row = value) {
                is DocRow.Header -> {
                    headerLabel.text       = row.category.displayName
                    headerLabel.background = list.background
                    headerLabel.foreground = list.foreground
                    headerLabel
                }
                is DocRow.Entry -> {
                    val symbol = if (row.available) "\u25CF" else "\u25CB"  // ● or ○
                    entryLabel.text       = "$symbol  ${row.target.label}"
                    entryLabel.foreground = if (row.available) {
                        if (isSelected) list.selectionForeground else list.foreground
                    } else {
                        Color.GRAY
                    }
                    hintLabel.text        = if (row.available) "" else row.target.hint
                    val bg = if (isSelected && row.available) list.selectionBackground else list.background
                    entryPanel.background = bg
                    entryLabel.background = bg
                    hintLabel.background  = bg
                    entryPanel.toolTipText = if (row.available) null else "Generate with: ${row.target.hint}"
                    entryPanel
                }
                null -> JLabel()
            }
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

```bash
mvn compile -pl needlecast-desktop -am -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocViewerPanel.kt
git commit -m "feat: add DocViewerPanel with grouped doc discovery list"
```

---

## Task 4: Wire DocViewerPanel into MainWindow

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

MainWindow is large (~1100 lines). Make three small, targeted edits. No import needed — `DocViewerPanel` is in the same package.

- [ ] **Step 1: Add panel and dockable field declarations**

Search for the line `private val docsDockable` in MainWindow.kt. Directly after the block of dockable declarations (the last one is `commandInputDockable`), add:

```kotlin
    private val docViewerPanel    = DocViewerPanel()
    private val docViewerDockable = DockablePanel(docViewerPanel, "doc-viewer", "Doc Viewer")
```

The resulting block should look like:

```kotlin
    private val docsDockable         = DockablePanel(docsPanel,        "docs",         "Docs")
    private val promptInputDockable  = DockablePanel(promptInputPanel,  "prompt-input", "Prompt Input")
    private val commandInputDockable = DockablePanel(commandInputPanel, "command-input","Command Input")
    private val docViewerPanel       = DocViewerPanel()
    private val docViewerDockable    = DockablePanel(docViewerPanel, "doc-viewer", "Doc Viewer")
```

- [ ] **Step 2: Register the dockable with ModernDocking**

Search for `Docking.registerDockable(docsDockable)` in MainWindow.kt. Add the new registration directly after it:

```kotlin
            Docking.registerDockable(docsDockable)
            Docking.registerDockable(docViewerDockable)
```

- [ ] **Step 3: Add loadProject call in applyProjectSelection**

Search for `commandPanel.loadProject(project)` in `applyProjectSelection`. Add `docViewerPanel.loadProject(project)` on the next line:

```kotlin
        if (pathChanged || commandsChanged) {
            commandPanel.loadProject(project)
            docViewerPanel.loadProject(project)
        }
```

- [ ] **Step 4: Compile to verify all three edits compile cleanly**

```bash
mvn compile -pl needlecast-desktop -am -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run the full unit test suite (non-desktop tests only)**

```bash
mvn test -pl needlecast-desktop -am -T 4 -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`. All existing tests pass; `DocRegistryTest` also passes.

- [ ] **Step 6: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat: register DocViewerPanel in MainWindow docking system"
```
