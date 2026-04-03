package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment

class QuickLaunchTerminalSettings(
    dark: Boolean = true,
    initialFg: Color? = null,
    initialBg: Color? = null,
    initialFontSize: Int = 13,
) : DefaultSettingsProvider() {

    private var isDark: Boolean = dark
    var fontSize: Int = initialFontSize.coerceIn(8, 36)
        private set

    /**
     * Platform-best monospace font for terminal rendering.
     *
     * - Windows: Cascadia Mono (ships with Windows 11 / Windows Terminal) → Consolas (ClearType-optimised)
     * - macOS:   Menlo → Monaco
     * - Linux:   JetBrains Mono → DejaVu Sans Mono → Liberation Mono
     *
     * Falls back to [Font.MONOSPACED] if none of the preferred families are installed.
     */
    private val terminalFontName: String by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        val preferred = when {
            os.contains("win") -> listOf("Cascadia Mono", "Cascadia Code", "Consolas", "Lucida Console")
            os.contains("mac") -> listOf("Menlo", "Monaco", "Courier New")
            else               -> listOf("JetBrains Mono", "DejaVu Sans Mono", "Liberation Mono", "Noto Mono")
        }
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        preferred.firstOrNull { it in available } ?: Font.MONOSPACED
    }

    fun changeFontSize(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(8, 36)
    }

    fun setFontSize(size: Int) {
        fontSize = size.coerceIn(8, 36)
    }

    // VSCode dark ANSI palette (16 colors: Black, Red, Green, Yellow, Blue, Magenta, Cyan, White,
    // BrightBlack, BrightRed, BrightGreen, BrightYellow, BrightBlue, BrightMagenta, BrightCyan, BrightWhite)
    private val darkAnsi = arrayOf(
        Color(0x000000), Color(0xCD3131), Color(0x0DBC79), Color(0xE5E510),
        Color(0x2472C8), Color(0xBC3FBC), Color(0x11A8CD), Color(0xE5E5E5),
        Color(0x666666), Color(0xF14C4C), Color(0x23D18B), Color(0xF5F543),
        Color(0x3B8EEA), Color(0xD670D6), Color(0x29B8DB), Color(0xE5E5E5),
    )

    // VSCode light ANSI palette
    private val lightAnsi = arrayOf(
        Color(0x000000), Color(0xCD3131), Color(0x00BC00), Color(0x949800),
        Color(0x0451A5), Color(0xBC05BC), Color(0x0598BC), Color(0x555555),
        Color(0x666666), Color(0xCD3131), Color(0x14CE14), Color(0xB5BA00),
        Color(0x0451A5), Color(0xBC05BC), Color(0x0598BC), Color(0xA5A5A5),
    )

    private fun makePalette(ansi: Array<Color>): ColorPalette = object : ColorPalette() {
        override fun getForegroundByColorIndex(index: Int): Color =
            if (index in ansi.indices) ansi[index] else Color.WHITE

        override fun getBackgroundByColorIndex(index: Int): Color =
            if (index in ansi.indices) ansi[index] else Color.BLACK
    }

    private var currentPalette: ColorPalette = makePalette(if (dark) darkAnsi else lightAnsi)

    // Manual overrides set by the user in Settings (highest priority)
    private var customForeground: Color? = initialFg
    private var customBackground: Color? = initialBg

    // Auto-derived from the active FlatLaf theme (lower priority than manual overrides)
    private var themeForeground: Color? = null
    private var themeBackground: Color? = null

    fun applyDark(dark: Boolean) {
        isDark = dark
        currentPalette = makePalette(if (dark) darkAnsi else lightAnsi)
    }

    fun applyColors(fg: Color?, bg: Color?) {
        customForeground = fg
        customBackground = bg
    }

    /** Called on every theme switch with colors read from the active FlatLaf UIManager palette. */
    fun applyThemeColors(fg: Color?, bg: Color?) {
        themeForeground = fg
        themeBackground = bg
    }

    override fun getTerminalFont(): Font = Font(terminalFontName, Font.PLAIN, fontSize)

    override fun getTerminalColorPalette(): ColorPalette = currentPalette

    override fun getDefaultStyle(): TextStyle {
        val fg = customForeground ?: themeForeground ?: if (isDark) Color(0xD4D4D4) else Color(0x1E1E1E)
        val bg = customBackground ?: themeBackground ?: if (isDark) Color(0x1E1E1E) else Color(0xFFFFFF)
        return TextStyle(TerminalColor.awt(fg), TerminalColor.awt(bg))
    }

    override fun getSelectionColor(): TextStyle {
        val bg = themeBackground ?: if (isDark) Color(0x1E1E1E) else Color(0xFFFFFF)
        val selBg = if (isDark) Color(0x264F78) else Color(0xADD6FF)
        val selFg = if (isDark) Color(0xD4D4D4) else Color(0x1E1E1E)
        // Lighten/darken the theme bg slightly for selection if we have a theme color
        return TextStyle(TerminalColor.awt(selFg), TerminalColor.awt(selBg))
    }
}
