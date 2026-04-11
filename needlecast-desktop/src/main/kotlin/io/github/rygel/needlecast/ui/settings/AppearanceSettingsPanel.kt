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
