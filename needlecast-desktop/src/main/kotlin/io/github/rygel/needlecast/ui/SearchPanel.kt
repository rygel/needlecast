package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.process.ProcessExecutor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.MalformedInputException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.PathMatcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker
import javax.swing.UIManager

/**
 * Phase 1 "Find in Files" panel: fast, non-indexed search across the active project.
 */
class SearchPanel(
    private val openFileAt: (file: File, line: Int, column: Int?) -> Unit,
) : JPanel(BorderLayout()) {

    private val queryField = JTextField(22)
    private val caseToggle = JCheckBox("Match case").apply { isSelected = false }
    private val wordToggle = JCheckBox("Whole word").apply { isSelected = false }
    private val regexToggle = JCheckBox("Regex").apply { isSelected = false }
    private val searchButton = JButton("Search")
    private val stopButton = JButton("Stop").apply { isEnabled = false }
    private val includeField = JTextField(18)
    private val excludeField = JTextField(18)
    private val sizeLimitToggle = JCheckBox("Limit MB").apply { isSelected = true }
    private val sizeLimitField = JTextField(DEFAULT_MAX_MB.toString(), 4)
    private val scopeLabel = JLabel("No project")
    private val statusLabel = JLabel(" ")

    private val resultsModel = DefaultListModel<SearchResult>()
    private val resultsList = JList(resultsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 44
        cellRenderer = ResultCellRenderer { currentRoot }
    }

    private var currentRoot: File? = null
    private var worker: SwingWorker<SearchStats, SearchResult>? = null
    @Volatile private var rgAvailable: Boolean? = null
    @Volatile private var rgProcess: Process? = null

    init {
        minimumSize = Dimension(0, 0)

        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel("Find:"))
            add(queryField)
            add(searchButton)
            add(stopButton)
            add(JLabel("  In:"))
            add(scopeLabel)
        }

        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(caseToggle)
            add(wordToggle)
            add(regexToggle)
            add(JLabel("  Include:"))
            add(includeField)
            add(JLabel("Exclude:"))
            add(excludeField)
            add(sizeLimitToggle)
            add(sizeLimitField)
        }

        val toolbar = JPanel(BorderLayout()).apply {
            add(row1, BorderLayout.NORTH)
            add(row2, BorderLayout.SOUTH)
        }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(resultsList).apply { minimumSize = Dimension(0, 0) }, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        searchButton.addActionListener { startSearch() }
        stopButton.addActionListener { stopSearch(showStatus = true) }
        queryField.addActionListener { startSearch() }
        queryField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    queryField.text = ""
                    resultsModel.clear()
                    statusLabel.text = " "
                }
            }
        })
        sizeLimitToggle.addActionListener { sizeLimitField.isEnabled = sizeLimitToggle.isSelected }
        sizeLimitField.isEnabled = sizeLimitToggle.isSelected

        resultsList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) openSelectedResult()
            }
        })
        resultsList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) openSelectedResult()
            }
        })

        Thread({
            rgAvailable = ProcessExecutor.isOnPath("rg")
        }, "rg-detector").apply { isDaemon = true; start() }
    }

    fun loadProject(path: String?) {
        stopSearch()
        currentRoot = path?.let { File(it) }
        resultsModel.clear()
        scopeLabel.text = path?.let { File(it).name } ?: "No project"
        scopeLabel.toolTipText = path ?: ""
        statusLabel.text = " "
    }

    fun requestFocusOnSearch() {
        queryField.requestFocusInWindow()
        queryField.selectAll()
    }

    private fun openSelectedResult() {
        val result = resultsList.selectedValue ?: return
        openFileAt(result.file, result.line, result.column)
    }

    private fun startSearch() {
        val root = currentRoot ?: run {
            statusLabel.text = "Select a project to search."
            return
        }
        if (!root.isDirectory) {
            statusLabel.text = "Project path is not a directory."
            return
        }
        val query = queryField.text.trim()
        if (query.isEmpty()) {
            statusLabel.text = "Enter a search query."
            return
        }
        val includeMatchers = try { buildMatchers(includeField.text) } catch (e: IllegalArgumentException) {
            statusLabel.text = e.message ?: "Invalid include pattern."
            return
        }
        val excludeMatchers = try { buildMatchers(excludeField.text, stripNegation = true) } catch (e: IllegalArgumentException) {
            statusLabel.text = e.message ?: "Invalid exclude pattern."
            return
        }
        val limitMb = if (sizeLimitToggle.isSelected) sizeLimitField.text.trim().toIntOrNull() else null
        if (sizeLimitToggle.isSelected && (limitMb == null || limitMb <= 0)) {
            statusLabel.text = "Invalid size limit (MB)."
            return
        }
        val sizeLimitBytes = if (sizeLimitToggle.isSelected && limitMb != null)
            limitMb.toLong() * 1024L * 1024L else null

        stopSearch()
        resultsModel.clear()
        val useRg = rgAvailable == true
        statusLabel.text = if (useRg) "Searching (rg)\u2026" else "Searching\u2026"
        searchButton.isEnabled = false
        stopButton.isEnabled = true

        val opts = SearchOptions(
            query = query,
            caseSensitive = caseToggle.isSelected,
            wholeWord = wordToggle.isSelected,
            regex = regexToggle.isSelected,
            includeGlobs = parseGlobs(includeField.text),
            excludeGlobs = parseGlobs(excludeField.text),
            includeMatchers = includeMatchers,
            excludeMatchers = excludeMatchers,
            sizeLimitBytes = sizeLimitBytes,
            useRipgrep = useRg,
        )

        val activeWorker = if (useRg) {
            buildRipgrepWorker(root, opts)
        } else {
            val matcher = try {
                buildMatcher(query, caseToggle.isSelected, wordToggle.isSelected, regexToggle.isSelected)
            } catch (e: PatternSyntaxException) {
                searchButton.isEnabled = true
                stopButton.isEnabled = false
                statusLabel.text = "Invalid regex: ${e.description}"
                return
            }
            buildBuiltinWorker(root, opts, matcher)
        }
        worker = activeWorker
        activeWorker.execute()
    }

    private fun buildBuiltinWorker(
        root: File,
        opts: SearchOptions,
        matcher: (String) -> Int?,
    ): SwingWorker<SearchStats, SearchResult> = object : SwingWorker<SearchStats, SearchResult>() {
        override fun doInBackground(): SearchStats {
            val stats = SearchStats()
            val rootPath = root.toPath()
            val start = System.nanoTime()
            var stopRequested = false
            val shouldStop = { isCancelled || stopRequested }

            Files.walkFileTree(rootPath, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (shouldStop()) return FileVisitResult.TERMINATE
                    if (dir != rootPath && shouldSkipDir(dir.fileName?.toString())) {
                        stats.skippedDirs++
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if (dir != rootPath && isHidden(dir)) {
                        stats.skippedDirs++
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if (attrs.isSymbolicLink) return FileVisitResult.SKIP_SUBTREE
                    val relDir = try { rootPath.relativize(dir) } catch (_: Exception) { dir.fileName }
                    if (relDir != null && opts.excludeMatchers.isNotEmpty()) {
                        if (matchesAny(relDir, dir.fileName?.toString(), opts.excludeMatchers)) {
                            stats.skippedDirs++
                            return FileVisitResult.SKIP_SUBTREE
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (shouldStop()) return FileVisitResult.TERMINATE
                    if (attrs.isSymbolicLink || attrs.isDirectory) return FileVisitResult.CONTINUE
                    if (isHidden(file)) {
                        stats.skippedFiles++
                        return FileVisitResult.CONTINUE
                    }
                    val fileName = file.fileName?.toString() ?: ""
                    if (shouldSkipFile(fileName)) {
                        stats.skippedFiles++
                        return FileVisitResult.CONTINUE
                    }
                    val relPath = try { rootPath.relativize(file) } catch (_: Exception) { file.fileName ?: file }
                    if (opts.includeMatchers.isNotEmpty() && !matchesAny(relPath, fileName, opts.includeMatchers)) {
                        stats.skippedFiles++
                        return FileVisitResult.CONTINUE
                    }
                    if (opts.excludeMatchers.isNotEmpty() && matchesAny(relPath, fileName, opts.excludeMatchers)) {
                        stats.skippedFiles++
                        return FileVisitResult.CONTINUE
                    }
                    val size = attrs.size()
                    if (opts.sizeLimitBytes != null && size > opts.sizeLimitBytes) {
                        stats.skippedLarge++
                        return FileVisitResult.CONTINUE
                    }
                    if (isBinary(file)) {
                        stats.skippedBinary++
                        return FileVisitResult.CONTINUE
                    }
                    stats.filesScanned++

                    val rel = try { rootPath.relativize(file).toString() } catch (_: Exception) { file.toString() }
                    val matched = scanFile(file, matcher, shouldStop) { lineNumber, column, lineText ->
                        stats.matches++
                        publish(SearchResult(file.toFile(), rel, lineNumber, column, preview(lineText)))
                        if (stats.matches >= MAX_RESULTS) {
                            stats.truncated = true
                            stopRequested = true
                        }
                    }
                    if (matched) stats.filesWithMatches++
                    return if (stopRequested) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }
            })

            stats.durationMs = (System.nanoTime() - start) / 1_000_000
            return stats
        }

        override fun process(chunks: List<SearchResult>) {
            if (this != worker) return
            chunks.forEach { resultsModel.addElement(it) }
            statusLabel.text = "Searching\u2026 ${resultsModel.size} result${if (resultsModel.size == 1) "" else "s"}"
        }

        override fun done() {
            if (this != worker) return
            searchButton.isEnabled = true
            stopButton.isEnabled = false
            val stats = try { get() } catch (_: Exception) {
                if (isCancelled) statusLabel.text = "Search cancelled."
                else statusLabel.text = "Search failed."
                return
            }
            statusLabel.text = formatSummary(stats)
        }
    }

    private fun buildRipgrepWorker(root: File, opts: SearchOptions): SwingWorker<SearchStats, SearchResult> =
        object : SwingWorker<SearchStats, SearchResult>() {
            override fun doInBackground(): SearchStats {
                val stats = SearchStats()
                val start = System.nanoTime()
                val rootPath = root.toPath()
                val argv = buildRipgrepArgs(opts)
                val seenFiles = HashSet<String>()

                try {
                    val pb = ProcessBuilder(argv).directory(root).redirectErrorStream(true)
                    val proc = pb.start()
                    rgProcess = proc
                    proc.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (isCancelled) {
                                proc.destroyForcibly()
                                break
                            }
                            val parsed = parseRipgrepLine(line ?: "") ?: continue
                            stats.matches++
                            if (seenFiles.add(parsed.path)) stats.filesWithMatches++
                            val relPath = parsed.path
                            val file = File(root, parsed.path)
                            publish(SearchResult(file, relPath, parsed.line, parsed.column, preview(parsed.text)))
                            if (stats.matches >= MAX_RESULTS) {
                                stats.truncated = true
                                proc.destroyForcibly()
                                break
                            }
                        }
                    }
                    proc.waitFor()
                } catch (_: Exception) {
                    // ignore
                } finally {
                    rgProcess = null
                }

                stats.durationMs = (System.nanoTime() - start) / 1_000_000
                return stats
            }

            override fun process(chunks: List<SearchResult>) {
                if (this != worker) return
                chunks.forEach { resultsModel.addElement(it) }
                statusLabel.text = "Searching (rg)\u2026 ${resultsModel.size} result${if (resultsModel.size == 1) "" else "s"}"
            }

            override fun done() {
                if (this != worker) return
                searchButton.isEnabled = true
                stopButton.isEnabled = false
                val stats = try { get() } catch (_: Exception) {
                    if (isCancelled) statusLabel.text = "Search cancelled."
                    else statusLabel.text = "Search failed."
                    return
                }
                statusLabel.text = formatSummary(stats)
            }
        }

    private fun stopSearch(showStatus: Boolean = false) {
        worker?.cancel(true)
        rgProcess?.destroyForcibly()
        rgProcess = null
        worker = null
        searchButton.isEnabled = true
        stopButton.isEnabled = false
        if (showStatus) statusLabel.text = "Search cancelled."
    }

    private fun buildMatcher(
        query: String,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
    ): (String) -> Int? {
        if (regex || wholeWord) {
            val base = if (regex) query else Pattern.quote(query)
            val wrapped = if (wholeWord) "\\b$base\\b" else base
            val flags = if (caseSensitive) 0 else (Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            val pattern = Pattern.compile(wrapped, flags)
            return { line ->
                val m = pattern.matcher(line)
                if (m.find()) m.start() else null
            }
        }
        val needle = if (caseSensitive) query else query.lowercase()
        return { line ->
            val hay = if (caseSensitive) line else line.lowercase()
            val idx = hay.indexOf(needle)
            if (idx >= 0) idx else null
        }
    }

    private fun scanFile(
        file: Path,
        matcher: (String) -> Int?,
        shouldStop: () -> Boolean,
        onMatch: (lineNumber: Int, column: Int, lineText: String) -> Unit,
    ): Boolean {
        var matched = false
        for (charset in READ_CHARSETS) {
            try {
                Files.newBufferedReader(file, charset).use { reader ->
                    var lineNumber = 0
                    while (true) {
                        if (shouldStop()) return matched
                        val line = reader.readLine() ?: break
                        lineNumber++
                        val idx = matcher(line) ?: continue
                        matched = true
                        onMatch(lineNumber, idx + 1, line)
                        if (shouldStop()) return matched
                    }
                }
                return matched
            } catch (_: MalformedInputException) {
                continue
            } catch (_: CharacterCodingException) {
                continue
            } catch (_: Exception) {
                return matched
            }
        }
        return matched
    }

    private fun buildRipgrepArgs(opts: SearchOptions): List<String> {
        val argv = mutableListOf("rg", "--vimgrep", "--no-messages")
        if (!opts.regex) argv += "--fixed-strings"
        argv += if (opts.caseSensitive) "-s" else "-i"
        if (opts.wholeWord) argv += "-w"
        if (opts.sizeLimitBytes != null) {
            val mb = (opts.sizeLimitBytes / (1024L * 1024L)).coerceAtLeast(1)
            argv += listOf("--max-filesize", "${mb}M")
        }
        opts.includeGlobs.forEach { argv += listOf("-g", it) }
        opts.excludeGlobs.forEach { argv += listOf("-g", if (it.startsWith("!")) it else "!$it") }
        argv += opts.query
        argv += "."
        return argv
    }

    private fun parseRipgrepLine(line: String): RipgrepHit? {
        val last = line.lastIndexOf(':')
        if (last <= 0) return null
        val prev = line.lastIndexOf(':', last - 1)
        if (prev <= 0) return null
        val prev2 = line.lastIndexOf(':', prev - 1)
        if (prev2 <= 0) return null
        val path = line.substring(0, prev2)
        val lineNum = line.substring(prev2 + 1, prev).toIntOrNull() ?: return null
        val colNum = line.substring(prev + 1, last).toIntOrNull() ?: return null
        val text = line.substring(last + 1)
        return RipgrepHit(path, lineNum, colNum, text)
    }

    private fun preview(line: String): String {
        val trimmed = line.trim()
        return if (trimmed.length <= 240) trimmed else trimmed.take(237) + "..."
    }

    private fun isHidden(path: Path): Boolean = try {
        Files.isHidden(path)
    } catch (_: Exception) {
        false
    }

    private fun shouldSkipDir(name: String?): Boolean =
        name != null && SKIP_DIRS.contains(name.lowercase())

    private fun shouldSkipFile(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith(".")) return true
        val ext = lower.substringAfterLast('.', "")
        return ext.isNotEmpty() && SKIP_FILE_EXTENSIONS.contains(ext)
    }

    private fun isBinary(file: Path): Boolean {
        return try {
            Files.newInputStream(file).use { stream ->
                val buf = ByteArray(4096)
                val read = stream.read(buf)
                if (read <= 0) return false
                for (i in 0 until read) {
                    if (buf[i].toInt() == 0) return true
                }
                false
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun parseGlobs(input: String): List<String> =
        input.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun buildMatchers(input: String, stripNegation: Boolean = false): List<PathMatcher> {
        val raw = parseGlobs(input)
        val patterns = if (stripNegation) raw.mapNotNull { it.trimStart('!').ifBlank { null } } else raw
        if (patterns.isEmpty()) return emptyList()
        val fs = FileSystems.getDefault()
        return patterns.map { pattern ->
            try {
                fs.getPathMatcher("glob:$pattern")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid glob: $pattern")
            }
        }
    }

    private fun matchesAny(path: Path, fileName: String?, matchers: List<PathMatcher>): Boolean {
        if (matchers.isEmpty()) return false
        if (matchers.any { it.matches(path) }) return true
        if (fileName != null) {
            val namePath = try { Path.of(fileName) } catch (_: Exception) { null }
            if (namePath != null && matchers.any { it.matches(namePath) }) return true
        }
        return false
    }

    private fun formatSummary(stats: SearchStats): String {
        val results = stats.matches
        val files = stats.filesWithMatches
        val base = if (results == 0) "No matches."
        else "$results match${if (results == 1) "" else "es"} in $files file${if (files == 1) "" else "s"}"
        val extra = buildString {
            append(" (${"%.2f".format(stats.durationMs / 1000.0)}s")
            if (stats.skippedLarge > 0) append(", ${stats.skippedLarge} large skipped")
            if (stats.skippedBinary > 0) append(", ${stats.skippedBinary} binary skipped")
            if (stats.skippedDirs + stats.skippedFiles > 0) append(", ${stats.skippedDirs + stats.skippedFiles} ignored")
            append(")")
        }
        return if (stats.truncated) "$base$extra — results capped at $MAX_RESULTS."
        else "$base$extra"
    }

    private data class SearchResult(
        val file: File,
        val relPath: String,
        val line: Int,
        val column: Int,
        val preview: String,
    )

    private data class SearchStats(
        var filesScanned: Int = 0,
        var filesWithMatches: Int = 0,
        var matches: Int = 0,
        var skippedLarge: Int = 0,
        var skippedBinary: Int = 0,
        var skippedDirs: Int = 0,
        var skippedFiles: Int = 0,
        var truncated: Boolean = false,
        var durationMs: Long = 0,
    )

    private data class SearchOptions(
        val query: String,
        val caseSensitive: Boolean,
        val wholeWord: Boolean,
        val regex: Boolean,
        val includeGlobs: List<String>,
        val excludeGlobs: List<String>,
        val includeMatchers: List<PathMatcher>,
        val excludeMatchers: List<PathMatcher>,
        val sizeLimitBytes: Long?,
        val useRipgrep: Boolean,
    )

    private data class RipgrepHit(
        val path: String,
        val line: Int,
        val column: Int,
        val text: String,
    )

    private class ResultCellRenderer(private val root: () -> File?) : ListCellRenderer<SearchResult> {
        private val panel = JPanel(BorderLayout(6, 2)).apply {
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        }
        private val pathLabel = JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            foreground = UIManager.getColor("Label.disabledForeground") ?: Color(0x888888)
        }
        private val previewLabel = JLabel().apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        }

        init {
            panel.add(pathLabel, BorderLayout.NORTH)
            panel.add(previewLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out SearchResult>, value: SearchResult?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): java.awt.Component {
            val bg = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else list.foreground
            panel.background = bg
            panel.isOpaque = true
            previewLabel.foreground = fg
            pathLabel.foreground = if (isSelected) fg else (UIManager.getColor("Label.disabledForeground") ?: Color(0x888888))

            val item = value
            if (item == null) {
                pathLabel.text = ""
                previewLabel.text = ""
                return panel
            }
            val base = root()?.toPath()
            val rel = if (base != null) {
                try { base.relativize(item.file.toPath()).toString() } catch (_: Exception) { item.relPath }
            } else item.relPath
            pathLabel.text = "$rel:${item.line}:${item.column}"
            previewLabel.text = item.preview
            return panel
        }
    }

    companion object {
        private const val DEFAULT_MAX_MB = 2
        private const val MAX_RESULTS = 10_000
        private val SKIP_DIRS = setOf(
            ".git", ".hg", ".svn", ".idea", ".gradle", ".mvn", ".cache",
            "node_modules", "target", "build", "dist", "out", "vendor",
        )
        private val SKIP_FILE_EXTENSIONS = setOf(
            "class", "jar", "war", "ear", "zip", "gz", "bz2", "7z",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "webp", "tif", "tiff",
            "svg", "pdf", "mp3", "mp4", "mov", "avi", "mkv",
            "exe", "dll", "so", "dylib", "bin", "dat",
            "ttf", "otf", "woff", "woff2",
            "pyc", "pyo",
        )

        private val READ_CHARSETS: List<Charset> = buildList {
            add(Charsets.UTF_8)
            val nativeName = System.getProperty("native.encoding")
                ?: System.getProperty("sun.jnu.encoding")
            if (nativeName != null) {
                try {
                    val native = Charset.forName(nativeName)
                    if (native != Charsets.UTF_8) add(native)
                } catch (_: Exception) {}
            }
            if (lastOrNull() != Charsets.ISO_8859_1) add(Charsets.ISO_8859_1)
        }
    }
}
