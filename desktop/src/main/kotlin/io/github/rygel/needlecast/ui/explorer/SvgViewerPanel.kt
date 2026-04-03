package io.github.rygel.needlecast.ui.explorer

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseWheelListener
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingWorker

class SvgViewerPanel(private val file: File) : JPanel(BorderLayout()) {

    private val logger = LoggerFactory.getLogger(SvgViewerPanel::class.java)

    private var document: SVGDocument? = null
    private var zoomFactor = 1.0
    private var fitToPanel = true
    private var loadedLastModified: Long = 0L
    private var loadedSize: Long = 0L

    private val infoLabel = JLabel("Loading…", SwingConstants.CENTER)

    private val svgPanel = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val doc = document ?: return
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,      RenderingHints.VALUE_STROKE_PURE)

            val svgSize = doc.size()
            val scale = if (fitToPanel) fitScale(svgSize.width, svgSize.height) else zoomFactor
            val drawW = (svgSize.width  * scale).toInt()
            val drawH = (svgSize.height * scale).toInt()
            val x = maxOf(0, (width  - drawW) / 2)
            val y = maxOf(0, (height - drawH) / 2)

            g2.translate(x, y)
            g2.scale(scale, scale)
            doc.render(this, g2)
        }

        override fun getPreferredSize(): Dimension {
            val doc = document ?: return super.getPreferredSize()
            val s = doc.size()
            return if (fitToPanel) super.getPreferredSize()
            else Dimension((s.width * zoomFactor).toInt(), (s.height * zoomFactor).toInt())
        }
    }

    private val scrollPane = JScrollPane(svgPanel)

    init {
        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(JLabel(file.name), BorderLayout.WEST)
            add(infoLabel, BorderLayout.CENTER)
        }
        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        scrollPane.border = null

        val zoomListener = MouseWheelListener { e ->
            if (e.isControlDown) {
                val doc = document ?: return@MouseWheelListener
                if (fitToPanel) {
                    val s = doc.size()
                    zoomFactor = fitScale(s.width, s.height)
                    fitToPanel = false
                }
                val delta = if (e.wheelRotation < 0) 1.1 else 1.0 / 1.1
                zoomFactor = (zoomFactor * delta).coerceIn(0.05, 32.0)
                updateInfoLabel()
                svgPanel.revalidate()
                svgPanel.repaint()
                e.consume()
            }
        }
        svgPanel.addMouseWheelListener(zoomListener)
        scrollPane.addMouseWheelListener(zoomListener)

        svgPanel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    fitToPanel = true
                    svgPanel.revalidate()
                    svgPanel.repaint()
                    updateInfoLabel()
                }
            }
        })

        loadSvg(file)
    }

    fun reloadIfChanged() {
        if (file.lastModified() != loadedLastModified || file.length() != loadedSize) {
            infoLabel.text = "Reloading…"
            loadSvg(file)
        }
    }

    private fun fitScale(svgW: Float, svgH: Float): Double {
        val pw = svgPanel.width.takeIf  { it > 0 } ?: return 1.0
        val ph = svgPanel.height.takeIf { it > 0 } ?: return 1.0
        return minOf(pw.toDouble() / svgW, ph.toDouble() / svgH).coerceAtMost(1.0)
    }

    private fun updateInfoLabel() {
        val doc = document ?: return
        val s = doc.size()
        val pct = if (fitToPanel) "fit" else "${(zoomFactor * 100).toInt()}%"
        infoLabel.text = "${s.width.toInt()} × ${s.height.toInt()} px  |  $pct"
    }

    private fun loadSvg(file: File) {
        object : SwingWorker<SVGDocument?, Unit>() {
            override fun doInBackground(): SVGDocument? =
                SVGLoader().load(file.toURI().toURL())

            override fun done() {
                val doc = try { get() } catch (e: Exception) {
                    logger.error("Failed to load SVG: ${file.absolutePath}", e)
                    null
                }
                if (doc == null) {
                    logger.warn("SVGLoader returned null for: ${file.absolutePath}")
                    infoLabel.text = "Could not load SVG"
                } else {
                    document = doc
                    loadedLastModified = file.lastModified()
                    loadedSize = file.length()
                    updateInfoLabel()
                    svgPanel.revalidate()
                    svgPanel.repaint()
                }
            }
        }.execute()
    }
}
