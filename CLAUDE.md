
<!-- PAPI_ENRICHMENT_TIER_1 -->

## Batch Building (unlocked at cycle 6)

For cycles with multiple tasks, batch build them without stopping between each:
- Build XS/S tasks first, then M/L — same-module tasks land on the cycle branch automatically regardless of size
- One commit per task for traceable history on the shared cycle branch
- After all tasks built, batch review them together

### Gestalt Pre-Build Check (multi-task cycles)

**Before `build_execute` on the first task of any multi-task cycle, read the cycle as a whole:**

1. Run `build_list` to see every task assigned to the current cycle.
2. Read the BUILD HANDOFFs together — not one at a time. Look for:
   - **Shared files** across handoffs — the same path in two FILES LIKELY TOUCHED lists usually means a refactor opportunity, a shared helper to extract first, or a sequencing constraint.
   - **Shared modules** — multiple tasks in the same module should land on a shared cycle branch (`feat/cycle-N-<module>`) so they merge together.
   - **Design decisions implicit across tasks** — e.g. one task introduces a new field, a later task consumes it. Build the producer first.
   - **Module split** — tasks in different modules will land on different cycle branches (`feat/cycle-N-<module>`) and merge separately. Flag any cross-module coordination required before kicking off.
3. Only then run `build_execute` on the first task.

This is a one-time check at the start of the cycle, not per-task. It catches scope conflicts, redundant work, and ordering hazards that an isolated handoff read can't see. Skip it for single-task cycles.

## Strategy Reviews

Every 5 cycles, PAPI offers a strategy review — a deep analysis of velocity, estimation accuracy, active decisions, and project direction.

- **Don't skip them.** They're where compounding value comes from.
- Strategy reviews run in their own session — don't mix with building.
- Reviews produce recommendations that feed into the next plan.
- If the review recommends AD changes, use `strategy_change` to apply them.

## Active Decision Lifecycle

Active Decisions (ADs) track architectural and product choices with confidence levels (LOW → MEDIUM → HIGH).

- Check ADs before making architectural choices — run `health` for the AD summary.
- ADs are for product/architecture choices only, not process preferences.
- When new evidence appears, update AD confidence via `strategy_change`.
- Supersede rather than overwrite — old decisions stay as history.
- New ADs should include a `### Reversal Trigger` section: specify the signal that would invalidate the stance, the action to take (modify/supersede/abandon), and why writing it now prevents sunk-cost drift later.

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
