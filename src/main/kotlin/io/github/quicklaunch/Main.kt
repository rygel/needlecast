package io.github.quicklaunch

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import io.github.quicklaunch.ui.MainWindow
import javax.swing.SwingUtilities

fun main() {
    val ctx = AppContext()
    if (ctx.config.theme == "dark") FlatDarkLaf.setup()
    else FlatLightLaf.setup()
    SwingUtilities.invokeLater {
        MainWindow(ctx).isVisible = true
    }
}
