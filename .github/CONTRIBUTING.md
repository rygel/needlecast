# Contributing

## Prerequisites

- JDK 21+ (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Git

## Build and test

```bash
mvn -pl desktop -am verify -T 4
```

The test suite runs in under two minutes. If it takes longer, something is wrong — investigate before continuing.

### Test limits

Maven Surefire is configured with `forkedProcessTimeoutInSeconds=300` and JUnit with `junit.jupiter.execution.timeout.default=120s`. Tests that hang will be killed and flagged as failures.

### Desktop / Swing tests

Tests that manipulate the display (mouse, keyboard capture) must only run inside a container with Xvfb:

```bash
mvn -pl desktop verify -Ptest-desktop
```

Do not run the `test-desktop` profile locally — it will capture your mouse and keyboard.

### Playwright tests

If you add browser-based tests, configure `PLAYWRIGHT_BROWSERS_PATH=target/playwright-browsers` so `mvn clean` removes the browser binaries. Do not let them accumulate on disk.

## Branch and PR workflow

- Never push directly to `main` or `develop`.
- Branch names: `feat/`, `fix/`, `refactor/`, `chore/`, `ci/` prefix.
- Open a PR targeting `develop`. `main` is updated via release only.
- CI must pass before merge.

## Code style

- Kotlin — follow standard Kotlin idioms; no wildcard imports.
- No `@Suppress` annotations to hide lint warnings — fix the underlying issue.
- No docstrings or comments on code that is self-explanatory.
- Add comments only where the logic is non-obvious.

## Adding a project scanner

1. Create `scanner/MyToolProjectScanner.kt` implementing `ProjectScanner`.
2. Register it in `CompositeProjectScanner`.
3. Add a `BuildTool` enum entry with `displayName`, `tagLabel`, and `tagColor`.
4. Write fixture-based tests in `src/test/kotlin/.../scanner/`.

## Config schema changes

1. Increment `ConfigMigrator.CURRENT_VERSION`.
2. Add a migration step in the `migrations` list.
3. Add a test in `ConfigMigratorTest`.

## Commit messages

Use present tense, imperative mood: `Add APM scanner`, `Fix EDT blocking in AI Tools menu`. One sentence is enough for most changes; add a body paragraph only when the motivation is not obvious from the diff.
