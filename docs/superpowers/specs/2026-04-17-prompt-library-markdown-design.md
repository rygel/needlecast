# Prompt & Command Library: Markdown File Storage

Date: 2026-04-17

## Problem

Prompt templates and CLI command shortcuts are hardcoded in `defaultPromptLibrary()` and `defaultCommandLibrary()` in `AppConfig.kt`. User customizations are stored inline in `config.json`. This makes it hard to edit templates outside the app, share them, or version-control them.

## Solution

Move both libraries to a directory structure under `~/.needlecast/` where each template is a standalone markdown file with YAML frontmatter. Category is represented by directory nesting. The `config.json` no longer stores library data.

## File Structure

```
~/.needlecast/
  prompts/
    Explore/
      onboarding.md
      what-does-this-file-do.md
      how-does-this-feature-work.md
      what-changed-recently.md
    Fix/
      fix-this-error.md
      fix-build-failure.md
      why-is-this-slow.md
      fix-flaky-test.md
    Review/
      review-my-changes.md
      is-this-safe-to-deploy.md
      security-audit.md
    Write/
      implement-this.md
      write-tests.md
      add-rest-endpoint.md
      write-db-migration.md
      refactor-this.md
      convert-to.md
    Git/
      write-commit-message.md
      write-pr-description.md
      explain-this-diff.md
    DevOps/
      write-dockerfile.md
      write-github-action.md
      debug-ci-failure.md
  commands/
    Git/
      status.md
      log-graph.md
      log-last-n.md
      diff-staged.md
      diff-from-branch.md
      new-branch.md
      stash.md
      stash-pop.md
      interactive-rebase.md
      blame.md
      find-commit-by-text.md
      clean-untracked.md
    Build/
      maven-verify.md
      maven-package-skip-tests.md
      maven-clean.md
      maven-dependency-tree.md
      gradle-build.md
      gradle-test.md
      gradle-dependencies.md
      npm-install.md
      npm-run-build.md
      npm-test.md
      npm-outdated.md
    Docker/
      docker-build.md
      docker-run-interactive.md
      docker-compose-up.md
      docker-compose-logs.md
      docker-ps.md
    Search/
      find-in-files.md
      find-todos.md
      find-file-by-name.md
      large-files.md
    Process/
      who-is-on-port.md
      who-is-on-port-windows.md
      kill-port.md
      java-processes.md
      tail-log.md
      disk-usage.md
```

## File Format

Each `.md` file contains YAML frontmatter followed by the template body:

```markdown
---
name: Onboard me to this repo
description: Quick orientation for an unfamiliar codebase.
---

Give me a 2-minute developer onboarding:
1. What does this project do and who uses it?
2. Architecture overview — modules, layers, how data flows.
```

- `name` (required) — display name shown in the UI.
- `description` (required, may be empty) — short summary shown in the UI.
- `category` — **not in frontmatter**. Derived from the parent directory name. This is the single source of truth for category and avoids sync issues between directory and file content.
- `body` — everything after the `---` closing delimiter. Leading/trailing blank lines are stripped.
- `id` — generated deterministically from the relative path (e.g. `"Explore/onboarding.md"`) so IDs are stable across restarts without storing a UUID in the file. Algorithm: `UUID.nameUUIDFromBytes(relativePath.toByteArray())` (type 3 UUID).
- Filename — a slug derived from the name (e.g. `"Onboard me to this repo"` → `onboarding.md`, `"Maven package (skip tests)"` → `maven-package-skip-tests.md`). Slugs are lowercase, hyphens replace spaces and special characters, parenthesized content is stripped or simplified.

## New Class: `PromptLibraryStore`

Package: `io.github.rygel.needlecast.config`

```kotlin
class PromptLibraryStore(
    private val promptsDir: Path,  // ~/.needlecast/prompts/
    private val commandsDir: Path, // ~/.needlecast/commands/
) {
    fun loadPrompts(): List<PromptTemplate>
    fun loadCommands(): List<PromptTemplate>
    fun save(template: PromptTemplate, isCommand: Boolean)
    fun delete(template: PromptTemplate, isCommand: Boolean)
}
```

### Loading

- Recursively walk the directory.
- Each `.md` file becomes a `PromptTemplate`.
- Category = parent directory name.
- ID = `UUID.nameUUIDFromBytes(relativePath)` where relativePath is e.g. `"Explore/onboarding.md"`.
- Parse YAML frontmatter with a lightweight parser (split on `---` delimiters, read key-value pairs).
- Body = everything after the second `---`, trimmed.

### Saving

- Slugify the name to produce the filename.
- Write YAML frontmatter + body to `<category>/<slug>.md`.
- Create the category directory if it doesn't exist.
- If the category changed (rename), delete the old file and write to the new directory.

### Deleting

- Delete the file.
- Remove the category directory if empty (best-effort).

### Slugify

```
"Fix this error"              → "fix-this-error"
"Maven package (skip tests)"  → "maven-package-skip-tests"
"What does this file do"      → "what-does-this-file-do"
"lsof -i :{port}"             → "who-is-on-port"  (for commands, slug is derived from name field)
```

Algorithm: lowercase, replace `[^a-z0-9]+` with `-`, strip leading/trailing hyphens, collapse consecutive hyphens.

## First-Run Seeding

On first launch when the prompts/commands directories don't exist:

1. Create the full directory structure.
2. Write all default templates as `.md` files from seed data.
3. The existing `defaultPromptLibrary()` and `defaultCommandLibrary()` functions are kept as the seed data source but are only called once during this initialization.

## Changes to Existing Code

### AppConfig.kt

- Remove `promptLibrary: List<PromptTemplate>` field.
- Remove `commandLibrary: List<PromptTemplate>` field.
- Keep `defaultPromptLibrary()` and `defaultCommandLibrary()` as `internal` — used only for first-run seeding.

### ConfigMigrator.kt

- Bump `CURRENT_VERSION` to 4.
- Add migration: strip `promptLibrary` and `commandLibrary` from loaded config (they are now loaded from disk).

### AppContext.kt

- Add `val promptLibraryStore: PromptLibraryStore`.
- Initialize it before `config` is loaded so it can run first-run seeding if needed.
- Remove any references to `config.promptLibrary` / `config.commandLibrary`.

### PromptLibraryDialog.kt

- Change `loadLibrary` / `saveLibrary` lambdas to use `PromptLibraryStore` instead of reading/writing through `AppConfig`.
- `saveLibrary` becomes a single-item save (write one file) instead of replacing the entire list.
- `deleteSelected` calls `store.delete()`.

### MainWindow.kt

- Update the two `PromptLibraryDialog` instantiations (prompts and commands) to pass the store-based load/save lambdas.

### Tests

- Update `ConfigRoundTripTest`, `ConfigMigratorTest`, and any test that references `config.promptLibrary` or `config.commandLibrary`.
- Add `PromptLibraryStoreTest` covering: loading from directory, saving new template, updating existing, changing category, deleting, slug generation, frontmatter parsing.

## YAML Frontmatter Parser

Minimal implementation — no external dependency. Split file content on `\n---\n` delimiters. First segment between `---` markers is YAML. Parse key: value pairs line by line. Everything after the second `---` is the body.

Handle edge cases:
- File starts with `---\n` (standard format).
- No frontmatter — treat entire file as body, name = filename without extension.
- Empty body — valid (body defaults to empty string).

## Scope

This change covers the storage layer only. The `PromptLibraryDialog` UI, variable substitution (`{varName}` placeholders), and "send to terminal" behavior remain unchanged.
