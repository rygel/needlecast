package io.github.rygel.needlecast

import io.github.rygel.needlecast.ui.MainWindow
import javax.swing.SwingUtilities

fun main() {
    // Set application name before AWT initialises — required for macOS Dock and some Linux desktops.
    System.setProperty("apple.awt.application.name", "Needlecast")
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    // Enable LCD subpixel antialiasing for all text (including JediTerm's terminal canvas).
    // Must be set before any AWT/Swing component is created.
    System.setProperty("awt.useSystemAAFontSettings", "lcd")
    System.setProperty("swing.aatext", "true")

    val ctx = AppContext()
    ThemeRegistry.apply(ctx.config.theme)
    SwingUtilities.invokeLater {
        val window = MainWindow(ctx)

        // Set taskbar icon and name where supported (Java 9+, macOS / certain Linux DEs)
        try {
            val taskbar = java.awt.Taskbar.getTaskbar()
            val icon = window.iconImage
            if (icon != null && taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = icon
            }
        } catch (_: Exception) {}

        window.isVisible = true
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
