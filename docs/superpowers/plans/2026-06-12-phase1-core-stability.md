# Phase 1: Core Stability & Hardening

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the process execution layer, add logging to swallowed exceptions, and fix cross-shell correctness issues.

**Architecture:** Targeted fixes to existing code. No new features. Each task produces a green build independently.

**Tech Stack:** Kotlin 2.2, Maven, JUnit 5, SLF4J + Logback

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `process/ProcessCommandRunner.kt` | Wrap `pb.start()` in try/catch, add watchdog timeout |
| Modify | `ui/terminal/TerminalPanel.kt` | Shell-aware `setDirectory()`, track `ShellKind` |
| Modify | `ui/terminal/TerminalManager.kt` | Propagate shell type to TerminalPanel |
| Modify | `ui/terminal/QuickLaunchTerminalSettings.kt` | N/A (read-only reference) |
| Modify | ~15 files | Add `logger.warn`/`logger.debug` to silent catches |

---

### Task 1: Harden ProcessCommandRunner

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/.../process/ProcessCommandRunner.kt`
- Test: `needlecast-desktop/src/test/kotlin/.../process/ProcessCommandRunnerTest.kt`

**Current issues:**
- `pb.start()` (line 16) is outside any try/catch — a bad command throws raw `IOException`
- No timeout — a hung process blocks the reader thread forever
- No charset parameter — uses platform default instead of UTF-8
- No logger — exceptions only reach the listener as synthetic strings

- [ ] **Step 1: Read current files**

Read `ProcessCommandRunner.kt` and `ProcessCommandRunnerTest.kt` to understand current state.

- [ ] **Step 2: Write failing test for start failure**

Add a test that verifies `ProcessCommandRunner.run()` calls `listener.onExit(-1)` when the command cannot be started (e.g., nonexistent executable). Currently this would throw `IOException`.

```kotlin
@Test
fun `run calls onExit with -1 when process fails to start`() {
    val runner = ProcessCommandRunner()
    val descriptor = CommandDescriptor(
        label = "bad",
        buildTool = BuildTool.MAVEN,
        argv = listOf("/nonexistent/command/that/does/not/exist"),
        workingDirectory = System.getProperty("user.dir"),
    )
    val exits = mutableListOf<Int>()
    val listener = object : ProcessOutputListener {
        override fun onLine(line: String) {}
        override fun onExit(code: Int) { exits.add(code) }
    }
    runner.run(descriptor, listener)
    eventually(Duration.ofSeconds(5)) {
        assertEquals(listOf(-1), exits)
    }
}
```

- [ ] **Step 3: Run test — expect failure (IOException propagates)**

```bash
mvn test -pl needlecast-desktop -Dtest="ProcessCommandRunnerTest" -q
```

Expected: test fails with `IOException`

- [ ] **Step 4: Wrap `pb.start()` in try/catch, add timeout, add charset, add logger**

Edit `ProcessCommandRunner.kt`:

```kotlin
package io.github.rygel.needlecast.process

import io.github.rygel.needlecast.model.CommandDescriptor
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class ProcessCommandRunner {
    private val logger = LoggerFactory.getLogger(ProcessCommandRunner::class.java)

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 300_000L
    }

    fun run(
        descriptor: CommandDescriptor,
        listener: ProcessOutputListener,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): RunningProcess {
        val pb =
            ProcessBuilder(descriptor.argv)
                .directory(File(descriptor.workingDirectory))
                .redirectErrorStream(true)
        if (descriptor.env.isNotEmpty()) pb.environment().putAll(descriptor.env)

        val process =
            try {
                pb.start()
            } catch (e: Exception) {
                logger.warn("Failed to start process: {}", descriptor.argv, e)
                listener.onLine("[Failed to start: ${e.message}]")
                listener.onExit(-1)
                return RunningProcess(NOOP_PROCESS, Thread.currentThread())
            }

        val readerThread =
            Thread({
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { line -> listener.onLine(line) }
                    }
                    listener.onExit(process.waitFor())
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    listener.onExit(-1)
                } catch (e: Exception) {
                    logger.warn("Error reading process output", e)
                    listener.onLine("[Error reading process output: ${e.message}]")
                    listener.onExit(-1)
                }
            }, "process-reader").apply {
                isDaemon = true
                start()
            }

        val watchdog =
            Thread({
                if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                    logger.warn("Process timed out after {}ms, destroying: {}", timeoutMs, descriptor.argv)
                    process.destroyForcibly()
                    readerThread.interrupt()
                }
            }, "process-watchdog").apply {
                isDaemon = true
                start()
            }

        return RunningProcess(process, readerThread)
    }

    private companion object {
        val NOOP_PROCESS =
            object : Process() {
                override fun getOutputStream() = System.`err`
                override fun getInputStream() = System.`in`
                override fun getErrorStream() = System.`err`
                override fun waitFor() = -1
                override fun exitValue() = -1
                override fun destroy() {}
            }
    }
}
```

Key changes:
- `pb.start()` wrapped in try/catch — calls listener.onExit(-1) on failure
- Watchdog thread enforces `timeoutMs` (default 5 min)
- Explicit `Charsets.UTF_8` on reader
- SLF4J logger added

- [ ] **Step 5: Run all ProcessCommandRunner tests**

```bash
mvn test -pl needlecast-desktop -Dtest="ProcessCommandRunnerTest" -q
```

Expected: all pass

- [ ] **Step 6: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: 0 failures, 0 errors

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "fix: harden ProcessCommandRunner — catch start failures, add watchdog timeout, fix charset"
```

---

### Task 2: Add logging to high-risk silent catches

**Files:** ~15 files with the most silent `catch (_:)` blocks

**Current issue:** 100+ `catch (_: Exception)` blocks across the codebase with zero logging. Failures in drag-and-drop, search, file scanning, etc. produce no diagnostic output.

**Strategy:** Add `logger.warn`/`logger.debug` to each silent catch. Don't change behavior — just make failures visible. Use `logger.debug` for expected/handled conditions (e.g., `BadLocationException` in diff rendering), `logger.warn` for unexpected failures.

**Priority files by risk:**

| File | Silent catches | Risk level |
|------|---------------|------------|
| `ui/ProjectTreeDndHandler.kt` | 10 | High — drag-drop failures invisible |
| `ui/SearchPanel.kt` | 13 | High — search failures invisible |
| `ui/explorer/ExplorerDropHandler.kt` | 7 | High — file drop failures invisible |
| `scanner/BuildFileWatcher.kt` | 4 | High — watch failures silent |
| `ui/explorer/EditorPanel.kt` | 4 | Medium — file read failures |
| `ui/GitLogPanel.kt` | 4 | Medium — git parse failures |
| `ui/explorer/MediaPlayerPanel.kt` | 4 | Medium — media errors |
| `ui/explorer/ExplorerPanel.kt` | 3 | Medium — navigation failures |
| `ui/explorer/FindBar.kt` | 3 | Medium — find failures |
| `ui/diff/DiffEditorPane.kt` | 6 | Low — `BadLocationException` is expected |
| `ui/diff/DiffSearchBar.kt` | 2 | Low |
| `ui/ProjectTreeIconUtils.kt` | 2 | Low — icon load failures |
| `ui/ProjectTreeCellRenderer.kt` | 2 | Low — rendering errors |
| `ui/TrayNotifier.kt` | 2 | Low — tray icon errors |
| `config/JsonConfigStore.kt` | 2 | Medium — config load failures |

- [ ] **Step 1: Add logger to each file, replace `catch (_:` with logging catches**

For each file listed above:

1. Add `private val logger = LoggerFactory.getLogger(ClassName::class.java)` at the top of the class (or as a top-level `val` for singletons/objects)
2. Add the import `import org.slf4j.LoggerFactory` if not present
3. Replace each `catch (_: SomeException) {` with:
   - `catch (e: SomeException) {` followed by `logger.warn("description", e)` for unexpected failures
   - `catch (e: SomeException) {` followed by `logger.debug("description", e)` for expected conditions

**Guidelines per file:**
- `ProjectTreeDndHandler.kt`: All 10 catches → `logger.warn`. Drag-drop failures are never expected.
- `SearchPanel.kt`: The 3 `MalformedInputException`/`CharacterCodingException` → `logger.debug` (expected for binary files). All others → `logger.warn`.
- `ExplorerDropHandler.kt`: All → `logger.warn`. File drop failures need visibility.
- `BuildFileWatcher.kt`: All → `logger.warn`. Watch service failures should be visible.
- `DiffEditorPane.kt`: All `BadLocationException` → `logger.debug`. The generic `catch (_: Exception)` → `logger.warn`.
- `JsonConfigStore.kt`: Both → `logger.warn`. Config load failures must be visible.
- Other files: Use `logger.warn` unless the exception is clearly expected (rendering, formatting).

- [ ] **Step 2: Run full test suite**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: 0 failures, 0 errors

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "fix: add logging to 100+ silent catch blocks across 15 files"
```

---

### Task 3: Fix cross-shell setDirectory

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/.../ui/terminal/TerminalPanel.kt`

**Current issue:** `setDirectory()` (line 200-211) uses `IS_WINDOWS` to decide between `cd /d` (cmd) and `cd` (Unix). This is wrong when the launched shell is Git Bash or WSL on Windows — `IS_WINDOWS` is true but the shell is bash, so `cd /d` is a syntax error.

**The fix:** Use the resolved shell command to determine the `cd` syntax, not `IS_WINDOWS`.

- [ ] **Step 1: Read TerminalPanel.kt — focus on resolveShellCommand() and setDirectory()**

Read lines 200-211 (setDirectory) and 431-436 (resolveShellCommand).

- [ ] **Step 2: Add shell-kind tracking**

Add a property to `TerminalPanel` that tracks what kind of shell was resolved:

```kotlin
private enum class ShellKind { CMD, POWERSHELL, BASH, SH, OTHER }

private fun classifyShell(cmd: Array<String>): ShellKind {
    val exe = cmd.firstOrNull()?.substringAfterLast('/')?.substringAfterLast('\\')?.lowercase() ?: return ShellKind.OTHER
    return when {
        exe == "cmd.exe" || exe == "cmd" -> ShellKind.CMD
        exe == "pwsh.exe" || exe == "pwsh" || exe == "powershell.exe" || exe == "powershell" -> ShellKind.POWERSHELL
        exe.endsWith("bash") || exe == "bash" -> ShellKind.BASH
        exe == "sh" -> ShellKind.SH
        exe == "wsl.exe" || exe == "wsl" -> ShellKind.BASH
        else -> ShellKind.OTHER
    }
}
```

Store the result when starting a shell:

```kotlin
private var shellKind: ShellKind = ShellKind.OTHER
```

In `startShell()`, after `val cmd = resolveShellCommand()`:
```kotlin
shellKind = classifyShell(cmd)
```

- [ ] **Step 3: Fix setDirectory()**

Replace the current `setDirectory()` implementation:

```kotlin
fun setDirectory(dir: String) {
    currentDir = dir
    val process = ptyProcess ?: return
    try {
        val escaped = dir.replace("\\", "\\\\").replace("\"", "\\\"")
        val cmd = when (shellKind) {
            ShellKind.CMD -> "cd /d \"$escaped\"\r\n"
            ShellKind.POWERSHELL -> "cd \"$escaped\"\r\n"
            else -> "cd \"$escaped\"\n"
        }
        process.outputStream.write(cmd.toByteArray(Charsets.UTF_8))
        process.outputStream.flush()
    } catch (e: Exception) {
        logger.warn("Failed to change terminal directory to {}", dir, e)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl needlecast-desktop -q
```

Expected: 0 failures, 0 errors

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "fix: use shell-aware cd syntax instead of IS_WINDOWS in TerminalPanel.setDirectory"
```

---

## Verification (run after all tasks)

- [ ] **Full build + tests**

```bash
mvn verify -pl needlecast-desktop -q
```

Expected: BUILD SUCCESS

- [ ] **Verify no new silent catches in changed files**

```bash
rg "catch \(_:" needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/process/ needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/terminal/TerminalPanel.kt
```

Expected: 0 matches (or only `InterruptedException` which is correctly handled)

- [ ] **Run app to verify terminal behavior**

```bash
mvn exec:java -pl needlecast-desktop
```

Expected: terminal opens, `cd` works correctly in cmd.exe, PowerShell, and Git Bash
