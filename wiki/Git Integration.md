# Git Integration

## Overview

Needlecast provides lightweight, read-only git visibility: branch and dirty-state badges in the Project Tree, and a commit log viewer with full diff display. All write operations (commit, branch, merge, push) are done in the [[Terminal]].

---

## Branch Status in the Project Tree

Every project row inside a git repository shows:

| Element | Format | Color |
|---|---|---|
| Branch name | `⎇ branch-name` | Gray (#888888) — normal |
| Branch name (dirty) | `⎇ branch-name*` | Amber (#E6A817) — uncommitted changes |

The asterisk is appended when `git status` reports uncommitted changes (modified, staged, or untracked files).

Git status is fetched **asynchronously** after each project scan and cached. Hovering the badge shows a tooltip with the full branch name.

---

## Git Log Panel

The **Git Log** tab (right column) shows the commit history for the active project.

### Commit List

Loads the last **40 commits** using:

```
git log --oneline --no-decorate -40
```

Each row shows:
- Abbreviated commit hash (monospace, gray)
- Commit subject line

### Commit Detail

Click any row to view the full commit using:

```
git show --stat -p {hash}
```

Output appears in the lower pane (60 % of the panel height). This includes:
- Author, date, full commit message
- File stats (lines added/removed per file)
- Full unified diff

**Large diffs:** Text is rendered in chunks via TextChunker to avoid freezing the UI. Diffs exceeding **400,000 characters** are truncated with the message `[Diff truncated: omitted N characters]`.

**Diff formatting:** Plain monospace text (Font.MONOSPACED, 11 pt). No syntax highlighting. No click-to-open for files shown in the diff.

**Timeout:** Git commands time out after 10 seconds.

### Limitations

| Feature | Status |
|---|---|
| Copy commit hash | Not available (no context menu) |
| Click file in diff to open | Not available |
| Checkout / cherry-pick / reset | Not available — use the Terminal |
| Search within log | Not available |
| Branch switching | Not available |

### Refreshing

Click the **↻** refresh button at the top of the panel, or switch projects (the log reloads automatically on project switch).

---

## Using Git in the Terminal

For all write and interactive git operations, use the [[Terminal]]. The terminal opens directly in the active project's directory, so `git status`, `git commit`, `git push`, etc. work immediately.

> [!tip]
> The [[Prompt Library]] has a **Git** category with ready-to-paste prompts: "Write commit message", "Write PR description", "Explain this diff", and more.

---

## Related

- [[Project Management]] — branch/dirty badges in the Project Tree
- [[Terminal]] — interactive git operations
- [[Prompt Library#Git Prompts|Git prompts]] — AI-assisted commit messages and PR descriptions
