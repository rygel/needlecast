# Prompt Library

## Overview

The **Prompt Library** is a collection of AI prompt templates and shell command templates. Templates support `{placeholder}` substitution and can be sent directly into the active [[Terminal]] session.

Two separate libraries share the same UI:
- **Prompt Library** — multi-line AI prompts (for Claude, Gemini, etc.)
- **Command Library** — shell one-liners (pasted as-is)

---

## Accessing the Libraries

- **AI Tools → Prompt Library…** — opens the full template manager dialog
- **AI Tools → Command Library…** — opens the command template manager
- The **Prompt Input** and **Command Input** panels (bottom of the main window) give a quick-access dropdown without opening the full dialog

---

## Prompt Library Dialog

A **modeless** dialog (stays open while you work). Default size 860×560, minimum 700×420.

### Left Pane (Template List)

- **Search field** — live filter matching name, category, and description (case-insensitive substring)
- **Template list** — each row shows the name in bold (12 pt) and category tag right-aligned (10 pt), cell height 44 px
- **+ New** button — opens a blank form to create a template
- **Delete** button — deletes the selected template after a confirmation dialog

### Right Pane (Edit Form)

| Field | Detail |
|---|---|
| **Name** | Short label for the template |
| **Category** | Grouping label (e.g. `Explore`, `Git`, `DevOps`) |
| **Description** | One-line description (shown in search results) |
| **Body** | The actual prompt or command text; monospace 12 pt, word-wrapped |

Hint text: *"Use `{varName}` placeholders — you will be prompted to fill them in before pasting."*

### Action Bar (Bottom)

| Button | Action |
|---|---|
| **Paste to Terminal** | Resolves placeholders then pastes into the active terminal |
| **Save** | Persists edits to `~/.needlecast/config.json` |

---

## Prompt Input Panel

Toggle via **Panels → Prompt Input**. A quick-access panel at the bottom of the main window.

| Element | Description |
|---|---|
| Category dropdown | Filter by category |
| Template dropdown | Select template |
| Body text area | Editable — not auto-saved |
| **Paste to Terminal** | Resolve placeholders and paste |
| **Save** | Save current edits |
| **+** | Create new template (opens a simpler New Prompt dialog with Name, Category, Body fields) |
| **−** | Delete selected template |

---

## Placeholder Substitution

Templates use `{variableName}` tokens. The pattern matched is `\{(\w+)}`.

When **Paste to Terminal** is clicked and placeholders are found, the **Variable Resolution Dialog** opens:
- Title: "Fill in Placeholders"
- One labeled text field per distinct variable name
- Hint: *"These placeholders were found in the prompt body. Fill them in to continue."*
- **Insert** button (default) — substitutes all values and pastes
- **Cancel** — does nothing
- First field is auto-focused on open
- Minimum dialog size 360×160

If the body contains **no** placeholders, the text is pasted immediately without a dialog.

---

## Built-in Prompt Templates (23 total)

### Explore

**Onboard me to this repo**
```
Give me a 2-minute developer onboarding:
1. What does this project do and who uses it?
2. Architecture overview — modules, layers, how data flows.
3. Where are the entry points (main, routes, handlers)?
4. What conventions or gotchas would trip up a new contributor?
Name actual files. Skip obvious things.
```

**What does this file do** *(placeholder: `{file}`)*
```
Explain {file} — what it owns, who calls it, what it depends on, and anything non-obvious about the implementation.
```

**How does this feature work** *(placeholder: `{feature}`)*
```
Trace {feature} end-to-end. Start at the trigger (click, request, event) and follow every step to the final side-effect. Name the actual classes and methods.
```

**What changed recently**
```
Summarize the last 20 commits. Group by area (feature, fix, refactor). Flag anything that looks risky or half-finished.
```

### Fix

**Fix this error** *(placeholder: `{error}`)*
```
I'm hitting this error:

{error}

Find the root cause in this codebase and fix it. Show me the exact change. If you need more context, tell me which file to look at instead of guessing.
```

**Fix this build failure** *(placeholder: `{error}`)*
```
The build is failing with:

{error}

Diagnose the issue — is it a dependency problem, a config issue, a code error, or an environment difference? Give me the fix, not just the explanation.
```

**Why is this slow** *(placeholder: `{target}`)*
```
{target} is too slow. Look at the implementation and tell me:
1. Where is time actually being spent?
2. What's the fix?
Don't suggest generic optimizations. Point at the specific bottleneck in this code.
```

**Fix the flaky test** *(placeholder: `{test}`)*
```
{test} is flaky — it passes locally but fails in CI (or vice versa). Look at it and find:
- Timing dependencies or race conditions
- Shared state between tests
- Environment assumptions (paths, ports, locale)
Give me the fix, not just the diagnosis.
```

### Review

**Review my changes**
```
Review my recent changes. Be direct. I want to know:
1. Bugs or edge cases I missed
2. Security issues (injection, auth gaps, data leaks)
3. Anything that doesn't fit the existing patterns
4. Missing test coverage
If it's fine, say so. Don't invent problems.
```

**Is this safe to deploy**
```
I'm about to deploy these changes. Check for:
- Breaking changes to public APIs or DB schemas
- Missing migrations or feature flags
- Config changes that need to happen before/after deploy
- Anything that could fail silently in production
Give me a go/no-go.
```

**Security audit** *(placeholder: `{target}`)*
```
Security audit of {target}:
- Injection (SQL, command, path traversal, XSS)
- Auth/authz gaps
- Sensitive data in logs or error messages
- Insecure defaults
Severity + fix for each finding. Skip theoretical risks — focus on what's actually exploitable.
```

### Write

**Implement this** *(placeholder: `{description}`)*
```
Implement: {description}

Before coding:
1. Which files need to change?
2. What's the approach and why?
3. Any trade-offs I should know about?

Then write the code. Follow existing patterns in this codebase. No new dependencies unless truly necessary.
```

**Write tests for this** *(placeholder: `{target}`)*
```
Write tests for {target}. Cover:
- Happy path
- Edge cases and boundaries
- Error/failure paths
- Any invariants that must always hold

Use the same framework and style as the existing tests in this project.
```

**Add a REST endpoint** *(placeholders: `{method}`, `{path}`, `{description}`)*
```
Add a {method} endpoint at {path} that {description}.

Follow the existing endpoint patterns in this codebase for:
- Request/response types
- Validation
- Error handling
- Authentication
Include the test.
```

**Write a database migration** *(placeholder: `{description}`)*
```
Write a migration to {description}.

Follow the existing migration style in this project. Include:
- The up migration
- The down/rollback migration
- Any data backfill if needed
Flag anything that could lock tables or be slow on large datasets.
```

**Refactor this** *(placeholder: `{target}`)*
```
Refactor {target}. Don't change behavior. Don't add abstractions that aren't needed yet. Follow existing conventions. Explain each change briefly.
```

**Convert to** *(placeholders: `{target}`, `{from}`, `{to}`)*
```
Convert {target} from {from} to {to}.

Handle semantic differences — don't just transliterate syntax. Show me the most complex part first so I can validate the approach before you do the rest.
```

### Git

**Write commit message**
```
Write a commit message for my staged changes.
Format: type(scope): subject

Types: feat, fix, refactor, test, chore, docs, perf, ci
Subject: imperative mood, under 72 chars, no period.
Body: explain why, not what. The diff shows what.
```

**Write PR description**
```
Write a PR description for my changes:

## Summary
What changed and why — bullet points, non-obvious context only.

## How to test
Steps a reviewer can follow to verify.

## Risks
Anything that might break, and how to roll back.
```

**Explain this diff**
```
Explain this diff to me. What's the intent behind these changes? Is anything suspicious or incomplete?
```

### DevOps

**Write a Dockerfile**
```
Write a Dockerfile for this project. It should:
- Use a minimal base image appropriate for the language/framework
- Leverage layer caching (dependencies before source)
- Run as non-root
- Work in CI and locally
If there's already a Dockerfile, improve it instead of starting over.
```

**Write a GitHub Action** *(placeholder: `{description}`)*
```
Create a GitHub Actions workflow that {description}.

Requirements:
- Pin action versions with SHA hashes
- Use minimal permissions
- Cache dependencies where possible
- Fail fast with clear error messages
```

**Debug CI failure** *(placeholder: `{error}`)*
```
This CI job is failing:

{error}

Is this a code issue, a config issue, or an environment issue? What's the fix? If it's flaky, explain why and how to make it deterministic.
```

---

## Built-in Command Templates (38 total)

### Git (12)

| Name | Command |
|---|---|
| Status | `git status -sb` |
| Log (graph) | `git log --oneline --graph --decorate -20` |
| Log (last N) | `git log --oneline -n {count}` |
| Diff staged | `git diff --staged` |
| Diff from branch | `git diff {base}...HEAD --stat` |
| New branch | `git checkout -b {branch}` |
| Stash | `git stash push -m "{message}"` |
| Stash pop | `git stash pop` |
| Interactive rebase | `git rebase -i HEAD~{n}` |
| Blame | `git blame {file}` |
| Find commit by text | `git log --all -S "{text}" --oneline` |
| Clean untracked | `git clean -nd` |

### Maven (4)

| Name | Command |
|---|---|
| Maven verify | `mvn verify -T 4` |
| Maven package (skip tests) | `mvn -q -DskipTests package` |
| Maven clean | `mvn clean` |
| Maven dependency tree | `mvn dependency:tree` |

### Gradle (3)

| Name | Command |
|---|---|
| Gradle build | `./gradlew build` |
| Gradle test | `./gradlew test` |
| Gradle dependencies | `./gradlew dependencies --configuration {config}` |

### npm (4)

| Name | Command |
|---|---|
| npm install | `npm install` |
| npm run build | `npm run build` |
| npm test | `npm test` |
| npm outdated | `npm outdated` |

### Docker (5)

| Name | Command |
|---|---|
| Docker build | `docker build -t {image}:{tag} .` |
| Docker run (interactive) | `docker run --rm -it {image} {cmd}` |
| Docker compose up | `docker compose up -d` |
| Docker compose logs | `docker compose logs -f {service}` |
| Docker ps | `docker ps` |

### Search (4)

| Name | Command |
|---|---|
| Find in files | `rg -n "{pattern}" {path}` |
| Find TODOs | `rg -n "TODO\|FIXME\|HACK\|XXX"` |
| Find file by name | `find . -name "{name}" -not -path "*/.*" -not -path "*/target/*" -not -path "*/node_modules/*"` |
| Large files | `find . -type f -not -path "*/.*" \| xargs du -sh 2>/dev/null \| sort -rh \| head -20` |

### Process / Network (6)

| Name | Command |
|---|---|
| Who is on port | `lsof -i :{port}` |
| Who is on port (Windows) | `netstat -ano \| findstr :{port}` |
| Kill port (Unix) | `lsof -ti :{port} \| xargs kill -9` |
| Java processes | `jps -l` |
| Tail log | `tail -f {logfile}` |
| Disk usage | `du -sh * \| sort -rh \| head -20` |

---

## Customising Templates

### Editing

Select any template. Its fields load into the form. Edit and click **Save**. Built-in and custom templates are stored together in `config.promptLibrary` / `config.commandLibrary`.

### Creating

Click **+ New**. Fill in Name, Category, and Body (Description is available in the full library dialog). Click **Save**. The template is assigned a UUID and appended to the library.

### Deleting

Select a template and click **Delete**. A confirmation dialog appears. Built-in templates can be deleted — they are not restored on app update unless you reset your config.

### Import / Export

Templates are part of the application config. Use **File → Export Config** / **Import Config** to back up or transfer the full library.

---

## AI CLI Detection

Needlecast checks 17 known CLIs on your `PATH` using `which` (Unix) or `where` (Windows) with a 3-second timeout per CLI:

| CLI | Command | Description |
|---|---|---|
| Claude Code | `claude` | Anthropic Claude Code |
| GitHub Copilot CLI | `copilot` | GitHub Copilot CLI |
| Gemini CLI | `gemini` | Google Gemini CLI |
| Aider | `aider` | AI pair programming in your terminal |
| OpenAI Codex | `codex` | OpenAI Codex CLI |
| OpenCode | `opencode` | OpenCode AI coding assistant |
| JetBrains Junie | `junie` | JetBrains Junie AI assistant |
| Kilocode | `kilocode` | Kilocode AI coding assistant |
| Amazon Q Developer | `q` | Amazon Q Developer CLI |
| Goose (Block) | `goose` | Block's AI developer agent |
| Plandex | `plandex` | AI coding engine for complex tasks |
| Amp | `amp` | Amp AI coding assistant |
| Sourcegraph Cody | `cody` | Sourcegraph Cody CLI |
| GPT Engineer | `gpt-engineer` | GPT Engineer |
| Mentat | `mentat` | Mentat AI coding assistant |
| Continue | `continue` | Continue dev CLI |
| APM | `apm` | Microsoft Agent Package Manager |

Found CLIs appear bold in **AI Tools → {name}** and can be launched directly in the terminal. Click **AI Tools → ↻ Rescan** to re-detect after installing a new CLI.

---

## Related

- [[Terminal]] — where prompts and commands are pasted
- [[Settings#Tab 2 — AI Tools|Settings → AI Tools]] — enabling and disabling individual CLIs
