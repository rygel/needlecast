package io.github.rygel.needlecast.ui.explorer

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseWheelListener
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingWorker
import org.slf4j.LoggerFactory

class ImageViewerPanel(private val file: File) : JPanel(BorderLayout()) {

    private val logger = LoggerFactory.getLogger(ImageViewerPanel::class.java)

    private var image: BufferedImage? = null
    private var zoomFactor = 1.0
    private var fitToPanel = true
    private var loadedLastModified: Long = 0L
    private var loadedSize: Long = 0L

    private val infoLabel = JLabel("Loading…", SwingConstants.CENTER)
    private val imagePanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val img = image ?: return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val scale = if (fitToPanel) fitScale(img) else zoomFactor
            val drawW = (img.width  * scale).toInt()
            val drawH = (img.height * scale).toInt()
            val x = maxOf(0, (width  - drawW) / 2)
            val y = maxOf(0, (height - drawH) / 2)
            g2.drawImage(img, x, y, drawW, drawH, null)
        }

        override fun getPreferredSize(): Dimension {
            val img = image ?: return super.getPreferredSize()
            return if (fitToPanel) {
                super.getPreferredSize()
            } else {
                Dimension(
                    (img.width  * zoomFactor).toInt(),
                    (img.height * zoomFactor).toInt(),
                )
            }
        }
    }
    private val scrollPane = JScrollPane(imagePanel)

    init {
        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(JLabel(file.name), BorderLayout.WEST)
            add(infoLabel, BorderLayout.CENTER)
        }
        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        scrollPane.border = null

        // Ctrl+scroll = zoom; plain scroll = pan (default scroll pane behavior)
        val zoomListener = MouseWheelListener { e ->
            if (e.isControlDown) {
                val img = image ?: return@MouseWheelListener
                if (fitToPanel) {
                    zoomFactor = fitScale(img)
                    fitToPanel = false
                }
                val delta = if (e.wheelRotation < 0) 1.1 else 1.0 / 1.1
                zoomFactor = (zoomFactor * delta).coerceIn(0.05, 32.0)
                updateInfoLabel(img)
                imagePanel.revalidate()
                imagePanel.repaint()
                e.consume()
            }
        }
        imagePanel.addMouseWheelListener(zoomListener)
        scrollPane.addMouseWheelListener(zoomListener)

        // Double-click resets to fit-to-panel
        imagePanel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    fitToPanel = true
                    imagePanel.revalidate()
                    imagePanel.repaint()
                    image?.let { updateInfoLabel(it) }
                }
            }
        })

        loadImage(file)
    }

    fun reloadIfChanged() {
        if (file.lastModified() != loadedLastModified || file.length() != loadedSize) {
            infoLabel.text = "Reloading…"
            loadImage(file)
        }
    }

    private fun fitScale(img: BufferedImage): Double {
        val pw = imagePanel.width.takeIf  { it > 0 } ?: return 1.0
        val ph = imagePanel.height.takeIf { it > 0 } ?: return 1.0
        return minOf(pw.toDouble() / img.width, ph.toDouble() / img.height).coerceAtMost(1.0)
    }

    private fun updateInfoLabel(img: BufferedImage) {
        val pct = if (fitToPanel) "fit" else "${(zoomFactor * 100).toInt()}%"
        infoLabel.text = "${img.width} × ${img.height} px  |  $pct"
    }

    private fun loadImage(file: File) {
        object : SwingWorker<BufferedImage?, Unit>() {
            override fun doInBackground(): BufferedImage? = ImageIO.read(file)
            override fun done() {
                val img = try { get() } catch (e: Exception) {
                    logger.error("Failed to load image: ${file.absolutePath}", e)
                    null
                }
                if (img == null) {
                    logger.warn("ImageIO returned null for: ${file.absolutePath} — format may be unsupported")
                    infoLabel.text = "Could not load image"
                    imagePanel.background = Color(0x3A, 0x10, 0x10)
                } else {
                    image = img
                    loadedLastModified = file.lastModified()
                    loadedSize = file.length()
                    updateInfoLabel(img)
                    imagePanel.revalidate()
                    imagePanel.repaint()
                }
            }
        }.execute()
    }
}
