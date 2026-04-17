# Docs Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dockable Docs panel that lists the active project's Markdown files and lets the user browse them in either rendered HTML or syntax-highlighted raw view.

**Architecture:** A single `DocsPanel` class (no external collaborators) owns a `JList` of relative file paths on the left and a `CardLayout` container on the right with two cards — `JEditorPane` for rendered HTML (via commonmark-java) and `RSyntaxTextArea` for raw Markdown. File-scanning and rendering run on a background executor. A lightweight cache keyed by `path + lastModified` avoids redundant disk reads.

**Tech Stack:** commonmark-java 0.22.0 (GFM tables + strikethrough extensions), `JEditorPane`, `RSyntaxTextArea` (already in project), `CardLayout`, `JList`, `java.nio.file.Files.walkFileTree`.

---

### Task 1: Add commonmark-java dependencies

**Files:**
- Modify: `pom.xml` (root — add version property + dependencyManagement entries)
- Modify: `needlecast-desktop/pom.xml` (add runtime dependencies)

- [ ] **Step 1: Add `commonmark.version` property to root `pom.xml`**

  In `pom.xml`, inside `<properties>`, after the `jsvg.version` line:
  ```xml
  <commonmark.version>0.22.0</commonmark.version>
  ```

- [ ] **Step 2: Add commonmark artifacts to root `<dependencyManagement>`**

  In `pom.xml`, inside `<dependencyManagement><dependencies>`, after the `imageio-webp` entry:
  ```xml
  <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark</artifactId>
      <version>${commonmark.version}</version>
  </dependency>
  <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark-ext-gfm-tables</artifactId>
      <version>${commonmark.version}</version>
  </dependency>
  <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark-ext-gfm-strikethrough</artifactId>
      <version>${commonmark.version}</version>
  </dependency>
  ```

- [ ] **Step 3: Add commonmark dependencies to `needlecast-desktop/pom.xml`**

  Inside `<dependencies>`, after the `vlcj` entry at the bottom:
  ```xml
  <!-- commonmark-java — Markdown to HTML rendering -->
  <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark</artifactId>
  </dependency>
  <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark-ext-gfm-tables</artifactId>
  </dependency>
  <dependency>
      <groupId>org.commonmark</groupId>
      <artifactId>commonmark-ext-gfm-strikethrough</artifactId>
  </dependency>
  ```

- [ ] **Step 4: Verify Maven resolves the new artifacts**

  Run: `mvn dependency:resolve -pl needlecast-desktop -q`
  Expected: BUILD SUCCESS, no "Artifact ... not found" errors.

- [ ] **Step 5: Commit**

  ```bash
  git add pom.xml needlecast-desktop/pom.xml
  git commit -m "chore: add commonmark-java 0.22.0 dependencies"
  ```

---

### Task 2: Extract and test file-discovery logic

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocsPanel.kt` (scanner companion only)
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/DocsPanelFileScannerTest.kt`

The scan + sort logic lives in a `companion object` function so it can be unit-tested without Swing.

- [ ] **Step 1: Write the failing tests**

  Create `DocsPanelFileScannerTest.kt`:
  ```kotlin
  package io.github.rygel.needlecast.ui

  import org.junit.jupiter.api.Assertions.*
  import org.junit.jupiter.api.Test
  import org.junit.jupiter.api.io.TempDir
  import java.io.File
  import java.nio.file.Path

  class DocsPanelFileScannerTest {

      @Test
      fun `returns empty list when directory has no md files`(@TempDir dir: Path) {
          File(dir.toFile(), "build.gradle").writeText("// nothing")
          assertEquals(emptyList<String>(), DocsPanel.collectMarkdownFiles(dir.toFile()))
      }

      @Test
      fun `README md is pinned first regardless of case`(@TempDir dir: Path) {
          val root = dir.toFile()
          File(root, "CHANGELOG.md").writeText("")
          File(root, "readme.md").writeText("")
          File(root, "ARCH.md").writeText("")
          val result = DocsPanel.collectMarkdownFiles(root)
          assertEquals("readme.md", result.first())
      }

      @Test
      fun `remaining files are sorted alphabetically by relative path`(@TempDir dir: Path) {
          val root = dir.toFile()
          File(root, "docs").mkdirs()
          File(root, "docs/GUIDE.md").writeText("")
          File(root, "docs/ARCH.md").writeText("")
          File(root, "CHANGELOG.md").writeText("")
          val result = DocsPanel.collectMarkdownFiles(root)
          assertEquals(listOf("CHANGELOG.md", "docs/ARCH.md", "docs/GUIDE.md"), result)
      }

      @Test
      fun `skips dot-git and build directories`(@TempDir dir: Path) {
          val root = dir.toFile()
          File(root, ".git/objects").mkdirs()
          File(root, ".git/objects/packed.md").writeText("")
          File(root, "target").mkdirs()
          File(root, "target/classes.md").writeText("")
          File(root, "node_modules").mkdirs()
          File(root, "node_modules/readme.md").writeText("")
          File(root, "build").mkdirs()
          File(root, "build/output.md").writeText("")
          File(root, ".gradle").mkdirs()
          File(root, ".gradle/config.md").writeText("")
          File(root, "REAL.md").writeText("")
          assertEquals(listOf("REAL.md"), DocsPanel.collectMarkdownFiles(root))
      }

      @Test
      fun `returns empty list when path is null`() {
          assertEquals(emptyList<String>(), DocsPanel.collectMarkdownFiles(null))
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  Run: `mvn test -pl needlecast-desktop -Dtest=DocsPanelFileScannerTest -q`
  Expected: FAIL — `DocsPanel` does not exist yet.

- [ ] **Step 3: Create `DocsPanel.kt` with companion object only**

  Create `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocsPanel.kt`:
  ```kotlin
  package io.github.rygel.needlecast.ui

  import java.awt.BorderLayout
  import java.io.File
  import java.nio.file.FileVisitResult
  import java.nio.file.Files
  import java.nio.file.Path
  import java.nio.file.SimpleFileVisitor
  import java.nio.file.attribute.BasicFileAttributes
  import javax.swing.JLabel
  import javax.swing.JPanel

  class DocsPanel : JPanel(BorderLayout()) {

      fun loadProject(path: String?) {
          // TODO: implemented in Task 3
      }

      companion object {
          private val SKIP_DIRS = setOf(".git", "target", "node_modules", "build", ".gradle")

          /** Collects relative paths of *.md files under [root], README variants first. */
          fun collectMarkdownFiles(root: File?): List<String> {
              if (root == null || !root.isDirectory) return emptyList()
              val found = mutableListOf<String>()
              Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
                  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                      val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                      return if (name in SKIP_DIRS) FileVisitResult.SKIP_SUBTREE
                      else FileVisitResult.CONTINUE
                  }
                  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                      if (file.fileName.toString().endsWith(".md", ignoreCase = true)) {
                          found.add(root.toPath().relativize(file).toString().replace(File.separatorChar, '/'))
                      }
                      return FileVisitResult.CONTINUE
                  }
              })
              return found.sortedWith(Comparator { a, b ->
                  val aIsReadme = a.substringAfterLast('/').equals("README.md", ignoreCase = true)
                  val bIsReadme = b.substringAfterLast('/').equals("README.md", ignoreCase = true)
                  when {
                      aIsReadme && !bIsReadme -> -1
                      !aIsReadme && bIsReadme ->  1
                      else -> a.compareTo(b, ignoreCase = true)
                  }
              })
          }
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  Run: `mvn test -pl needlecast-desktop -Dtest=DocsPanelFileScannerTest -q`
  Expected: BUILD SUCCESS — all 5 tests pass.

- [ ] **Step 5: Commit**

  ```bash
  git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocsPanel.kt
  git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/DocsPanelFileScannerTest.kt
  git commit -m "feat: add DocsPanel file scanner with tests"
  ```

---

### Task 3: Implement the full panel UI (list + rendered + raw + toggle)

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocsPanel.kt`

Replace the stub from Task 2 with the complete implementation.

- [ ] **Step 1: Replace `DocsPanel.kt` with the full implementation**

  Overwrite the file with:
  ```kotlin
  package io.github.rygel.needlecast.ui

  import com.fifesoft.rsyntaxtextarea.RSyntaxTextArea
  import com.fifesoft.rsyntaxtextarea.SyntaxConstants
  import com.fifesoft.rsyntaxtextarea.Theme
  import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
  import org.commonmark.ext.gfm.tables.TablesExtension
  import org.commonmark.parser.Parser
  import org.commonmark.renderer.html.HtmlRenderer
  import org.fife.ui.rtextarea.RTextScrollPane
  import java.awt.BorderLayout
  import java.awt.CardLayout
  import java.awt.Color
  import java.awt.FlowLayout
  import java.io.File
  import java.nio.file.FileVisitResult
  import java.nio.file.Files
  import java.nio.file.Path
  import java.nio.file.SimpleFileVisitor
  import java.nio.file.attribute.BasicFileAttributes
  import java.util.concurrent.Executors
  import javax.swing.BorderFactory
  import javax.swing.ButtonGroup
  import javax.swing.DefaultListModel
  import javax.swing.JButton
  import javax.swing.JEditorPane
  import javax.swing.JLabel
  import javax.swing.JList
  import javax.swing.JPanel
  import javax.swing.JRadioButton
  import javax.swing.JScrollPane
  import javax.swing.JSplitPane
  import javax.swing.ListSelectionModel
  import javax.swing.SwingUtilities
  import javax.swing.UIManager

  class DocsPanel : JPanel(BorderLayout()) {

      // ── UI components ───────────────────────────────────────────────────────
      private val fileListModel = DefaultListModel<String>()
      private val fileList      = JList(fileListModel).apply {
          selectionMode = ListSelectionModel.SINGLE_SELECTION
      }
      private val renderedPane  = JEditorPane("text/html", "").apply {
          isEditable = false
          putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
      }
      private val rawArea       = RSyntaxTextArea().apply {
          syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_MARKDOWN
          isEditable         = false
          isCodeFoldingEnabled = false
      }
      private val cardLayout    = CardLayout()
      private val contentCards  = JPanel(cardLayout)

      private val renderedToggle = JRadioButton("Rendered", true)
      private val rawToggle      = JRadioButton("Raw",      false)
      private val refreshButton  = JButton("⟳ Refresh")

      private val placeholder    = JLabel("No project selected", JLabel.CENTER)

      // ── State ────────────────────────────────────────────────────────────────
      private var projectRoot: File? = null
      /** Cache: relative path → Pair(lastModified, rendered HTML) */
      private val htmlCache = HashMap<String, Pair<Long, String>>()
      private val executor  = Executors.newSingleThreadExecutor { r ->
          Thread(r, "docs-panel-worker").apply { isDaemon = true }
      }

      // ── commonmark ───────────────────────────────────────────────────────────
      private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
      private val mdParser   = Parser.builder().extensions(extensions).build()
      private val mdRenderer = HtmlRenderer.builder().extensions(extensions).build()

      init {
          ButtonGroup().apply {
              add(renderedToggle)
              add(rawToggle)
          }

          val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
              add(refreshButton)
              add(renderedToggle)
              add(rawToggle)
          }

          contentCards.add(JScrollPane(renderedPane), CARD_RENDERED)
          contentCards.add(RTextScrollPane(rawArea),  CARD_RAW)
          cardLayout.show(contentCards, CARD_RENDERED)

          val splitPane = JSplitPane(
              JSplitPane.HORIZONTAL_SPLIT,
              JScrollPane(fileList),
              contentCards,
          ).apply {
              dividerLocation = 200
              resizeWeight    = 0.0
          }

          add(toolbar,     BorderLayout.NORTH)
          add(splitPane,   BorderLayout.CENTER)

          // ── Listeners ────────────────────────────────────────────────────────
          fileList.addListSelectionListener { e ->
              if (!e.valueIsAdjusting) loadSelectedFile()
          }
          renderedToggle.addActionListener {
              cardLayout.show(contentCards, CARD_RENDERED)
              loadSelectedFile()
          }
          rawToggle.addActionListener {
              cardLayout.show(contentCards, CARD_RAW)
              loadSelectedFile()
          }
          refreshButton.addActionListener {
              htmlCache.clear()
              refresh()
          }

          showPlaceholder("No project selected")
      }

      // ── Public API ────────────────────────────────────────────────────────────

      fun loadProject(path: String?) {
          projectRoot = path?.let { File(it) }
          htmlCache.clear()
          refresh()
      }

      // ── Private helpers ────────────────────────────────────────────────────────

      private fun refresh() {
          val root = projectRoot
          if (root == null) { showPlaceholder("No project selected"); return }

          executor.execute {
              val files = collectMarkdownFiles(root)
              SwingUtilities.invokeLater {
                  fileListModel.clear()
                  if (files.isEmpty()) {
                      showPlaceholder("No Markdown files found in this project")
                  } else {
                      hidePlaceholder()
                      files.forEach { fileListModel.addElement(it) }
                      if (fileList.selectedIndex < 0 || fileList.selectedIndex >= fileListModel.size) {
                          fileList.selectedIndex = 0
                      } else {
                          loadSelectedFile()
                      }
                  }
              }
          }
      }

      private fun loadSelectedFile() {
          val relativePath = fileList.selectedValue ?: return
          val root         = projectRoot         ?: return
          val file         = File(root, relativePath)

          executor.execute {
              val text = try {
                  file.readText()
              } catch (e: Exception) {
                  SwingUtilities.invokeLater { showContentError("Could not read file: $relativePath") }
                  return@execute
              }

              if (renderedToggle.isSelected) {
                  val lastMod = file.lastModified()
                  val cached  = htmlCache[relativePath]
                  val html    = if (cached != null && cached.first == lastMod) {
                      cached.second
                  } else {
                      buildHtml(text).also { htmlCache[relativePath] = lastMod to it }
                  }
                  SwingUtilities.invokeLater { renderedPane.text = html; renderedPane.caretPosition = 0 }
              } else {
                  SwingUtilities.invokeLater { rawArea.text = text; rawArea.caretPosition = 0 }
              }
          }
      }

      private fun buildHtml(markdown: String): String {
          val bg   = colorHex(UIManager.getColor("Panel.background")    ?: Color(0x1E, 0x1E, 0x1E))
          val fg   = colorHex(UIManager.getColor("Label.foreground")    ?: Color(0xD4, 0xD4, 0xD4))
          val code = colorHex(UIManager.getColor("TextArea.background") ?: Color(0x2D, 0x2D, 0x2D))
          val link = colorHex(UIManager.getColor("Component.linkColor") ?: Color(0x4F, 0xC3, 0xF7))
          val body = mdRenderer.render(mdParser.parse(markdown))
          return """<html><head><style>
              body { background:$bg; color:$fg; font-family:sans-serif; margin:12px; }
              code { background:$code; padding:1px 4px; border-radius:3px; }
              pre  { background:$code; padding:8px; border-radius:4px; overflow-x:auto; }
              a    { color:$link; }
              table{ border-collapse:collapse; }
              th,td{ border:1px solid #555; padding:4px 8px; }
              </style></head><body>$body</body></html>"""
      }

      private fun colorHex(c: Color) = "#%02x%02x%02x".format(c.red, c.green, c.blue)

      private fun showPlaceholder(message: String) {
          placeholder.text = message
          if (placeholder.parent == null) {
              removeAll()
              add(placeholder, BorderLayout.CENTER)
              revalidate(); repaint()
          } else {
              placeholder.text = message
          }
      }

      private fun hidePlaceholder() {
          if (placeholder.parent != null) {
              removeAll()
              val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                  add(refreshButton)
                  add(renderedToggle)
                  add(rawToggle)
              }
              val splitPane = JSplitPane(
                  JSplitPane.HORIZONTAL_SPLIT,
                  JScrollPane(fileList),
                  contentCards,
              ).apply { dividerLocation = 200; resizeWeight = 0.0 }
              add(toolbar,   BorderLayout.NORTH)
              add(splitPane, BorderLayout.CENTER)
              revalidate(); repaint()
          }
      }

      private fun showContentError(message: String) {
          renderedPane.text = "<html><body><em>$message</em></body></html>"
          rawArea.text      = message
      }

      // ── Companion ─────────────────────────────────────────────────────────────

      companion object {
          private const val CARD_RENDERED = "rendered"
          private const val CARD_RAW      = "raw"
          private val SKIP_DIRS = setOf(".git", "target", "node_modules", "build", ".gradle")

          fun collectMarkdownFiles(root: File?): List<String> {
              if (root == null || !root.isDirectory) return emptyList()
              val found = mutableListOf<String>()
              Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
                  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                      val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                      return if (name in SKIP_DIRS) FileVisitResult.SKIP_SUBTREE
                      else FileVisitResult.CONTINUE
                  }
                  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                      if (file.fileName.toString().endsWith(".md", ignoreCase = true)) {
                          found.add(root.toPath().relativize(file).toString().replace(File.separatorChar, '/'))
                      }
                      return FileVisitResult.CONTINUE
                  }
              })
              return found.sortedWith(Comparator { a, b ->
                  val aIsReadme = a.substringAfterLast('/').equals("README.md", ignoreCase = true)
                  val bIsReadme = b.substringAfterLast('/').equals("README.md", ignoreCase = true)
                  when {
                      aIsReadme && !bIsReadme -> -1
                      !aIsReadme && bIsReadme ->  1
                      else -> a.compareTo(b, ignoreCase = true)
                  }
              })
          }
      }
  }
  ```

  > Note: `showPlaceholder` / `hidePlaceholder` swap the whole content area. This is intentional — the toolbar is only shown when a project is loaded.

- [ ] **Step 2: Verify the file still compiles**

  Run: `mvn compile -pl needlecast-desktop -q`
  Expected: BUILD SUCCESS.

- [ ] **Step 3: Re-run the scanner tests to confirm they still pass**

  Run: `mvn test -pl needlecast-desktop -Dtest=DocsPanelFileScannerTest -q`
  Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

  ```bash
  git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DocsPanel.kt
  git commit -m "feat: implement DocsPanel UI with rendered/raw toggle and file cache"
  ```

---

### Task 4: Wire DocsPanel into MainWindow

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`

Four edits — each is a one-liner that follows existing patterns.

- [ ] **Step 1: Instantiate DocsPanel alongside `renovatePanel` (line 71)**

  Current:
  ```kotlin
      private val renovatePanel = RenovatePanel()
  ```
  Change to:
  ```kotlin
      private val renovatePanel = RenovatePanel()
      private val docsPanel     = DocsPanel()
  ```

- [ ] **Step 2: Create the dockable wrapper alongside `searchDockable` (line 124)**

  Current:
  ```kotlin
      private val searchDockable       = DockablePanel(searchPanel,                   "search",       "Search")
  ```
  Change to:
  ```kotlin
      private val searchDockable       = DockablePanel(searchPanel,                   "search",       "Search")
      private val docsDockable         = DockablePanel(docsPanel,                     "docs",         "Docs")
  ```

- [ ] **Step 3: Register the dockable (after line 203)**

  Current:
  ```kotlin
              Docking.registerDockable(commandInputDockable)
  ```
  Change to:
  ```kotlin
              Docking.registerDockable(commandInputDockable)
              Docking.registerDockable(docsDockable)
  ```

- [ ] **Step 4: Call `loadProject` in `applyProjectSelection` (after line 308)**

  Current:
  ```kotlin
              renovatePanel.loadProject(path)
  ```
  Change to:
  ```kotlin
              renovatePanel.loadProject(path)
              docsPanel.loadProject(path)
  ```

- [ ] **Step 5: Add to default docking layout — tabbed alongside `searchDockable` (after line 395)**

  Current:
  ```kotlin
          // 5c. Search tabbed alongside Log Viewer
          Docking.dock(searchDockable,      logViewerDockable,   DockingRegion.CENTER)
  ```
  Change to:
  ```kotlin
          // 5c. Search tabbed alongside Log Viewer
          Docking.dock(searchDockable,      logViewerDockable,   DockingRegion.CENTER)
          // 5d. Docs tabbed alongside Search
          Docking.dock(docsDockable,        searchDockable,      DockingRegion.CENTER)
  ```

- [ ] **Step 6: Add `docsDockable` to the `resetLayout` undock list (line 414)**

  Current:
  ```kotlin
          listOf(projectTreeDockable, terminalDockable, commandsDockable,
                 gitLogDockable, logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable, promptInputDockable)
  ```
  Change to:
  ```kotlin
          listOf(projectTreeDockable, terminalDockable, commandsDockable,
                 gitLogDockable, logViewerDockable, searchDockable, renovateDockable, explorerDockable, editorDockable, consoleDockable, promptInputDockable, docsDockable)
  ```

- [ ] **Step 7: Full build and test**

  Run: `mvn verify -pl needlecast-desktop -T 4 -q`
  Expected: BUILD SUCCESS — all non-UI tests pass.

- [ ] **Step 8: Commit**

  ```bash
  git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
  git commit -m "feat: wire DocsPanel into MainWindow as dockable panel"
  ```

---

### Task 5: Open PR

- [ ] **Step 1: Push branch**

  ```bash
  git push -u origin feat/docs-panel
  ```

- [ ] **Step 2: Create PR targeting `develop`**

  ```bash
  gh pr create --base develop --title "feat: add Docs panel for browsing project markdown files" --body "$(cat <<'EOF'
  ## Summary
  - Adds a dockable **Docs** panel that scans the active project for `*.md` files
  - Left pane: file list with `README.md` pinned first, rest sorted alphabetically
  - Right pane: toggle between **Rendered** (commonmark HTML in JEditorPane) and **Raw** (RSyntaxTextArea with Markdown highlighting)
  - Conversion results are cached by path + last-modified to avoid re-reading disk on tab switches
  - Panel shows placeholder text when no project is selected or no `.md` files are found
  - Docked by default in the same tab group as Search / Log Viewer / Git Log

  ## Test plan
  - [ ] Unit tests for `DocsPanel.collectMarkdownFiles` pass (`DocsPanelFileScannerTest`)
  - [ ] Full `mvn verify` passes
  - [ ] Open a project with markdown files — file list populates, README pinned first
  - [ ] Click a file — content appears in rendered view
  - [ ] Toggle to Raw — same file shown in syntax-highlighted raw view
  - [ ] Click Refresh — list reloads, current file reloads
  - [ ] Switch project — list and content update
  - [ ] Open project with no `.md` files — "No Markdown files found" placeholder shown
  - [ ] Close project (null) — "No project selected" placeholder shown

  🤖 Generated with [Claude Code](https://claude.com/claude-code)
  EOF
  )"
  ```

- [ ] **Step 3: Check for merge conflicts and CI status**

  ```bash
  gh pr view --json number --jq .number   # get PR number
  gh pr view <number> --json mergeable --jq .mergeable
  gh pr checks <number>
  ```
  Expected: `MERGEABLE`, CI green.
