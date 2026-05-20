package io.github.rygel.needlecast.ui.components

import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class ContextualHintPanel(
    val hintId: String,
    headline: String,
    description: String,
    icon: javax.swing.Icon? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: ((String) -> Unit)? = null,
) : JPanel(BorderLayout(12, 0)) {

    var isDismissed: Boolean = false
        private set

    init {
        border = BorderFactory.createEmptyBorder(24, 24, 24, 24)
        background = resolveBackground()

        icon?.let { ic ->
            add(JLabel(ic), BorderLayout.WEST)
        }

        val centerPanel = JPanel(BorderLayout(4, 4)).apply {
            isOpaque = false
            add(JLabel(headline).apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }, BorderLayout.NORTH)
            add(JLabel("<html>$description</html>").apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = Color(0xAAAAAA)
            }, BorderLayout.CENTER)
        }
        add(centerPanel, BorderLayout.CENTER)

        val buttonBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            if (actionLabel != null && onAction != null) {
                add(JButton(actionLabel).apply {
                    addActionListener { onAction() }
                })
            }
            add(Box.createHorizontalStrut(8))
            add(JButton("\u00d7").apply {
                toolTipText = "Dismiss this hint"
                border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
                addActionListener {
                    isDismissed = true
                    onDismiss?.invoke(hintId)
                    isVisible = false
                }
            })
        }
        add(buttonBar, BorderLayout.SOUTH)
    }

    fun dismiss() {
        isDismissed = true
        isVisible = false
    }

    companion object {
        private fun resolveBackground(): Color {
            return try {
                UIManager.getColor("TextArea.background") ?: Color(0x1E1E1E)
            } catch (_: Exception) {
                Color(0x1E1E1E)
            }
        }
    }
}
