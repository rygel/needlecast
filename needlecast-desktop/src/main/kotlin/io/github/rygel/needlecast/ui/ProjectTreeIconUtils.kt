package io.github.rygel.needlecast.ui

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JLabel

internal fun plusOverlayIcon(base: Icon?): Icon? {
    if (base == null) return null
    return object : Icon {
        override fun getIconWidth() = base.iconWidth

        override fun getIconHeight() = base.iconHeight

        override fun paintIcon(
            c: Component?,
            g: Graphics,
            x: Int,
            y: Int,
        ) {
            base.paintIcon(c, g, x, y)
            val g2 = g.create() as java.awt.Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val size = 9
            val px = x + base.iconWidth - size
            val py = y + base.iconHeight - size
            g2.color = Color(0x4CAF50)
            g2.fillOval(px, py, size, size)
            g2.color = Color.WHITE
            g2.stroke = java.awt.BasicStroke(1.5f)
            val cx = px + size / 2
            val cy = py + size / 2
            g2.drawLine(cx - 2, cy, cx + 2, cy)
            g2.drawLine(cx, cy - 2, cx, cy + 2)
            g2.dispose()
        }
    }
}

internal fun colorSwatchIcon(hex: String): Icon {
    val fill =
        try {
            Color.decode(hex)
        } catch (_: Exception) {
            Color.GRAY
        }
    val border = fill.darker()
    return object : Icon {
        override fun getIconWidth() = 14

        override fun getIconHeight() = 14

        override fun paintIcon(
            c: Component?,
            g: Graphics,
            x: Int,
            y: Int,
        ) {
            g.color = fill
            g.fillRoundRect(x, y, 14, 14, 4, 4)
            g.color = border
            g.drawRoundRect(x, y, 13, 13, 4, 4)
        }
    }
}

internal fun badge(
    text: String,
    colorHex: String,
) = JLabel(text).apply {
    font = font.deriveFont(java.awt.Font.BOLD, 9f)
    foreground = Color.WHITE
    background =
        try {
            Color.decode(colorHex)
        } catch (_: Exception) {
            Color.GRAY
        }
    isOpaque = true
    border = javax.swing.BorderFactory.createEmptyBorder(1, 4, 1, 4)
    preferredSize = java.awt.Dimension(preferredSize.width, 14)
}
