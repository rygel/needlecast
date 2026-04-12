package io.github.rygel.needlecast.ui.logviewer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.event.AdjustmentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.io.RandomAccessFile
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.text.DefaultHighlighter
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Dockable panel that discovers and displays log files from the selected project.
 *
 * Features: auto-discovery, live tailing, colour-coded levels, level filtering,
 * follow mode, and search.
 */
class LogViewerPanel : JPanel(BorderLayout()) {

    // ── State ────────────────────────────────────────────────────────────────

    private var currentProjectPath: String? = null
    private var entries = mutableListOf<LogEntry>()
    private var visibleLevels = LogLevel.entries.toMutableSet()
    private var following = true

    // Tail state
    private var tailFile: File? = null
    private var tailOffset = 0L
    private var tailingEnabled = false
    private val maxTailBytes = 512 * 1024
    private val tailTimer = Timer(500) { pollTail() }.apply { isRepeats = true }
    private val renderQueue = java.util.ArrayDeque<LogEntry>()
    private val renderTimer = Timer(10) { renderNextBatch() }.apply { isRepeats = true }
    private var rendering = false
    private var searchRebuildPending = false

    // ── UI components ────────────────────────────────────────────────────────

    private val fileCombo = JComboBox<File>().apply {
        renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, (value as? File)?.let {
                it.toRelativeString(File(currentProjectPath ?: ""))
            } ?: "", index, isSelected, cellHasFocus)
        }
        addActionListener { onFileSelected() }
    }

    private val levelButtons = LogLevel.entries.filter { it != LogLevel.UNKNOWN }.map { level ->
        JToggleButton(level.label).apply {
            isSelected = true
            font = Font(Font.SANS_SERIF, Font.BOLD, 9)
            isFocusPainted = false
            margin = java.awt.Insets(2, 6, 2, 6)
            toolTipText = "Toggle ${level.label} entries"
            addActionListener {
                if (isSelected) visibleLevels.add(level) else visibleLevels.remove(level)
                rebuildDisplay()
            }
        }
    }

    private val followButton = JToggleButton("\u21E3").apply {
        isSelected = true
        toolTipText = "Follow (auto-scroll to newest)"
        isFocusPainted = false
        addActionListener { following = isSelected }
    }

    private val textPane = JTextPane().apply {
        isEditable = false
        font = Font(monoFont(), Font.PLAIN, 11)
    }
    private val scrollPane = JScrollPane(textPane).apply {
        minimumSize = Dimension(0, 0)
        verticalScrollBar.unitIncrement = 16
        verticalScrollBar.blockIncrement = 64
    }

    // Search
    private val searchField = JTextField(16)
    private val searchStatus = JLabel(" ")
    private val matchPainter = DefaultHighlighter.DefaultHighlightPainter(Color(0xFFD700))
    private val currentPainter = DefaultHighlighter.DefaultHighlightPainter(Color(0xFF8C00))
    private var matches: List<Pair<Int, Int>> = emptyList()
    private var currentMatch = -1

    // ── Styled attributes ────────────────────────────────────────────────────

    private fun levelAttrs(level: LogLevel): SimpleAttributeSet {
        val attrs = SimpleAttributeSet()
        val fg = when (level) {
            LogLevel.ERROR -> Color(0xF44336)
            LogLevel.WARN  -> Color(0xFFA726)
            LogLevel.DEBUG, LogLevel.TRACE ->
                UIManager.getColor("Label.disabledForeground") ?: Color(0x888888)
            else -> UIManager.getColor("TextPane.foreground")
                ?: UIManager.getColor("TextArea.foreground")
                ?: textPane.foreground
        }
        StyleConstants.setForeground(attrs, fg)
        if (level == LogLevel.ERROR) StyleConstants.setBold(attrs, true)
        if (level == LogLevel.TRACE) StyleConstants.setItalic(attrs, true)
        return attrs
    }

    private fun stackTraceAttrs(level: LogLevel): SimpleAttributeSet {
        val attrs = levelAttrs(level)
        val fg = UIManager.getColor("Label.disabledForeground") ?: Color(0x888888)
        StyleConstants.setForeground(attrs, fg)
        StyleConstants.setBold(attrs, false)
        return attrs
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        minimumSize = Dimension(0, 0)

        // Toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel("Log:"))
            add(fileCombo)
            add(JLabel("  "))
            levelButtons.forEach { add(it) }
            add(JLabel("  "))
            add(followButton)
            add(JLabel("  Find:"))
            add(searchField)
            add(searchStatus)
        }

        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        // Detect manual scroll → disable follow
        scrollPane.verticalScrollBar.addAdjustmentListener { e: AdjustmentEvent ->
            if (!e.valueIsAdjusting) return@addAdjustmentListener
            val sb = scrollPane.verticalScrollBar
            val atBottom = sb.value + sb.visibleAmount >= sb.maximum - 16
            if (!atBottom && following) {
                following = false
                followButton.isSelected = false
            }
        }

        // Search field listeners
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_ESCAPE                 -> { searchField.text = ""; rebuildSearchMatches() }
                    e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown -> stepMatch(-1)
                    e.keyCode == KeyEvent.VK_ENTER                  -> stepMatch(+1)
                }
            }
        })
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = rebuildSearchMatches()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = rebuildSearchMatches()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
        })

        // Ctrl+F focuses search
        val ctrlF = javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        textPane.inputMap.put(ctrlF, "focus-search")
        textPane.actionMap.put("focus-search", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                searchField.requestFocusInWindow()
                searchField.selectAll()
            }
        })
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun loadProject(path: String?) {
        tailTimer.stop()
        tailFile = null
        tailOffset = 0
        entries.clear()
        textPane.text = ""
        currentProjectPath = path

        if (path == null) {
            fileCombo.model = DefaultComboBoxModel()
            return
        }

        // Discover log files in background
        object : SwingWorker<List<File>, Void>() {
            override fun doInBackground(): List<File> = LogFileScanner.scan(path)
            override fun done() {
                val files = try { get() } catch (_: Exception) { emptyList() }
                fileCombo.model = DefaultComboBoxModel(files.toTypedArray())
                if (files.isNotEmpty()) {
                    fileCombo.selectedIndex = 0
                    // onFileSelected() fires via actionListener
                }
            }
        }.execute()
    }

    fun dispose() {
        tailingEnabled = false
        tailTimer.stop()
    }

    // ── File selection & tailing ─────────────────────────────────────────────

    private fun onFileSelected() {
        tailTimer.stop()
        tailingEnabled = false
        val file = fileCombo.selectedItem as? File ?: return
        tailFile = file
        tailOffset = 0
        entries.clear()
        textPane.text = ""

        // Initial load in background
        object : SwingWorker<List<LogEntry>, Void>() {
            override fun doInBackground(): List<LogEntry> {
                val lines = file.readLines(Charsets.UTF_8)
                return LogParser.parse(lines)
            }
            override fun done() {
                val parsed = try { get() } catch (_: Exception) { emptyList() }
                entries.addAll(parsed)
                tailOffset = (tailFile?.length() ?: 0)
                rebuildDisplay()
                tailingEnabled = true
                tailTimer.start()
            }
        }.execute()
    }

    private fun pollTail() {
        if (!tailingEnabled) return
        val file = tailFile ?: return
        if (!file.exists()) return
        val currentSize = file.length()

        // Rotation detection
        if (currentSize < tailOffset) {
            tailOffset = 0
            entries.clear()
        }

        if (currentSize <= tailOffset) return

        try {
            RandomAccessFile(file, "r").use { raf ->
                val delta = currentSize - tailOffset
                var startOffset = tailOffset
                var skippedBytes = 0L
                if (delta > maxTailBytes) {
                    skippedBytes = delta - maxTailBytes
                    startOffset = currentSize - maxTailBytes
                }
                raf.seek(startOffset)
                val newBytes = ByteArray((currentSize - startOffset).toInt())
                raf.readFully(newBytes)
                tailOffset = currentSize
                var newLines = String(newBytes, Charsets.UTF_8).lines().filter { it.isNotEmpty() }
                if (skippedBytes > 0 && newLines.isNotEmpty()) {
                    // Drop the first line since we likely started mid-line.
                    newLines = newLines.drop(1)
                }
                if (newLines.isNotEmpty()) {
                    val newEntries = mutableListOf<LogEntry>()
                    if (skippedBytes > 0) {
                        val msg = "[log tail truncated] Skipped $skippedBytes bytes to avoid large read"
                        newEntries += LogEntry(null, null, LogLevel.UNKNOWN, null, msg, raw = msg)
                    }
                    newEntries += LogParser.parse(newLines)
                    SwingUtilities.invokeLater {
                        entries.addAll(newEntries)
                        appendEntries(newEntries)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ── Display ──────────────────────────────────────────────────────────────

    private fun rebuildDisplay() {
        renderTimer.stop()
        renderQueue.clear()
        rendering = true
        searchRebuildPending = true
        val doc = textPane.styledDocument
        doc.remove(0, doc.length)
        val filtered = entries.filter { it.level in visibleLevels || it.level == LogLevel.UNKNOWN }
        renderQueue.addAll(filtered)
        renderTimer.start()
    }

    private fun appendEntries(newEntries: List<LogEntry>) {
        if (rendering) {
            newEntries.filterTo(renderQueue) { it.level in visibleLevels || it.level == LogLevel.UNKNOWN }
            return
        }
        val doc = textPane.styledDocument
        for (entry in newEntries) {
            if (entry.level !in visibleLevels && entry.level != LogLevel.UNKNOWN) continue
            appendEntry(entry, doc)
        }
        if (following) scrollToEnd()
    }

    private fun renderNextBatch() {
        val doc = textPane.styledDocument
        var count = 0
        val maxPerTick = 200
        while (count < maxPerTick && renderQueue.isNotEmpty()) {
            val entry = renderQueue.removeFirst()
            appendEntry(entry, doc)
            count++
        }
        if (renderQueue.isEmpty()) {
            renderTimer.stop()
            rendering = false
            if (following) scrollToEnd()
            if (searchRebuildPending) {
                searchRebuildPending = false
                rebuildSearchMatches()
            }
        }
    }

    private fun appendEntry(entry: LogEntry, doc: javax.swing.text.StyledDocument) {
        val attrs = levelAttrs(entry.level)
        doc.insertString(doc.length, entry.raw + "\n", attrs)
        entry.stackTrace?.let { st ->
            doc.insertString(doc.length, st + "\n", stackTraceAttrs(entry.level))
        }
    }

    private fun scrollToEnd() {
        SwingUtilities.invokeLater {
            textPane.caretPosition = textPane.document.length
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────

    private fun rebuildSearchMatches() {
        if (rendering) {
            searchRebuildPending = true
            return
        }
        textPane.highlighter.removeAllHighlights()
        val query = searchField.text
        if (query.isEmpty()) {
            matches = emptyList()
            currentMatch = -1
            searchStatus.text = " "
            return
        }
        val text = textPane.text.lowercase()
        val q = query.lowercase()
        val found = mutableListOf<Pair<Int, Int>>()
        var idx = 0
        while (true) {
            val pos = text.indexOf(q, idx)
            if (pos < 0) break
            found += pos to (pos + q.length)
            textPane.highlighter.addHighlight(pos, pos + q.length, matchPainter)
            idx = pos + 1
        }
        matches = found
        currentMatch = -1
        if (found.isEmpty()) {
            searchStatus.foreground = Color(0xF44336)
            searchStatus.text = "Not found"
        } else {
            searchStatus.foreground = Color(0x4CAF50)
            searchStatus.text = "${found.size} match${if (found.size == 1) "" else "es"}"
            stepMatch(+1)
        }
    }

    private fun stepMatch(direction: Int) {
        if (matches.isEmpty()) return
        currentMatch = Math.floorMod(currentMatch + direction, matches.size)
        textPane.highlighter.removeAllHighlights()
        matches.forEachIndexed { i, (s, e) ->
            textPane.highlighter.addHighlight(s, e, if (i == currentMatch) currentPainter else matchPainter)
        }
        val (start, _) = matches[currentMatch]
        textPane.caretPosition = start
        textPane.scrollRectToVisible(textPane.modelToView2D(start).bounds)
        searchStatus.foreground = Color(0x4CAF50)
        searchStatus.text = "${currentMatch + 1} / ${matches.size}"
    }

    private fun monoFont(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        val preferred = when {
            os.contains("win") -> listOf("Cascadia Code", "Cascadia Mono", "JetBrains Mono", "Fira Code", "Consolas")
            os.contains("mac") -> listOf("SF Mono", "Menlo", "JetBrains Mono", "Fira Code", "Monaco")
            else -> listOf("JetBrains Mono", "Fira Code", "DejaVu Sans Mono", "Liberation Mono", "Noto Mono")
        }
        return preferred.firstOrNull { it in available } ?: Font.MONOSPACED
    }
}
