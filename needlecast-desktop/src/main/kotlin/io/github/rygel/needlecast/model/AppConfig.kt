package io.github.rygel.needlecast.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

// CommandHistoryEntry is defined in CommandDescriptor.kt (same package)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ProjectTreeEntry.Folder::class,  name = "folder"),
    JsonSubTypes.Type(value = ProjectTreeEntry.Project::class, name = "project"),
)
sealed class ProjectTreeEntry {
    abstract val id: String

    data class Folder(
        override val id: String = UUID.randomUUID().toString(),
        val name: String,
        val color: String? = null,
        val children: List<ProjectTreeEntry> = emptyList(),
    ) : ProjectTreeEntry()

    data class Project(
        override val id: String = UUID.randomUUID().toString(),
        val directory: ProjectDirectory,
        val tags: List<String> = emptyList(),
    ) : ProjectTreeEntry()
}

data class PromptTemplate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val body: String = "",
)

data class ExternalEditor(
    val name: String,
    val executable: String,
)

private fun defaultEditors() = listOf(
    ExternalEditor("VS Code", "code"),
    ExternalEditor("Zed", "zed"),
    ExternalEditor("IntelliJ IDEA", "idea"),
)

internal fun defaultPromptLibrary() = listOf(

    // ── Understand ────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Onboard me",
        category = "Understand",
        description = "Comprehensive first-look at an unfamiliar codebase.",
        body = """Read the codebase and give me a developer onboarding summary:
1. What problem does this project solve and for whom?
2. High-level architecture — main modules, layers, data flow.
3. Key entry points (main classes, routes, event handlers).
4. Non-obvious design decisions or domain-specific conventions I should know.
5. Where would I start if I needed to add a new feature or fix a bug?
Be concrete — name actual files and classes.""",
    ),
    PromptTemplate(
        name = "Explain file",
        category = "Understand",
        description = "Deep-dive on a single file and its role in the system.",
        body = """Explain the file {file}:
- What responsibility does this class/module own?
- How does it interact with the rest of the codebase (callers, dependencies)?
- Are there any surprising implementation choices or known limitations?""",
    ),
    PromptTemplate(
        name = "Trace feature end-to-end",
        category = "Understand",
        description = "Follow a feature or request from trigger to result.",
        body = """Trace how {feature} works end-to-end in this codebase.
Start at the user-facing entry point (UI event, API endpoint, CLI command) and follow the call chain all the way through to the final side-effect (DB write, response, file change, etc.).
Name the actual classes and methods at each step.""",
    ),
    PromptTemplate(
        name = "Map dependencies",
        category = "Understand",
        description = "Show what a class or module depends on and what depends on it.",
        body = """Map the dependency graph around {target}:
- What does {target} depend on directly?
- What depends on {target}?
- Are there any circular dependencies or surprising couplings?""",
    ),

    // ── Debug ─────────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Debug this error",
        category = "Debug",
        description = "Diagnose an exception or error message.",
        body = """I'm getting this error:

{error}

Help me diagnose the root cause:
1. What does this error mean?
2. What are the most likely causes given this codebase?
3. What should I check or add to narrow it down?
Don't guess — tell me what you'd need to see to be certain.""",
    ),
    PromptTemplate(
        name = "Why is this slow",
        category = "Debug",
        description = "Investigate a performance problem.",
        body = """{target} is slower than expected. Help me investigate:
1. What are the most common causes of slowness in this type of code?
2. Looking at the implementation, where are the likely bottlenecks?
3. What would you profile or measure first?
4. Suggest concrete improvements, but only after understanding the actual hotspot.""",
    ),
    PromptTemplate(
        name = "Find the race condition",
        category = "Debug",
        description = "Spot concurrency issues in async or multi-threaded code.",
        body = """Review {target} for concurrency issues:
- Shared mutable state accessed without synchronization
- Operations that need to be atomic but aren't
- Callbacks or coroutines that resume on unexpected threads
- Anything that would behave differently under load or slow I/O""",
    ),

    // ── Quality ───────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Review these changes",
        category = "Quality",
        description = "Opinionated review of recent or staged changes.",
        body = """Review my recent changes with a senior engineer's eye. I care about:
1. Correctness — any bugs or edge cases I missed?
2. Safety — SQL injection, unvalidated input, accidental data exposure, exception swallowing?
3. Design — does this fit the existing patterns? Is it more complex than it needs to be?
4. Tests — what scenarios am I not testing?
Be direct. If something is wrong, say so.""",
    ),
    PromptTemplate(
        name = "Security audit",
        category = "Quality",
        description = "Look for security vulnerabilities in a file or feature.",
        body = """Security audit of {target}. Check for:
- Injection vulnerabilities (SQL, command, path traversal)
- Authentication/authorization gaps
- Sensitive data in logs, error messages, or responses
- Insecure defaults or missing input validation
- Dependency issues or unsafe API usage
Report findings with severity and a recommended fix for each.""",
    ),
    PromptTemplate(
        name = "Write tests",
        category = "Quality",
        description = "Generate unit or integration tests for a class or function.",
        body = """Write tests for {target}.
- Happy path
- Boundary conditions and edge cases
- Error paths — what happens when dependencies fail?
- Any invariants that should always hold

Use the same test framework and style already in this project.
Don't mock things that don't need mocking.""",
    ),
    PromptTemplate(
        name = "Find dead code",
        category = "Quality",
        description = "Identify unused code that can be removed.",
        body = """Look through the codebase for dead code:
- Unused classes, methods, or fields
- Unreachable branches
- Feature flags that are always on/off
- Duplicate implementations of the same thing
For each finding, confirm it's actually unused before suggesting removal.""",
    ),

    // ── Develop ───────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Implement feature",
        category = "Develop",
        description = "Ask the AI to implement something new.",
        body = """Implement: {description}

Before writing any code:
1. Identify the files that need to change.
2. Describe the approach you'll take and why.
3. Flag any design decisions where there's a meaningful trade-off.

Then implement it following the existing patterns in this codebase. No new dependencies unless necessary.""",
    ),
    PromptTemplate(
        name = "Refactor",
        category = "Develop",
        description = "Improve a piece of code without changing its behavior.",
        body = """Refactor {target} to improve clarity and maintainability.
Constraints:
- Don't change observable behavior
- Don't introduce abstractions that aren't needed yet
- Follow the conventions already in this codebase
Explain each change and why it's an improvement.""",
    ),
    PromptTemplate(
        name = "Migrate to",
        category = "Develop",
        description = "Replace one API, library, or pattern with another.",
        body = """Migrate {from} to {to}.
1. What needs to change?
2. Are there semantic differences I need to handle?
3. Show me a concrete migration for the most complex call site.
4. Is there anything that can't be migrated automatically?""",
    ),

    // ── Git ───────────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Commit message",
        category = "Git",
        description = "Write a conventional commit for staged changes.",
        body = """Write a conventional commit message for my staged changes.
Format: <type>(<scope>): <subject>

Rules:
- type: feat | fix | refactor | test | chore | docs | perf | ci
- subject: imperative mood, ≤72 chars, no period
- Body (optional): explain the *why*, not the what — the diff already shows the what
- If there's a breaking change, add a footer: BREAKING CHANGE: <description>""",
    ),
    PromptTemplate(
        name = "PR description",
        category = "Git",
        description = "Generate a pull request description from recent commits.",
        body = """Write a pull request description for my changes.

## Summary
What changed and why — 3-5 bullet points, non-obvious context only.

## How to test
Concrete steps a reviewer can follow to verify the change works.

## Notes for reviewers
Anything tricky, assumptions made, or follow-up work deferred.""",
    ),

    // ── Docs ──────────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Document this",
        category = "Docs",
        description = "Generate KDoc/Javadoc/docstring for a function or class.",
        body = """Write documentation for {target}.
- One-sentence summary of what it does
- Explain non-obvious parameters and return value
- Note any preconditions, side effects, or exceptions
- If the implementation has a subtle trick, explain it in a NOTE

Match the doc style already used in this file.""",
    ),
)

internal fun defaultCommandLibrary() = listOf(

    // ── Git ───────────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Status",
        category = "Git",
        description = "Short status with branch name.",
        body = "git status -sb",
    ),
    PromptTemplate(
        name = "Log (graph)",
        category = "Git",
        description = "Compact decorated graph of recent commits.",
        body = "git log --oneline --graph --decorate -20",
    ),
    PromptTemplate(
        name = "Log (last N)",
        category = "Git",
        description = "Last N commits, one line each.",
        body = "git log --oneline -n {count}",
    ),
    PromptTemplate(
        name = "Diff staged",
        category = "Git",
        description = "Show what is staged for commit.",
        body = "git diff --staged",
    ),
    PromptTemplate(
        name = "Diff from branch",
        category = "Git",
        description = "Changes between current branch and another.",
        body = "git diff {base}...HEAD --stat",
    ),
    PromptTemplate(
        name = "New branch",
        category = "Git",
        description = "Create and switch to a new branch.",
        body = "git checkout -b {branch}",
    ),
    PromptTemplate(
        name = "Stash",
        category = "Git",
        description = "Stash all uncommitted changes.",
        body = "git stash push -m \"{message}\"",
    ),
    PromptTemplate(
        name = "Stash pop",
        category = "Git",
        description = "Apply the most recent stash and remove it.",
        body = "git stash pop",
    ),
    PromptTemplate(
        name = "Interactive rebase",
        category = "Git",
        description = "Edit/squash the last N commits.",
        body = "git rebase -i HEAD~{n}",
    ),
    PromptTemplate(
        name = "Blame",
        category = "Git",
        description = "Annotate a file with the last commit per line.",
        body = "git blame {file}",
    ),
    PromptTemplate(
        name = "Find commit by text",
        category = "Git",
        description = "Search commit history for a string.",
        body = "git log --all -S \"{text}\" --oneline",
    ),
    PromptTemplate(
        name = "Clean untracked",
        category = "Git",
        description = "Remove untracked files (dry-run first).",
        body = "git clean -nd",
    ),

    // ── Build — Maven ─────────────────────────────────────────────────────
    PromptTemplate(
        name = "Maven verify",
        category = "Build",
        description = "Full build + tests with 4 threads.",
        body = "mvn verify -T 4",
    ),
    PromptTemplate(
        name = "Maven package (skip tests)",
        category = "Build",
        description = "Fast local build, no tests.",
        body = "mvn -q -DskipTests package",
    ),
    PromptTemplate(
        name = "Maven clean",
        category = "Build",
        description = "Delete target/ directories.",
        body = "mvn clean",
    ),
    PromptTemplate(
        name = "Maven dependency tree",
        category = "Build",
        description = "Print the full dependency tree.",
        body = "mvn dependency:tree",
    ),

    // ── Build — Gradle ────────────────────────────────────────────────────
    PromptTemplate(
        name = "Gradle build",
        category = "Build",
        description = "Assemble and test.",
        body = "./gradlew build",
    ),
    PromptTemplate(
        name = "Gradle test",
        category = "Build",
        description = "Run tests only.",
        body = "./gradlew test",
    ),
    PromptTemplate(
        name = "Gradle dependencies",
        category = "Build",
        description = "Print the dependency tree for a configuration.",
        body = "./gradlew dependencies --configuration {config}",
    ),

    // ── Build — Node / npm ────────────────────────────────────────────────
    PromptTemplate(
        name = "npm install",
        category = "Build",
        description = "Install dependencies.",
        body = "npm install",
    ),
    PromptTemplate(
        name = "npm run build",
        category = "Build",
        description = "Run the build script.",
        body = "npm run build",
    ),
    PromptTemplate(
        name = "npm test",
        category = "Build",
        description = "Run the test script.",
        body = "npm test",
    ),
    PromptTemplate(
        name = "npm outdated",
        category = "Build",
        description = "Show outdated packages.",
        body = "npm outdated",
    ),

    // ── Docker ────────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Docker build",
        category = "Docker",
        description = "Build an image from the local Dockerfile.",
        body = "docker build -t {image}:{tag} .",
    ),
    PromptTemplate(
        name = "Docker run (interactive)",
        category = "Docker",
        description = "Start a container interactively and remove on exit.",
        body = "docker run --rm -it {image} {cmd}",
    ),
    PromptTemplate(
        name = "Docker compose up",
        category = "Docker",
        description = "Start all services in the background.",
        body = "docker compose up -d",
    ),
    PromptTemplate(
        name = "Docker compose logs",
        category = "Docker",
        description = "Follow logs for all (or one) service.",
        body = "docker compose logs -f {service}",
    ),
    PromptTemplate(
        name = "Docker ps",
        category = "Docker",
        description = "List running containers.",
        body = "docker ps",
    ),

    // ── Search ────────────────────────────────────────────────────────────
    PromptTemplate(
        name = "Find in files",
        category = "Search",
        description = "Recursive text search with line numbers.",
        body = "rg -n \"{pattern}\" {path}",
    ),
    PromptTemplate(
        name = "Find TODOs",
        category = "Search",
        description = "List all TODO / FIXME comments.",
        body = "rg -n \"TODO|FIXME|HACK|XXX\"",
    ),
    PromptTemplate(
        name = "Find file by name",
        category = "Search",
        description = "Locate a file anywhere under the current directory.",
        body = "find . -name \"{name}\" -not -path \"*/.*\" -not -path \"*/target/*\" -not -path \"*/node_modules/*\"",
    ),
    PromptTemplate(
        name = "Large files",
        category = "Search",
        description = "List the 20 largest files under the current directory.",
        body = "find . -type f -not -path \"*/.*\" | xargs du -sh 2>/dev/null | sort -rh | head -20",
    ),

    // ── Process / Network ─────────────────────────────────────────────────
    PromptTemplate(
        name = "Who is on port",
        category = "Process",
        description = "Find which process is listening on a port (Unix).",
        body = "lsof -i :{port}",
    ),
    PromptTemplate(
        name = "Who is on port (Windows)",
        category = "Process",
        description = "Find which process is listening on a port (Windows).",
        body = "netstat -ano | findstr :{port}",
    ),
    PromptTemplate(
        name = "Kill port (Unix)",
        category = "Process",
        description = "Kill whatever is listening on a port.",
        body = "lsof -ti :{port} | xargs kill -9",
    ),
    PromptTemplate(
        name = "Java processes",
        category = "Process",
        description = "List all running JVM processes.",
        body = "jps -l",
    ),
    PromptTemplate(
        name = "Tail log",
        category = "Process",
        description = "Follow a log file in real time.",
        body = "tail -f {logfile}",
    ),
    PromptTemplate(
        name = "Disk usage",
        category = "Process",
        description = "Summarize disk usage of each item in the current directory.",
        body = "du -sh * | sort -rh | head -20",
    ),
)

data class AppConfig(
    /** Incremented when a breaking schema change requires migration. Current: 2. */
    val configVersion: Int = 2,
    val groups: List<ProjectGroup> = emptyList(),
    val windowWidth: Int = 1200,
    val windowHeight: Int = 800,
    val lastSelectedGroupId: String? = null,
    val theme: String = "dark",
    /** BCP 47 language tag (e.g. "en", "de", "fr"). Drives the I18nService locale. */
    val language: String = "en",
    val externalEditors: List<ExternalEditor> = defaultEditors(),
    // Per-project command history keyed by working directory path (max 20 per project)
    val commandHistory: Map<String, List<CommandHistoryEntry>> = emptyMap(),
    /** Overridden keyboard shortcuts keyed by action name. Empty = use built-in defaults. */
    val shortcuts: Map<String, String> = emptyMap(),
    val promptLibrary: List<PromptTemplate> = defaultPromptLibrary(),
    val commandLibrary: List<PromptTemplate> = defaultCommandLibrary(),
    val projectTree: List<ProjectTreeEntry> = emptyList(),
    /** Whether the console output pane is visible. */
    val showConsole: Boolean = true,
    /** Whether the file explorer tab is visible in the right panel. */
    val showExplorer: Boolean = true,
    /**
     * Per-CLI enable/disable toggles keyed by cli.command.
     * Absent key → defaults to enabled.
     */
    val aiCliEnabled: Map<String, Boolean> = emptyMap(),
    /** User-defined AI CLIs added via Settings. */
    val customAiClis: List<AiCliDefinition> = emptyList(),
    /** Whether docking panel tabs appear at the top (true) or bottom (false). */
    val tabsOnTop: Boolean = true,
    /** Whether to draw a highlight border on the panel the mouse is currently hovering over. */
    val panelHoverHighlight: Boolean = false,
    /** Whether ModernDocking draws an active-panel highlight border (Settings.setActiveHighlighterEnabled). */
    val dockingActiveHighlight: Boolean = false,
    /**
     * Global default shell used when a project has no per-project [ProjectDirectory.shellExecutable].
     * Null means OS default (cmd.exe on Windows, /bin/bash on Unix).
     * Example values: "powershell", "pwsh", "bash", "zsh".
     */
    val defaultShell: String? = null,
    /**
     * RSyntaxTextArea syntax-highlight theme. "auto" = follow dark/light app theme.
     * Other values are RSTA built-in theme file names without the .xml extension:
     * "monokai", "dark", "druid", "idea", "eclipse", "default", "default-alt", "vs".
     */
    val syntaxTheme: String = "auto",
    /** Custom terminal background color as a hex string (e.g. "#1E1E1E"). Null = theme default. */
    val terminalBackground: String? = null,
    /** Custom terminal foreground color as a hex string (e.g. "#D4D4D4"). Null = theme default. */
    val terminalForeground: String? = null,
    /** Terminal font size in points. Range [8, 36]. Default 13. */
    val terminalFontSize: Int = 13,
)

data class AiCliDefinition(
    val name: String,
    val command: String,
    val description: String = "",
)

data class ProjectGroup(
    val id: String,
    val name: String,
    val directories: List<ProjectDirectory> = emptyList(),
    /** Optional hex color string (e.g. "#FF5722") shown as a left-edge stripe in the group list. */
    val color: String? = null,
)

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
) {
    fun label(): String = displayName ?: path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}
