# Script Directory Scanner — Design Spec

## Goal

Allow users to run shell and language scripts stored in their project repositories as
first-class Needlecast commands. Auto-detect the two most common script directories
(`scripts/`, `bin/`) and let users add any extra directories on a per-project basis.

---

## Architecture

Three files change; one new file is created:

| File | Change |
|------|--------|
| `model/CommandDescriptor.kt` | Add `BuildTool.SCRIPT` enum value |
| `model/AppConfig.kt` | Add `extraScanDirs: List<String>` to `ProjectDirectory` |
| `scanner/ScriptDirectoryScanner.kt` | **New** — detects scripts and creates commands |
| `scanner/CompositeProjectScanner.kt` | Add `ScriptDirectoryScanner` to scanner list |
| `ui/ProjectTreePanel.kt` | Add "Script Directories…" context menu item + dialog |

---

## Fix 1 — `BuildTool.SCRIPT`

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/CommandDescriptor.kt`

Add one entry to the `BuildTool` enum after `ZIG`:

```kotlin
SCRIPT("Script", "script", "#5D7B6F"),
```

The muted green distinguishes script commands visually from build-system tools.

---

## Fix 2 — `extraScanDirs` on `ProjectDirectory`

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/model/AppConfig.kt`

Add one field with a default to `ProjectDirectory`:

```kotlin
data class ProjectDirectory(
    val path: String,
    val displayName: String? = null,
    val color: String? = null,
    val env: Map<String, String> = emptyMap(),
    val shellExecutable: String? = null,
    val startupCommand: String? = null,
    val extraScanDirs: List<String> = emptyList(),   // NEW
)
```

Paths are stored relative to the project root when the chosen directory is a
subdirectory of it (e.g. `tools/`), and absolute otherwise. The default is an empty
list, so existing serialised configs deserialise without migration.

---

## Fix 3 — `ScriptDirectoryScanner`

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScanner.kt`

### Scanned directories

The scanner builds a deduplicated list of directories to inspect:

1. `<projectRoot>/scripts/` — included if it exists
2. `<projectRoot>/bin/` — included if it exists
3. Each entry in `directory.extraScanDirs` — resolved relative to the project root
   when the path is not absolute, then included if the resolved directory exists

### File selection

For each candidate directory, the scanner lists its **direct children only** (no
recursion). A file is included when its extension appears in the interpreter table
below. Files with unrecognised extensions are silently skipped.

### Interpreter table

| Extension(s) | Command prefix |
|---|---|
| `.sh`, `.bash` | `bash` |
| `.zsh` | `zsh` |
| `.fish` | `fish` |
| `.py` | `python3` |
| `.rb` | `ruby` |
| `.js` | `node` |
| `.ts` | `npx`, `ts-node` |
| `.pl` | `perl` |
| `.php` | `php` |
| `.ps1` | `pwsh` |

### `CommandDescriptor` fields

For a script at `<projectRoot>/scripts/deploy.sh`:

```kotlin
CommandDescriptor(
    label           = "scripts/deploy.sh",           // relative to project root
    buildTool       = BuildTool.SCRIPT,
    argv            = listOf("bash", "/abs/.../scripts/deploy.sh"),
    workingDirectory = directory.path,               // project root
    env             = emptyMap(),
)
```

The label is always the path relative to the project root.  
The argv uses the absolute path so the working-directory override does not affect
script resolution.

### Return value

Returns `null` when no matching files are found in any candidate directory
(consistent with every other `ProjectScanner`). When at least one file matches,
returns a `DetectedProject` with `buildTools = setOf(BuildTool.SCRIPT)` and the
full command list.

### Adding to `CompositeProjectScanner`

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/scanner/CompositeProjectScanner.kt`

Append `ScriptDirectoryScanner()` to the existing `scanners` list. The existing
per-scanner try-catch already isolates any failures.

---

## Fix 4 — UI: "Script Directories…" context menu item

**File:** `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/ProjectTreePanel.kt`

### Context menu

Add one item to the project context menu, after "Environment…":

```kotlin
menu.add(JMenuItem("Script Directories\u2026").apply {
    addActionListener { editScriptDirs(node, entry) }
})
```

### `editScriptDirs()` dialog

The dialog contains:
- A `JList<String>` (scrollable, 6 visible rows) populated with
  `dir.extraScanDirs`
- **Add** button — opens a `JFileChooser` in `DIRECTORIES_ONLY` mode, defaulting
  to `dir.path`; on confirmation the chosen path is made relative to `dir.path`
  when it is a subdirectory of it, otherwise stored as absolute; duplicates are
  silently ignored
- **Remove** button — removes the selected list entry; disabled when nothing is
  selected

The auto-detected directories (`scripts/`, `bin/`) are **not** shown — they are
always scanned automatically and do not require configuration.

Hint text below the list:
```
"scripts/" and "bin/" in the project root are always scanned automatically.
```

On OK: persist the updated `extraScanDirs` to the tree model and config, then
trigger a rescan so the command list refreshes immediately.

```kotlin
private fun editScriptDirs(node: DefaultMutableTreeNode, entry: ProjectTreeEntry.Project) {
    val owner = SwingUtilities.getWindowAncestor(this) ?: return
    val dir   = entry.directory
    val listModel = DefaultListModel<String>().apply { dir.extraScanDirs.forEach { addElement(it) } }
    val list  = JList(listModel).apply { selectionMode = ListSelectionModel.SINGLE_SELECTION }
    val addBtn    = JButton("Add\u2026")
    val removeBtn = JButton("Remove").apply { isEnabled = false }

    list.addListSelectionListener { removeBtn.isEnabled = list.selectedIndex >= 0 }
    addBtn.addActionListener {
        val chooser = JFileChooser(dir.path).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return@addActionListener
        val chosen = chooser.selectedFile.canonicalPath
        val stored = makeRelativeIfPossible(chosen, dir.path)
        if ((0 until listModel.size).none { listModel.getElementAt(it) == stored }) listModel.addElement(stored)
    }
    removeBtn.addActionListener {
        val i = list.selectedIndex; if (i >= 0) listModel.remove(i)
    }

    val form = JPanel(BorderLayout(4, 4)).apply {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        add(JScrollPane(list), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(addBtn); add(removeBtn) }, BorderLayout.SOUTH)
        add(JLabel("<html><small>\"scripts/\" and \"bin/\" in the project root are always scanned automatically.</small></html>"),
            BorderLayout.NORTH)
    }

    if (JOptionPane.showConfirmDialog(owner, form, "Script Directories \u2014 ${dir.label()}",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return

    val newDirs = (0 until listModel.size).map { listModel.getElementAt(it) }
    node.userObject = entry.copy(directory = dir.copy(extraScanDirs = newDirs))
    treeModel.nodeChanged(node)
    persist()
    scanProject(entry.directory.copy(extraScanDirs = newDirs))  // rescan immediately
}

/** Returns [absolute] as a path relative to [base] when it is a subdirectory, otherwise returns [absolute]. */
private fun makeRelativeIfPossible(absolute: String, base: String): String {
    val rel = File(base).toPath().relativize(File(absolute).toPath()).toString()
    return if (rel.startsWith("..")) absolute else rel
}
```

---

## Tests

**New file:** `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/scanner/ScriptDirectoryScannerTest.kt`

| Test | Verifies |
|------|----------|
| `auto-detects scripts dir` | A `scripts/deploy.sh` in the project root produces one `SCRIPT` command labelled `scripts/deploy.sh` with `["bash", absPath]` argv |
| `auto-detects bin dir` | A `bin/run` with no recognised extension is skipped; a `bin/start.py` produces `["python3", absPath]` |
| `extraScanDirs relative path` | A relative entry `tools/` resolves against project root and its scripts appear |
| `extraScanDirs absolute path` | An absolute entry outside the project root is accepted |
| `unrecognised extension skipped` | A `scripts/README.md` does not produce a command |
| `deduplicates dirs` | If `extraScanDirs` contains `scripts/` as well, its scripts appear only once |
| `returns null when empty` | No `scripts/`, `bin/`, no `extraScanDirs` → returns `null` |
| `ts extension produces npx argv` | `scripts/build.ts` → argv `["npx", "ts-node", absPath]` |
