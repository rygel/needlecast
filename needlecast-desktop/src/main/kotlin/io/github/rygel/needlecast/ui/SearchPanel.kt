package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.process.ProcessExecutor
import org.slf4j.LoggerFactory
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
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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
private val searchPanelLogger = LoggerFactory.getLogger("SearchPanel")

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
    private val resultsList =
        JList(resultsModel).apply {
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

        val row1 =
            JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                add(JLabel("Find:"))
                add(queryField)
                add(searchButton)
                add(stopButton)
                add(JLabel("  In:"))
                add(scopeLabel)
            }

        val row2 =
            JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
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

        val toolbar =
            JPanel(BorderLayout()).apply {
                add(row1, BorderLayout.NORTH)
                add(row2, BorderLayout.SOUTH)
            }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(resultsList).apply { minimumSize = Dimension(0, 0) }, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        searchButton.addActionListener { startSearch() }
        stopButton.addActionListener { stopSearch(showStatus = true) }
        queryField.addActionListener { startSearch() }
        queryField.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ESCAPE) {
                        queryField.text = ""
                        resultsModel.clear()
                        statusLabel.text = " "
                    }
                }
            },
        )
        sizeLimitToggle.addActionListener { sizeLimitField.isEnabled = sizeLimitToggle.isSelected }
        sizeLimitField.isEnabled = sizeLimitToggle.isSelected

        resultsList.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) openSelectedResult()
                }
            },
        )
        resultsList.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) openSelectedResult()
                }
            },
        )

        Thread({
            rgAvailable = ProcessExecutor.isOnPath("rg")
        }, "rg-detector").apply {
            isDaemon = true
            start()
        }
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
        val root =
            currentRoot ?: run {
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
        val includeMatchers =
            try {
                SearchEngine.buildMatchers(includeField.text)
            } catch (e: IllegalArgumentException) {
                statusLabel.text = e.message ?: "Invalid include pattern."
                return
            }
        val excludeMatchers =
            try {
                SearchEngine.buildMatchers(excludeField.text, stripNegation = true)
            } catch (e: IllegalArgumentException) {
                statusLabel.text = e.message ?: "Invalid exclude pattern."
                return
            }
        val limitMb = if (sizeLimitToggle.isSelected) sizeLimitField.text.trim().toIntOrNull() else null
        if (sizeLimitToggle.isSelected && (limitMb == null || limitMb <= 0)) {
            statusLabel.text = "Invalid size limit (MB)."
            return
        }
        val sizeLimitBytes =
            if (sizeLimitToggle.isSelected && limitMb != null) {
                limitMb.toLong() * 1024L * 1024L
            } else {
                null
            }

        stopSearch()
        resultsModel.clear()
        val useRg = rgAvailable == true
        statusLabel.text = if (useRg) "Searching (rg)\u2026" else "Searching\u2026"
        searchButton.isEnabled = false
        stopButton.isEnabled = true

        val opts =
            SearchOptions(
                query = query,
                caseSensitive = caseToggle.isSelected,
                wholeWord = wordToggle.isSelected,
                regex = regexToggle.isSelected,
                includeGlobs = SearchEngine.parseGlobs(includeField.text),
                excludeGlobs = SearchEngine.parseGlobs(excludeField.text),
                includeMatchers = includeMatchers,
                excludeMatchers = excludeMatchers,
                sizeLimitBytes = sizeLimitBytes,
                useRipgrep = useRg,
            )

        val activeWorker =
            if (useRg) {
                buildRipgrepWorker(root, opts)
            } else {
                val matcher =
                    try {
                        SearchEngine.buildMatcher(query, caseToggle.isSelected, wordToggle.isSelected, regexToggle.isSelected)
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
    ): SwingWorker<SearchStats, SearchResult> =
        object : SwingWorker<SearchStats, SearchResult>() {
            override fun doInBackground(): SearchStats {
                val stats = SearchStats()
                val rootPath = root.toPath()
                val start = System.nanoTime()
                var stopRequested = false
                val shouldStop = { isCancelled || stopRequested }

                Files.walkFileTree(
                    rootPath,
                    object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(
                            dir: Path,
                            attrs: BasicFileAttributes,
                        ): FileVisitResult {
                            if (shouldStop()) return FileVisitResult.TERMINATE
                            if (dir != rootPath && SearchEngine.shouldSkipDir(dir.fileName?.toString())) {
                                stats.skippedDirs++
                                return FileVisitResult.SKIP_SUBTREE
                            }
                            if (dir != rootPath && isHidden(dir)) {
                                stats.skippedDirs++
                                return FileVisitResult.SKIP_SUBTREE
                            }
                            if (attrs.isSymbolicLink) return FileVisitResult.SKIP_SUBTREE
                            val relDir =
                                try {
                                    rootPath.relativize(dir)
                                } catch (e: Exception) {
                                    searchPanelLogger.warn("Failed to relativize directory path", e)
                                    dir.fileName
                                }
                            if (relDir != null && opts.excludeMatchers.isNotEmpty()) {
                                if (SearchEngine.matchesAny(relDir, dir.fileName?.toString(), opts.excludeMatchers)) {
                                    stats.skippedDirs++
                                    return FileVisitResult.SKIP_SUBTREE
                                }
                            }
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFile(
                            file: Path,
                            attrs: BasicFileAttributes,
                        ): FileVisitResult {
                            if (shouldStop()) return FileVisitResult.TERMINATE
                            if (attrs.isSymbolicLink || attrs.isDirectory) return FileVisitResult.CONTINUE
                            if (isHidden(file)) {
                                stats.skippedFiles++
                                return FileVisitResult.CONTINUE
                            }
                            val fileName = file.fileName?.toString() ?: ""
                            if (SearchEngine.shouldSkipFile(fileName)) {
                                stats.skippedFiles++
                                return FileVisitResult.CONTINUE
                            }
                            val relPath =
                                try {
                                    rootPath.relativize(file)
                                } catch (e: Exception) {
                                    searchPanelLogger.warn("Failed to relativize file path for matching", e)
                                    file.fileName ?: file
                                }
                            if (opts.includeMatchers.isNotEmpty() && !SearchEngine.matchesAny(relPath, fileName, opts.includeMatchers)) {
                                stats.skippedFiles++
                                return FileVisitResult.CONTINUE
                            }
                            if (opts.excludeMatchers.isNotEmpty() && SearchEngine.matchesAny(relPath, fileName, opts.excludeMatchers)) {
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

                            val rel =
                                try {
                                    rootPath.relativize(file).toString()
                                } catch (e: Exception) {
                                    searchPanelLogger.warn("Failed to relativize file path", e)
                                    file.toString()
                                }
                            val matched =
                                scanFile(file, matcher, shouldStop) { lineNumber, column, lineText ->
                                    stats.matches++
                                    publish(SearchResult(file.toFile(), rel, lineNumber, column, SearchEngine.preview(lineText)))
                                    if (stats.matches >= MAX_RESULTS) {
                                        stats.truncated = true
                                        stopRequested = true
                                    }
                                }
                            if (matched) stats.filesWithMatches++
                            return if (stopRequested) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                        }
                    },
                )

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
                val stats =
                    try {
                        get()
                    } catch (e: Exception) {
                        if (isCancelled) {
                            statusLabel.text = "Search cancelled."
                        } else {
                            searchPanelLogger.warn("Builtin search worker failed", e)
                            statusLabel.text = "Search failed."
                        }
                        return
                    }
                statusLabel.text = SearchEngine.formatSummary(stats, MAX_RESULTS)
            }
        }

    private fun buildRipgrepWorker(
        root: File,
        opts: SearchOptions,
    ): SwingWorker<SearchStats, SearchResult> =
        object : SwingWorker<SearchStats, SearchResult>() {
            override fun doInBackground(): SearchStats {
                val stats = SearchStats()
                val start = System.nanoTime()
                val rootPath = root.toPath()
                val argv = SearchEngine.buildRipgrepArgs(opts)
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
                            val parsed = SearchEngine.parseRipgrepLine(line ?: "") ?: continue
                            stats.matches++
                            if (seenFiles.add(parsed.path)) stats.filesWithMatches++
                            val relPath = parsed.path
                            val file = File(root, parsed.path)
                            publish(SearchResult(file, relPath, parsed.line, parsed.column, SearchEngine.preview(parsed.text)))
                            if (stats.matches >= MAX_RESULTS) {
                                stats.truncated = true
                                proc.destroyForcibly()
                                break
                            }
                        }
                    }
                    proc.waitFor()
                } catch (e: Exception) {
                    searchPanelLogger.warn("Ripgrep search process failed", e)
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
                val stats =
                    try {
                        get()
                    } catch (e: Exception) {
                        if (isCancelled) {
                            statusLabel.text = "Search cancelled."
                        } else {
                            searchPanelLogger.warn("Ripgrep search worker failed", e)
                            statusLabel.text = "Search failed."
                        }
                        return
                    }
                statusLabel.text = SearchEngine.formatSummary(stats, MAX_RESULTS)
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
            } catch (e: MalformedInputException) {
                searchPanelLogger.debug("Binary file detected, trying next charset", e)
                continue
            } catch (e: CharacterCodingException) {
                searchPanelLogger.debug("Character coding error, trying next charset", e)
                continue
            } catch (e: Exception) {
                searchPanelLogger.warn("Failed to scan file", e)
                return matched
            }
        }
        return matched
    }

    private fun isHidden(path: Path): Boolean =
        try {
            Files.isHidden(path)
        } catch (_: Exception) {
            false
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
        } catch (e: Exception) {
            searchPanelLogger.warn("Failed to check if file is binary", e)
            true
        }
    }

    private class ResultCellRenderer(
        private val root: () -> File?,
    ) : ListCellRenderer<SearchResult> {
        private val panel =
            JPanel(BorderLayout(6, 2)).apply {
                border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            }
        private val pathLabel =
            JLabel().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
                foreground = UIManager.getColor("Label.disabledForeground") ?: Color(0x888888)
            }
        private val previewLabel =
            JLabel().apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            }

        init {
            panel.add(pathLabel, BorderLayout.NORTH)
            panel.add(previewLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out SearchResult>,
            value: SearchResult?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
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
            val rel =
                if (base != null) {
                    try {
                        base.relativize(item.file.toPath()).toString()
                    } catch (e: Exception) {
                        searchPanelLogger.warn("Failed to relativize path for search result", e)
                        item.relPath
                    }
                } else {
                    item.relPath
                }
            pathLabel.text = "$rel:${item.line}:${item.column}"
            previewLabel.text = item.preview
            return panel
        }
    }

    companion object {
        private const val DEFAULT_MAX_MB = 2
        private const val MAX_RESULTS = 10_000

        private val READ_CHARSETS: List<Charset> =
            buildList {
                add(Charsets.UTF_8)
                val nativeName =
                    System.getProperty("native.encoding")
                        ?: System.getProperty("sun.jnu.encoding")
                if (nativeName != null) {
                    try {
                        val native = Charset.forName(nativeName)
                        if (native != Charsets.UTF_8) add(native)
                    } catch (e: Exception) {
                        searchPanelLogger.debug("Charset lookup failed for native encoding", e)
                    }
                }
                if (lastOrNull() != Charsets.ISO_8859_1) add(Charsets.ISO_8859_1)
            }
    }
}
