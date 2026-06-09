# Auto-Update Pipeline Investigation

> Date: 2026-06-09
> Status: Investigation complete — findings pending owner approval
> Related: AD-5 (Fat JAR Distribution with Auto-Update), task-40

## Executive Summary

The auto-update pipeline is completely broken end-to-end. Users running v0.8.0-beta.2 cannot discover or install updates through the app. Three P0 root causes and two secondary issues were identified. The user has directed that the fix should use a manual-only release model (`workflow_dispatch`) — no automatic release scheduling.

## Architecture Overview

The update pipeline has three layers:

1. **CI/CD layer** — `auto-release.yml` watches for pom.xml version changes on `main`, creates a GitHub Release tag. `release.yml` builds native packages and generates `appcast.xml` on release creation.
2. **Distribution layer** — `appcast.xml` is hosted at `https://github.com/rygel/needlecast/releases/latest/download/appcast.xml`. Sparkle4j polls this URL.
3. **In-app layer** — `MainWindow.kt` runs a 15-minute periodic `updateTimer` (initial delay 30s). Each tick calls `checkForUpdates()` → `buildSparkle4j(0).checkNow()`. `currentVersion()` reads `/version.properties` (populated at build time from pom.xml).

## P0 Root Causes

### P0-1: PAPI releases create git tags but not GitHub Releases

PAPI's `release` tool creates lightweight git tags (e.g., `v0.8.2-beta.1`) but does NOT create GitHub Releases. The `auto-release.yml` workflow is the only mechanism that creates GitHub Releases, and it only triggers when pom.xml changes on `main`. PAPI tags pushed to `main` don't trigger `auto-release.yml` because the workflow watches for pom.xml file changes, not tag pushes.

**Impact:** No GitHub Release → `release.yml` never triggers → no native builds → no `appcast.xml` update → Sparkle4j sees nothing new.

**Evidence:** GitHub releases page shows no releases for v0.8.0-beta.2 through v0.8.3. The last release is v0.8.2-beta.1.

### P0-2: pom.xml stuck at 0.8.0-beta.2

The pom.xml `<version>` has never been bumped from `0.8.0-beta.2`. Since `auto-release.yml` only triggers when pom.xml changes on `main`, and the version hasn't changed, the auto-release workflow has never fired for any post-beta.2 release.

**Impact:** Even if PAPI tags were creating GitHub Releases, the pipeline wouldn't trigger because the version detection step (`Detect version change`) compares current vs. previous pom.xml versions and finds them identical.

**Evidence:** `pom.xml` line 1: `<version>0.8.0-beta.2</version>` as of 2026-06-09.

### P0-3: appcast.xml stale at v0.8.2-beta.1

The `appcast.xml` on GitHub (at `/releases/latest/download/appcast.xml`) still advertises v0.8.2-beta.1. Since no GitHub Release has been created since then, the `update-appcast` job in `release.yml` hasn't run.

**Impact:** Even if the in-app update timer fires correctly, Sparkle4j would compare current version (0.8.0-beta.2) against the appcast (0.8.2-beta.1), find an available update, but the download links in the appcast point to v0.8.2-beta.1 assets — not the latest develop build.

## Secondary Issues

### P1: Update timer may never fire (unconfirmed)

`currentVersion()` reads `/version.properties` which is populated from pom.xml at build time. If the property contains `${project.version}` (unresolved Maven filter), the function returns `null` due to the `!it.contains("\${")` guard. When `currentVersion()` returns null, `buildSparkle4j()` returns null and the timer tick becomes a no-op.

**Status:** Unconfirmed — the v0.8.0-beta.2 builds may have this property correctly resolved. The `version.properties` filter is configured in `pom.xml`. Need to verify by checking the actual built JAR's `version.properties` or logs. If the timer IS firing but failing, the error would appear in `needlecast.log` as `updateLogger.warn` entries. Zero log entries would suggest the timer isn't firing at all.

**Investigation needed:** Check `needlecast.log` for any entries containing "Periodic update check", "Building sparkle4j instance", or "Cannot determine app version".

### P2: Network failures are invisible to the user

When update checks fail due to network issues (HttpConnectTimeoutException, ConnectException, SSL handshake errors), the error is logged via `logUpdateCheckFailure()` but the user sees nothing. There is no feedback mechanism for persistent failures. Users behind corporate proxies or with intermittent connectivity have no way to know that updates are failing silently.

## Fix Recommendations

### Recommended Fix (User-Approved)

The user has directed that auto-releases should be **manual-only** via `workflow_dispatch`:

1. **Convert `auto-release.yml` to manual trigger** — Remove the auto-trigger on pom.xml push to main. Add `workflow_dispatch` with a `version` input parameter. The workflow should: accept target version → bump pom.xml `<version>` to that version → commit to main → create git tag → create GitHub Release → `release.yml` triggers for native builds + appcast.xml.

2. **Fix update timer startup** — Verify that `currentVersion()` returns a valid version string in the built JAR. If it returns null, the timer fires but does nothing. Check the Maven resource filtering configuration in `pom.xml` for `version.properties`.

3. **Add user-visible failure feedback** — When update checks fail 3+ consecutive times, show a notification in the status bar or a non-modal toast. This gives users visibility into network/proxy issues.

### Not Recommended

- Switching to GitHub Releases API polling (vs. appcast.xml) — the Sparkle4j + appcast architecture is sound when the pipeline works.
- Automatic release scheduling — user explicitly wants manual control.

## User Direction

> User wants manual-only GitHub Release trigger. `auto-release.yml` should be converted to `workflow_dispatch` with version input. PAPI releases = git tags only, user decides when to create GitHub Release with native builds + appcast.xml.

## Key Files

| File | Role |
|------|------|
| `needlecast-desktop/src/main/kotlin/.../ui/MainWindow.kt` | Update timer, `checkForUpdates()`, `currentVersion()`, `buildSparkle4j()` |
| `.github/workflows/auto-release.yml` | Auto-trigger on pom.xml push to main — needs conversion to manual |
| `.github/workflows/release.yml` | Builds native packages + generates appcast.xml on GitHub Release creation |
| `pom.xml` | Version stuck at `0.8.0-beta.2` |
| `needlecast-desktop/src/main/resources/version.properties` | Build-time version property, read by `currentVersion()` |

## Security Considerations

The existing Sparkle4j signature verification (`classifyUpdateError()`, `allowUnsignedUpdates()`) is the security boundary for auto-update downloads. The manual `workflow_dispatch` trigger requires GitHub repo write access (maintainer-only), which acts as an access-control gate on what gets published. No changes to the download/signature verification model are proposed.
