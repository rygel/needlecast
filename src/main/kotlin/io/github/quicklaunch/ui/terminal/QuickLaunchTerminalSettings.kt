package io.github.quicklaunch.ui.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Color
import java.awt.Font

class QuickLaunchTerminalSettings(dark: Boolean = true) : DefaultSettingsProvider() {

    private var isDark: Boolean = dark
    var fontSize: Int = 13
        private set

    fun changeFontSize(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(8, 36)
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

    fun applyDark(dark: Boolean) {
        isDark = dark
        currentPalette = makePalette(if (dark) darkAnsi else lightAnsi)
    }

    override fun getTerminalFont(): Font = Font(Font.MONOSPACED, Font.PLAIN, fontSize)

    override fun getTerminalColorPalette(): ColorPalette = currentPalette

    override fun getDefaultStyle(): TextStyle {
        return if (isDark) {
            TextStyle(
                TerminalColor.awt(Color(0xD4D4D4)),
                TerminalColor.awt(Color(0x1E1E1E)),
            )
        } else {
            TextStyle(
                TerminalColor.awt(Color(0x1E1E1E)),
                TerminalColor.awt(Color(0xFFFFFF)),
            )
        }
    }

    override fun getSelectionColor(): TextStyle {
        return if (isDark) {
            TextStyle(
                TerminalColor.awt(Color(0xD4D4D4)),
                TerminalColor.awt(Color(0x264F78)),
            )
        } else {
            TextStyle(
                TerminalColor.awt(Color(0x1E1E1E)),
                TerminalColor.awt(Color(0xADD6FF)),
            )
        }
    }
}
