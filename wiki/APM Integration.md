# APM Integration

## Overview

Needlecast integrates with **APM** (Agent Package Manager by Microsoft), a tool that manages AI agent dependencies — skills, prompts, plugins, and MCP servers — via an `apm.yml` manifest file.

---

## Project Detection

If a project root contains `apm.yml`, the [[Build Tool Detection|APM scanner]] automatically adds four commands to the Commands panel:

| Command | Description |
|---|---|
| `apm install` | Install all agent dependencies listed in `apm.yml` |
| `apm audit` | Audit packages for security issues |
| `apm update` | Update packages to their latest compatible versions |
| `apm bundle` | Bundle the agent for distribution |

On Windows these are wrapped as `cmd /c apm <subcommand>`.

---

## APM Settings Tab

**[[Settings]] → APM** shows installation status and install helpers.

| Element | Description |
|---|---|
| **Status** | ✓ `v{version}` (green) if `apm` is on `PATH`, ✗ "Not found on PATH" (red) otherwise |
| **Install buttons** | OS-appropriate methods (see below) |
| **Recheck** button | Re-runs PATH detection |
| **Output pane** | Live streaming output from install commands |
| **Info text** | Describes APM's purpose |

### Installation Methods

| Method | Command |
|---|---|
| **curl** (Unix/Linux) | `curl -sSL https://aka.ms/apm-unix \| sh` |
| **PowerShell** (Windows) | `irm https://aka.ms/apm-windows \| iex` |
| **Homebrew** (macOS/Linux) | `brew install microsoft/apm/apm` |
| **pip** (all platforms) | `pip install apm-cli` |
| **Scoop** (Windows) | `scoop install apm` |

---

## What Is `apm.yml`?

`apm.yml` is APM's manifest format. It describes the AI agent's dependencies — the skills, prompt templates, plugins, and MCP (Model Context Protocol) servers it relies on. APM resolves and installs these on behalf of the agent.

This is analogous to `package.json` for npm or `pom.xml` for Maven, but for AI agent components.

---

## Related

- [[Build Tool Detection]] — how the APM scanner fits into the scanner pipeline
- [[Prompt Library#AI CLI Detection|AI CLI Detection]] — APM also appears as a detected CLI tool (`apm` command)
- [[Settings#Tab 4 — APM|Settings → APM tab]]
