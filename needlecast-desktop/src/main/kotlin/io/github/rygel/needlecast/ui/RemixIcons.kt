package io.github.rygel.needlecast.ui

import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

object RemixIcons {

    private val cache = mutableMapOf<String, Icon>()

    fun icon(name: String, size: Int = 16, color: Color? = null): Icon {
        val key = "$name|$size|${color?.rgb ?: 0}"
        return cache.getOrPut(key) {
            val url = RemixIcons::class.java.getResource("/icons/$name-$size.png")
                ?: return@getOrPut fallbackIcon(size)
            val img = javax.imageio.ImageIO.read(url)
            tintImage(img, color ?: Color(0xBBBBBB))
            ImageIcon(img)
        }
    }

    private fun tintImage(img: BufferedImage, color: Color) {
        val w = img.width
        val h = img.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = img.getRGB(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha > 0) {
                    img.setRGB(x, y, (alpha shl 24) or (color.rgb and 0x00FFFFFF))
                }
            }
        }
    }

    private fun fallbackIcon(size: Int): Icon {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        g2.color = Color.GRAY
        g2.drawRect(0, 0, size - 1, size - 1)
        g2.dispose()
        return ImageIcon(img)
    }
}
