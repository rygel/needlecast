# Dependency Updates

## Overview

The **Renovate** panel scans the active project for outdated dependencies using [Renovate](https://renovateapp.com/) in local mode, presents available updates in a table, and applies selected ones directly to source files — no GitHub token or CI pipeline required.

---

## Prerequisites

Renovate must be installed. If it is not, the panel shows `✗ Not installed (install via Settings)` and the **Scan for Updates** button is disabled.

Install it from **[[Settings]] → Renovate**, or manually:

| Method | Command |
|---|---|
| npm | `npm add -g renovate` |
| pnpm | `pnpm add -g renovate` |
| Homebrew (macOS/Linux) | `brew install renovate` |
| Scoop (Windows) | `scoop install renovate` |
| Chocolatey (Windows) | `choco install renovate` |

When installed, the panel shows `✓ v{version}`.

---

## Scanning for Updates

Click **Scan for Updates**. Needlecast runs:

```
renovate --platform=local --report-type=file --report-path="<project>/target/renovate-report.json"
```

The command runs with `LOG_LEVEL=info` (or `debug` when the Verbose toggle is on), in the project's root directory.

Renovate reads all supported dependency files, queries registries for newer versions, and writes a JSON report. Needlecast parses that report and populates the table.

---

## Results Table

| Column | Width | Content |
|---|---|---|
| ✓ | 30 px | Checkbox — select/deselect this update |
| **Manager** | 100 px | Build tool (maven, npm, gradle, docker, etc.) |
| **Dependency** | 280 px | Package or image name |
| **Current** | 110 px | Version string currently in your file |
| **Available** | 110 px | Newest version Renovate found |
| **Type** | 70 px | Update type (colored) |

### Update Type Colors

| Type | Color | Meaning |
|---|---|---|
| `patch` | Green | Bug fixes, generally safe |
| `minor` | Orange | New features, backwards-compatible |
| `major` | Red | Potentially breaking changes |
| `pinDigest` | Blue | Docker image digest pin |
| Other | Gray | Any other type |

All updates are **selected by default** when the scan completes.

---

## Selecting Updates

| Button | Selects |
|---|---|
| **All** | Every row |
| **None** | No rows |
| **Patch only** | Rows where `updateType == "patch"` |

You can also manually check or uncheck individual rows.

---

## Applying Updates

Click **Apply Selected**.

Before applying any **major**-type updates, a confirmation dialog lists the affected packages and version changes.

### How Files Are Modified

Needlecast modifies the source files in-place using these strategies:

**Maven shared properties**
If a version is defined via a shared property (e.g. `<jackson.version>2.14.0</jackson.version>`), the property value is updated — not each individual `<version>` tag. Multiple dependencies sharing the same property are updated together in one replacement.

**Dockerfile image tags**
Uses Renovate's `replaceString` and `autoReplaceTemplate` fields. If the update includes a digest pin, the template substitutes `{{depName}}`, `{{newValue}}`, and `{{newDigest}}` into the new image reference.

**Direct version replacements**
For all other managers: the old version string is located using Renovate's `fileReplacePosition` and replaced with the new value.

> [!warning]
> There is no rollback mechanism. Updates are applied directly to your files. Commit your changes before scanning if you want an easy recovery point.

---

## Logs Toggle

Click **Show Logs** to reveal the raw Renovate CLI output. Click **Verbose** to increase `LOG_LEVEL` to `debug` for the next scan.

---

## Supported File Types

Anything Renovate supports — including but not limited to:
- `pom.xml` (Maven)
- `build.gradle`, `versions.toml` (Gradle)
- `package.json` (npm/yarn/pnpm)
- `Dockerfile`, `docker-compose.yml`
- `requirements.txt`, `pyproject.toml`, `poetry.lock` (Python)
- `go.mod` (Go)
- `Cargo.toml` (Rust)
- `Gemfile` (Ruby)
- 20+ additional formats

---

## Related

- [[Build Tool Detection]] — the dependency file formats Needlecast understands
- [[Command Execution]] — run your build after applying updates to verify
- [[Settings#Renovate|Settings → Renovate]] — installing and checking Renovate status
