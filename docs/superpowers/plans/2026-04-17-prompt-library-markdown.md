# Prompt & Command Library: Markdown File Storage — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the prompt and command libraries from hardcoded Kotlin functions and config.json to a directory structure of markdown files with YAML frontmatter under `~/.needlecast/`.

**Architecture:** A new `PromptLibraryStore` class handles reading/writing markdown files in `~/.needlecast/prompts/` and `~/.needlecast/commands/`. Category is derived from the parent directory name. Each file is a `.md` with YAML frontmatter (`name`, `description`) and a body. On first run, default templates are seeded from the existing `defaultPromptLibrary()` / `defaultCommandLibrary()` functions. The `promptLibrary` and `commandLibrary` fields are removed from `AppConfig`.

**Tech Stack:** Kotlin, java.nio.file, Jackson (already in project), JUnit 5

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/kotlin/.../config/PromptLibraryStore.kt` | Read/write markdown template files |
| Modify | `src/main/kotlin/.../model/AppConfig.kt` | Remove `promptLibrary`/`commandLibrary` fields |
| Modify | `src/main/kotlin/.../config/ConfigMigrator.kt` | Bump version to 4, strip library fields |
| Modify | `src/main/kotlin/.../config/JsonConfigStore.kt` | No changes needed (migrator handles it) |
| Modify | `src/main/kotlin/.../AppContext.kt` | Add `promptLibraryStore`, wire into config loading |
| Modify | `src/main/kotlin/.../ui/PromptInputPanel.kt` | Load/save via store instead of config |
| Modify | `src/main/kotlin/.../ui/PromptLibraryDialog.kt` | Load/save via store instead of config |
| Modify | `src/main/kotlin/.../ui/MainWindow.kt` | Pass store to panels/dialogs |
| Modify | `src/main/kotlin/.../tools/ScreenshotTour.kt` | Use store for demo config |
| Create | `src/test/kotlin/.../config/PromptLibraryStoreTest.kt` | Tests for the new store |
| Modify | `src/test/kotlin/.../config/ConfigMigratorTest.kt` | Update for v4 migration |
| Modify | `src/test/kotlin/.../config/ConfigRoundTripTest.kt` | Remove `promptLibrary` round-trip test |
| Modify | `src/test/kotlin/.../config/JsonConfigStoreTest.kt` | Remove library assertions |

---

### Task 1: Create `PromptLibraryStore` — core read/write logic

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/config/PromptLibraryStore.kt`
- Test: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/PromptLibraryStoreTest.kt`

- [ ] **Step 1: Write the failing test for `loadPrompts` and `loadCommands`**

```kotlin
package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.PromptTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PromptLibraryStoreTest {

    @Test
    fun `loadPrompts reads markdown files from category directories`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val commandsDir = base.resolve("commands")
        Files.createDirectories(promptsDir.resolve("Explore"))
        Files.writeString(promptsDir.resolve("Explore/onboarding.md"), """
            ---
            name: Onboard me to this repo
            description: Quick orientation for an unfamiliar codebase.
            ---
            Give me a 2-minute developer onboarding:
            1. What does this project do?
        """.trimIndent())

        val store = PromptLibraryStore(promptsDir, commandsDir)
        val prompts = store.loadPrompts()

        assertEquals(1, prompts.size)
        assertEquals("Onboard me to this repo", prompts[0].name)
        assertEquals("Explore", prompts[0].category)
        assertEquals("Quick orientation for an unfamiliar codebase.", prompts[0].description)
        assertTrue(prompts[0].body.startsWith("Give me a 2-minute developer onboarding:"))
        assertNotNull(prompts[0].id)
    }

    @Test
    fun `loadPrompts returns empty list when directory does not exist`(@TempDir base: Path) {
        val store = PromptLibraryStore(base.resolve("nonexistent-prompts"), base.resolve("nonexistent-commands"))
        assertTrue(store.loadPrompts().isEmpty())
        assertTrue(store.loadCommands().isEmpty())
    }

    @Test
    fun `loadPrompts handles multiple categories`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        Files.createDirectories(promptsDir.resolve("Explore"))
        Files.createDirectories(promptsDir.resolve("Fix"))
        Files.writeString(promptsDir.resolve("Explore/onboarding.md"), """
            ---
            name: Onboard me
            description: Orientation.
            ---
            Onboard text.
        """.trimIndent())
        Files.writeString(promptsDir.resolve("Fix/fix-error.md"), """
            ---
            name: Fix this error
            description: Diagnose and fix.
            ---
            Fix text.
        """.trimIndent())

        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val prompts = store.loadPrompts()

        assertEquals(2, prompts.size)
        assertEquals(listOf("Explore", "Fix"), prompts.map { it.category }.sorted())
    }

    @Test
    fun `ID is deterministic from relative path`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        Files.createDirectories(promptsDir.resolve("Explore"))
        val content = """
            ---
            name: Onboard me
            description: desc
            ---
            Body.
        """.trimIndent()
        Files.writeString(promptsDir.resolve("Explore/onboarding.md"), content)

        val store1 = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val store2 = PromptLibraryStore(promptsDir, base.resolve("commands"))

        assertEquals(store1.loadPrompts()[0].id, store2.loadPrompts()[0].id)
    }

    @Test
    fun `loadCommands reads from commands directory`(@TempDir base: Path) {
        val commandsDir = base.resolve("commands")
        Files.createDirectories(commandsDir.resolve("Git"))
        Files.writeString(commandsDir.resolve("Git/status.md"), """
            ---
            name: Status
            description: Short status with branch name.
            ---
            git status -sb
        """.trimIndent())

        val store = PromptLibraryStore(base.resolve("prompts"), commandsDir)
        val commands = store.loadCommands()

        assertEquals(1, commands.size)
        assertEquals("Status", commands[0].name)
        assertEquals("Git", commands[0].category)
        assertEquals("git status -sb", commands[0].body.trim())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl needlecast-desktop -Dtest=PromptLibraryStoreTest -Dsurefire.failIfNoSpecifiedTests=false -T 4`
Expected: FAIL — `PromptLibraryStore` does not exist.

- [ ] **Step 3: Write `PromptLibraryStore` implementation**

```kotlin
package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.PromptTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString

class PromptLibraryStore(
    private val promptsDir: Path,
    private val commandsDir: Path,
) {
    fun loadPrompts(): List<PromptTemplate> = loadFromDirectory(promptsDir)

    fun loadCommands(): List<PromptTemplate> = loadFromDirectory(commandsDir)

    fun save(template: PromptTemplate, isCommand: Boolean) {
        val baseDir = if (isCommand) commandsDir else promptsDir
        val category = template.category.ifBlank { "Uncategorized" }
        val categoryDir = baseDir.resolve(category)
        Files.createDirectories(categoryDir)
        val slug = slugify(template.name)
        val targetFile = categoryDir.resolve("$slug.md")

        val existing = findExistingFile(baseDir, template.id)
        if (existing != null && existing != targetFile) {
            Files.deleteIfExists(existing)
            cleanupEmptyDirectories(baseDir)
        }

        val content = buildString {
            appendLine("---")
            appendLine("name: ${template.name}")
            appendLine("description: ${template.description}")
            appendLine("---")
            appendLine(template.body)
        }
        val tmp = targetFile.resolveSibling(targetFile.name + ".tmp")
        Files.writeString(tmp, content)
        Files.move(tmp, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }

    fun delete(template: PromptTemplate, isCommand: Boolean) {
        val baseDir = if (isCommand) commandsDir else promptsDir
        val existing = findExistingFile(baseDir, template.id)
        if (existing != null) {
            Files.deleteIfExists(existing)
            cleanupEmptyDirectories(baseDir)
        }
    }

    fun seedDefaults(prompts: List<PromptTemplate>, commands: List<PromptTemplate>) {
        if (Files.exists(promptsDir) && loadFromDirectory(promptsDir).isNotEmpty()) return
        Files.createDirectories(promptsDir)
        prompts.forEach { save(it, isCommand = false) }

        if (Files.exists(commandsDir) && loadFromDirectory(commandsDir).isNotEmpty()) return
        Files.createDirectories(commandsDir)
        commands.forEach { save(it, isCommand = true) }
    }

    private fun loadFromDirectory(dir: Path): List<PromptTemplate> {
        if (!Files.exists(dir)) return emptyList()
        val result = mutableListOf<PromptTemplate>()
        Files.list(dir).use { entries ->
            entries.filter { it.isDirectory() }.sortedBy { it.name }.forEach { categoryDir ->
                val category = categoryDir.name
                Files.list(categoryDir).use { files ->
                    files.filter { it.isRegularFile() && it.extension == "md" }
                        .sortedBy { it.name }
                        .forEach { file -> result.add(parseFile(file, category, dir)) }
                }
            }
        }
        return result
    }

    private fun parseFile(file: Path, category: String, baseDir: Path): PromptTemplate {
        val raw = Files.readString(file)
        val relativePath = baseDir.relativize(file).pathString.replace('\\', '/')
        val id = deterministicId(relativePath)

        val (frontmatter, body) = splitFrontmatter(raw)
        val name = frontmatter["name"] ?: file.name.removeSuffix(".md")
        val description = frontmatter["description"] ?: ""

        return PromptTemplate(id = id, name = name, category = category, description = description, body = body)
    }

    private fun splitFrontmatter(raw: String): Pair<Map<String, String>, String> {
        val lines = raw.lines()
        if (lines.isEmpty() || lines[0].trim() != "---") return emptyMap() to raw
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end == -1) return emptyMap() to raw
        val yamlLines = lines.subList(1, end + 1)
        val body = lines.subList(end + 2, lines.size).joinToString("\n").trim()
        val map = mutableMapOf<String, String>()
        for (line in yamlLines) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                map[key] = value
            }
        }
        return map to body
    }

    private fun findExistingFile(baseDir: Path, id: String): Path? {
        if (!Files.exists(baseDir)) return null
        Files.list(baseDir).use { entries ->
            entries.filter { it.isDirectory() }.forEach { categoryDir ->
                Files.list(categoryDir).use { files ->
                    for (file in files.filter { it.isRegularFile() && it.extension == "md" }) {
                        val relativePath = baseDir.relativize(file).pathString.replace('\\', '/')
                        if (deterministicId(relativePath) == id) return file
                    }
                }
            }
        }
        return null
    }

    private fun cleanupEmptyDirectories(baseDir: Path) {
        if (!Files.exists(baseDir)) return
        try {
            Files.list(baseDir).use { entries ->
                entries.filter { it.isDirectory() }
                    .filter { dir -> Files.list(dir).use { it.findAny().isEmpty } }
                    .forEach { Files.deleteIfExists(it) }
            }
        } catch (_: Exception) {}
    }

    companion object {
        fun deterministicId(relativePath: String): String =
            UUID.nameUUIDFromBytes(relativePath.toByteArray(Charsets.UTF_8)).toString()

        fun slugify(name: String): String =
            name.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("[\\s]+"), "-")
                .replace(Regex("-+"), "-")
                .trim('-')
                .ifEmpty { "untitled" }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl needlecast-desktop -Dtest=PromptLibraryStoreTest -Dsurefire.failIfNoSpecifiedTests=false -T 4`
Expected: All 5 tests PASS.

- [ ] **Step 5: Write additional tests for save, delete, slugify**

Add to `PromptLibraryStoreTest.kt`:

```kotlin
    @Test
    fun `save creates file in category directory`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val template = PromptTemplate(
            name = "Fix this error",
            category = "Fix",
            description = "Diagnose and fix.",
            body = "Find the root cause.",
        )

        store.save(template, isCommand = false)

        val file = promptsDir.resolve("Fix/fix-this-error.md")
        assertTrue(Files.exists(file))
        val content = Files.readString(file)
        assertTrue(content.contains("name: Fix this error"))
        assertTrue(content.contains("description: Diagnose and fix."))
        assertTrue(content.contains("Find the root cause."))
    }

    @Test
    fun `save moves file when category changes`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val original = PromptTemplate(name = "Test", category = "Old", description = "", body = "body")
        store.save(original, isCommand = false)
        val loaded = store.loadPrompts()
        val id = loaded[0].id

        val moved = loaded[0].copy(category = "New")
        store.save(moved, isCommand = false)

        assertFalse(Files.exists(promptsDir.resolve("Old/test.md")))
        assertTrue(Files.exists(promptsDir.resolve("New/test.md")))
        val reloaded = store.loadPrompts()
        assertEquals(id, reloaded[0].id)
    }

    @Test
    fun `delete removes file and empty directory`(@TempDir base: Path) {
        val promptsDir = base.resolve("prompts")
        val store = PromptLibraryStore(promptsDir, base.resolve("commands"))
        val template = PromptTemplate(name = "Test", category = "Solo", description = "", body = "body")
        store.save(template, isCommand = false)

        val loaded = store.loadPrompts()
        store.delete(loaded[0], isCommand = false)

        assertFalse(Files.exists(promptsDir.resolve("Solo/test.md")))
        assertFalse(Files.exists(promptsDir.resolve("Solo")))
        assertTrue(store.loadPrompts().isEmpty())
    }

    @Test
    fun `slugify handles special characters`() {
        assertEquals("fix-this-error", PromptLibraryStore.slugify("Fix this error"))
        assertEquals("maven-package-skip-tests", PromptLibraryStore.slugify("Maven package (skip tests)"))
        assertEquals("what-does-this-file-do", PromptLibraryStore.slugify("What does this file do"))
        assertEquals("untitled", PromptLibraryStore.slugify("!!!"))
    }
```

- [ ] **Step 6: Run all store tests**

Run: `mvn test -pl needlecast-desktop -Dtest=PromptLibraryStoreTest -Dsurefire.failIfNoSpecifiedTests=false -T 4`
Expected: All 9 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/config/PromptLibraryStore.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/PromptLibraryStoreTest.kt
git commit -m "feat: add PromptLibraryStore for markdown file-based template storage"
```

---

### Task 2: Remove `promptLibrary` / `commandLibrary` from `AppConfig`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`

- [ ] **Step 1: Remove the two fields from `AppConfig` data class**

In `AppConfig.kt`, remove lines 562-563:

```kotlin
// DELETE these two lines:
    val promptLibrary: List<PromptTemplate> = defaultPromptLibrary(),
    val commandLibrary: List<PromptTemplate> = defaultCommandLibrary(),
```

The `defaultPromptLibrary()` and `defaultCommandLibrary()` functions (lines 50-538) must remain — they are used for first-run seeding.

- [ ] **Step 2: Build to verify compilation errors**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: FAIL — many references to `config.promptLibrary` and `config.commandLibrary` break. This is expected; we fix them in subsequent tasks.

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt
git commit -m "refactor: remove promptLibrary and commandLibrary from AppConfig"
```

---

### Task 3: Update `ConfigMigrator` to version 4

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/config/ConfigMigrator.kt`
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigMigratorTest.kt`

- [ ] **Step 1: Update `ConfigMigrator`**

Since `promptLibrary` and `commandLibrary` are removed from `AppConfig`, the migrator just needs to bump the version. No special migration logic needed — Jackson will ignore unknown properties when loading old configs (already configured via `FAIL_ON_UNKNOWN_PROPERTIES = false`).

```kotlin
object ConfigMigrator {

    const val CURRENT_VERSION = 4

    fun migrate(config: AppConfig): AppConfig {
        if (config.configVersion >= CURRENT_VERSION) return config
        return runMigrations(config).copy(configVersion = CURRENT_VERSION)
    }

    private fun runMigrations(config: AppConfig): AppConfig {
        return config
    }
}
```

- [ ] **Step 2: Update `ConfigMigratorTest`**

Replace the `migration preserves prompt library` test — `promptLibrary` no longer exists on `AppConfig`. Update the existing test and add a v3→v4 test:

```kotlin
    @Test
    fun `migration strips unknown properties gracefully`() {
        val old = AppConfig(configVersion = 0, theme = "light", windowWidth = 1920)
        val result = ConfigMigrator.migrate(old)
        assertEquals("light", result.theme)
        assertEquals(1920, result.windowWidth)
    }

    @Test
    fun `migrate is idempotent`() {
        val config = AppConfig(configVersion = 0)
        val once  = ConfigMigrator.migrate(config)
        val twice = ConfigMigrator.migrate(once)
        assertEquals(once.configVersion, twice.configVersion)
    }
```

Delete the old `migration preserves prompt library` test.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl needlecast-desktop -Dtest=ConfigMigratorTest -Dsurefire.failIfNoSpecifiedTests=false -T 4`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/config/ConfigMigrator.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigMigratorTest.kt
git commit -m "refactor: bump ConfigMigrator to v4 for file-based library storage"
```

---

### Task 4: Update `AppContext` to initialize `PromptLibraryStore` and seed defaults

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/AppContext.kt`

- [ ] **Step 1: Add `PromptLibraryStore` to `AppContext`**

Add the store and wire it to seed defaults on first run. The store is initialized before config is loaded (though seeding only needs to happen once when the directories don't exist yet).

```kotlin
import io.github.rygel.needlecast.model.defaultPromptLibrary
import io.github.rygel.needlecast.model.defaultCommandLibrary

class AppContext(
    val configStore: ConfigStore = JsonConfigStore(),
    val scanner: ProjectScanner = CompositeProjectScanner(),
    val commandRunner: CommandRunner = ProcessCommandRunner(),
    val gitService: GitService = ProcessGitService(),
    val promptLibraryStore: PromptLibraryStore = PromptLibraryStore(
        Path.of(System.getProperty("user.home"), ".needlecast", "prompts"),
        Path.of(System.getProperty("user.home"), ".needlecast", "commands"),
    ),
) {
    var config: AppConfig = configStore.load()
        private set

    init {
        promptLibraryStore.seedDefaults(defaultPromptLibrary(), defaultCommandLibrary())
    }
    // ... rest unchanged
```

Also add the imports:
```kotlin
import io.github.rygel.needlecast.config.PromptLibraryStore
import java.nio.file.Path
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: PASS (this file compiles; callers that reference `config.promptLibrary` still break — fixed in later tasks).

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/AppContext.kt
git commit -m "feat: add PromptLibraryStore to AppContext with first-run seeding"
```

---

### Task 5: Update `PromptInputPanel` to use `PromptLibraryStore`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PromptInputPanel.kt`

- [ ] **Step 1: Replace config-based load/save with store-based**

The panel currently takes `loadLibrary: (AppConfig) -> List<PromptTemplate>` and `updateLibrary: (AppConfig, List<PromptTemplate>) -> AppConfig`. Replace with a simpler interface that uses the store directly.

Change the constructor parameters:

```kotlin
class PromptInputPanel(
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    sendButtonLabel: String = "Send to Terminal",
    private val itemLabel: String = "Prompt",
    private val isCommand: Boolean = false,
) : JPanel(BorderLayout()) {
```

Replace the config listener and all `loadLibrary`/`updateLibrary` calls. Key changes:

- In `init` block, replace `ctx.addConfigListener { config -> SwingUtilities.invokeLater { refreshTree(loadLibrary(config)) } }` with a no-op or remove it (the tree refreshes on demand).
- `refreshTree` initial call: `refreshTree(currentLibrary())`
- Add helper: `private fun currentLibrary() = if (isCommand) ctx.promptLibraryStore.loadCommands() else ctx.promptLibraryStore.loadPrompts()`
- `createNewPrompt()`: `ctx.promptLibraryStore.save(template, isCommand)`
- `editSelectedPrompt()`: `ctx.promptLibraryStore.save(updated, isCommand)`
- `deleteSelectedPrompt()`: `ctx.promptLibraryStore.delete(prompt, isCommand)` then `refreshTree(currentLibrary())`
- Remove the `loadLibrary` and `updateLibrary` constructor parameters entirely.

After editing, the full constructor and relevant methods become:

```kotlin
class PromptInputPanel(
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    sendButtonLabel: String = "Send to Terminal",
    private val itemLabel: String = "Prompt",
    private val isCommand: Boolean = false,
) : JPanel(BorderLayout()) {
    // ... tree and UI fields unchanged ...

    init {
        // ... layout unchanged ...
        refreshTree(currentLibrary())
        wireListeners()
    }

    private fun currentLibrary(): List<PromptTemplate> =
        if (isCommand) ctx.promptLibraryStore.loadCommands() else ctx.promptLibraryStore.loadPrompts()

    // refreshTree, wireListeners, showContextMenu unchanged except as noted below

    private fun createNewPrompt() {
        val owner  = SwingUtilities.getWindowAncestor(this)
        val dialog = NewPromptDialog(owner, "New $itemLabel")
        dialog.isVisible = true
        val template = dialog.result ?: return
        ctx.promptLibraryStore.save(template, isCommand)
        refreshTree(currentLibrary())
    }

    private fun editSelectedPrompt() {
        val existing = selectedPrompt() ?: return
        val owner    = SwingUtilities.getWindowAncestor(this)
        val dialog   = NewPromptDialog(owner, "Edit $itemLabel", existing)
        dialog.isVisible = true
        val updated = dialog.result ?: return
        ctx.promptLibraryStore.save(updated, isCommand)
        refreshTree(currentLibrary())
    }

    private fun deleteSelectedPrompt() {
        val prompt  = selectedPrompt() ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete \"${prompt.name}\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (confirm != JOptionPane.YES_OPTION) return
        ctx.promptLibraryStore.delete(prompt, isCommand)
        textArea.text = ""
        refreshTree(currentLibrary())
    }
    // ... doSend, selectedPrompt, cell renderer unchanged ...
```

Remove the now-unused import of `AppConfig` (only needed for the old lambda types).

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: PASS for this file; `MainWindow.kt` will have errors due to removed constructor params — fixed in Task 7.

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PromptInputPanel.kt
git commit -m "refactor: PromptInputPanel uses PromptLibraryStore instead of config"
```

---

### Task 6: Update `PromptLibraryDialog` to use `PromptLibraryStore`

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PromptLibraryDialog.kt`

- [ ] **Step 1: Replace `loadLibrary`/`saveLibrary` with `isCommand` flag**

Change the constructor:

```kotlin
class PromptLibraryDialog(
    owner: Window,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    title: String = "Prompt Library",
    private val sendButtonLabel: String = "Paste to Terminal",
    private val isCommand: Boolean = false,
) : JDialog(owner, title, ModalityType.MODELESS) {
```

Add helper and update all usages:

```kotlin
    private fun currentLibrary(): List<PromptTemplate> =
        if (isCommand) ctx.promptLibraryStore.loadCommands() else ctx.promptLibraryStore.loadPrompts()
```

In `saveCurrentForm()`:
```kotlin
    private fun saveCurrentForm() {
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "Name must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        val updated = (editing ?: PromptTemplate()).copy(
            name        = name,
            category    = categoryField.text.trim(),
            description = descField.text.trim(),
            body        = bodyArea.text,
        )
        ctx.promptLibraryStore.save(updated, isCommand)
        editing = updated
        val newList = currentLibrary()
        populateList(newList)
        for (i in 0 until listModel.size) {
            if (listModel.getElementAt(i).id == updated.id) { promptList.selectedIndex = i; break }
        }
    }
```

In `deleteSelected()`:
```kotlin
    private fun deleteSelected() {
        val sel = promptList.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this, "Delete \"${sel.name}\"?", "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
        )
        if (confirm != JOptionPane.YES_OPTION) return
        ctx.promptLibraryStore.delete(sel, isCommand)
        editing = null
        populateList(currentLibrary())
        clearForm()
        deleteButton.isEnabled = false
    }
```

In `init` block, replace `populateList(loadLibrary())` with `populateList(currentLibrary())`.

In `applyFilter()`:
```kotlin
    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        val all = currentLibrary()
        val filtered = if (query.isEmpty()) all
                       else all.filter {
                           it.name.lowercase().contains(query) ||
                           it.category.lowercase().contains(query) ||
                           it.description.lowercase().contains(query)
                       }
        populateList(filtered)
    }
```

Remove the `loadLibrary` and `saveLibrary` constructor parameters.

- [ ] **Step 2: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/PromptLibraryDialog.kt
git commit -m "refactor: PromptLibraryDialog uses PromptLibraryStore instead of config"
```

---

### Task 7: Update `MainWindow` and `ScreenshotTour` callers

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ScreenshotTour.kt`

- [ ] **Step 1: Update `MainWindow` PromptInputPanel instantiations**

Replace the two `PromptInputPanel` constructions (around lines 58-66):

```kotlin
    private val promptInputPanel  = PromptInputPanel(ctx, sendToTerminal = { terminalPanel.sendInput(it) })
    private val commandInputPanel = PromptInputPanel(
        ctx,
        sendToTerminal  = { terminalPanel.sendInput(it) },
        sendButtonLabel = "Run in Terminal",
        itemLabel       = "Command",
        isCommand       = true,
    )
```

- [ ] **Step 2: Update `MainWindow` PromptLibraryDialog instantiations**

For the Prompt Library dialog (around line 673):
```kotlin
                PromptLibraryDialog(owner = this@MainWindow, ctx = ctx,
                    sendToTerminal = { text -> terminalPanel.sendInput(text) },
                ).isVisible = true
```

For the Command Library dialog (around line 680):
```kotlin
                PromptLibraryDialog(
                    owner           = this@MainWindow,
                    ctx             = ctx,
                    sendToTerminal  = { cmd -> terminalPanel.sendInput(cmd) },
                    title           = "Command Library",
                    sendButtonLabel = "Run in Terminal",
                    isCommand       = true,
                ).isVisible = true
```

- [ ] **Step 3: Update `ScreenshotTour`**

In `ScreenshotTour.kt`, the `PromptLibraryDialog` constructor calls at lines ~1335-1342 need the same updates (replace `loadLibrary`/`saveLibrary` with `isCommand = true`):

```kotlin
                PromptLibraryDialog(
                    w, ctx,
                    sendToTerminal  = {},
                    title           = "Command Library",
                    sendButtonLabel = "Run in Terminal",
                    isCommand       = true,
                ).isVisible = true
```

The demo `AppConfig` at lines ~2248-2293 still references `promptLibrary` and `commandLibrary`. Remove those fields from the `AppConfig(...)` constructor call. Since they no longer exist on `AppConfig`, just delete lines 2248-2293. The `ScreenshotTour` uses a `FixedConfigStore` with this config — it doesn't need library data since the dialogs load from the store.

- [ ] **Step 4: Verify full compilation**

Run: `mvn compile -pl needlecast-desktop -T 4`
Expected: PASS — no remaining references to removed fields.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/tools/ScreenshotTour.kt
git commit -m "refactor: update MainWindow and ScreenshotTour for store-based library"
```

---

### Task 8: Update existing tests

**Files:**
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigRoundTripTest.kt`
- Modify: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/JsonConfigStoreTest.kt`

- [ ] **Step 1: Remove `promptLibrary round-trips correctly` test from `ConfigRoundTripTest`**

Delete the entire test method at lines 136-155. The `promptLibrary` field no longer exists on `AppConfig`.

Remove the import of `PromptTemplate` if no other test uses it.

- [ ] **Step 2: Update `JsonConfigStoreTest`**

In `returns default config when file does not exist` (lines 44-53), remove the two assertions:
```kotlin
        assertTrue(config.promptLibrary.isNotEmpty())
        assertTrue(config.commandLibrary.isNotEmpty())
```

These fields no longer exist on `AppConfig`. Replace with a different assertion or remove them. The remaining assertions (`theme`, `windowWidth`, `groups`) are still valid.

- [ ] **Step 3: Run the affected tests**

Run: `mvn test -pl needlecast-desktop -Dtest="ConfigRoundTripTest,JsonConfigStoreTest,ConfigMigratorTest" -Dsurefire.failIfNoSpecifiedTests=false -T 4`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/ConfigRoundTripTest.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/config/JsonConfigStoreTest.kt
git commit -m "test: update config tests for file-based library storage"
```

---

### Task 9: Update UI tests that construct `AppContext` or reference library

**Files:**
- Any test files in `src/test/kotlin/.../ui/` that construct `AppContext` with `JsonConfigStore`

- [ ] **Step 1: Search for remaining compilation errors**

Run: `mvn test-compile -pl needlecast-desktop -T 4`
Expected: If any test files reference `config.promptLibrary` or `config.commandLibrary`, they will fail to compile.

Fix each one by removing the reference. The UI tests (like `MainWindowUiTest`, `SettingsDialogUiTest`, etc.) construct `AppContext` or `JsonConfigStore` directly — they should work unchanged since the `AppContext` constructor now has a default for `promptLibraryStore` and the removed fields have defaults.

- [ ] **Step 2: Run full test suite**

Run: `mvn verify -pl needlecast-desktop -T 4`
Expected: All tests PASS.

- [ ] **Step 3: Commit any test fixes**

```bash
git add -u
git commit -m "test: fix remaining test references to removed library fields"
```

---

### Task 10: Make `defaultPromptLibrary()` / `defaultCommandLibrary()` internal (not called at runtime)

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`

- [ ] **Step 1: Verify the functions are already `internal`**

Check that `defaultPromptLibrary()` and `defaultCommandLibrary()` are marked `internal`. They should already be (from the existing code). They are only called from `AppContext.init` for seeding — confirm this is the only call site.

Run: `grep -r "defaultPromptLibrary\|defaultCommandLibrary" --include="*.kt"`
Expected: Only `AppConfig.kt` (definition) and `AppContext.kt` (seed call).

- [ ] **Step 2: Commit (if any changes were needed)**

```bash
git add -u && git commit -m "refactor: ensure default library functions are internal"
```

(If no changes needed, skip this commit.)

---

### Task 11: Final verification

- [ ] **Step 1: Run full build**

Run: `mvn verify -pl needlecast-desktop -T 4`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Run the application and verify**

Manually verify (or note for manual testing):
1. Delete `~/.needlecast/prompts/` and `~/.needlecast/commands/` directories if they exist.
2. Launch the app.
3. Verify `~/.needlecast/prompts/Explore/`, `Fix/`, etc. are created with `.md` files.
4. Verify `~/.needlecast/commands/Git/`, `Build/`, etc. are created.
5. Open Prompt Library — verify templates load.
6. Open Command Library — verify commands load.
7. Create a new prompt — verify it creates a `.md` file.
8. Edit a prompt — verify the file updates.
9. Delete a prompt — verify the file is removed.
10. Verify `~/.needlecast/config.json` no longer contains `promptLibrary` or `commandLibrary`.

- [ ] **Step 3: Final commit with all remaining changes**

```bash
git add -u
git commit -m "feat: complete migration to markdown-based prompt/command library"
```
