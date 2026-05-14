package io.github.rygel.needlecast.ui.diff

import java.awt.Color
import javax.swing.UIManager

object DiffColors {

    val addedBackground: Color
        get() = resolveColor("Diff.addedBackground") { Color(70, 180, 70, 30) }

    val removedBackground: Color
        get() = resolveColor("Diff.removedBackground") { Color(255, 70, 70, 30) }

    val addedInline: Color
        get() = resolveColor("Diff.addedInline") { Color(70, 180, 70, 90) }

    val removedInline: Color
        get() = resolveColor("Diff.removedInline") { Color(255, 70, 70, 90) }

    val addedForeground: Color
        get() = resolveColor("Diff.addedForeground") { Color(106, 135, 89) }

    val removedForeground: Color
        get() = resolveColor("Diff.removedForeground") { Color(199, 91, 91) }

    val gutterStripeAdded: Color
        get() = resolveColor("Diff.gutterStripeAdded") { Color(0x4C, 0xAF, 0x50) }

    val gutterStripeRemoved: Color
        get() = resolveColor("Diff.gutterStripeRemoved") { Color(0xC7, 0x5B, 0x5B) }

    val lineNumberColor: Color
        get() = resolveColor("Diff.lineNumberColor") { Color(0x60, 0x60, 0x60) }

    val overviewAdded: Color
        get() = resolveColor("Diff.overviewAdded") { Color(0x4C, 0xAF, 0x50, 180) }

    val overviewRemoved: Color
        get() = resolveColor("Diff.overviewRemoved") { Color(0xC7, 0x5B, 0x5B, 180) }

    val overviewViewport: Color
        get() = resolveColor("Diff.overviewViewport") { Color(255, 255, 255, 20) }

    val searchHighlight: Color
        get() = resolveColor("Diff.searchHighlight") { Color(255, 255, 0, 100) }

    val contextForeground: Color
        get() = UIManager.getColor("TextPane.foreground") ?: Color(0xA9, 0xB7, 0xC6)

    private inline fun resolveColor(key: String, fallback: () -> Color): Color {
        return UIManager.getColor(key) ?: fallback()
    }
}
