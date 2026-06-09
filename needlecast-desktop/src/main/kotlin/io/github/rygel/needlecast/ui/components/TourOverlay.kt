package io.github.rygel.needlecast.ui.components

import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JWindow
import javax.swing.SwingUtilities

data class TourStep(
    val title: String,
    val description: String,
    val panelId: String,
    val tabName: String? = null,
)

class TourOverlay(
    private val rootPane: JRootPane,
    private val steps: List<TourStep>,
    private val findPanel: (String) -> java.awt.Component?,
    private val selectTab: (String, String) -> Unit = { _, _ -> },
    private val onComplete: () -> Unit,
    private val onSkip: () -> Unit,
) {
    private var currentStep = 0
    private var overlayWindow: JWindow? = null
    private var bubbleWindow: JWindow? = null

    fun start() {
        if (steps.isEmpty()) {
            onComplete()
            return
        }
        currentStep = 0
        showStep()
    }

    private fun showStep() {
        val step = steps[currentStep]
        step.tabName?.let { tab -> selectTab(step.panelId, tab) }
        val target =
            findPanel(step.panelId) ?: run {
                nextStep()
                return
            }
        showScrim(target)
        showBubble(target, step)
    }

    private fun showScrim(target: java.awt.Component) {
        overlayWindow?.dispose()
        val window = JWindow(SwingUtilities.getWindowAncestor(rootPane))
        val rootLoc = rootPane.locationOnScreen
        val rootSize = rootPane.size
        val targetRect =
            SwingUtilities.convertRectangle(
                target.parent ?: target,
                Rectangle(0, 0, target.width, target.height),
                rootPane,
            )

        val scrim =
            object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = Color(0, 0, 0, 150)
                    g2.fillRect(0, 0, width, height)
                    val pad = 4
                    val hx = targetRect.x - pad
                    val hy = targetRect.y - pad
                    val hw = targetRect.width + pad * 2
                    val hh = targetRect.height + pad * 2
                    g2.composite = AlphaComposite.Clear
                    g2.fillRoundRect(hx, hy, hw, hh, 8, 8)
                    g2.composite = AlphaComposite.SrcOver
                    g2.color = Color(0x88AACC)
                    g2.drawRoundRect(hx, hy, hw, hh, 8, 8)
                }
            }
        scrim.preferredSize = rootSize
        window.contentPane = scrim
        window.bounds = Rectangle(rootLoc, rootSize)
        window.isAlwaysOnTop = true
        window.isVisible = true
        overlayWindow = window
    }

    private fun showBubble(
        target: java.awt.Component,
        step: TourStep,
    ) {
        bubbleWindow?.dispose()
        val window = JWindow(SwingUtilities.getWindowAncestor(rootPane))

        val content =
            JPanel(BorderLayout(8, 4)).apply {
                border =
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(0x88AACC), 1, true),
                        BorderFactory.createEmptyBorder(12, 16, 12, 16),
                    )
                background = Color(0x2D2D30)

                add(
                    JPanel(BorderLayout(2, 2)).apply {
                        isOpaque = false
                        add(
                            JLabel(step.title).apply {
                                font = font.deriveFont(Font.BOLD, 14f)
                                foreground = Color(0xE0E0E0)
                            },
                            BorderLayout.NORTH,
                        )
                        add(
                            JLabel("<html>${step.description}</html>").apply {
                                font = font.deriveFont(Font.PLAIN, 12f)
                                foreground = Color(0xBBBBBB)
                            },
                            BorderLayout.CENTER,
                        )
                    },
                    BorderLayout.CENTER,
                )

                add(
                    JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4)).apply {
                        isOpaque = false
                        add(
                            JLabel("${currentStep + 1} of ${steps.size}").apply {
                                font = font.deriveFont(Font.PLAIN, 11f)
                                foreground = Color(0x888888)
                            },
                        )
                        add(
                            JButton(if (currentStep < steps.size - 1) "Next" else "Finish").apply {
                                foreground = Color(0x88AACC)
                                addActionListener { nextStep() }
                            },
                        )
                        add(
                            JButton("Skip tour").apply {
                                foreground = Color(0x888888)
                                addActionListener { skip() }
                            },
                        )
                    },
                    BorderLayout.SOUTH,
                )
            }

        window.contentPane = content
        window.pack()

        val targetLoc = target.locationOnScreen
        val x = targetLoc.x + target.width / 2 - window.width / 2
        val y = targetLoc.y + target.height + 12
        window.location = Point(x.coerceAtLeast(0), y)
        window.isAlwaysOnTop = true
        window.isVisible = true
        bubbleWindow = window
    }

    private fun nextStep() {
        currentStep++
        if (currentStep >= steps.size) complete() else showStep()
    }

    private fun complete() {
        dispose()
        onComplete()
    }

    private fun skip() {
        dispose()
        onSkip()
    }

    private fun dispose() {
        overlayWindow?.dispose()
        overlayWindow = null
        bubbleWindow?.dispose()
        bubbleWindow = null
    }
}
