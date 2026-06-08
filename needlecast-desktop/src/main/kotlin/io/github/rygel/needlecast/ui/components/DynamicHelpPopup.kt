package io.github.rygel.needlecast.ui.components

import io.github.rygel.needlecast.AppContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer

class DynamicHelpPopup(
    private val ctx: AppContext,
    private val hintId: String,
    private val text: String,
    private val anchor: java.awt.Component,
    private val durationMs: Int = 8000,
) {
    fun showIfNotSeen() {
        if (!ctx.config.showHelpPopups) return
        if (hintId in ctx.config.shownHints) return
        ctx.updateConfig(ctx.config.copy(shownHints = ctx.config.shownHints + hintId))

        val window = JWindow(SwingUtilities.getWindowAncestor(anchor))
        val content =
            JPanel(BorderLayout(8, 4)).apply {
                border =
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(0x555555), 1, true),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12),
                    )
                background = Color(0x2D2D30)
                add(
                    JLabel(text).apply {
                        foreground = Color(0xCCCCCC)
                        font = font.deriveFont(Font.PLAIN, 12f)
                    },
                    BorderLayout.CENTER,
                )
                add(
                    JButton("Got it").apply {
                        border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
                        foreground = Color(0x88AACC)
                        addActionListener { window.dispose() }
                    },
                    BorderLayout.EAST,
                )
            }
        window.contentPane = content
        window.pack()

        val anchorLoc = anchor.locationOnScreen
        window.location = Point(anchorLoc.x, anchorLoc.y + anchor.height + 4)
        window.isVisible = true

        Timer(durationMs) { window.dispose() }.apply {
            isRepeats = false
            start()
        }
    }
}
