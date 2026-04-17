# Terminal

## Overview

Needlecast includes a full **PTY-based terminal** powered by JediTerm (the same widget used in IntelliJ IDEA), backed by `pty4j`. The shell receives a true pseudo-terminal — interactive programs, readline history, full-screen TUIs, and ANSI color codes all work correctly.

---

## Opening the Terminal

- Press `Ctrl+T` to open or focus the terminal.
- The terminal opens in the **active project's directory**.
- **Double-click** a project in the tree to activate its terminal directly.
- When no session is active, a placeholder card is shown — right-click it to open the [[#Shell Picker Popup]].

---

## Multiple Terminal Tabs

Each project maintains its own `JTabbedPane`. Tabs are independent shell processes.

| Action | Result |
|---|---|
| Click **+** in terminal toolbar | New tab named "Terminal N" |
| Click a tab | Switch to that session |
| Click ✕ on a tab | Close that session |
| Last tab | Close button disabled — one tab always stays |

Font size and colors update **all tabs simultaneously** when you zoom or change colors.

**Status aggregation:** The [[Project Management#Agent Status LED|agent LED]] shows the *worst* state across all tabs — THINKING if any tab is THINKING, WAITING if any is WAITING, otherwise NONE.

---

## Shell Selection

### Global Default

**[[Settings]] → Layout & Terminal → Default shell**. Choices:
- OS default (blank)
- Auto-detected shells (populated by ShellDetector)
- Manual entry (type any shell path)

Takes effect on the **next** terminal activation.

### Per-Project Override

**Right-click project → Shell Settings…** — overrides the global default for that project only. See [[Project Management#Shell Settings]].

### Shell Picker Popup

Right-clicking the "no session" placeholder shows a picker with, in order:

1. **AI CLI tools** found on `PATH` (clicking activates the terminal with that CLI as the startup command)
2. **Project's custom shell** (if configured in Shell Settings), shown **bold**
3. **System shells**, OS-grouped:

| Platform | Shells (in priority order) |
|---|---|
| **Windows** | PowerShell 7+ (pwsh), Windows PowerShell 5 (powershell), Git Bash (bash), WSL (wsl), cmd.exe |
| **macOS** | /bin/zsh, /opt/homebrew/bin/zsh, /usr/local/bin/zsh, /bin/bash, /opt/homebrew/bin/bash, fish, ksh, tcsh, sh |
| **Linux** | From `/etc/shells` + known fallbacks (bash, zsh, fish, sh, ksh, tcsh, csh) |

Detection uses `which`/`where` + `File.canExecute()` checks.

---

## Per-Project Configuration

Set via **right-click → Shell Settings…** and **right-click → Environment…**.

### Startup Command

Sent to the shell's stdin immediately after it opens:

```bash
conda activate ml-project
nvm use 20
source .venv/bin/activate
export $(cat .env | xargs)
```

### Environment Variables

Key/value pairs merged on top of the system environment before the shell process starts. See [[Project Management#Environment Variables]].

---

## PTY Lifecycle

| Event | Detail |
|---|---|
| Shell startup | `PtyProcessBuilder`; PTY size 120 × 30; `TERM=xterm-256color` |
| Shell exits normally | Session marked inactive; agent LED → NONE |
| Forcibly cancelled | `process.destroyForcibly()` + reader thread interrupt; exit code reported as −1 |

---

## Platform-Specific Default Fonts

| Platform | Priority |
|---|---|
| **Windows** | Cascadia Mono → Cascadia Code → Consolas → Lucida Console |
| **macOS** | Menlo → Monaco → Courier New |
| **Linux** | JetBrains Mono → DejaVu Sans Mono → Liberation Mono → Noto Mono |
| Fallback | `Font.MONOSPACED` |

Override in **[[Settings]] → Terminal font family**.

---

## Font Zoom

`Ctrl+Scroll` adjusts the terminal font size **8–36 pt**. Change applies to all open tabs simultaneously and is persisted immediately to config.

---

## Colors

Priority (highest wins):

1. **Manual hex overrides** set in [[Settings]] (`terminalForeground` / `terminalBackground`)
2. **FlatLaf theme colors** from `UIManager.getColor("TextArea.background")` and `TextArea.foreground`
3. **Hardcoded fallbacks** — dark: `#D4D4D4`/`#1E1E1E`; light: `#1E1E1E`/`#FFFFFF`

ANSI palettes follow **VS Code Dark** / **VS Code Light** depending on the active theme.

> [!note]
> JediTerm caches style state on session creation. Existing tabs update via reflection on JediTerm internals after a color change; new tabs always pick up the latest settings.

---

## Clipboard / Paste

`Ctrl+V` is intercepted by a global `KeyEventDispatcher` before JediTerm processes it. The clipboard string is sent to the PTY as UTF-8 bytes with platform line endings (CRLF on Windows, LF elsewhere).

---

## AI CLI Status Detection

### Non-Claude sessions — heuristic detection

Needlecast monitors terminal output for the **braille spinner** characters:

```
⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏
```

- Spinner detected → status **THINKING**
- 2-second silence after last output → status **WAITING**
- Output within 100 ms of user input is ignored (echo suppression)

### Claude Code sessions — hook-based detection

When the startup command or user input contains `claude` or `claude.exe`, Needlecast switches to hook-based detection and disables the heuristics.

**Claude Code Hook Server** runs on `localhost:17312`:

| Endpoint | Status |
|---|---|
| `POST /hook/claude/start` | → THINKING |
| `POST /hook/claude/stop` | → WAITING |
| `POST /hook/claude/idle` | → WAITING |

When **Claude Code hooks** is enabled in [[Settings]], Needlecast merges these entries into `~/.claude/settings.json`:

```json
{
  "hooks": {
    "UserPromptSubmit": [{
      "matcher": "",
      "hooks": [{"type": "command",
        "command": "curl -s -X POST 'http://localhost:17312/hook/claude/start' -H 'Content-Type: application/json' -d @-"}]
    }],
    "Stop": [{
      "matcher": "",
      "hooks": [{"type": "command",
        "command": "curl -s -X POST 'http://localhost:17312/hook/claude/stop' -H 'Content-Type: application/json' -d @-"}]
    }],
    "Notification": [{
      "matcher": "idle_prompt",
      "hooks": [{"type": "command",
        "command": "curl -s -X POST 'http://localhost:17312/hook/claude/idle' -H 'Content-Type: application/json' -d @-"}]
    }]
  }
}
```

On Windows, `curl.exe` is used with double-quoted arguments. Installation is idempotent — existing entries are checked before adding. Hooks are removed from `~/.claude/settings.json` when hooks are disabled or the app exits.

---

## Input Method Support

Text composed via OS input methods (IME for CJK, voice-to-text) is forwarded to the PTY via an `InputMethodListener`.

---

## Related

- [[Project Management#Per-Project Settings|Per-project shell, startup command, env vars]]
- [[Settings]] — global default shell, terminal font and colors
- [[Prompt Library]] — sending AI prompts into the terminal
