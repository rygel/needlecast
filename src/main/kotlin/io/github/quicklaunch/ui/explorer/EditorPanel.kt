package io.github.quicklaunch.ui.explorer

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.ExternalEditor
import io.github.quicklaunch.scanner.IS_WINDOWS
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

    private val fileLabel = JLabel("No file open")
    private val editor = RSyntaxTextArea(20, 80).apply {
        isEditable = true
        font = Font(monoFont(), Font.PLAIN, 12)
        antiAliasingEnabled = true
        tabSize = 4
        isCodeFoldingEnabled = true
    }
    private val scrollPane = RTextScrollPane(editor)

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

        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        applyTheme(ctx.config.theme == "dark")
    }

    fun applyTheme(dark: Boolean) {
        val themeFile = if (dark) "monokai.xml" else "idea.xml"
        try {
            val stream = Theme::class.java.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/$themeFile")
            if (stream != null) {
                val theme = Theme.load(stream)
                theme.apply(editor)
            }
        } catch (_: Exception) {
            // Fall back to defaults
        }
        if (dark) {
            editor.background = java.awt.Color(0x1E1E1E)
            editor.foreground = java.awt.Color(0xD4D4D4)
            editor.caretColor = java.awt.Color(0xAEAFAD)
            scrollPane.background = java.awt.Color(0x1E1E1E)
        } else {
            editor.background = java.awt.Color.WHITE
            editor.foreground = java.awt.Color(0x1E1E1E)
            editor.caretColor = java.awt.Color.BLACK
            scrollPane.background = java.awt.Color.WHITE
        }
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

    fun openFile(file: File) {
        val maxBytes = 2 * 1024 * 1024L
        if (file.length() > maxBytes) {
            isLoadingFile = true
            editor.text = "[File too large to display (${file.length() / 1024} KB > 2 MB limit)]"
            editor.syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_NONE
            isLoadingFile = false
            fileLabel.text = file.name
            currentFile = null
            isModified = false
            return
        }

        val content = run {
            var result: String? = null
            for (cs in readCharsets) {
                try { result = Files.readString(file.toPath(), cs); break }
                catch (_: MalformedInputException) { }
            }
            result!! // ISO_8859_1 is last and never throws
        }

        isLoadingFile = true
        editor.syntaxEditingStyle = syntaxStyleFor(file)
        editor.text = content
        editor.caretPosition = 0
        isLoadingFile = false

        currentFile = file
        isModified = false
        fileLabel.text = file.name
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
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        return listOf("JetBrains Mono", "Cascadia Code", "Consolas", "Courier New").firstOrNull { it in available }
            ?: Font.MONOSPACED
    }
}
