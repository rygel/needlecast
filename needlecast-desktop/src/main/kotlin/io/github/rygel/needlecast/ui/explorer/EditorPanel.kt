package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ExternalEditor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.ui.TextChunker
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Ordered list of charsets to try when reading a file.
 * Built once at startup from the actual OS native encoding (e.g. windows-1252 on Western-European
 * Windows) rather than the JVM default (which Java 17+ hardcodes to UTF-8).
 * ISO-8859-1 is always last — it maps every byte 0–255 to a character and never throws.
 */
private val readCharsets: List<Charset> = buildList {
    add(Charsets.UTF_8)
    val nativeName = System.getProperty("native.encoding")
        ?: System.getProperty("sun.jnu.encoding")
    if (nativeName != null) {
        try {
            val native = Charset.forName(nativeName)
            if (native != Charsets.UTF_8) add(native)
        } catch (_: Exception) { }
    }
    if (last() != Charsets.ISO_8859_1) add(Charsets.ISO_8859_1)
}

class EditorPanel(private val ctx: AppContext) : JPanel(BorderLayout()) {

    private var currentFile: File? = null
    private var isModified = false
    private var isLoadingFile = false
    private var editorFontSize = 12
    private var loadWorker: javax.swing.SwingWorker<String, Void>? = null
    private var loadSeq: Long = 0L
    private var pendingCaret: CaretTarget? = null

    private val fileLabel = JLabel("No file open")
    private val editor = RSyntaxTextArea(20, 80).apply {
        isEditable = true
        font = Font(monoFont(), Font.PLAIN, editorFontSize)
        antiAliasingEnabled = true
        tabSize = 4
        isCodeFoldingEnabled = true
    }
    private val scrollPane = RTextScrollPane(editor)
    private val findBar = FindBar(editor)

    init {
        val saveButton = JButton("Save").apply {
            addActionListener { saveFile() }
        }
        val openWithButton = JButton("Open with \u25BE").apply {
            addActionListener { showOpenWithMenu(this) }
        }

        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(fileLabel, BorderLayout.CENTER)
            val btnPanel = JPanel()
            btnPanel.add(saveButton)
            btnPanel.add(openWithButton)
            add(btnPanel, BorderLayout.EAST)
        }

        // Ctrl+S save
        val ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        editor.inputMap.put(ctrlS, "save-file")
        editor.actionMap.put("save-file", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) = saveFile()
        })

        // Track modifications
        editor.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = markModified()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = markModified()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
            private fun markModified() {
                if (!isLoadingFile && currentFile != null && !isModified) {
                    isModified = true
                    SwingUtilities.invokeLater { fileLabel.text = "* ${currentFile!!.name}" }
                }
            }
        })

        // Ctrl+F — find
        val ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        editor.inputMap.put(ctrlF, "show-find")
        editor.actionMap.put("show-find", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) = findBar.showBar(replaceMode = false)
        })

        // Ctrl+H — find & replace
        val ctrlH = KeyStroke.getKeyStroke(KeyEvent.VK_H, java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
        editor.inputMap.put(ctrlH, "show-replace")
        editor.actionMap.put("show-replace", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) = findBar.showBar(replaceMode = true)
        })

        // Ctrl+scroll — zoom font size
        val zoomListener = java.awt.event.MouseWheelListener { e ->
            if (e.isControlDown) {
                editorFontSize = (editorFontSize - e.wheelRotation.toInt()).coerceIn(6, 72)
                editor.font = editor.font.deriveFont(editorFontSize.toFloat())
                e.consume()
            }
        }
        editor.addMouseWheelListener(zoomListener)
        scrollPane.addMouseWheelListener(zoomListener)

        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        findBar.isVisible = false
        add(findBar, BorderLayout.SOUTH)

        applyTheme(ctx.config.theme == "dark")
    }

    fun applyTheme(dark: Boolean) {
        val syntaxTheme = ctx.config.syntaxTheme
        val themeFile = when (syntaxTheme) {
            "auto" -> if (dark) "monokai.xml" else "idea.xml"
            else   -> "$syntaxTheme.xml"
        }
        try {
            val stream = Theme::class.java.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/$themeFile")
            if (stream != null) {
                val theme = Theme.load(stream)
                theme.apply(editor)
            }
        } catch (_: Exception) {
            // Fall back to defaults
        }
        // Preserve current zoom level — RSTA theme XML may override the font
        editor.font = editor.font.deriveFont(editorFontSize.toFloat())

        // Derive colors from the active FlatLaf theme so the editor matches the
        // surrounding application.  UIManager colors update when the L&F changes.
        val bg = javax.swing.UIManager.getColor("TextArea.background")
            ?: javax.swing.UIManager.getColor("Panel.background")
            ?: if (dark) java.awt.Color(0x1E1E1E) else java.awt.Color.WHITE
        val fg = javax.swing.UIManager.getColor("TextArea.foreground")
            ?: javax.swing.UIManager.getColor("Panel.foreground")
            ?: if (dark) java.awt.Color(0xD4D4D4) else java.awt.Color(0x1E1E1E)
        val caret = javax.swing.UIManager.getColor("TextArea.caretForeground")
            ?: fg

        editor.background = bg
        editor.foreground = fg
        editor.caretColor = caret
        scrollPane.background = bg

        // Theme the gutter (line numbers area) to match
        val gutter = scrollPane.gutter
        gutter.background = bg
        gutter.borderColor = bg
        gutter.lineNumberColor = javax.swing.UIManager.getColor("Label.disabledForeground")
            ?: fg.let { java.awt.Color(it.red, it.green, it.blue, 140) }
    }

    fun checkUnsaved(): Boolean {
        if (!isModified || currentFile == null) return true
        val result = JOptionPane.showOptionDialog(
            this,
            "Save changes to ${currentFile!!.name}?",
            "Unsaved Changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            arrayOf("Save", "Discard", "Cancel"),
            "Save",
        )
        return when (result) {
            JOptionPane.YES_OPTION -> { saveFile(); true }
            JOptionPane.NO_OPTION -> true
            else -> false
        }
    }

    fun openFile(file: File, line: Int? = null, column: Int? = null) {
        loadWorker?.cancel(true)
        TextChunker.cancel(editor)
        pendingCaret = if (line != null) CaretTarget(line, column) else null
        val maxBytes = 2 * 1024 * 1024L
        if (file.length() > maxBytes) {
            isLoadingFile = true
            editor.text = "[File too large to display (${file.length() / 1024} KB > 2 MB limit)]"
            editor.syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_NONE
            isLoadingFile = false
            fileLabel.text = file.name
            currentFile = null
            isModified = false
            pendingCaret = null
            return
        }
        val seq = ++loadSeq
        isLoadingFile = true
        editor.syntaxEditingStyle = syntaxStyleFor(file)
        editor.text = "Loading\u2026"
        editor.caretPosition = 0
        currentFile = file
        isModified = false
        fileLabel.text = file.name

        loadWorker = object : javax.swing.SwingWorker<String, Void>() {
            override fun doInBackground(): String {
                var result: String? = null
                for (cs in readCharsets) {
                    try { result = Files.readString(file.toPath(), cs); break }
                    catch (_: MalformedInputException) { }
                }
                return result!! // ISO_8859_1 is last and never throws
            }

            override fun done() {
                if (isCancelled || seq != loadSeq) return
                val content = try { get() } catch (_: Exception) {
                    isLoadingFile = false
                    editor.text = "Failed to load file."
                    return
                }
                TextChunker.setTextChunked(editor, content) {
                    editor.caretPosition = 0
                    isLoadingFile = false
                    applyPendingCaret()
                }
            }
        }.also { it.execute() }
    }

    fun focusLocation(line: Int, column: Int? = null) {
        pendingCaret = CaretTarget(line, column)
        if (!isLoadingFile) applyPendingCaret()
    }

    private fun saveFile() {
        val file = currentFile ?: return
        try {
            val tmp = file.toPath().resolveSibling(file.name + ".tmp")
            Files.writeString(tmp, editor.text, StandardCharsets.UTF_8)
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            isModified = false
            fileLabel.text = file.name
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this, "Save failed: ${e.message}", "Save Error", JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun applyPendingCaret() {
        val target = pendingCaret ?: return
        pendingCaret = null
        moveCaretTo(target.line, target.column)
    }

    private fun moveCaretTo(line: Int, column: Int?) {
        val root = editor.document.defaultRootElement
        val lineIdx = (line - 1).coerceAtLeast(0)
        if (lineIdx >= root.elementCount) return
        val elem = root.getElement(lineIdx)
        var offset = elem.startOffset
        if (column != null) {
            offset = (offset + column - 1).coerceAtMost(elem.endOffset - 1)
        }
        SwingUtilities.invokeLater {
            editor.caretPosition = offset
            editor.requestFocusInWindow()
            val rect = editor.modelToView2D(offset)
            if (rect != null) editor.scrollRectToVisible(rect.bounds)
        }
    }

    private fun showOpenWithMenu(button: JButton) {
        val file = currentFile ?: run {
            JOptionPane.showMessageDialog(this, "No file is currently open.", "Open With", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        val menu = JPopupMenu()
        val editors = ctx.config.externalEditors
        if (editors.isEmpty()) {
            val item = JMenuItem("No editors configured")
            item.isEnabled = false
            menu.add(item)
        } else {
            editors.forEach { editor ->
                menu.add(JMenuItem(editor.name).apply {
                    addActionListener { launchEditor(file, editor) }
                })
            }
        }
        menu.show(button, 0, button.height)
    }

    private fun launchEditor(file: File, editor: ExternalEditor) {
        try {
            val cmd = if (IS_WINDOWS) listOf("cmd", "/c", editor.executable, file.absolutePath)
                      else listOf(editor.executable, file.absolutePath)
            ProcessBuilder(cmd).start()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this, "Failed to launch ${editor.name}: ${e.message}", "Launch Error", JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun syntaxStyleFor(file: File): String = when (file.extension.lowercase()) {
        "kt", "kts"         -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
        "java"              -> SyntaxConstants.SYNTAX_STYLE_JAVA
        "xml", "pom"        -> SyntaxConstants.SYNTAX_STYLE_XML
        "html", "htm"       -> SyntaxConstants.SYNTAX_STYLE_HTML
        "json"              -> SyntaxConstants.SYNTAX_STYLE_JSON
        "js", "mjs"         -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
        "ts"                -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
        "css"               -> SyntaxConstants.SYNTAX_STYLE_CSS
        "py"                -> SyntaxConstants.SYNTAX_STYLE_PYTHON
        "sh", "bash"        -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
        "bat", "cmd"        -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH
        "sql"               -> SyntaxConstants.SYNTAX_STYLE_SQL
        "yaml", "yml"       -> SyntaxConstants.SYNTAX_STYLE_YAML
        "toml"              -> SyntaxConstants.SYNTAX_STYLE_NONE
        "properties"        -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
        "cs"                -> SyntaxConstants.SYNTAX_STYLE_CSHARP
        "c", "h"            -> SyntaxConstants.SYNTAX_STYLE_C
        "cpp", "cxx", "hpp" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS
        "go"                -> SyntaxConstants.SYNTAX_STYLE_GO
        "rs"                -> SyntaxConstants.SYNTAX_STYLE_RUST
        "rb"                -> SyntaxConstants.SYNTAX_STYLE_RUBY
        "md"                -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN
        "gradle"            -> SyntaxConstants.SYNTAX_STYLE_GROOVY
        else                -> SyntaxConstants.SYNTAX_STYLE_NONE
    }

    private fun monoFont(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        val preferred = when {
            os.contains("win") -> listOf(
                "Cascadia Mono",       // ships with Windows 11 / Windows Terminal — crisp at all sizes
                "Cascadia Code",       // ligature variant
                "JetBrains Mono",      // popular dev font, excellent readability
                "Fira Code",           // widely installed via dev toolchains
                "Consolas",            // ClearType-optimised, every Windows since Vista
                "Lucida Console",
            )
            os.contains("mac") -> listOf(
                "SF Mono",             // Apple's system monospace (macOS 10.15+)
                "Menlo",               // macOS default monospace
                "JetBrains Mono",
                "Fira Code",
                "Monaco",
                "Courier New",
            )
            else -> listOf(
                "JetBrains Mono",
                "Fira Code",
                "DejaVu Sans Mono",
                "Liberation Mono",
                "Noto Mono",
                "Ubuntu Mono",
            )
        }
        return preferred.firstOrNull { it in available } ?: Font.MONOSPACED
    }

    private data class CaretTarget(val line: Int, val column: Int?)
}
