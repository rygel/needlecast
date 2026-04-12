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
