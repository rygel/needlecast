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
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
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
        preferredSize = Dimension(760, 560)
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
            add(AiToolsSettingsPanel(ctx, callbacks),            "ai-tools")
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

        val closeButton = JButton("Close").apply {
            addActionListener { dispose() }
        }
        // Settings are applied live on every change, so Apply is equivalent to Close.
        val applyButton = JButton("Apply").apply {
            addActionListener { dispose() }
        }
        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8)).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0,
                UIManager.getColor("Separator.foreground") ?: Color.GRAY)
            add(applyButton)
            add(closeButton)
        }
        getRootPane().defaultButton = closeButton

        contentPane = JPanel(BorderLayout()).apply {
            add(sidebarScroll, BorderLayout.WEST)
            add(contentPanel,  BorderLayout.CENTER)
            add(buttonBar,     BorderLayout.SOUTH)
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
