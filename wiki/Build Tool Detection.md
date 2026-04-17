# Build Tool Detection

## Overview

When you add a project or switch to one, Needlecast runs a **CompositeProjectScanner** that chains all 17 build-tool scanners in sequence. Each scanner checks for its marker files, parses them for extra context, and contributes `CommandDescriptor` objects to the final list shown in the Commands panel.

The [[#Build File Watcher]] re-triggers the scan whenever a relevant file changes on disk. Press `F5` or click the ↻ button in the Project Tree toolbar to rescan manually.

---

## Maven (`pom.xml`)

Extracts the standard Maven lifecycle goals plus plugin-specific goals:

| Command | Description |
|---|---|
| `mvn compile` | Compile source code |
| `mvn test` | Run unit tests |
| `mvn package` | Package to JAR/WAR |
| `mvn install` | Install to local repository |
| `mvn verify` | Run integration tests |
| `mvn clean` | Delete build output |
| `mvn clean install` | Full clean build |

**Plugin detection** adds extra commands:

| Plugin | Extra commands |
|---|---|
| `spring-boot-maven-plugin` | `mvn spring-boot:run` |
| Quarkus | `mvn quarkus:dev`, `mvn quarkus:build` |
| `javafx-maven-plugin` | `mvn javafx:run` |
| `exec-maven-plugin` | `mvn exec:java` |

**Multi-module projects:** Submodule paths are used (e.g. `mvn -pl my-module test`).

---

## Gradle (`build.gradle` / `build.gradle.kts`)

| Command | Description |
|---|---|
| `./gradlew build` | Full build |
| `./gradlew test` | Run tests |
| `./gradlew clean` | Delete build output |
| `./gradlew run` | Run (if `application` plugin detected) |
| `./gradlew check` | All verification tasks |
| `./gradlew assemble` | Assemble without running tests |
| `./gradlew bootRun` | Spring Boot run (if detected) |

**Gradle subprojects** (`settings.gradle` / `settings.gradle.kts`): Task names include the subproject prefix, e.g. `./gradlew :api:test`.

---

## npm (`package.json`)

All `"scripts"` entries are extracted. Common lifecycle names (`start`, `build`, `test`, `lint`) are ordered first; the rest follow alphabetically. `npm install` is always added as a fallback.

---

## Python

| Detected by | Tool | Example commands |
|---|---|---|
| `uv.lock` | uv | `uv sync`, `uv run`, `uv test` |
| `poetry.lock` | Poetry | `poetry install`, `poetry run`, `poetry build` |
| `requirements.txt` | pip | `pip install -r requirements.txt` |
| `pyproject.toml` | pyproject | `python -m build` |

---

## Rust (`Cargo.toml`)

| Command | Description |
|---|---|
| `cargo build` | Compile |
| `cargo test` | Run tests |
| `cargo run` | Build and run |
| `cargo check` | Fast type check without linking |
| `cargo clippy` | Lint |
| `cargo fmt` | Format source |
| `cargo clean` | Delete build artifacts |

**Workspace members**: Per-crate commands added (e.g. `cargo build -p mycrate`).

---

## Go (`go.mod`)

| Command | Description |
|---|---|
| `go build ./...` | Build all packages |
| `go test ./...` | Test all packages |
| `go vet ./...` | Static analysis |
| `go fmt ./...` | Format source |
| `go run .` | Run (when `main.go` detected) |
| `go mod tidy` | Prune unused dependencies |

**`cmd/` subdirectories**: Detected and each gets its own `go build` command.

---

## .NET (`*.sln` / `*.csproj` / `*.fsproj` / `*.vbproj`)

Solution files are parsed to enumerate all projects. Commands include `dotnet build`, `dotnet test`, `dotnet run`, `dotnet publish`.

Project type detection:
- `Microsoft.NET.Sdk.Web` → `dotnet run --launch-profile`
- Worker Service → `dotnet run`
- Class library → `dotnet build`, `dotnet pack`

---

## PHP — Composer (`composer.json`)

Scripts from `composer.json` are extracted. Laravel artisan detection adds `php artisan serve`, `php artisan migrate`, `php artisan queue:work`.

---

## Ruby (`Gemfile`)

`bundle install`, `bundle exec rake` (if `Rakefile` exists), `bundle exec rspec`. Rails detection adds `rails server`, `rails console`, `rails db:migrate`.

---

## Swift (`Package.swift`)

`swift build`, `swift test`, `swift run`.

---

## Dart / Flutter (`pubspec.yaml`)

**Flutter** (when `flutter` appears in dependencies): `flutter run`, `flutter build apk`, `flutter build ios`, `flutter test`, `flutter pub get`.

**Plain Dart**: `dart run`, `dart test`, `dart pub get`.

---

## CMake (`CMakeLists.txt`)

`cmake -B build`, `cmake --build build`, `ctest --test-dir build`.

---

## Make (`Makefile`)

Top-level `.PHONY` targets extracted and presented as runnable commands.

---

## Scala — sbt (`build.sbt`)

`sbt compile`, `sbt test`, `sbt run`, `sbt clean`, `sbt assembly`.

---

## Elixir — Mix (`mix.exs`)

`mix compile`, `mix test`, `mix deps.get`. Phoenix detection adds `mix phx.server`. Ecto detection adds `mix ecto.migrate`, `mix ecto.setup`.

---

## Zig (`build.zig`)

`zig build`, `zig build test`, `zig build run`.

---

## APM (`apm.yml`)

Detected when an `apm.yml` file is present in the project root. Generates four commands:

| Command | Description |
|---|---|
| `apm install` | Install agent dependencies |
| `apm audit` | Audit agent package security |
| `apm update` | Update agent packages |
| `apm bundle` | Bundle the agent |

On Windows the commands are wrapped: `cmd /c apm <subcommand>`.

---

## IntelliJ Run Configurations (`.idea/runConfigurations/*.xml`)

Parsed XML run configurations appear as named commands. Marked as "not directly executable" (grayed out) if the IntelliJ CLI is not on `PATH`.

---

## Windows Behavior

On Windows, all shell-invoked commands are prefixed with `cmd /c` to work correctly (e.g. `cmd /c mvn test`, `cmd /c ./gradlew build`).

---

## Build File Watcher

Uses Java NIO `WatchService` to monitor `ENTRY_CREATE` and `ENTRY_MODIFY` events. The exact set of monitored filenames:

```
pom.xml           build.gradle       build.gradle.kts
package.json      apm.yml            pyproject.toml
requirements.txt  Cargo.toml         go.mod
composer.json     Gemfile            Package.swift
pubspec.yaml      CMakeLists.txt     Makefile
build.sbt         mix.exs            build.zig
*.sln             *.csproj
```

A **500 ms debounce** per directory prevents multiple rescans on rapid saves. Changes invoke the `onChanged(dirPath)` callback on a daemon thread, which triggers a full rescan of that project.

---

## Build Tool Badges

Each detected tool appears as a colored pill badge in the project row in the [[Project Management|Project Tree]]. If the scanner throws an error, a red ⚠ fail badge replaces the normal badges.

---

## Related

- [[Command Execution]] — running, queuing, and re-running detected commands
- [[Project Management#Environment Variables|Per-project environment variables]] — injected into every command run
- [[APM Integration]] — details on the Agent Package Manager scanner
