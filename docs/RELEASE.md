# Release Workflow

This document explains how releases are produced in this repository and which workflows are involved.

## Overview

Releases are built entirely by GitHub Actions. A release consists of:

- Installers for Windows, macOS, and Linux
- Portable ZIPs for Windows and macOS
- Portable TAR.GZ for Linux
- Fat JAR plus SHA-256 checksum
- `appcast.xml` for Sparkle update checks

## Release-Related Workflows

- `Release: Open PR (develop → main)` (`.github/workflows/release-pr.yml`)
  - Opens or updates a develop → main PR with a release checklist
- `Release: Auto Tag` (`.github/workflows/auto-release.yml`)
  - On push to `main` with a changed `pom.xml` version, creates a GitHub release tag (for example `v0.6.17`)
- `Release: Build & Upload` (`.github/workflows/release.yml`)
  - Builds all artifacts and uploads them to the GitHub release
  - Triggered when a release is published or when run manually

## Normal Release Flow (Recommended)

1. Update the version in `pom.xml`.
2. Merge to `main`.
3. `Release: Auto Tag` creates a published GitHub release tag.
4. `Release: Build & Upload` runs and uploads all assets.

This path is fully automated once `pom.xml` is updated.

## Manual Release Flow (One-Click)

You can trigger a release manually from the Actions tab:

1. Open Actions → `Release: Build & Upload`.
2. Click **Run workflow**.
3. Optional: provide a version (for example `0.6.17`).
   - If empty, the workflow reads the version from `pom.xml`.
4. The workflow creates the release tag if missing and uploads all artifacts.

Use this for re-running a failed release or backfilling missing assets.

## Expected Artifacts

- `needlecast-<version>-win64.exe`
- `needlecast-<version>-windows-portable.zip`
- `needlecast-<version>-macos.dmg`
- `needlecast-<version>-macos-portable.zip`
- `needlecast-<version>-linux-amd64.deb`
- `needlecast-<version>-linux-portable.tar.gz`
- `needlecast-<version>.jar`
- `needlecast-<version>.jar.sha256`
- `appcast.xml`

## Troubleshooting

**Release exists but has no assets**

- The release tag was created, but `Release: Build & Upload` did not run.
- Fix: manually run `Release: Build & Upload` for that version.

**Assets exist but appcast is missing**

- The `update-appcast` job failed or did not run.
- Re-run `Release: Build & Upload` (it regenerates and uploads `appcast.xml`).

## Best Practices

- Ensure these workflows pass before releasing:
  - `CI`
  - `UI Tests`
  - `Native Check`
- Prefer the normal flow (version bump → merge → auto tag → build & upload).
- Use the manual flow only for recovery or backfill.
