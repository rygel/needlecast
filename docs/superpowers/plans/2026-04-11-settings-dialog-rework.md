# Settings Dialog Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 7-tab, 1,095-line `SettingsDialog` with a vertical-sidebar layout and split each category into its own focused panel class.

**Architecture:** A `JList<SidebarEntry>` sidebar on the left drives a `CardLayout` content area on the right. Ten panel files live in `ui/settings/`; `SettingsDialog.kt` becomes a ~150-line wiring shell. The 8 reactive callbacks are grouped into a `SettingsCallbacks` data class.

**Tech Stack:** Kotlin, Java Swing (`JList`, `CardLayout`, `JPanel`, `SwingWorker`), Maven (`mvn compile -pl needlecast-desktop`), JUnit 5.

---

## File map

| Action | Path | Responsibility |
|--------|------|---------------|
| Create | `ui/settings/SettingsCallbacks.kt` | Data class grouping all 8 reactive callbacks |
| Create | `ui/settings/SettingsUtils.kt` | Shared helpers: fonts, monospace detection, command streaming |
| Create | `ui/settings/AppearanceSettingsPanel.kt` | UI font, editor font, syntax theme |
| Create | `ui/settings/TerminalSettingsPanel.kt` | Terminal font (family + size), default shell, terminal colors |
| Create | `ui/settings/LayoutSettingsPanel.kt` | Layout toggles, diagnostics checkboxes |
| Create | `ui/settings/ShortcutsSettingsPanel.kt` | Key-recording shortcut fields |
| Create | `ui/settings/LanguageSettingsPanel.kt` | Language selector |
| Create | `ui/settings/ExternalEditorsSettingsPanel.kt` | External editor list |
| Create | `ui/settings/AiToolsSettingsPanel.kt` | AI CLI checkboxes |
| Create | `ui/settings/RenovateSettingsPanel.kt` | Renovate status + install |
| Create | `ui/settings/ApmSettingsPanel.kt` | APM status + install |
| Modify | `ui/SettingsDialog.kt` | Sidebar shell, replaces tab pane |
| Modify | `ui/MainWindow.kt:556-566` | Update SettingsDialog call-site |
| Create | `test/.../ui/settings/SettingsUtilsTest.kt` | Unit tests for `isMonospaced()` |

---

### Task 1: SettingsCallbacks data class

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsCallbacks.kt`

- [ ] **Step 1: Create `SettingsCallbacks.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import java.awt.Color

data class SettingsCallbacks(
    val onShortcutsChanged: () -> Unit = {},
    val onLayoutChanged: () -> Unit = {},
    val onTerminalColorsChanged: (fg: Color?, bg: Color?) -> Unit = { _, _ -> },
    val onFontSizeChanged: (Int) -> Unit = {},
    val onUiFontChanged: (family: String?, size: Int?) -> Unit = { _, _ -> },
    val onEditorFontChanged: (family: String?, size: Int) -> Unit = { _, _ -> },
    val onTerminalFontChanged: (family: String?) -> Unit = {},
    val onSyntaxThemeChanged: () -> Unit = {},
)
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsCallbacks.kt
git commit -m "feat: add SettingsCallbacks data class for settings dialog rework"
```

---

### Task 2: SettingsUtils — shared helpers + tests

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtils.kt`
- Create: `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtilsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtilsTest.kt`:

```kotlin
package io.github.rygel.needlecast.ui.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Font

class SettingsUtilsTest {

    @Test
    fun `Font_MONOSPACED logical font is detected as monospaced`() {
        assertTrue(isMonospaced(Font.MONOSPACED))
    }

    @Test
    fun `Font_SANS_SERIF logical font is detected as not monospaced`() {
        assertFalse(isMonospaced(Font.SANS_SERIF))
    }

    @Test
    fun `availableMonospaceFamilies returns non-empty list`() {
        val families = availableMonospaceFamilies()
        assertTrue(families.isNotEmpty(), "Expected at least one monospaced font family")
    }

    @Test
    fun `availableFontFamilies returns non-empty list containing Sans Serif`() {
        val families = availableFontFamilies()
        assertTrue(families.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the test — confirm it fails (function not defined)**

```bash
mvn test -pl needlecast-desktop -Dtest=SettingsUtilsTest -q 2>&1 | tail -20
```

Expected: compilation error — `isMonospaced`, `availableMonospaceFamilies`, `availableFontFamilies` not found.

- [ ] **Step 3: Create `SettingsUtils.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.scanner.IS_MAC
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.swing.JTextArea
import javax.swing.SwingWorker
import javax.swing.UIManager

fun monoFont(): String {
    val available = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .availableFontFamilyNames.toHashSet()
    val preferred = when {
        IS_WINDOWS -> listOf("Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Consolas")
        IS_MAC     -> listOf("SF Mono", "Menlo", "JetBrains Mono", "Monaco")
        else       -> listOf("JetBrains Mono", "Fira Code", "DejaVu Sans Mono", "Liberation Mono")
    }
    return preferred.firstOrNull { it in available } ?: Font.MONOSPACED
}

fun uiBaseFont(): Font =
    UIManager.getFont("defaultFont")
        ?: UIManager.getFont("Label.font")
        ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)

fun availableFontFamilies(): List<String> =
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .availableFontFamilyNames.toList().sorted()

fun availableMonospaceFamilies(): List<String> =
    availableFontFamilies().filter { isMonospaced(it) }

fun isMonospaced(name: String): Boolean {
    val font = Font(name, Font.PLAIN, 12)
    val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    val fm = g.getFontMetrics(font)
    val w1 = fm.charWidth('i')
    val w2 = fm.charWidth('W')
    val w3 = fm.charWidth('m')
    g.dispose()
    return w1 > 0 && w1 == w2 && w2 == w3
}

fun buildOutputArea(): JTextArea = JTextArea().apply {
    isEditable = false
    font = Font(monoFont(), Font.PLAIN, 11)
    lineWrap = true
    wrapStyleWord = false
    rows = 8
}

/**
 * Runs [command] via the OS shell and streams stdout+stderr line-by-line into [outputArea].
 * Call on the EDT; the actual process runs in a background thread.
 * [onFinished] is called on the EDT when the process exits.
 */
fun runCommandStreaming(
    command: String,
    outputArea: JTextArea,
    env: Map<String, String> = emptyMap(),
    onFinished: () -> Unit,
) {
    outputArea.text = ""
    outputArea.append("$ $command\n")

    object : SwingWorker<Int, String>() {
        override fun doInBackground(): Int {
            val argv = if (IS_WINDOWS) listOf("cmd", "/c", command) else listOf("sh", "-c", command)
            val pb = ProcessBuilder(argv).redirectErrorStream(true)
            pb.environment()["PATH"] = System.getenv("PATH") ?: ""
            env.forEach { (k, v) -> pb.environment()[k] = v }
            val proc = pb.start()
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) publish(line)
            }
            return proc.waitFor()
        }

        override fun process(chunks: List<String>) {
            for (line in chunks) {
                outputArea.append("$line\n")
                outputArea.caretPosition = outputArea.document.length
            }
        }

        override fun done() {
            val exitCode = try { get() } catch (e: Exception) {
                outputArea.append("\nError: ${e.cause?.message ?: e.message}\n")
                -1
            }
            if (exitCode == 0) outputArea.append("\nCompleted successfully.\n")
            else if (exitCode > 0) outputArea.append("\nCommand failed (exit code $exitCode).\n")
            outputArea.caretPosition = outputArea.document.length
            onFinished()
        }
    }.execute()
}
```

- [ ] **Step 4: Run the tests — confirm they pass**

```bash
mvn test -pl needlecast-desktop -Dtest=SettingsUtilsTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtils.kt
git add needlecast-desktop/src/test/kotlin/io/github/rygel/needlecast/ui/settings/SettingsUtilsTest.kt
git commit -m "feat: add SettingsUtils shared helpers with tests"
```

---

### Task 3: AppearanceSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/AppearanceSettingsPanel.kt`

- [ ] **Step 1: Create `AppearanceSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

class AppearanceSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(GridBagLayout()) {

    init {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val gc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0; gridy = 0; weightx = 1.0
        }

        // ── Fonts section ────────────────────────────────────────────────
        add(JLabel("Fonts").apply { font = font.deriveFont(Font.BOLD) }, gc)

        data class FontChoice(val label: String, val value: String?)
        fun JComboBox<FontChoice>.installRenderer() {
            setRenderer { list, value, index, isSelected, cellHasFocus ->
                javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value?.label ?: "", index, isSelected, cellHasFocus)
            }
        }

        val uiBase     = uiBaseFont()
        val allFonts   = availableFontFamilies()
        val monoFonts  = availableMonospaceFamilies()

        val uiChoices   = listOf(FontChoice("System default", null)) + allFonts.map { FontChoice(it, it) }
        val monoChoices = listOf(FontChoice("Auto (monospace)", null)) + monoFonts.map { FontChoice(it, it) }

        // UI font
        val uiCombo = JComboBox(uiChoices.toTypedArray()).apply {
            installRenderer()
            selectedItem = uiChoices.firstOrNull { it.value == ctx.config.uiFontFamily } ?: uiChoices.first()
            preferredSize = Dimension(220, preferredSize.height)
        }
        val uiSizeSpinner = javax.swing.JSpinner(
            javax.swing.SpinnerNumberModel(ctx.config.uiFontSize ?: uiBase.size, 9, 32, 1)
        ).apply { preferredSize = Dimension(70, preferredSize.height) }
        val uiResetBtn = JButton("Reset").apply {
            addActionListener {
                uiCombo.selectedIndex = 0
                uiSizeSpinner.value = uiBase.size
                ctx.updateConfig(ctx.config.copy(uiFontFamily = null, uiFontSize = null))
                callbacks.onUiFontChanged(null, null)
            }
        }
        fun saveUiFont() {
            val choice = uiCombo.selectedItem as? FontChoice
            val size = uiSizeSpinner.value as Int
            val sizeValue = if (size == uiBase.size) null else size
            ctx.updateConfig(ctx.config.copy(uiFontFamily = choice?.value, uiFontSize = sizeValue))
            callbacks.onUiFontChanged(choice?.value, sizeValue)
        }
        uiCombo.addActionListener { saveUiFont() }
        uiSizeSpinner.addChangeListener { saveUiFont() }
        gc.gridy = 1
        add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JLabel("UI font:")); add(uiCombo); add(JLabel("Size:")); add(uiSizeSpinner); add(uiResetBtn)
        }, gc)

        // Editor font
        val editorCombo = JComboBox(monoChoices.toTypedArray()).apply {
            installRenderer()
            selectedItem = monoChoices.firstOrNull { it.value == ctx.config.editorFontFamily } ?: monoChoices.first()
            preferredSize = Dimension(220, preferredSize.height)
        }
        val editorSizeSpinner = javax.swing.JSpinner(
            javax.swing.SpinnerNumberModel(ctx.config.editorFontSize, 6, 72, 1)
        ).apply { preferredSize = Dimension(70, preferredSize.height) }
        val editorResetBtn = JButton("Reset").apply {
            addActionListener {
                editorCombo.selectedIndex = 0
                editorSizeSpinner.value = 12
                ctx.updateConfig(ctx.config.copy(editorFontFamily = null, editorFontSize = 12))
                callbacks.onEditorFontChanged(null, 12)
            }
        }
        fun saveEditorFont() {
            val choice = editorCombo.selectedItem as? FontChoice
            val size = editorSizeSpinner.value as Int
            ctx.updateConfig(ctx.config.copy(editorFontFamily = choice?.value, editorFontSize = size))
            callbacks.onEditorFontChanged(choice?.value, size)
        }
        editorCombo.addActionListener { saveEditorFont() }
        editorSizeSpinner.addChangeListener { saveEditorFont() }
        gc.gridy = 2
        add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JLabel("Editor font:")); add(editorCombo); add(JLabel("Size:")); add(editorSizeSpinner); add(editorResetBtn)
        }, gc)

        // ── Syntax theme section ─────────────────────────────────────────
        gc.gridy = 3; gc.insets = Insets(16, 4, 4, 4)
        add(JLabel("Syntax Theme").apply { font = font.deriveFont(Font.BOLD) }, gc)

        val syntaxThemes = linkedMapOf(
            "auto"        to "Auto (follows app theme)",
            "monokai"     to "Monokai (dark)",
            "dark"        to "Dark",
            "druid"       to "Druid (dark)",
            "idea"        to "IntelliJ IDEA (light)",
            "eclipse"     to "Eclipse (light)",
            "default"     to "Default (light)",
            "default-alt" to "Default Alt (light)",
            "vs"          to "Visual Studio (light)",
        )
        val themeKeys = syntaxThemes.keys.toList()
        val syntaxThemeCombo = JComboBox(syntaxThemes.values.toTypedArray())
        syntaxThemeCombo.selectedIndex = themeKeys.indexOf(ctx.config.syntaxTheme).takeIf { it >= 0 } ?: 0
        syntaxThemeCombo.addActionListener {
            val key = themeKeys[syntaxThemeCombo.selectedIndex]
            ctx.updateConfig(ctx.config.copy(syntaxTheme = key))
            callbacks.onSyntaxThemeChanged()
        }
        gc.gridy = 4; gc.insets = Insets(4, 4, 4, 4)
        add(syntaxThemeCombo, gc)

        // Spacer
        gc.gridy = 5; gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0
        add(JPanel(), gc)
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/AppearanceSettingsPanel.kt
git commit -m "feat: add AppearanceSettingsPanel (fonts + syntax theme)"
```

---

### Task 4: TerminalSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/TerminalSettingsPanel.kt`

- [ ] **Step 1: Create `TerminalSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.ui.ShellDetector
import io.github.rygel.needlecast.ui.ShellInfo
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

class TerminalSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(GridBagLayout()) {

    private var shellWorker: SwingWorker<List<ShellInfo>, Unit>? = null

    // Shell combo state — built in init, populated by addNotify()
    private val manualItem  = ShellInfo("Manual entry…", "")
    private val osDefaultLabel = when {
        IS_WINDOWS -> "OS default (cmd.exe)"
        IS_MAC     -> "OS default (zsh)"
        else       -> "OS default (bash)"
    }
    private val osDefault  = ShellInfo(osDefaultLabel, "")
    private val shellCombo = JComboBox(arrayOf<Any>(osDefault, manualItem))
    private val shellField = JTextField(ctx.config.defaultShell ?: "", 28).apply {
        isVisible = ctx.config.defaultShell?.isNotBlank() == true
    }

    init {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val gc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0; gridy = 0; weightx = 1.0
        }

        // ── Terminal font section ─────────────────────────────────────────
        add(JLabel("Terminal Font").apply { font = font.deriveFont(Font.BOLD) }, gc)

        data class FontChoice(val label: String, val value: String?)
        val monoFonts  = availableMonospaceFamilies()
        val monoChoices = listOf(FontChoice("Auto (monospace)", null)) + monoFonts.map { FontChoice(it, it) }

        val terminalCombo = JComboBox(monoChoices.toTypedArray()).apply {
            setRenderer { list, value, index, isSelected, cellHasFocus ->
                javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value?.label ?: "", index, isSelected, cellHasFocus)
            }
            selectedItem = monoChoices.firstOrNull { it.value == ctx.config.terminalFontFamily } ?: monoChoices.first()
            preferredSize = Dimension(220, preferredSize.height)
        }
        terminalCombo.addActionListener {
            val choice = terminalCombo.selectedItem as? FontChoice
            ctx.updateConfig(ctx.config.copy(terminalFontFamily = choice?.value))
            callbacks.onTerminalFontChanged(choice?.value)
        }

        val fontSizeSpinner = javax.swing.JSpinner(
            javax.swing.SpinnerNumberModel(ctx.config.terminalFontSize, 8, 36, 1)
        ).apply { preferredSize = Dimension(70, preferredSize.height) }
        fontSizeSpinner.addChangeListener {
            val size = fontSizeSpinner.value as Int
            ctx.updateConfig(ctx.config.copy(terminalFontSize = size))
            callbacks.onFontSizeChanged(size)
        }

        gc.gridy = 1
        add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JLabel("Family:")); add(terminalCombo)
            add(JLabel("Size:")); add(fontSizeSpinner)
        }, gc)

        // ── Default shell section ─────────────────────────────────────────
        gc.gridy = 2; gc.insets = Insets(16, 4, 4, 4)
        add(JLabel("Default Shell").apply { font = font.deriveFont(Font.BOLD) }, gc)

        gc.gridy = 3; gc.insets = Insets(4, 4, 4, 4)
        add(JLabel("Default shell (per-project shell overrides this):"), gc)

        shellCombo.addActionListener {
            when (shellCombo.selectedItem) {
                osDefault  -> { shellField.isVisible = false; ctx.updateConfig(ctx.config.copy(defaultShell = null)) }
                manualItem -> { shellField.isVisible = true }
                is ShellInfo -> {
                    val s = shellCombo.selectedItem as ShellInfo
                    shellField.isVisible = false
                    ctx.updateConfig(ctx.config.copy(defaultShell = s.command))
                }
            }
            shellField.revalidate(); shellField.repaint()
            revalidate(); repaint()
        }

        gc.gridy = 4; add(shellCombo, gc)
        gc.gridy = 5; add(shellField, gc)

        val applyShellBtn = JButton("Apply").apply {
            isVisible = shellField.isVisible
            addActionListener {
                val v = shellField.text.trim().takeIf { it.isNotEmpty() }
                ctx.updateConfig(ctx.config.copy(defaultShell = v))
            }
        }
        shellField.addPropertyChangeListener("visible") { applyShellBtn.isVisible = shellField.isVisible }

        gc.gridy = 6; gc.insets = Insets(0, 4, 4, 4)
        add(JLabel("<html><i>Takes effect on next terminal activation.</i></html>").apply {
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
        }, gc)
        gc.gridy = 7; gc.insets = Insets(4, 4, 4, 4); gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.EAST
        add(applyShellBtn, gc)

        // ── Terminal colors section ───────────────────────────────────────
        gc.gridy = 8; gc.insets = Insets(16, 4, 4, 4); gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
        add(JLabel("Terminal Colors").apply { font = font.deriveFont(Font.BOLD) }, gc)

        fun colorSwatch(hex: String?): JButton {
            val btn = JButton()
            btn.preferredSize = Dimension(60, 22)
            btn.background = hex?.let { runCatching { Color.decode(it) }.getOrNull() } ?: Color.GRAY
            btn.isOpaque = true
            btn.isBorderPainted = true
            return btn
        }
        fun hexOrNull(c: Color?): String? = c?.let { "#%02X%02X%02X".format(it.red, it.green, it.blue) }

        var currentFgColor: Color? = ctx.config.terminalForeground?.let { runCatching { Color.decode(it) }.getOrNull() }
        var currentBgColor: Color? = ctx.config.terminalBackground?.let { runCatching { Color.decode(it) }.getOrNull() }

        val fgSwatch = colorSwatch(ctx.config.terminalForeground)
        val bgSwatch = colorSwatch(ctx.config.terminalBackground)

        fun saveAndApply() {
            ctx.updateConfig(ctx.config.copy(
                terminalForeground = hexOrNull(currentFgColor),
                terminalBackground = hexOrNull(currentBgColor),
            ))
            callbacks.onTerminalColorsChanged(currentFgColor, currentBgColor)
        }

        fgSwatch.addActionListener {
            val chosen = javax.swing.JColorChooser.showDialog(this, "Terminal Foreground", currentFgColor) ?: return@addActionListener
            currentFgColor = chosen; fgSwatch.background = chosen; saveAndApply()
        }
        bgSwatch.addActionListener {
            val chosen = javax.swing.JColorChooser.showDialog(this, "Terminal Background", currentBgColor) ?: return@addActionListener
            currentBgColor = chosen; bgSwatch.background = chosen; saveAndApply()
        }

        val resetColorsBtn = JButton("Reset").apply {
            toolTipText = "Reset to theme defaults"
            addActionListener {
                currentFgColor = null; currentBgColor = null
                fgSwatch.background = Color.GRAY; bgSwatch.background = Color.GRAY
                saveAndApply()
            }
        }

        gc.gridy = 9; gc.insets = Insets(4, 4, 4, 4)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JLabel("Foreground:")); add(fgSwatch)
            add(JLabel("Background:")); add(bgSwatch)
            add(resetColorsBtn)
        }, gc)

        gc.gridy = 10; gc.insets = Insets(0, 4, 4, 4)
        add(JLabel("<html><i>Click a swatch to pick a color. Takes full effect in new terminal tabs.</i></html>").apply {
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
        }, gc)

        // Spacer
        gc.gridy = 11; gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0; gc.insets = Insets(4, 4, 4, 4)
        add(JPanel(), gc)
    }

    override fun addNotify() {
        super.addNotify()
        if (shellWorker != null) return
        val worker = buildShellWorker()
        shellWorker = worker
        SwingUtilities.getWindowAncestor(this)?.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) { worker.cancel(true) }
        })
        worker.execute()
    }

    private fun buildShellWorker(): SwingWorker<List<ShellInfo>, Unit> {
        val currentShell = ctx.config.defaultShell
        return object : SwingWorker<List<ShellInfo>, Unit>() {
            override fun doInBackground() = ShellDetector.detect()
            override fun done() {
                if (isCancelled) return
                val shells = try { get() } catch (_: Exception) { emptyList() }
                val currentSelected = shellCombo.selectedItem
                shellCombo.removeAllItems()
                shellCombo.addItem(osDefault)
                shells.forEach { shellCombo.addItem(it) }
                shellCombo.addItem(manualItem)
                shellCombo.setRenderer { list, value, index, sel, focus ->
                    javax.swing.DefaultListCellRenderer()
                        .getListCellRendererComponent(list, value, index, sel, focus)
                        .also { c -> if (value is ShellInfo) (c as? JLabel)?.text = value.displayName }
                }
                // Restore selection
                val current = currentShell?.trim()
                if (current.isNullOrBlank()) {
                    shellCombo.selectedItem = osDefault
                } else {
                    val match = shells.firstOrNull { it.command == current }
                    shellCombo.selectedItem = match ?: manualItem
                    if (match == null) shellField.text = current
                }
                if (currentSelected == manualItem) shellCombo.selectedItem = manualItem
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/TerminalSettingsPanel.kt
git commit -m "feat: add TerminalSettingsPanel (font, shell, colors)"
```

---

### Task 5: LayoutSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/LayoutSettingsPanel.kt`

- [ ] **Step 1: Create `LayoutSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel

class LayoutSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(GridBagLayout()) {

    init {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val gc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0; gridy = 0; weightx = 1.0
        }

        // ── Layout section ────────────────────────────────────────────────
        add(JLabel("Layout").apply { font = font.deriveFont(Font.BOLD) }, gc)

        val tabsOnTopCb = JCheckBox("Show panel tabs at the top", ctx.config.tabsOnTop)
        gc.gridy = 1
        add(tabsOnTopCb, gc)
        tabsOnTopCb.addActionListener {
            ctx.updateConfig(ctx.config.copy(tabsOnTop = tabsOnTopCb.isSelected))
            callbacks.onLayoutChanged()
        }

        val dockingHighlightCb = JCheckBox(
            "Highlight active docking panel border  [alpha]",
            ctx.config.dockingActiveHighlight,
        ).apply {
            toolTipText = "ModernDocking draws a border around the currently active panel. Experimental — may look odd with some themes."
        }
        gc.gridy = 2
        add(dockingHighlightCb, gc)
        dockingHighlightCb.addActionListener {
            ctx.updateConfig(ctx.config.copy(dockingActiveHighlight = dockingHighlightCb.isSelected))
            io.github.andrewauclair.moderndocking.settings.Settings.setActiveHighlighterEnabled(dockingHighlightCb.isSelected)
        }

        // ── Diagnostics section ───────────────────────────────────────────
        gc.gridy = 3; gc.insets = Insets(16, 4, 4, 4)
        add(JLabel("Diagnostics").apply { font = font.deriveFont(Font.BOLD) }, gc)

        val clickTraceCb = JCheckBox("Enable project tree click tracing", ctx.config.treeClickTraceEnabled)
        gc.gridy = 4; gc.insets = Insets(4, 4, 4, 4)
        add(clickTraceCb, gc)
        clickTraceCb.addActionListener {
            ctx.updateConfig(ctx.config.copy(treeClickTraceEnabled = clickTraceCb.isSelected))
        }

        val edtTraceCb = JCheckBox("Enable EDT stall monitor", ctx.config.edtStallTraceEnabled)
        gc.gridy = 5
        add(edtTraceCb, gc)
        edtTraceCb.addActionListener {
            ctx.updateConfig(ctx.config.copy(edtStallTraceEnabled = edtTraceCb.isSelected))
        }

        gc.gridy = 6; gc.insets = Insets(0, 4, 4, 4)
        add(JLabel("<html><i>Logs go to ~/.needlecast/needlecast.log. Enable only while diagnosing lag.</i></html>").apply {
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            foreground = foreground.darker()
        }, gc)

        // Spacer
        gc.gridy = 7; gc.insets = Insets(4, 4, 4, 4)
        gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0
        add(JPanel(), gc)
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/LayoutSettingsPanel.kt
git commit -m "feat: add LayoutSettingsPanel (layout toggles + diagnostics)"
```

---

### Task 6: ShortcutsSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ShortcutsSettingsPanel.kt`

- [ ] **Step 1: Create `ShortcutsSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke

class ShortcutsSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(BorderLayout(0, 8)) {

    init {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val current = ctx.config.shortcuts.toMutableMap()
        val fields  = mutableMapOf<String, JTextField>()

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }
        val gc = GridBagConstraints().apply { insets = Insets(3, 4, 3, 4) }

        defaultShortcuts.entries.forEachIndexed { row, (id, default) ->
            gc.gridy = row

            gc.gridx = 0; gc.weightx = 0.4; gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.WEST
            grid.add(JLabel(actionLabels[id] ?: id), gc)

            val field = JTextField(current[id] ?: default, 16).apply {
                name = id
                toolTipText = "Click and press a key combination to record"
                addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        val ks  = KeyStroke.getKeyStrokeForEvent(e)
                        val txt = ks.toString()
                            .replace("pressed ", "")
                            .replace("released ", "")
                            .trim()
                        if (txt.isNotEmpty()) { text = txt; e.consume() }
                    }
                })
            }
            fields[id] = field
            gc.gridx = 1; gc.weightx = 0.6; gc.fill = GridBagConstraints.HORIZONTAL
            grid.add(field, gc)

            gc.gridx = 2; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
            grid.add(JButton("Reset").apply {
                addActionListener { field.text = default }
            }, gc)
        }

        val saveButton = JButton("Save Shortcuts").apply {
            addActionListener {
                val updated = fields.mapValues { (id, f) ->
                    val v = f.text.trim()
                    if (v == defaultShortcuts[id]) null else v
                }.filterValues { it != null }.mapValues { it.value!! }
                ctx.updateConfig(ctx.config.copy(shortcuts = updated))
                callbacks.onShortcutsChanged()
                JOptionPane.showMessageDialog(
                    this@ShortcutsSettingsPanel,
                    ctx.i18n.translate("settings.saved"),
                    ctx.i18n.translate("settings.savedTitle"),
                    JOptionPane.INFORMATION_MESSAGE,
                )
            }
        }

        add(JLabel("<html><i>Click a field and press a key combination to record it. Reset restores the default.</i></html>").apply {
            border = BorderFactory.createEmptyBorder(6, 8, 2, 8)
        }, BorderLayout.NORTH)
        add(JScrollPane(grid).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(saveButton) }, BorderLayout.SOUTH)
    }

    companion object {
        val defaultShortcuts: LinkedHashMap<String, String> = linkedMapOf(
            "rescan"            to "F5",
            "activate-terminal" to "ctrl T",
            "focus-projects"    to "ctrl 1",
            "focus-explorer"    to "ctrl 2",
            "focus-terminal"    to "ctrl 3",
            "project-switcher"  to "ctrl P",
        )
        val actionLabels: Map<String, String> = mapOf(
            "rescan"            to "Rescan projects (F5)",
            "activate-terminal" to "Activate terminal",
            "focus-projects"    to "Focus project list",
            "focus-explorer"    to "Focus file explorer",
            "focus-terminal"    to "Focus terminal",
            "project-switcher"  to "Global project switcher",
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ShortcutsSettingsPanel.kt
git commit -m "feat: add ShortcutsSettingsPanel (key recording, moves defaultShortcuts here)"
```

---

### Task 7: LanguageSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/LanguageSettingsPanel.kt`

- [ ] **Step 1: Create `LanguageSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.outerstellar.i18n.Language
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

class LanguageSettingsPanel(
    private val ctx: AppContext,
) : JPanel(GridBagLayout()) {

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        val i18n      = ctx.i18n
        val languages = Language.availableLanguages()
        val currentLocale = i18n.getLocale()

        val combo = JComboBox(languages.map { it.nativeName }.toTypedArray())
        val currentIdx = languages.indexOfFirst { it.locale.language == currentLocale.language }
        if (currentIdx >= 0) combo.selectedIndex = currentIdx

        val applyButton = JButton(i18n.translate("settings.language.apply")).apply {
            addActionListener {
                val selected = languages[combo.selectedIndex]
                ctx.switchLocale(selected.locale)
                JOptionPane.showMessageDialog(
                    this@LanguageSettingsPanel,
                    i18n.translate("settings.language.applied", selected.displayName),
                    i18n.translate("settings.language.title"),
                    JOptionPane.INFORMATION_MESSAGE,
                )
            }
        }

        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        gc.gridy = 0; gc.gridx = 0; gc.weightx = 0.0; gc.anchor = GridBagConstraints.WEST
        add(JLabel(i18n.translate("settings.language.select")), gc)

        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
        add(combo, gc)

        gc.gridy = 1; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 0.0
        gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.WEST
        add(JLabel("<html><i>${i18n.translate("settings.language.description")}</i></html>").apply {
            foreground = foreground.darker()
        }, gc)

        gc.gridy = 2; gc.gridwidth = 2; gc.fill = GridBagConstraints.NONE; gc.anchor = GridBagConstraints.EAST
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply { add(applyButton) }, gc)

        gc.gridy = 3; gc.weighty = 1.0; gc.fill = GridBagConstraints.BOTH
        add(JPanel(), gc)
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/LanguageSettingsPanel.kt
git commit -m "feat: add LanguageSettingsPanel"
```

---

### Task 8: ExternalEditorsSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ExternalEditorsSettingsPanel.kt`

- [ ] **Step 1: Create `ExternalEditorsSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ExternalEditor
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel

class ExternalEditorsSettingsPanel(
    private val ctx: AppContext,
) : JPanel(BorderLayout(0, 4)) {

    init {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val model = DefaultListModel<ExternalEditor>().apply {
            ctx.config.externalEditors.forEach { addElement(it) }
        }
        val list = JList(model).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }

        val addButton    = JButton("+")
        val removeButton = JButton("−").apply { isEnabled = false }

        list.addListSelectionListener {
            removeButton.isEnabled = list.selectedValue != null
        }

        addButton.addActionListener {
            val nameField = JTextField(12)
            val execField = JTextField(12)
            val form = JPanel(GridBagLayout()).apply {
                val c = GridBagConstraints().apply {
                    insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST
                }
                c.gridx = 0; c.gridy = 0; add(JLabel("Name:"), c)
                c.gridx = 1; add(nameField, c)
                c.gridx = 0; c.gridy = 1; add(JLabel("Executable:"), c)
                c.gridx = 1; add(execField, c)
            }
            val result = JOptionPane.showConfirmDialog(
                this, form, "Add External Editor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
            )
            if (result == JOptionPane.OK_OPTION) {
                val name = nameField.text.trim()
                val exec = execField.text.trim()
                if (name.isNotEmpty() && exec.isNotEmpty()) {
                    val editor = ExternalEditor(name, exec)
                    model.addElement(editor)
                    saveEditors(model)
                }
            }
        }

        removeButton.addActionListener {
            val selected = list.selectedValue ?: return@addActionListener
            model.removeElement(selected)
            saveEditors(model)
            removeButton.isEnabled = false
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(addButton); add(removeButton)
        }

        add(JLabel("Editors shown in the 'Open with' menu:"), BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)
        add(toolbar, BorderLayout.SOUTH)
    }

    private fun saveEditors(model: DefaultListModel<ExternalEditor>) {
        val editors = (0 until model.size).map { model.getElementAt(it) }
        ctx.updateConfig(ctx.config.copy(externalEditors = editors))
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ExternalEditorsSettingsPanel.kt
git commit -m "feat: add ExternalEditorsSettingsPanel"
```

---

### Task 9: AiToolsSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/AiToolsSettingsPanel.kt`

- [ ] **Step 1: Create `AiToolsSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.AiCliDefinition
import io.github.rygel.needlecast.ui.AiCli
import io.github.rygel.needlecast.ui.KNOWN_AI_CLIS
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

class AiToolsSettingsPanel(
    private val ctx: AppContext,
) : JPanel(BorderLayout(0, 6)) {

    init {
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)

        val enabledMap = ctx.config.aiCliEnabled.toMutableMap()
        val builtIn    = KNOWN_AI_CLIS.map { it to false }
        val customDefs = ctx.config.customAiClis.map { AiCli(it.name, it.command, it.description) to true }
        val allClis    = (builtIn + customDefs).toMutableList()

        val listPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }

        fun rebuildList() {
            listPanel.removeAll()
            val gc = GridBagConstraints().apply { insets = Insets(2, 4, 2, 4); anchor = GridBagConstraints.WEST }
            allClis.forEachIndexed { i, (cli, isCustom) ->
                gc.gridy = i
                val enabled = enabledMap[cli.command] != false
                val cb = JCheckBox(cli.name, enabled).apply {
                    toolTipText = cli.description
                    addActionListener {
                        enabledMap[cli.command] = isSelected
                        ctx.updateConfig(ctx.config.copy(aiCliEnabled = enabledMap.toMap()))
                    }
                }
                gc.gridx = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
                listPanel.add(cb, gc)
                gc.gridx = 1; gc.weightx = 0.0
                listPanel.add(JLabel("<html><tt>${cli.command}</tt></html>").apply {
                    foreground = Color(0x888888)
                }, gc)
                gc.gridx = 2; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
                listPanel.add(JLabel(cli.description).apply {
                    font = font.deriveFont(Font.PLAIN, 11f)
                    foreground = Color(0x888888)
                }, gc)
            }
            gc.gridy = allClis.size; gc.gridx = 0; gc.gridwidth = 3
            gc.weighty = 1.0; gc.fill = GridBagConstraints.BOTH
            listPanel.add(JPanel(), gc)
            listPanel.revalidate(); listPanel.repaint()
        }

        rebuildList()

        val addBtn = JButton("+ Add Custom CLI").apply {
            addActionListener {
                val nameField = JTextField(14)
                val cmdField  = JTextField(14)
                val descField = JTextField(20)
                val form = JPanel(GridBagLayout()).apply {
                    val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST }
                    gc.gridx = 0; gc.gridy = 0; add(JLabel("Name:"), gc)
                    gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(nameField, gc)
                    gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; add(JLabel("Command:"), gc)
                    gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(cmdField, gc)
                    gc.gridx = 0; gc.gridy = 2; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; add(JLabel("Description:"), gc)
                    gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; add(descField, gc)
                }
                if (JOptionPane.showConfirmDialog(this@AiToolsSettingsPanel, form, "Add Custom CLI",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return@addActionListener
                val name = nameField.text.trim()
                val cmd  = cmdField.text.trim()
                if (name.isEmpty() || cmd.isEmpty()) return@addActionListener
                val def = AiCliDefinition(name, cmd, descField.text.trim())
                ctx.updateConfig(ctx.config.copy(customAiClis = ctx.config.customAiClis + def))
                allClis.add(AiCli(name, cmd, descField.text.trim()) to true)
                rebuildList()
            }
        }

        val removeBtn = JButton("− Remove Custom CLI").apply {
            addActionListener {
                val customOnly = allClis.filter { it.second }.map { it.first }
                if (customOnly.isEmpty()) {
                    JOptionPane.showMessageDialog(this@AiToolsSettingsPanel, "No custom CLIs to remove.", "Remove", JOptionPane.INFORMATION_MESSAGE)
                    return@addActionListener
                }
                val names  = customOnly.map { it.name }.toTypedArray()
                val choice = JOptionPane.showInputDialog(this@AiToolsSettingsPanel, "Select CLI to remove:",
                    "Remove Custom CLI", JOptionPane.PLAIN_MESSAGE, null, names, names[0]) as? String ?: return@addActionListener
                val toRemove = customOnly.first { it.name == choice }
                ctx.updateConfig(ctx.config.copy(customAiClis = ctx.config.customAiClis.filter { it.command != toRemove.command }))
                allClis.removeAll { it.second && it.first.command == toRemove.command }
                rebuildList()
            }
        }

        add(JLabel("<html>Check the AI tools shown in the project tree and AI Tools menu.<br>" +
            "Built-in tools are detected automatically; custom tools use PATH lookup.</html>").apply {
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
        }, BorderLayout.NORTH)
        add(JScrollPane(listPanel).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(addBtn); add(removeBtn) }, BorderLayout.SOUTH)
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/AiToolsSettingsPanel.kt
git commit -m "feat: add AiToolsSettingsPanel"
```

---

### Task 10: RenovateSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/RenovateSettingsPanel.kt`

- [ ] **Step 1: Create `RenovateSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingWorker

class RenovateSettingsPanel(
    private val ctx: AppContext,
    @Suppress("UNUSED_PARAMETER") sendToTerminal: (String) -> Unit = {},
) : JPanel(BorderLayout(0, 8)) {

    private val statusLabel  = JLabel("Checking…", SwingConstants.CENTER).apply { font = font.deriveFont(Font.BOLD) }
    private val versionLabel = JLabel("", SwingConstants.CENTER)

    init {
        border = BorderFactory.createEmptyBorder(12, 14, 12, 14)

        val infoLabel = JLabel(
            "<html>Renovate keeps your dependencies up to date by opening automated PRs.<br>" +
            "Install it globally, then use the <b>Renovate</b> panel (Panels menu) to run it.</html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel,  BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val outputArea = buildOutputArea()

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel   = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        val installButtons = mutableListOf<JButton>()

        buildInstallOptions().forEach { (label, cmd) ->
            val btn = JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    installButtons.forEach { it.isEnabled = false }
                    runCommandStreaming(cmd, outputArea) {
                        installButtons.forEach { it.isEnabled = true }
                        checkRenovate()
                    }
                }
            }
            installButtons.add(btn)
            buttonsPanel.add(btn)
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply { addActionListener { checkRenovate() } }

        val topSection = JPanel(BorderLayout(0, 8)).apply {
            add(infoLabel, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 8)).apply {
                add(statusPanel, BorderLayout.NORTH)
                add(installPanel, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(recheckButton) }, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }

        add(topSection, BorderLayout.NORTH)
        add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
            preferredSize = Dimension(0, 160)
        }, BorderLayout.CENTER)

        checkRenovate()
    }

    private fun checkRenovate() {
        statusLabel.text = "Checking…"
        versionLabel.text = ""
        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val found = ProcessExecutor.isOnPath("renovate")
                if (!found) return false to ""
                val version = ProcessExecutor.run(listOf("renovate", "--version"), timeoutMs = 5_000L)
                    ?.output?.lines()?.firstOrNull()?.trim() ?: ""
                return true to version
            }
            override fun done() {
                val (found, version) = get()
                if (found) {
                    statusLabel.text = "✓  Renovate is installed"
                    statusLabel.foreground = Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "version $version" else ""
                } else {
                    statusLabel.text = "✗  Renovate not found on PATH"
                    statusLabel.foreground = Color(0xF44336)
                    versionLabel.text = "Use one of the buttons below to install it"
                }
            }
        }.execute()
    }

    private fun buildInstallOptions(): List<Pair<String, String>> = buildList {
        val npm = if (ProcessExecutor.isOnPath("pnpm")) "pnpm" else "npm"
        add(npm to "$npm add -g renovate")
        if (!IS_WINDOWS) add("Homebrew" to "brew install renovate")
        if (IS_WINDOWS)  add("Scoop"    to "scoop install renovate")
        if (IS_WINDOWS)  add("Chocolatey" to "choco install renovate")
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/RenovateSettingsPanel.kt
git commit -m "feat: add RenovateSettingsPanel"
```

---

### Task 11: ApmSettingsPanel

**Files:**
- Create: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ApmSettingsPanel.kt`

- [ ] **Step 1: Create `ApmSettingsPanel.kt`**

```kotlin
package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingWorker

class ApmSettingsPanel(
    private val ctx: AppContext,
    @Suppress("UNUSED_PARAMETER") sendToTerminal: (String) -> Unit = {},
) : JPanel(BorderLayout(0, 8)) {

    private val statusLabel  = JLabel("Checking…", SwingConstants.CENTER).apply { font = font.deriveFont(Font.BOLD) }
    private val versionLabel = JLabel("", SwingConstants.CENTER)

    init {
        border = BorderFactory.createEmptyBorder(12, 14, 12, 14)

        val infoLabel = JLabel(
            "<html>APM (Agent Package Manager) by Microsoft manages AI agent dependencies:<br>" +
            "skills, prompts, instructions, plugins, and MCP servers via an <code>apm.yml</code> manifest.<br>" +
            "Projects with <code>apm.yml</code> are automatically detected and their commands surfaced.</html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel,  BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val outputArea = buildOutputArea()

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel   = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        val installButtons = mutableListOf<JButton>()

        buildInstallOptions().forEach { (label, cmd) ->
            val btn = JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    installButtons.forEach { it.isEnabled = false }
                    runCommandStreaming(cmd, outputArea) {
                        installButtons.forEach { it.isEnabled = true }
                        checkApm()
                    }
                }
            }
            installButtons.add(btn)
            buttonsPanel.add(btn)
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply { addActionListener { checkApm() } }

        val topSection = JPanel(BorderLayout(0, 8)).apply {
            add(infoLabel, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 8)).apply {
                add(statusPanel, BorderLayout.NORTH)
                add(installPanel, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(recheckButton) }, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }

        add(topSection, BorderLayout.NORTH)
        add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
            preferredSize = Dimension(0, 160)
        }, BorderLayout.CENTER)

        checkApm()
    }

    private fun checkApm() {
        statusLabel.text = "Checking…"
        versionLabel.text = ""
        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val found = ProcessExecutor.isOnPath("apm")
                if (!found) return false to ""
                val version = ProcessExecutor.run(listOf("apm", "--version"), timeoutMs = 5_000L)
                    ?.output?.lines()?.firstOrNull()?.trim() ?: ""
                return true to version
            }
            override fun done() {
                val (found, version) = get()
                if (found) {
                    statusLabel.text = "✓  APM is installed"
                    statusLabel.foreground = Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "version $version" else ""
                } else {
                    statusLabel.text = "✗  APM not found on PATH"
                    statusLabel.foreground = Color(0xF44336)
                    versionLabel.text = "Use one of the buttons below to install it"
                }
            }
        }.execute()
    }

    private fun buildInstallOptions(): List<Pair<String, String>> = buildList {
        if (!IS_WINDOWS) add("curl"       to "curl -sSL https://aka.ms/apm-unix | sh")
        if (IS_WINDOWS)  add("PowerShell" to "irm https://aka.ms/apm-windows | iex")
        if (!IS_WINDOWS) add("Homebrew"   to "brew install microsoft/apm/apm")
        add("pip"                         to "pip install apm-cli")
        if (IS_WINDOWS)  add("Scoop"      to "scoop install apm")
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/settings/ApmSettingsPanel.kt
git commit -m "feat: add ApmSettingsPanel"
```

---

### Task 12: Rework SettingsDialog + update MainWindow

This task replaces the tab pane in `SettingsDialog` with the sidebar shell and updates the call-site in `MainWindow`.

**Files:**
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SettingsDialog.kt`
- Modify: `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt:556-566`

- [ ] **Step 1: Replace `SettingsDialog.kt` entirely**

The new file keeps the `SettingsDialog` name and package. It removes all `buildXxxTab()` methods and the companion object (moved to `ShortcutsSettingsPanel`), replacing everything with the sidebar + `CardLayout` wiring. Paste this content as the full file:

```kotlin
package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ui.settings.AiToolsSettingsPanel
import io.github.rygel.needlecast.ui.settings.ApmSettingsPanel
import io.github.rygel.needlecast.ui.settings.AppearanceSettingsPanel
import io.github.rygel.needlecast.ui.settings.ExternalEditorsSettingsPanel
import io.github.rygel.needlecast.ui.settings.LanguageSettingsPanel
import io.github.rygel.needlecast.ui.settings.LayoutSettingsPanel
import io.github.rygel.needlecast.ui.settings.RenovateSettingsPanel
import io.github.rygel.needlecast.ui.settings.SettingsCallbacks
import io.github.rygel.needlecast.ui.settings.ShortcutsSettingsPanel
import io.github.rygel.needlecast.ui.settings.TerminalSettingsPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.UIManager

class SettingsDialog(
    owner: JFrame,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JDialog(owner, ctx.i18n.translate("settings.title"), true) {

    private sealed class SidebarEntry {
        data class Header(val title: String)   : SidebarEntry()
        data class Category(val key: String, val label: String) : SidebarEntry()
    }

    init {
        size = Dimension(760, 560)
        minimumSize = Dimension(640, 460)
        setLocationRelativeTo(owner)
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val sidebarModel = DefaultListModel<SidebarEntry>().apply {
            listOf(
                SidebarEntry.Header("GENERAL"),
                SidebarEntry.Category("appearance",  "Appearance"),
                SidebarEntry.Category("layout",      "Layout"),
                SidebarEntry.Category("terminal",    "Terminal"),
                SidebarEntry.Header("INTEGRATIONS"),
                SidebarEntry.Category("editors",     "External Editors"),
                SidebarEntry.Category("ai-tools",    "AI Tools"),
                SidebarEntry.Category("renovate",    "Renovate"),
                SidebarEntry.Header("ADVANCED"),
                SidebarEntry.Category("apm",         "APM"),
                SidebarEntry.Category("shortcuts",   "Shortcuts"),
                SidebarEntry.Category("language",    "Language"),
            ).forEach { addElement(it) }
        }

        val cardLayout   = CardLayout()
        val contentPanel = JPanel(cardLayout).apply {
            add(AppearanceSettingsPanel(ctx, callbacks),        "appearance")
            add(LayoutSettingsPanel(ctx, callbacks),            "layout")
            add(TerminalSettingsPanel(ctx, callbacks),          "terminal")
            add(ExternalEditorsSettingsPanel(ctx),              "editors")
            add(AiToolsSettingsPanel(ctx),                      "ai-tools")
            add(RenovateSettingsPanel(ctx, sendToTerminal),     "renovate")
            add(ApmSettingsPanel(ctx, sendToTerminal),          "apm")
            add(ShortcutsSettingsPanel(ctx, callbacks),         "shortcuts")
            add(LanguageSettingsPanel(ctx),                     "language")
        }

        var lastValidIndex = 1  // index of "appearance" in the model

        val sidebarList = JList(sidebarModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            setCellRenderer(SidebarCellRenderer())
            addListSelectionListener { e ->
                if (e.valueIsAdjusting) return@addListSelectionListener
                when (val entry = selectedValue) {
                    is SidebarEntry.Header   -> { selectedIndex = lastValidIndex; return@addListSelectionListener }
                    is SidebarEntry.Category -> { lastValidIndex = selectedIndex; cardLayout.show(contentPanel, entry.key) }
                    null -> {}
                }
            }
            selectedIndex = 1
        }

        val sidebarScroll = JScrollPane(sidebarList).apply {
            preferredSize = Dimension(160, 0)
            border = BorderFactory.createMatteBorder(0, 0, 0, 1,
                UIManager.getColor("Separator.foreground") ?: Color.GRAY)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        contentPane = JPanel(BorderLayout()).apply {
            add(sidebarScroll, BorderLayout.WEST)
            add(contentPanel,  BorderLayout.CENTER)
        }
    }

    private inner class SidebarCellRenderer : ListCellRenderer<SidebarEntry> {
        private val headerLabel = JLabel().apply {
            border = BorderFactory.createEmptyBorder(8, 8, 2, 8)
            font   = font.deriveFont(Font.BOLD, 10f)
            foreground = Color.GRAY
            isOpaque   = true
        }
        private val categoryLabel = JLabel().apply {
            border   = BorderFactory.createEmptyBorder(4, 16, 4, 8)
            isOpaque = true
        }

        override fun getListCellRendererComponent(
            list: JList<out SidebarEntry>, value: SidebarEntry?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component = when (value) {
            is SidebarEntry.Header -> {
                headerLabel.text       = value.title
                headerLabel.background = list.background
                headerLabel
            }
            is SidebarEntry.Category -> {
                categoryLabel.text       = value.label
                categoryLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
                categoryLabel.background = if (isSelected) list.selectionBackground else list.background
                categoryLabel
            }
            null -> categoryLabel.also { it.text = "" }
        }
    }
}
```

- [ ] **Step 2: Update the MainWindow call-site**

In `needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt`, replace lines 556–566:

Old:
```kotlin
SettingsDialog(this@MainWindow, ctx,
    sendToTerminal = { cmd -> terminalPanel.sendInput(cmd) },
    onShortcutsChanged = { reloadShortcuts() },
    onLayoutChanged = { resetLayout() },
    onTerminalColorsChanged = { fg, bg -> terminalPanel.applyTerminalColors(fg, bg) },
    onFontSizeChanged = { size -> terminalPanel.applyFontSize(size) },
    onUiFontChanged = { _, _ -> applyUiFontFromConfig() },
    onEditorFontChanged = { family, size -> explorerPanel.applyEditorFont(family, size) },
    onTerminalFontChanged = { family -> terminalPanel.applyFontFamily(family) },
    onSyntaxThemeChanged = { explorerPanel.applyTheme(ThemeRegistry.isDark(ctx.config.theme)) },
).isVisible = true
```

New:
```kotlin
SettingsDialog(
    owner = this@MainWindow,
    ctx   = ctx,
    sendToTerminal = { cmd -> terminalPanel.sendInput(cmd) },
    callbacks = io.github.rygel.needlecast.ui.settings.SettingsCallbacks(
        onShortcutsChanged      = { reloadShortcuts() },
        onLayoutChanged         = { resetLayout() },
        onTerminalColorsChanged = { fg, bg -> terminalPanel.applyTerminalColors(fg, bg) },
        onFontSizeChanged       = { size -> terminalPanel.applyFontSize(size) },
        onUiFontChanged         = { _, _ -> applyUiFontFromConfig() },
        onEditorFontChanged     = { family, size -> explorerPanel.applyEditorFont(family, size) },
        onTerminalFontChanged   = { family -> terminalPanel.applyFontFamily(family) },
        onSyntaxThemeChanged    = { explorerPanel.applyTheme(ThemeRegistry.isDark(ctx.config.theme)) },
    ),
).isVisible = true
```

Also remove the `SettingsDialog.defaultShortcuts` companion reference if it appears anywhere else in `MainWindow.kt`. To check:
```bash
grep -n "SettingsDialog\." needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
```
If any results appear that reference `SettingsDialog.defaultShortcuts` or `SettingsDialog.actionLabels`, replace them with `ShortcutsSettingsPanel.defaultShortcuts` / `ShortcutsSettingsPanel.actionLabels`.

- [ ] **Step 3: Verify the full module compiles**

```bash
mvn compile -pl needlecast-desktop -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Run the unit tests**

```bash
mvn test -pl needlecast-desktop -Dtest=SettingsUtilsTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/SettingsDialog.kt
git add needlecast-desktop/src/main/kotlin/io/github/rygel/needlecast/ui/MainWindow.kt
git commit -m "feat: rework SettingsDialog to sidebar + CardLayout (settings panels extracted)"
```

---

## Verification

After all tasks complete:

- [ ] Run the full build and test suite:
  ```bash
  mvn verify -pl needlecast-desktop -T 4 -q 2>&1 | tail -20
  ```
  Expected: `BUILD SUCCESS`

- [ ] Launch the application and open Settings (File → Settings):
  - Sidebar shows 3 groups (GENERAL, INTEGRATIONS, ADVANCED) with 9 categories
  - Clicking each category shows its panel on the right
  - Clicking a group header does not change the selection
  - Dialog opens to "Appearance" by default
  - All existing functionality works: fonts update live, syntax theme changes, shortcuts save, shell detection populates the combo, terminal colors pick correctly
  - Dialog is 760×560 and resizable down to 640×460

  **Note:** Full UI testing (mouse/keyboard interaction) requires the `-Ptest-desktop` profile inside Podman. Manual smoke-testing on a local display is sufficient for this task.
