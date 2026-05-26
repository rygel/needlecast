package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.PromptTemplate

internal fun defaultPromptLibrary() = listOf(

    PromptTemplate(
        name = "Onboard me to this repo",
        category = "Explore",
        description = "Quick orientation for an unfamiliar codebase.",
        body = """Give me a 2-minute developer onboarding:
1. What does this project do and who uses it?
2. Architecture overview — modules, layers, how data flows.
3. Where are the entry points (main, routes, handlers)?
4. What conventions or gotchas would trip up a new contributor?
Name actual files. Skip obvious things.""",
    ),
    PromptTemplate(
        name = "What does this file do",
        category = "Explore",
        description = "Explain a file's purpose and how it fits into the project.",
        body = "Explain {file} — what it owns, who calls it, what it depends on, and anything non-obvious about the implementation.",
    ),
    PromptTemplate(
        name = "How does this feature work",
        category = "Explore",
        description = "Trace a feature from user action to final effect.",
        body = "Trace {feature} end-to-end. Start at the trigger (click, request, event) and follow every step to the final side-effect. Name the actual classes and methods.",
    ),
    PromptTemplate(
        name = "What changed recently",
        category = "Explore",
        description = "Summarize recent git activity.",
        body = "Summarize the last 20 commits. Group by area (feature, fix, refactor). Flag anything that looks risky or half-finished.",
    ),

    PromptTemplate(
        name = "Fix this error",
        category = "Fix",
        description = "Diagnose and fix an error or exception.",
        body = """I'm hitting this error:

{error}

Find the root cause in this codebase and fix it. Show me the exact change. If you need more context, tell me which file to look at instead of guessing.""",
    ),
    PromptTemplate(
        name = "Fix this build failure",
        category = "Fix",
        description = "Diagnose a build or CI failure.",
        body = """The build is failing with:

{error}

Diagnose the issue — is it a dependency problem, a config issue, a code error, or an environment difference? Give me the fix, not just the explanation.""",
    ),
    PromptTemplate(
        name = "Why is this slow",
        category = "Fix",
        description = "Profile and fix a performance issue.",
        body = """{target} is too slow. Look at the implementation and tell me:
1. Where is time actually being spent?
2. What's the fix?
Don't suggest generic optimizations. Point at the specific bottleneck in this code.""",
    ),
    PromptTemplate(
        name = "Fix the flaky test",
        category = "Fix",
        description = "Diagnose a test that passes sometimes and fails sometimes.",
        body = """{test} is flaky — it passes locally but fails in CI (or vice versa). Look at it and find:
- Timing dependencies or race conditions
- Shared state between tests
- Environment assumptions (paths, ports, locale)
Give me the fix, not just the diagnosis.""",
    ),

    PromptTemplate(
        name = "Review my changes",
        category = "Review",
        description = "Code review of staged or recent changes.",
        body = """Review my recent changes. Be direct. I want to know:
1. Bugs or edge cases I missed
2. Security issues (injection, auth gaps, data leaks)
3. Anything that doesn't fit the existing patterns
4. Missing test coverage
If it's fine, say so. Don't invent problems.""",
    ),
    PromptTemplate(
        name = "Is this safe to deploy",
        category = "Review",
        description = "Pre-deployment sanity check.",
        body = """I'm about to deploy these changes. Check for:
- Breaking changes to public APIs or DB schemas
- Missing migrations or feature flags
- Config changes that need to happen before/after deploy
- Anything that could fail silently in production
Give me a go/no-go.""",
    ),
    PromptTemplate(
        name = "Security audit",
        category = "Review",
        description = "Check for security vulnerabilities.",
        body = """Security audit of {target}:
- Injection (SQL, command, path traversal, XSS)
- Auth/authz gaps
- Sensitive data in logs or error messages
- Insecure defaults
Severity + fix for each finding. Skip theoretical risks — focus on what's actually exploitable.""",
    ),

    PromptTemplate(
        name = "Implement this",
        category = "Write",
        description = "Build a feature following existing patterns.",
        body = """Implement: {description}

Before coding:
1. Which files need to change?
2. What's the approach and why?
3. Any trade-offs I should know about?

Then write the code. Follow existing patterns in this codebase. No new dependencies unless truly necessary.""",
    ),
    PromptTemplate(
        name = "Write tests for this",
        category = "Write",
        description = "Generate tests for a class or function.",
        body = """Write tests for {target}. Cover:
- Happy path
- Edge cases and boundaries
- Error/failure paths
- Any invariants that must always hold

Use the same framework and style as the existing tests in this project.""",
    ),
    PromptTemplate(
        name = "Add a REST endpoint",
        category = "Write",
        description = "Create a new API endpoint following existing patterns.",
        body = """Add a {method} endpoint at {path} that {description}.

Follow the existing endpoint patterns in this codebase for:
- Request/response types
- Validation
- Error handling
- Authentication
Include the test.""",
    ),
    PromptTemplate(
        name = "Write a database migration",
        category = "Write",
        description = "Create a schema migration.",
        body = """Write a migration to {description}.

Follow the existing migration style in this project. Include:
- The up migration
- The down/rollback migration
- Any data backfill if needed
Flag anything that could lock tables or be slow on large datasets.""",
    ),
    PromptTemplate(
        name = "Refactor this",
        category = "Write",
        description = "Improve code without changing behavior.",
        body = """Refactor {target}. Don't change behavior. Don't add abstractions that aren't needed yet. Follow existing conventions. Explain each change briefly.""",
    ),
    PromptTemplate(
        name = "Convert to",
        category = "Write",
        description = "Convert code from one language, framework, or pattern to another.",
        body = """Convert {target} from {from} to {to}.

Handle semantic differences — don't just transliterate syntax. Show me the most complex part first so I can validate the approach before you do the rest.""",
    ),

    PromptTemplate(
        name = "Write commit message",
        category = "Git",
        description = "Conventional commit for staged changes.",
        body = """Write a commit message for my staged changes.
Format: type(scope): subject

Types: feat, fix, refactor, test, chore, docs, perf, ci
Subject: imperative mood, under 72 chars, no period.
Body: explain why, not what. The diff shows what.""",
    ),
    PromptTemplate(
        name = "Write PR description",
        category = "Git",
        description = "Pull request summary from recent commits.",
        body = """Write a PR description for my changes:

## Summary
What changed and why — bullet points, non-obvious context only.

## How to test
Steps a reviewer can follow to verify.

## Risks
Anything that might break, and how to roll back.""",
    ),
    PromptTemplate(
        name = "Explain this diff",
        category = "Git",
        description = "Explain what a diff or set of changes does.",
        body = "Explain this diff to me. What's the intent behind these changes? Is anything suspicious or incomplete?",
    ),

    PromptTemplate(
        name = "Write a Dockerfile",
        category = "DevOps",
        description = "Create or improve a Dockerfile for this project.",
        body = """Write a Dockerfile for this project. It should:
- Use a minimal base image appropriate for the language/framework
- Leverage layer caching (dependencies before source)
- Run as non-root
- Work in CI and locally
If there's already a Dockerfile, improve it instead of starting over.""",
    ),
    PromptTemplate(
        name = "Write a GitHub Action",
        category = "DevOps",
        description = "Create or fix a CI/CD workflow.",
        body = """Create a GitHub Actions workflow that {description}.

Requirements:
- Pin action versions with SHA hashes
- Use minimal permissions
- Cache dependencies where possible
- Fail fast with clear error messages""",
    ),
    PromptTemplate(
        name = "Debug CI failure",
        category = "DevOps",
        description = "Diagnose why a CI pipeline is failing.",
        body = """This CI job is failing:

{error}

Is this a code issue, a config issue, or an environment issue? What's the fix? If it's flaky, explain why and how to make it deterministic.""",
    ),
)

internal fun defaultCommandLibrary() = listOf(

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
