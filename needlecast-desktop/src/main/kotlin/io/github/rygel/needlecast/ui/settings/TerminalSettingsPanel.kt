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
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

class TerminalSettingsPanel(
    private val ctx: AppContext,
    private val callbacks: SettingsCallbacks = SettingsCallbacks(),
) : JPanel(GridBagLayout()) {

    private var shellWorker: SwingWorker<List<ShellInfo>, Unit>? = null

    private val manualItem = ShellInfo("Manual entry…", "")
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
