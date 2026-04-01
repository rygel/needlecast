package io.github.quicklaunch

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import io.github.quicklaunch.ui.MainWindow
import javax.swing.SwingUtilities

fun main() {
    val ctx = AppContext()
    when (ctx.config.theme) {
        "dark"   -> FlatDarkLaf.setup()
        "system" -> if (isOsDark()) FlatDarkLaf.setup() else FlatLightLaf.setup()
        else     -> FlatLightLaf.setup()
    }
    SwingUtilities.invokeLater {
        MainWindow(ctx).isVisible = true
    }
}

/**
 * Detects whether the OS is currently using a dark colour scheme.
 * Checks the FlatLaf-provided hint first (populated after LAF setup), then
 * falls back to a Windows desktop property so it also works before any LAF
 * is applied.
 */
internal fun isOsDark(): Boolean {
    // FlatLaf sets "laf.dark" in UIManager after setup — reliable when a LAF is already active
    val lafDark = javax.swing.UIManager.get("laf.dark")
    if (lafDark is Boolean) return lafDark

    // Windows-specific fallback: the console background colour is dark on dark themes
    val winProp = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("win.lconsole.backgroundColor")
    if (winProp is java.awt.Color) {
        val luminance = (0.299 * winProp.red + 0.587 * winProp.green + 0.114 * winProp.blue) / 255.0
        return luminance < 0.5
    }

    return false
}
