package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.ui.terminal.AgentStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * A small painted LED that reflects [AgentStatus].
 *
 * - [AgentStatus.NONE]    → hidden (component not visible)
 * - [AgentStatus.WAITING] → amber glow, static
 * - [AgentStatus.THINKING] → green glow, pulses via [blinkOn]
 *
 * Used as a rubber-stamp cell renderer component; [status] and [blinkOn] are set
 * before each render call, no repaint() needed.
 */
class LedIndicator : JComponent() {

    var status: AgentStatus = AgentStatus.NONE
    var blinkOn: Boolean = true

    init {
        preferredSize = Dimension(SIZE, SIZE)
        isOpaque = false
    }

    override fun getPreferredSize(): Dimension = Dimension(SIZE, SIZE)
    override fun getMinimumSize():   Dimension = Dimension(SIZE, SIZE)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val cx = width  / 2
        val cy = height / 2
        val r  = LED_R

        when (status) {
            AgentStatus.NONE -> { /* hidden — caller sets isVisible = false */ }

            AgentStatus.WAITING -> {
                // Outer glow
                g2.color = AMBER_GLOW
                g2.fillOval(cx - r - 2, cy - r - 2, (r + 2) * 2, (r + 2) * 2)
                // Body
                g2.color = AMBER
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
                // Dark rim
                g2.color = AMBER_DARK
                g2.drawOval(cx - r, cy - r, r * 2 - 1, r * 2 - 1)
                // Specular highlight
                g2.color = SHINE
                g2.fillOval(cx - r / 2, cy - r + 1, r - 1, (r * 2) / 3)
            }

            AgentStatus.THINKING -> {
                val body      = if (blinkOn) GREEN_BRIGHT else GREEN_DIM
                val glow      = if (blinkOn) GREEN_GLOW_ON else GREEN_GLOW_OFF
                val shineAlpha = if (blinkOn) 170 else 70
                // Outer glow
                g2.color = glow
                g2.fillOval(cx - r - 2, cy - r - 2, (r + 2) * 2, (r + 2) * 2)
                // Body
                g2.color = body
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
                // Dark rim
                g2.color = GREEN_DARK
                g2.drawOval(cx - r, cy - r, r * 2 - 1, r * 2 - 1)
                // Specular highlight
                g2.color = Color(255, 255, 255, shineAlpha)
                g2.fillOval(cx - r / 2, cy - r + 1, r - 1, (r * 2) / 3)
            }
        }

        g2.dispose()
    }

    companion object {
        const val SIZE  = 12          // component footprint (px)
        const val LED_R =  4          // LED radius (px)

        private val AMBER       = Color(0xFF, 0xA0, 0x00)
        private val AMBER_DARK  = Color(0xCC, 0x70, 0x00)
        private val AMBER_GLOW  = Color(0xFF, 0xA0, 0x00, 50)

        private val GREEN_BRIGHT   = Color(0x4C, 0xAF, 0x50)
        private val GREEN_DIM      = Color(0x2E, 0x7D, 0x32)
        private val GREEN_DARK     = Color(0x1B, 0x5E, 0x20)
        private val GREEN_GLOW_ON  = Color(0x4C, 0xAF, 0x50, 70)
        private val GREEN_GLOW_OFF = Color(0x4C, 0xAF, 0x50, 15)

        private val SHINE = Color(255, 255, 255, 140)
    }
}
