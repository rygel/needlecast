# Doc Viewer Panel — Design Spec

## Goal

Add a dockable **Doc Viewer** panel that detects generated documentation for the active project and opens it in the system browser. Covers all languages and build systems supported by Needlecast: Java, Kotlin, Groovy, Scala, Rust, Python, JavaScript/TypeScript, Elixir, Ruby, PHP, Dart/Flutter, Swift, C/C++, and .NET.

## Architecture

Four new files in `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/`:

```
model/DocCategory.kt      — enum for grouping (API_DOCS, TEST_REPORTS, COVERAGE, SITE)
model/DocTarget.kt        — data class describing one doc output
service/DocRegistry.kt    — maps BuildTool → List<DocTarget>; pure data, no I/O
ui/DocViewerPanel.kt      — the dockable Swing panel
```

`MainWindow.kt` is modified to register the panel in the docking system.

## Data Model

### `DocCategory`

```kotlin
enum class DocCategory(val displayName: String) {
    API_DOCS("API Docs"),
    TEST_REPORTS("Test Reports"),
    COVERAGE("Coverage"),
    SITE("Site"),
}
```

Display order in panel: `API_DOCS`, `TEST_REPORTS`, `COVERAGE`, `SITE`.

### `DocTarget`

```kotlin
data class DocTarget(
    val label: String,           // e.g. "Javadoc", "rustdoc"
    val relativePath: String,    // relative to project root, e.g. "target/site/apidocs/index.html"
    val buildTool: BuildTool,
    val category: DocCategory,
    val hint: String,            // command to run to generate, shown when file missing
)
```

## Doc Registry

`DocRegistry` is a Kotlin `object` with a single method:

```kotlin
fun targetsFor(buildTools: Set<BuildTool>): List<DocTarget>
```

Returns all targets whose `buildTool` is in `buildTools`, in registry insertion order.

### Complete target list

| Build Tool | Label | Relative path | Category | Hint |
|---|---|---|---|---|
| MAVEN | Javadoc | `target/site/apidocs/index.html` | API_DOCS | `mvn javadoc:javadoc` |
| MAVEN | Test Javadoc | `target/site/testapidocs/index.html` | API_DOCS | `mvn javadoc:test-javadoc` |
| MAVEN | Surefire Report | `target/site/surefire-report.html` | TEST_REPORTS | `mvn surefire-report:report` |
| MAVEN | JaCoCo Coverage | `target/site/jacoco/index.html` | COVERAGE | `mvn jacoco:report` |
| MAVEN | Maven Site | `target/site/index.html` | SITE | `mvn site` |
| GRADLE | Javadoc | `build/docs/javadoc/index.html` | API_DOCS | `./gradlew javadoc` |
| GRADLE | Groovydoc | `build/docs/groovydoc/index.html` | API_DOCS | `./gradlew groovydoc` |
| GRADLE | Dokka HTML | `build/docs/dokka/html/index.html` | API_DOCS | `./gradlew dokkaHtml` |
| GRADLE | Dokka Javadoc | `build/docs/dokka/javadoc/index.html` | API_DOCS | `./gradlew dokkaJavadoc` |
| GRADLE | JaCoCo Coverage | `build/reports/jacoco/test/html/index.html` | COVERAGE | `./gradlew jacocoTestReport` |
| GRADLE | Test Results | `build/reports/tests/test/index.html` | TEST_REPORTS | `./gradlew test` |
| NPM | TypeDoc | `docs/index.html` | API_DOCS | `npx typedoc` |
| NPM | JSDoc | `out/index.html` | API_DOCS | `npx jsdoc` |
| NPM | JSDoc (alt) | `jsdoc/index.html` | API_DOCS | `npx jsdoc` |
| NPM | documentation.js | `documentation/index.html` | API_DOCS | `npx documentation build` |
| CARGO | rustdoc | `target/doc/index.html` | API_DOCS | `cargo doc` |
| UV | Sphinx | `docs/_build/html/index.html` | API_DOCS | `make -C docs html` |
| UV | MkDocs | `site/index.html` | SITE | `mkdocs build` |
| UV | pdoc | `html/index.html` | API_DOCS | `pdoc --html .` |
| POETRY | Sphinx | `docs/_build/html/index.html` | API_DOCS | `make -C docs html` |
| POETRY | MkDocs | `site/index.html` | SITE | `mkdocs build` |
| POETRY | pdoc | `html/index.html` | API_DOCS | `pdoc --html .` |
| PIP | Sphinx | `docs/_build/html/index.html` | API_DOCS | `make -C docs html` |
| PIP | MkDocs | `site/index.html` | SITE | `mkdocs build` |
| PIP | pdoc | `html/index.html` | API_DOCS | `pdoc --html .` |
| MIX | ExDoc | `doc/index.html` | API_DOCS | `mix docs` |
| SBT | Scaladoc (2.x) | `target/scala-2.13/api/index.html` | API_DOCS | `sbt doc` |
| SBT | Scaladoc (3.x) | `target/scala-3/api/index.html` | API_DOCS | `sbt doc` |
| BUNDLER | YARD | `doc/index.html` | API_DOCS | `yard doc` |
| BUNDLER | RDoc | `rdoc/index.html` | API_DOCS | `rdoc` |
| COMPOSER | phpDocumentor | `docs/api/index.html` | API_DOCS | `phpdoc` |
| PUB | dartdoc | `doc/api/index.html` | API_DOCS | `dart doc` |
| FLUTTER | dartdoc | `doc/api/index.html` | API_DOCS | `dart doc` |
| SPM | DocC | `docs/index.html` | API_DOCS | `swift package generate-documentation` |
| CMAKE | Doxygen | `docs/html/index.html` | API_DOCS | `doxygen` |
| CMAKE | Doxygen (build/) | `build/docs/html/index.html` | API_DOCS | `doxygen` |
| MAKE | Doxygen | `docs/html/index.html` | API_DOCS | `doxygen` |
| MAKE | Doxygen (build/) | `build/docs/html/index.html` | API_DOCS | `doxygen` |
| DOTNET | DocFX | `_site/index.html` | SITE | `docfx build` |

## Panel Layout

```
┌─────────────────────────────────────────┐
│  [↻ Refresh]                            │  ← toolbar
├─────────────────────────────────────────┤
│  API Docs                               │  ← bold category header
│    ● Javadoc                            │  ← available (normal text)
│    ○ Dokka HTML       (not generated)   │  ← unavailable (grey + italic hint)
│  Coverage                               │
│    ● JaCoCo Coverage                    │
│  Test Reports                           │
│    ○ Surefire Report  (not generated)   │
├─────────────────────────────────────────┤
│                    [Open in Browser]    │  ← enabled when available entry selected
└─────────────────────────────────────────┘
```

- Category headers are non-selectable bold rows.
- Available entries: filled circle `●`, normal foreground, selectable.
- Unavailable entries: open circle `○`, grey foreground, italic "not generated" suffix, selectable but "Open" button stays disabled. Tooltip shows the `hint` command.
- Categories with no targets for the active project's build tools are hidden entirely.
- Available entries sort before unavailable within each category.
- Double-click on an available entry opens the browser (same as clicking "Open in Browser").
- "Refresh" re-checks disk existence without changing the project.
- When no project is loaded, the list is empty and the "Open" button is disabled.

## Opening Behaviour

```kotlin
Desktop.getDesktop().browse(File(projectDir, target.relativePath).toURI())
```

Wrapped in a try/catch; on failure a `JOptionPane.showMessageDialog` shows the error.

## Data Flow

1. User selects a project in the tree → `MainWindow` calls `docViewerPanel.loadProject(project)`.
2. `loadProject` calls `DocRegistry.targetsFor(project.buildTools)`.
3. For each target, checks `File(project.directory.path, target.relativePath).exists()`.
4. Builds a flat list model of `DocRow` items (category headers + doc entries), grouped by `DocCategory` display order, available-first within each category.
5. "Refresh" repeats steps 2–4 without a project change.
6. `loadProject(null)` clears the list.

## List Model

The `JList` uses a single flat `DefaultListModel<DocRow>` where `DocRow` is a sealed class:

```kotlin
sealed class DocRow {
    data class Header(val category: DocCategory) : DocRow()
    data class Entry(val target: DocTarget, val available: Boolean) : DocRow()
}
```

The cell renderer distinguishes `Header` from `Entry` and applies the appropriate style. Header rows have `ListSelectionModel` exclusion handled by a custom `ListSelectionModel` that skips non-`Entry` rows, or simply by checking in the selection listener.

## MainWindow Integration

```kotlin
val docViewerPanel = DocViewerPanel(ctx)
val docViewerDockable = DockablePanel(docViewerPanel, "doc-viewer", "Docs")
Docking.registerDockable(docViewerDockable)
```

`docViewerPanel.loadProject(project)` called alongside the existing `commandPanel.loadProject(project)` call in the project selection handler.

No `AppConfig` changes needed — panel visibility is managed by ModernDocking like all other panels.

## Error Handling

- `Desktop.getDesktop().browse()` failure → `JOptionPane` error dialog.
- `DocRegistry.targetsFor()` is pure data — no I/O, no exceptions.
- File existence checks use `File.exists()` — no exceptions possible.

## Testing

- Unit test `DocRegistry`: verify `targetsFor(setOf(BuildTool.MAVEN))` returns exactly the 5 Maven entries with correct paths and categories.
- Unit test `DocRegistry`: verify `targetsFor(emptySet())` returns an empty list.
- Unit test `DocRegistry`: verify each supported build tool returns at least one target.
- No UI tests required — the panel logic is in `DocRegistry` which is pure and easily testable.
