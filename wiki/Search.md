# Search

## Overview

The **Search** panel provides fast full-text search across all files in the active project. It uses **ripgrep** when available and falls back to a built-in Java file walker. Results show the file path, line, column, and a preview snippet; double-clicking opens the file in the [[Code Editor]] at the exact location.

---

## Opening Search

- `Ctrl+Shift+F` — global shortcut
- Click the **Search** tab in the right column
- Click the 🔍 icon in the menu bar
- **View → Search** menu item

---

## Running a Search

1. Type a query in the search field.
2. Configure options (see below).
3. Press `Enter` or click **Search**.
4. Click **Stop** to cancel a running search.

---

## Search Options

| Option | Label | Description |
|---|---|---|
| Case sensitive | "Match case" | Case-sensitive matching |
| Whole word | "Whole word" | Match only at word boundaries (`\b`) |
| Regex | "Regex" | Treat query as a regular expression (disables Whole word) |
| Include globs | "Include:" | Only search files matching these patterns |
| Exclude globs | "Exclude:" | Skip files matching these patterns |
| File size limit | "Limit MB" checkbox + field | Skip files larger than this many MB |

**Glob separators:** commas (`,`) or semicolons (`;`) — both are treated the same. Prefix a pattern with `!` to negate it in the Exclude field.

---

## Ripgrep Integration

When `rg` is on `PATH`, Needlecast builds this command:

```
rg --vimgrep --no-messages [--fixed-strings] [-s|-i] [-w]
   [--max-filesize NM]
   [-g INCLUDE_GLOB]...
   [-g !EXCLUDE_GLOB]...
   QUERY
   .
```

- `--fixed-strings` is added when Regex is **off**
- `-s` = case sensitive, `-i` = case insensitive
- `-w` = whole word

### Installing ripgrep

| OS | Command |
|---|---|
| macOS | `brew install ripgrep` |
| Windows | `scoop install ripgrep` or `winget install BurntSushi.ripgrep.MSVC` |
| Linux | `apt install ripgrep` / `pacman -S ripgrep` |

---

## Built-in Fallback Searcher

When ripgrep is not available, Needlecast uses a Java `Files.walkFileTree()` searcher:
- Reads files with the same UTF-8 → native → ISO-8859-1 charset fallback as the editor
- Detects binary files by scanning the first 4 KB for null bytes (0x00) and skips them
- Applies the same include/exclude glob logic

---

## Results Table

| Column | Content |
|---|---|
| File | Path relative to project root |
| Line | Line number of the match |
| Column | Column of the first matching character |
| Preview | The matching line |

Results are sortable by clicking column headers.

**Double-click** or press `Enter` on a selected result to open the file in the [[Code Editor]] at the exact line and column.

**Result limit:** 10,000 results. When the limit is reached, the status shows `results capped at 10,000`.

---

## Status / Summary Line

After a search completes, the panel shows:

```
N match(es) in M file(s) (0.XX s, X large skipped, X binary skipped, X ignored)
```

Count labels are shown only when their value is greater than zero.

Other status messages:

| Message | Meaning |
|---|---|
| `Select a project to search.` | No active project |
| `Project path is not a directory.` | Project root does not exist |
| `Enter a search query.` | Empty search field |
| `Invalid include pattern.` | Bad glob in Include field |
| `Searching (rg)…` / `Searching…` | In progress |
| `Search cancelled.` | User clicked Stop |

---

## Automatically Excluded Paths

These directories are always skipped regardless of glob settings:

```
.git  .hg  .svn  .idea  .gradle  .mvn  .cache
node_modules  target  build  dist  out  vendor
```

Dotfiles and hidden files are also excluded.

---

## Automatically Excluded File Extensions

```
class  jar  war  ear  zip  gz  bz2  7z
png  jpg  jpeg  gif  bmp  ico  webp  tif  tiff  svg
pdf  mp3  mp4  mov  avi  mkv
exe  dll  so  dylib  bin  dat
ttf  otf  woff  woff2
pyc  pyo
```

---

## Related

- [[Code Editor]] — where results open
- [[Project Management]] — search scope is always the active project
