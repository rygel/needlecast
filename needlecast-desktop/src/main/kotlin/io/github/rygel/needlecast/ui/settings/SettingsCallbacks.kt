package io.github.rygel.needlecast.ui.settings

import java.awt.Color

data class SettingsCallbacks(
    val onShortcutsChanged: () -> Unit = {},
    val onLayoutChanged: () -> Unit = {},
    val onTerminalColorsChanged: (fg: Color?, bg: Color?) -> Unit = { _, _ -> },
    val onFontSizeChanged: (Int) -> Unit = {},
    val onUiFontChanged: (family: String?, size: Int?) -> Unit = { _, _ -> },
    val onEditorFontChanged: (family: String?, size: Int) -> Unit = { _, _ -> },
    val onTerminalFontChanged: (family: String?) -> Unit = { _ -> },
    val onSyntaxThemeChanged: () -> Unit = {},
)
