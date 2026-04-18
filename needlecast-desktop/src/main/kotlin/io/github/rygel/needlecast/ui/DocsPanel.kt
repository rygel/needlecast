package io.github.rygel.needlecast.ui

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme as RstaTheme
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.UIManager

class DocsPanel : JPanel(BorderLayout()) {

    // ── UI components ────────────────────────────────────────────────────────
    private val fileListModel = DefaultListModel<String>()
    private val fileList      = JList(fileListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val renderedPane  = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }
    private val rawArea       = RSyntaxTextArea().apply {
        syntaxEditingStyle   = SyntaxConstants.SYNTAX_STYLE_MARKDOWN
        isEditable           = false
        isCodeFoldingEnabled = false
    }
    private val cardLayout   = CardLayout()
    private val contentCards = JPanel(cardLayout)

    private val renderedToggle = JRadioButton("Rendered", true)
    private val rawToggle      = JRadioButton("Raw",      false)
    private val refreshButton  = JButton("⟳ Refresh")

    private val placeholder = JLabel("No project selected", JLabel.CENTER)

    // ── State ────────────────────────────────────────────────────────────────
    private var projectRoot: File? = null
    /** Cache: relative path → Pair(lastModified, rendered HTML) */
    private val htmlCache = HashMap<String, Pair<Long, String>>()
    private val executor  = Executors.newSingleThreadExecutor { r ->
        Thread(r, "docs-panel-worker").apply { isDaemon = true }
    }

    // ── commonmark ───────────────────────────────────────────────────────────
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val mdParser   = Parser.builder().extensions(extensions).build()
    private val mdRenderer = HtmlRenderer.builder().extensions(extensions).build()

    init {
        ButtonGroup().apply {
            add(renderedToggle)
            add(rawToggle)
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(refreshButton)
            add(renderedToggle)
            add(rawToggle)
        }

        contentCards.add(JScrollPane(renderedPane), CARD_RENDERED)
        contentCards.add(RTextScrollPane(rawArea),  CARD_RAW)
        cardLayout.show(contentCards, CARD_RENDERED)

        val splitPane = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JScrollPane(fileList),
            contentCards,
        ).apply {
            dividerLocation = 200
            resizeWeight    = 0.0
        }

        add(toolbar,   BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)

        // ── Listeners ────────────────────────────────────────────────────────
        fileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) loadSelectedFile()
        }
        renderedToggle.addActionListener {
            cardLayout.show(contentCards, CARD_RENDERED)
            loadSelectedFile()
        }
        rawToggle.addActionListener {
            cardLayout.show(contentCards, CARD_RAW)
            loadSelectedFile()
        }
        refreshButton.addActionListener {
            htmlCache.clear()
            refresh()
        }

        showPlaceholder("No project selected")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadProject(path: String?) {
        projectRoot = path?.let { File(it) }
        htmlCache.clear()
        refresh()
    }

    fun applyTheme(dark: Boolean) {
        val themeFile = if (dark) "monokai.xml" else "idea.xml"
        try {
            val stream = RstaTheme::class.java.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/$themeFile")
            if (stream != null) RstaTheme.load(stream).apply(rawArea)
        } catch (_: Exception) {}

        val bg = UIManager.getColor("TextArea.background")
            ?: UIManager.getColor("Panel.background")
            ?: if (dark) Color(0x1E1E1E) else Color.WHITE
        val fg = UIManager.getColor("TextArea.foreground")
            ?: UIManager.getColor("Panel.foreground")
            ?: if (dark) Color(0xD4D4D4) else Color(0x1E1E1E)
        rawArea.background = bg
        rawArea.foreground = fg
        rawArea.caretColor = fg

        htmlCache.clear()
        loadSelectedFile()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun refresh() {
        val root = projectRoot
        if (root == null) { showPlaceholder("No project selected"); return }

        executor.execute {
            val files = collectMarkdownFiles(root)
            SwingUtilities.invokeLater {
                fileListModel.clear()
                if (files.isEmpty()) {
                    showPlaceholder("No Markdown files found in this project")
                } else {
                    hidePlaceholder()
                    files.forEach { fileListModel.addElement(it) }
                    if (fileList.selectedIndex < 0 || fileList.selectedIndex >= fileListModel.size) {
                        fileList.selectedIndex = 0
                    } else {
                        loadSelectedFile()
                    }
                }
            }
        }
    }

    private fun loadSelectedFile() {
        val relativePath = fileList.selectedValue ?: return
        val root         = projectRoot             ?: return
        val file         = File(root, relativePath)

        executor.execute {
            val text = try {
                file.readText()
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showContentError("Could not read file: $relativePath") }
                return@execute
            }

            if (renderedToggle.isSelected) {
                val lastMod = file.lastModified()
                val cached  = htmlCache[relativePath]
                val html    = if (cached != null && cached.first == lastMod) {
                    cached.second
                } else {
                    buildHtml(text).also { htmlCache[relativePath] = lastMod to it }
                }
                SwingUtilities.invokeLater { renderedPane.text = html; renderedPane.caretPosition = 0 }
            } else {
                SwingUtilities.invokeLater { rawArea.text = text; rawArea.caretPosition = 0 }
            }
        }
    }

    private fun buildHtml(markdown: String): String {
        val bg   = colorHex(UIManager.getColor("Panel.background")    ?: Color(0x1E, 0x1E, 0x1E))
        val fg   = colorHex(UIManager.getColor("Label.foreground")    ?: Color(0xD4, 0xD4, 0xD4))
        val code = colorHex(UIManager.getColor("TextArea.background") ?: Color(0x2D, 0x2D, 0x2D))
        val link = colorHex(UIManager.getColor("Component.linkColor") ?: Color(0x4F, 0xC3, 0xF7))
        val body = mdRenderer.render(mdParser.parse(markdown))
        return """<html><head><style>
            body { background:$bg; color:$fg; font-family:sans-serif; margin:12px; }
            code { background:$code; padding:1px 4px; border-radius:3px; }
            pre  { background:$code; padding:8px; border-radius:4px; overflow-x:auto; }
            a    { color:$link; }
            table{ border-collapse:collapse; }
            th,td{ border:1px solid #555; padding:4px 8px; }
            </style></head><body>$body</body></html>"""
    }

    private fun colorHex(c: Color) = "#%02x%02x%02x".format(c.red, c.green, c.blue)

    private fun showPlaceholder(message: String) {
        placeholder.text = message
        if (placeholder.parent == null) {
            removeAll()
            add(placeholder, BorderLayout.CENTER)
            revalidate(); repaint()
        }
    }

    private fun hidePlaceholder() {
        if (placeholder.parent != null) {
            removeAll()
            val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                add(refreshButton)
                add(renderedToggle)
                add(rawToggle)
            }
            val splitPane = JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                JScrollPane(fileList),
                contentCards,
            ).apply { dividerLocation = 200; resizeWeight = 0.0 }
            add(toolbar,   BorderLayout.NORTH)
            add(splitPane, BorderLayout.CENTER)
            revalidate(); repaint()
        }
    }

    private fun showContentError(message: String) {
        renderedPane.text = "<html><body><em>$message</em></body></html>"
        rawArea.text      = message
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val CARD_RENDERED = "rendered"
        private const val CARD_RAW      = "raw"
        private val SKIP_DIRS = setOf(".git", "target", "node_modules", "build", ".gradle")

        fun collectMarkdownFiles(root: File?): List<String> {
            if (root == null || !root.isDirectory) return emptyList()
            val found = mutableListOf<String>()
            Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
                    return if (name in SKIP_DIRS) FileVisitResult.SKIP_SUBTREE
                    else FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (file.fileName.toString().endsWith(".md", ignoreCase = true)) {
                        found.add(root.toPath().relativize(file).toString().replace(File.separatorChar, '/'))
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            return found.sortedWith(Comparator { a, b ->
                val aIsReadme = a.substringAfterLast('/').equals("README.md", ignoreCase = true)
                val bIsReadme = b.substringAfterLast('/').equals("README.md", ignoreCase = true)
                when {
                    aIsReadme && !bIsReadme -> -1
                    !aIsReadme && bIsReadme ->  1
                    else -> a.compareTo(b, ignoreCase = true)
                }
            })
        }
    }
}
