
## Build & Test Commands

- **Compile:** `mvn -pl needlecast-desktop compile`
- **Run:** `mvn -pl needlecast-desktop compile exec:java`
- **Unit tests (host-safe):** `mvn -pl needlecast-desktop test -T 4`
- **UI tests (container only):** `podman build -f Dockerfile.uitest -t needlecast-uitest . && podman run --rm needlecast-uitest`
- **Full verify:** `mvn -pl needlecast-desktop verify -T 4`
- **Fat JAR:** `mvn -pl needlecast-desktop -am package -DskipTests`

## Code Style Conventions

- **Language:** Kotlin 2.2, targeting JVM 21. No Java source files.
- **Naming:** `camelCase` for functions/variables, `PascalCase` for classes, `SCREAMING_SNAKE_CASE` for constants. File names match the primary class.
- **Imports:** Use full qualified names for disambiguation. Avoid star imports.
- **No comments in code.** The codebase does not use inline comments or KDoc unless explicitly requested.
- **Null safety:** Use `?.`, `?:`, and `!!` appropriately. Prefer `val` over `var`. Use `lateinit` for Swing components initialized in `init` blocks.
- **Kotlin init order matters:** Properties are initialized in declaration order. If a `val tree` initializer references `scanResults`, `gitStatusCache`, etc., those MUST be declared before `tree`. Violating this causes `Unresolved reference` at runtime.
- **Recursive type inference:** Inside a `JTree().apply {}` block, references like `TransferHandler.MOVE` can cause Kotlin's recursive type inference error. Move such code to an `init {}` block instead.

## UI Conventions

- **Panels extend `DockablePanel`** and are registered in `PanelRegistry`. Docking layout managed by `DockingController`.
- **Inter-panel communication** goes through `PanelCoordinator` — panels do not hold direct references to each other.
- **Settings callbacks** are coordinated by `PanelCoordinator` as factory methods.
- **Swing threading:** All UI mutations must run on the EDT. Use `SwingUtilities.invokeLater {}` for off-EDT → EDT transitions. Never block the EDT with I/O.
- **Theme-aware colors:** Use `ThemeRegistry` for consistent theming. Do not hardcode colors — use theme properties.

## Testing Conventions

- **Unit tests:** JUnit 5 + MockK + AssertJ. Located in `src/test/kotlin/` mirroring the source package structure.
- **UI tests:** AssertJ Swing, named `*UiTest.kt` or `*E2ETest.kt`. Excluded from default `mvn test` by surefire config.
- **UI tests run in containers ONLY** — never on the host machine. They capture mouse and keyboard.
- **Container for UI tests:** `Dockerfile.uitest` uses `maven:3.9-eclipse-temurin-21-jammy` (Ubuntu). Alpine's Xvfb does not work with AssertJ Swing.
- **Maven profile:** `-Ptest-desktop` enables UI tests and adds `--add-opens` JVM args for Swing module access.

## Error Handling

- **SLF4J + Logback** for all logging. Never use `println` or `System.err.println`.
- **Configure both console and file appenders** in `logback.xml`. Log files go in the output directory or `logs/`.
- **Log all external API calls** at INFO level: URL, request body, HTTP status, response body (truncated).
- **Log errors at ERROR** with full context: HTTP status, response body, exception message, request IDs.
- **Never swallow exceptions** silently. At minimum, log at DEBUG level.

## Git & PR Conventions

- **Branch naming:** `feat/`, `fix/`, `refactor/`, `chore/`, `ci/`, `docs/`
- **PRs target `develop`.** Release PRs go `develop → main`.
- **Commit messages:** Conventional commits (`feat:`, `fix:`, `refactor:`, `chore:`, `ci:`, `docs:`, `test:`).
- **Never push directly to `develop` or `main`** — always via PR.
- **Dependencies:** Renovate configured via `renovate.json`. Manual bumps via `renovate-local.sh`.

## Dogfood Logging

After each `release`, append a dogfood entry capturing observations from the cycle.
Call the adapter method with structured entries for each observation:

- **friction** — workflow pain points, confusing flows, things that broke or slowed you down
- **methodology** — what worked or didn't in the plan/build/review cycle
- **signal** — indicators of product-market fit, user value, or growth potential
- **commercial** — cost, pricing, or business model observations

This is autonomous plumbing — log observations after release without asking.
