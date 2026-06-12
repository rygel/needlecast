# Ponytail Audit Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove ~4,200 lines of dead code, 2 unused dependencies, duplicated scanner boilerplate, and a single-impl interface identified by the 2026-06-12 ponytail audit.

**Architecture:** Pure deletion and refactoring. No new features. Each task produces a green build independently. Tasks are ordered so deletions happen before refactorings (reduces merge conflicts).

**Tech Stack:** Kotlin 2.2, Maven, JUnit 5

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Delete | `needlecast-desktop/src/main/kotlin/.../tools/ScreenshotTour.kt` | Debug scaffolding (2,768 lines) |
| Delete | `needlecast-desktop/src/main/kotlin/.../tools/ProjectTreeDebug.kt` | Debug harness |
| Delete | `needlecast-desktop/src/main/kotlin/.../tools/CdsTraining.kt` | CI training entrypoint |
| Delete | `needlecast-desktop/src/main/kotlin/.../ui/DirectoryPanel.kt` | Superseded by ProjectTreePanel |
| Delete | `needlecast-desktop/src/main/kotlin/.../ui/GroupPanel.kt` | Only used by DirectoryPanel |
| Delete | `needlecast-desktop/src/main/kotlin/.../ui/DragAndDrop.kt` | Only used by DirectoryPanel/GroupPanel |
| Delete | `needlecast-desktop/src/main/kotlin/.../ui/renderers/CompactProjectDirectoryRenderer.kt` | Only used by DirectoryPanel |
| Delete | `needlecast-desktop/src/main/kotlin/.../service/ProjectService.kt` | Only used by DirectoryPanel |
| Delete | `docs/TODO.md` | All 27 items completed |
| Delete | `archive/needlecast-web/` | Dead web module |
| Modify | `pom.xml` (parent) | Remove guava + mockk from dependencyManagement |
| Modify | `needlecast-desktop/pom.xml` | Remove guava + mockk from dependencies |
| Modify | `.../scanner/ProjectScanner.kt` | Add shared `scannerCmd()` function |
| Modify | 11 scanner files | Replace private `cmd()` with `scannerCmd()` |
| Modify | `.../process/ProcessCommandRunner.kt` | Remove `: CommandRunner` supertype |
| Modify | `.../AppContext.kt` | Change type from `CommandRunner` to `ProcessCommandRunner` |
| Delete | `.../process/CommandRunner.kt` | Collapsed into ProcessCommandRunner |
| Modify | `.../ui/ProjectTreePanel.kt` | Remove click-trace fields and guarded logging |
| Modify | `.../model/AppConfig.kt` | Remove `treeClickTraceEnabled` field |
| Modify | `.../ui/settings/LayoutSettingsPanel.kt` | Remove click-trace checkbox |

---

### Task 1: Delete tools/ package

**Files:**
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ScreenshotTour.kt`
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ProjectTreeDebug.kt`
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/CdsTraining.kt`

These three files are debug scaffolding with zero production callers. `ScreenshotTour.kt` alone is 2,768 lines (126 KB) of `println` calls.

- [ ] **Step 1: Delete the files**

```bash
cd C:\Develop\Claude\projects\needlecast
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\tools\ScreenshotTour.kt" -Force
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\tools\ProjectTreeDebug.kt" -Force
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\tools\CdsTraining.kt" -Force
```

If the `tools/` directory is now empty, remove it:

```bash
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\tools" -Recurse -Force
```

- [ ] **Step 2: Verify no remaining references to the deleted files**

```bash
rg -l "ScreenshotTour|ProjectTreeDebug|CdsTraining" needlecast-desktop/src/
```

Expected: no matches. If matches exist, they are in dead code being deleted in Task 2.

- [ ] **Step 3: Verify build compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run existing tests**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS (tests pass)

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "chore: delete tools/ debug scaffolding (2,945 lines)"
```

---

### Task 2: Delete dead UI cluster + ProjectService

**Files:**
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DirectoryPanel.kt`
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/GroupPanel.kt`
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/DragAndDrop.kt`
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/renderers/CompactProjectDirectoryRenderer.kt`
- Delete: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/service/ProjectService.kt`

These are all superseded by `ProjectTreePanel` and its extracted helper files. `ProjectService` is only imported by `DirectoryPanel`.

- [ ] **Step 1: Verify no live code references these files**

```bash
rg -l "DirectoryPanel|GroupPanel|DirectoryDragHandler|DirectoryDropHandler|CompactProjectDirectoryRenderer|ProjectService" needlecast-desktop/src/main/kotlin/
```

Expected: only self-references and the files themselves. If any other live file imports them, stop and assess before deleting.

- [ ] **Step 2: Delete the files**

```bash
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\ui\DirectoryPanel.kt" -Force
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\ui\GroupPanel.kt" -Force
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\ui\DragAndDrop.kt" -Force
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\ui\renderers\CompactProjectDirectoryRenderer.kt" -Force
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\service\ProjectService.kt" -Force
```

If the `renderers/` directory is now empty, remove it:

```bash
$dir = "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\ui\renderers"
if ((Get-ChildItem -LiteralPath $dir).Count -eq 0) { Remove-Item -LiteralPath $dir -Force }
```

- [ ] **Step 3: Verify build compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run existing tests**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "chore: delete dead UI cluster and ProjectService (~1,063 lines)"
```

---

### Task 3: Remove unused dependencies (guava, mockk)

**Files:**
- Modify: `pom.xml` (parent, dependencyManagement)
- Modify: `needlecast-desktop/pom.xml` (dependencies)

`guava` has zero source imports (jediterm pulls its own transitively). `mockk` has zero imports in any test file.

- [ ] **Step 1: Remove guava from parent pom.xml dependencyManagement**

In `pom.xml`, delete the guava dependency block:

```xml
            <dependency>
                <groupId>com.google.guava</groupId>
                <artifactId>guava</artifactId>
                <version>33.6.0-jre</version>
            </dependency>
```

- [ ] **Step 2: Remove guava from needlecast-desktop pom.xml**

In `needlecast-desktop/pom.xml`, delete:

```xml
        <!-- Guava — required by JediTerm -->
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </dependency>
```

- [ ] **Step 3: Remove mockk from parent pom.xml dependencyManagement**

In `pom.xml`, delete:

```xml
            <dependency>
                <groupId>io.mockk</groupId>
                <artifactId>mockk</artifactId>
                <version>${mockk.version}</version>
                <scope>test</scope>
            </dependency>
```

- [ ] **Step 4: Remove mockk from needlecast-desktop pom.xml**

In `needlecast-desktop/pom.xml`, delete:

```xml
        <dependency>
            <groupId>io.mockk</groupId>
            <artifactId>mockk</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 5: Remove mockk version property from parent pom.xml**

In `pom.xml`, delete the property line:

```xml
        <mockk.version>1.14.9</mockk.version>
```

- [ ] **Step 6: Verify build compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Run existing tests**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "chore: remove unused guava and mockk dependencies"
```

---

### Task 4: Extract shared scanner `scannerCmd()` helper

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/.../scanner/ProjectScanner.kt`
- Modify: 11 scanner files (replace private `cmd()` with `scannerCmd()`)

13 scanners have a private `cmd()` function with the identical `IS_WINDOWS` ternary pattern. Two variants exist:
- **Simple** (8 files): hardcoded `BuildTool` inside the function
- **Parameterized** (3 files): `BuildTool` passed as parameter

GradleProjectScanner, MavenProjectScanner, ApmProjectScanner, and DotNetProjectScanner have custom `cmd()` logic and are **not** modified.

- [ ] **Step 1: Add shared `scannerCmd()` to ProjectScanner.kt**

Edit `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/ProjectScanner.kt` and add after the existing code:

```kotlin
package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory

interface ProjectScanner {
    fun scan(directory: ProjectDirectory): DetectedProject?
}

val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().contains("win")
val IS_MAC: Boolean = System.getProperty("os.name").lowercase().let { it.contains("mac") || it.contains("darwin") }

fun scannerCmd(
    label: String,
    dir: ProjectDirectory,
    buildTool: BuildTool,
    vararg args: String,
): CommandDescriptor =
    CommandDescriptor(
        label = label,
        buildTool = buildTool,
        argv = if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
        workingDirectory = dir.path,
    )
```

- [ ] **Step 2: Replace private `cmd()` in ZigProjectScanner.kt**

Delete the private `cmd()` function (lines 33-43) and replace all call sites:

```kotlin
// Before (lines 19-24):
cmd("zig build", directory, "zig", "build"),
cmd("zig build test", directory, "zig", "build", "test"),
cmd("zig build run", directory, "zig", "build", "run"),
cmd("zig fmt", directory, "zig", "fmt", "."),
cmd("zig test", directory, "zig", "test", "src/main.zig"),

// After:
scannerCmd("zig build", directory, BuildTool.ZIG, "zig", "build"),
scannerCmd("zig build test", directory, BuildTool.ZIG, "zig", "build", "test"),
scannerCmd("zig build run", directory, BuildTool.ZIG, "zig", "build", "run"),
scannerCmd("zig fmt", directory, BuildTool.ZIG, "zig", "fmt", "."),
scannerCmd("zig test", directory, BuildTool.ZIG, "zig", "test", "src/main.zig"),
```

- [ ] **Step 3: Replace private `cmd()` in SwiftProjectScanner.kt**

Delete private `cmd()` (lines 34-44). Replace call sites:

```kotlin
// After:
scannerCmd("swift build", directory, BuildTool.SPM, "swift", "build"),
scannerCmd("swift build -c release", directory, BuildTool.SPM, "swift", "build", "-c", "release"),
scannerCmd("swift test", directory, BuildTool.SPM, "swift", "test"),
scannerCmd("swift run", directory, BuildTool.SPM, "swift", "run"),
scannerCmd("swift package resolve", directory, BuildTool.SPM, "swift", "package", "resolve"),
scannerCmd("swift package update", directory, BuildTool.SPM, "swift", "package", "update"),
```

- [ ] **Step 4: Replace private `cmd()` in ElixirProjectScanner.kt**

Delete private `cmd()` (lines 54-64). Replace call sites:

```kotlin
// After:
scannerCmd("mix compile", directory, BuildTool.MIX, "mix", "compile"),
scannerCmd("mix test", directory, BuildTool.MIX, "mix", "test"),
scannerCmd("mix deps.get", directory, BuildTool.MIX, "mix", "deps.get"),
scannerCmd("mix deps.update --all", directory, BuildTool.MIX, "mix", "deps.update", "--all"),
scannerCmd("mix format", directory, BuildTool.MIX, "mix", "format"),
// ... etc (same pattern for all cmd() calls)
```

- [ ] **Step 5: Replace private `cmd()` in RubyProjectScanner.kt**

Delete private `cmd()` (lines 51-61). Replace call sites:

```kotlin
// After:
scannerCmd("bundle install", directory, BuildTool.BUNDLER, "bundle", "install"),
scannerCmd("rails server", directory, BuildTool.BUNDLER, "bundle", "exec", "rails", "server"),
// ... etc
```

- [ ] **Step 6: Replace private `cmd()` in GoProjectScanner.kt**

Delete private `cmd()` (lines 61-71). Replace call sites:

```kotlin
// After:
scannerCmd("go build ./...", directory, BuildTool.GO, "go", "build", "./..."),
scannerCmd("go test ./...", directory, BuildTool.GO, "go", "test", "./..."),
// ... etc
```

- [ ] **Step 7: Replace private `cmd()` in PhpProjectScanner.kt**

Delete private `cmd()` (lines 65-75). Replace call sites:

```kotlin
// After:
scannerCmd("composer install", directory, BuildTool.COMPOSER, "composer", "install"),
scannerCmd("composer update", directory, BuildTool.COMPOSER, "composer", "update"),
// ... etc
```

- [ ] **Step 8: Replace private `cmd()` in SbtProjectScanner.kt**

Delete private `cmd()` (lines 35-45). Replace call sites:

```kotlin
// After:
scannerCmd("sbt compile", directory, BuildTool.SBT, "sbt", "compile"),
scannerCmd("sbt test", directory, BuildTool.SBT, "sbt", "test"),
// ... etc
```

- [ ] **Step 9: Replace private `cmd()` in RustProjectScanner.kt**

Delete private `cmd()` (lines 104-114). Replace call sites:

```kotlin
// After:
scannerCmd("cargo build", directory, BuildTool.CARGO, "cargo", "build"),
scannerCmd("cargo test", directory, BuildTool.CARGO, "cargo", "test"),
// ... etc
```

- [ ] **Step 10: Replace private `cmd()` in CMakeProjectScanner.kt**

Delete private `cmd()` (lines 63-74). Replace call sites (note: CMake already takes BuildTool as a parameter):

```kotlin
// After:
scannerCmd("cmake -B build", directory, BuildTool.CMAKE, "cmake", "-B", "build"),
scannerCmd("cmake --build build", directory, BuildTool.CMAKE, "cmake", "--build", "build"),
// ... etc
```

- [ ] **Step 11: Replace private `cmd()` in DartProjectScanner.kt**

Delete private `cmd()` (lines 62-73). Replace call sites (Dart already takes BuildTool as parameter):

```kotlin
// After:
scannerCmd("dart run", directory, BuildTool.DART, "dart", "run"),
// ... etc
```

- [ ] **Step 12: Replace private `cmd()` and `shellArgv()` in PythonProjectScanner.kt**

Delete private `cmd()` (lines 134-139) and `shellArgv()` (line 141). Replace call sites:

```kotlin
// After:
scannerCmd("uv run .", directory, BuildTool.UV, "uv", "run", "."),
// ... etc (use appropriate BuildTool per call)
```

- [ ] **Step 13: Run scanner tests**

```bash
mvn test -pl needlecast-desktop -Dtest="*ScannerTest" -q
```

Expected: all pass

- [ ] **Step 14: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 15: Commit**

```bash
git add -A && git commit -m "refactor: extract shared scannerCmd() to eliminate cmd() duplication across 11 scanners"
```

---

### Task 5: Collapse CommandRunner interface into ProcessCommandRunner

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/.../process/ProcessCommandRunner.kt`
- Modify: `needlecast-desktop/src/main/kotlin/.../AppContext.kt`
- Delete: `needlecast-desktop/src/main/kotlin/.../process/CommandRunner.kt`

`CommandRunner` is a 10-line interface with a single production implementation (`ProcessCommandRunner`) and zero test fakes. The indirection adds nothing.

- [ ] **Step 1: Remove `: CommandRunner` supertype from ProcessCommandRunner.kt**

Edit `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/process/ProcessCommandRunner.kt`:

```kotlin
// Before:
class ProcessCommandRunner : CommandRunner {
    override fun run(

// After:
class ProcessCommandRunner {
    fun run(
```

Remove the `override` keyword from the `run` method. The rest of the file stays the same.

- [ ] **Step 2: Update AppContext.kt to use concrete type**

Edit `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/AppContext.kt`:

```kotlin
// Before (lines 13-14):
import io.github.rygel.needlecast.process.CommandRunner
import io.github.rygel.needlecast.process.ProcessCommandRunner

// After:
import io.github.rygel.needlecast.process.ProcessCommandRunner
```

```kotlin
// Before (line 30):
    val commandRunner: CommandRunner = ProcessCommandRunner(),

// After:
    val commandRunner: ProcessCommandRunner = ProcessCommandRunner(),
```

- [ ] **Step 3: Delete CommandRunner.kt**

```bash
Remove-Item -LiteralPath "needlecast-desktop\src\main\kotlin\io\github\rygel\needlecast\process\CommandRunner.kt" -Force
```

- [ ] **Step 4: Verify no remaining references to CommandRunner**

```bash
rg "CommandRunner" needlecast-desktop/src/
```

Expected: no matches. (CdsTraining.kt referenced the string `"io.github.rygel.needlecast.process.ProcessCommandRunner"` but was deleted in Task 1.)

- [ ] **Step 5: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: collapse CommandRunner interface into ProcessCommandRunner"
```

---

### Task 6: Remove click-trace infrastructure

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/.../ui/ProjectTreePanel.kt`
- Modify: `needlecast-desktop/src/main/kotlin/.../model/AppConfig.kt`
- Modify: `needlecast-desktop/src/main/kotlin/.../ui/settings/LayoutSettingsPanel.kt`

Click-trace is diagnostic scaffolding (guarded by system properties and a config toggle) that was used to debug selection issues. If the bug is resolved, this is dead code.

- [ ] **Step 1: Remove click-trace fields from ProjectTreePanel.kt**

Delete these lines from `ProjectTreePanel.kt`:

```kotlin
    private val clickTraceForced =
        System.getProperty("needlecast.tree.clickTrace")?.equals("true", ignoreCase = true) == true ||
            (System.getenv("NEEDLECAST_TREE_CLICK_TRACE")?.equals("true", ignoreCase = true) == true) ||
            (System.getenv("NEEDLECAST_TREE_CLICK_TRACE") == "1")

    private fun isClickTraceEnabled(): Boolean = clickTraceForced || ctx.config.treeClickTraceEnabled

    private var clickSeq: Long = 0L
    private var lastClickTimeNs: Long = 0L
    private var lastClickKey: String? = null
    private var lastClickRow: Int = -1
```

- [ ] **Step 2: Remove click-trace guarded blocks from ProjectTreePanel.kt**

Find and remove all `if (isClickTraceEnabled()) { ... }` blocks and their contents. These are at approximately lines 356-366, 406-411. Each block is 3-5 lines of `logger.info(...)` calls and counter increments.

- [ ] **Step 3: Remove `treeClickTraceEnabled` from AppConfig.kt**

In `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`, delete:

```kotlin
    val treeClickTraceEnabled: Boolean = false,
```

- [ ] **Step 4: Remove click-trace checkbox from LayoutSettingsPanel.kt**

In `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/LayoutSettingsPanel.kt`, find and remove the click-trace checkbox and its action listener (approximately lines 61-66):

```kotlin
        val clickTraceCb = JCheckBox("Enable project tree click tracing", ctx.config.treeClickTraceEnabled)
        // ... GridBagConstraints setup ...
        add(clickTraceCb, gc)
        clickTraceCb.addActionListener {
            ctx.updateConfig(ctx.config.copy(treeClickTraceEnabled = clickTraceCb.isSelected))
        }
```

- [ ] **Step 5: Verify build compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "chore: remove click-trace diagnostic scaffolding from ProjectTreePanel"
```

---

### Task 7: Delete archive/needlecast-web

**Files:**
- Delete: `archive/needlecast-web/` (entire directory tree)

The web companion module is dead code with compiled `target/classes/` still present.

- [ ] **Step 1: Verify no references to archive/needlecast-web**

```bash
rg "needlecast-web" --type-not binary
```

Expected: only references in `.gitignore`, `pom.xml` (if the module was ever listed), or CI config. No production code references.

- [ ] **Step 2: Delete the directory**

```bash
Remove-Item -LiteralPath "archive\needlecast-web" -Recurse -Force
```

If the `archive/` directory is now empty, remove it:

```bash
if ((Get-ChildItem -LiteralPath "archive" -Force).Count -eq 0) { Remove-Item -LiteralPath "archive" -Force }
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "chore: delete dead archive/needlecast-web module"
```

---

### Task 8: Clean up stale docs

**Files:**
- Delete: `docs/TODO.md`
- Delete: `docs/ARCHITECTURE.md` (or mark as stale)

- [ ] **Step 1: Delete docs/TODO.md**

All 27 items are checked `[x]`. Remove it.

```bash
Remove-Item -LiteralPath "docs\TODO.md" -Force
```

- [ ] **Step 2: Delete docs/ARCHITECTURE.md**

This document references deleted classes (`DirectoryPanel`, `FileExplorerPanel`) and doesn't reflect the current package structure. Delete it — it will mislead more than help until rewritten.

```bash
Remove-Item -LiteralPath "docs\ARCHITECTURE.md" -Force
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "chore: delete stale docs (TODO.md all done, ARCHITECTURE.md outdated)"
```

---

## Not included (deferred)

These findings from the audit are deferred because they require more investigation or have higher risk:

| Finding | Reason for deferral |
|---------|-------------------|
| `flatlaf-extras` (finding 3) | 0 source imports, but may provide runtime SVG icon loading. Needs manual runtime testing. |
| `jediterm-typeahead` (finding 4) | 0 source imports, but may be transitive runtime dep of jediterm-pty. Needs manual runtime testing. |
| `ConfigStore` interface (finding 6) | After tools/ deletion, only 1 prod impl remains. However, `WorkspaceSnapshot` import/export depends on the interface. Collapsing requires updating import/export flow. |
| `ProjectGroup`, `groups`, `lastSelectedGroupId` (finding 17, partial) | Still actively used by `migrateOrLoad()`, `WorkspaceSnapshot`, `ProjectSwitcherDialog`, and `SkillsPanel`. Only `ProjectService` was truly dead (deleted in Task 2). Removing `groups` requires a migration plan. |
| `docs/ARCHITECTURE.md` rewrite | Would need a full audit of the current package structure. Out of scope for this cleanup. |

---

## Verification (run after all tasks)

- [ ] **Full build + tests**

```bash
mvn verify -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Verify no broken imports**

```bash
rg "import.*tools\.|import.*DirectoryPanel|import.*GroupPanel|import.*DragAndDrop|import.*CompactProjectDirectory|import.*ProjectService|import.*CommandRunner" needlecast-desktop/src/
```

Expected: no matches

- [ ] **Verify line count reduction**

```bash
(Get-ChildItem -LiteralPath "needlecast-desktop\src\main\kotlin" -Recurse -Filter "*.kt" | Get-Content | Measure-Object -Line).Lines
```

Expected: ~25,870 minus ~4,200 = ~21,670 lines (approximately)

- [ ] **Run app to verify no runtime errors**

```bash
mvn exec:java -pl needlecast-desktop
```

Expected: app window opens, no ClassNotFoundException or NoClassDefFoundError.
