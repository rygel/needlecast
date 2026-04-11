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

        gc.gridy = 7; gc.insets = Insets(4, 4, 4, 4)
        gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0
        add(JPanel(), gc)
    }
}
