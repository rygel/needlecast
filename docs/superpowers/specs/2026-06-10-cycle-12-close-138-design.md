# Cycle 12: Close Out #138 — Design Spec

**Date:** 2026-06-10
**Cycle:** 12
**Goal:** Close out issue #138 once and for all

---

## 1. Update Issue #138 Checklist

Mark all items as done:
- Build system tags: confirmed not a bug — pipeline correct at every stage, integration tests cover Maven+npm monorepo
- Enable editing existing commands: done in C11 (edit via right-click + reset to default)
- Explorer open in Finder/Explorer: done in C11 (context menu entries for dirs and files)
- CI screenshot script: deferred (see below)

Close the issue with a summary comment.

## 2. Tag Verification Test Enhancement

The existing `CompositeProjectScannerIntegrationTest` already covers the Maven+npm case. Add a test for a **three-tool** project (e.g. Maven + npm + Gradle) to increase confidence, and add an explicit assertion on the badge count.

**File:** `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScannerIntegrationTest.kt`

Add test: `"merges build tools for project with three build systems"` — create a temp dir with `pom.xml`, `package.json`, and `build.gradle`, verify all three build tools appear.

## 3. CI Screenshot Script (Deferred)

Per AGENTS.md rules, UI tests must run inside Podman/container environments. The screenshot script requires:
- A headless display (Xvfb or similar)
- Needlecast launched in a container with test data
- Screenshot capture via AWT Robot or similar

This is a non-trivial infrastructure task that deserves its own cycle. Defer to cycle 13+.

---

## Files

- `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScannerIntegrationTest.kt`

## Out of Scope

- CI screenshot automation (deferred)
- Architecture changes
- New features
