package io.github.rygel.needlecast.ui.components

import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

class BannerNotification(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit = {},
    autoDismissMs: Int = 10000,
) : JPanel(BorderLayout(8, 0)) {

    init {
        background = Color(0x2D3A2D)
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, Color(0x4CAF50))

        add(JLabel(text).apply {
            foreground = Color(0xCCDDCC)
            font = font.deriveFont(Font.PLAIN, 13f)
        }, BorderLayout.CENTER)

        add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4)).apply {
            isOpaque = false
            add(JButton(actionLabel).apply {
                foreground = Color(0x88CC88)
                addActionListener {
                    onAction()
                    this@BannerNotification.isVisible = false
                    val parent = this@BannerNotification.parent
                    parent?.remove(this@BannerNotification)
                    parent?.revalidate()
                    parent?.repaint()
                }
            })
            add(JButton("Dismiss").apply {
                foreground = Color(0x888888)
                addActionListener {
                    onDismiss()
                    this@BannerNotification.isVisible = false
                    val parent = this@BannerNotification.parent
                    parent?.remove(this@BannerNotification)
                    parent?.revalidate()
                    parent?.repaint()
                }
            })
        }, BorderLayout.EAST)

        if (autoDismissMs > 0) {
            Timer(autoDismissMs) {
                onDismiss()
                isVisible = false
                val parent = parent
                parent?.remove(this@BannerNotification)
                parent?.revalidate()
                parent?.repaint()
            }.apply { isRepeats = false; start() }
        }
    }
}
