package io.github.rygel.needlecast.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Verifies that every icon referenced from production code actually exists in the
 * resources directory and that RemixIcons returns a non-empty image for each.
 *
 * Icon references are automatically extracted from main source files by scanning
 * for [RemixIcons.icon] calls, so adding a new icon requires no test changes.
 */
class IconLoadingTest {
    private data class IconRef(
        val name: String,
        val size: Int,
    )

    private fun scanIconRefs(): Set<IconRef> {
        val refs = mutableSetOf<IconRef>()
        val srcDir = File("src/main/kotlin")
        if (!srcDir.isDirectory) return refs
        srcDir
            .walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                val pattern = Regex("""\.icon\("([^"]+)"(?:\s*,\s*(\d+))?""")
                pattern.findAll(text).forEach { match ->
                    val name = match.groupValues[1]
                    val size = match.groupValues[2].toIntOrNull() ?: 16
                    refs.add(IconRef(name, size))
                }
            }
        return refs
    }

    @Test
    fun `all icon resource files exist on classpath`() {
        val missing =
            scanIconRefs().filter { ref ->
                RemixIcons::class.java.getResource("/icons/${ref.name}-${ref.size}.png") == null
            }
        assertThat(missing)
            .withFailMessage(
                "Missing icon resources: %s",
                missing.joinToString { "${it.name}-${it.size}" },
            ).isEmpty()
    }

    @Test
    fun `every icon resource loads as a valid image`() {
        val broken = mutableListOf<String>()
        scanIconRefs().forEach { ref ->
            val url =
                RemixIcons::class.java.getResource("/icons/${ref.name}-${ref.size}.png")
                    ?: return@forEach
            val img = ImageIO.read(url)
            if (img == null || img.width <= 0 || img.height <= 0) {
                broken.add("${ref.name}-${ref.size} (null or empty)")
            }
        }
        assertThat(broken)
            .withFailMessage("Broken icon images: %s", broken.joinToString())
            .isEmpty()
    }

    @Test
    fun `RemixIcons returns non-empty icon for every reference`() {
        val refs = scanIconRefs()
        val invalid =
            refs.mapNotNull { ref ->
                val icon: Icon = RemixIcons.icon(ref.name, ref.size)
                val w = icon.iconWidth
                val h = icon.iconHeight
                if (icon !is ImageIcon || w != ref.size || h != ref.size) {
                    "${ref.name}-${ref.size}: got ${icon.javaClass.simpleName} ${w}x$h"
                } else {
                    null
                }
            }
        assertThat(invalid)
            .withFailMessage("RemixIcons.icon() returned wrong size: %s", invalid.joinToString())
            .isEmpty()
    }

    @Test
    fun `RemixIcons tints icon when color is provided`() {
        val tinted = RemixIcons.icon("ri-play-line", 16, Color.RED)
        val canvas = BufferedImage(tinted.iconWidth, tinted.iconHeight, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        tinted.paintIcon(null, g, 0, 0)
        g.dispose()
        var nonTransparentPixels = 0
        for (y in 0 until canvas.height) {
            for (x in 0 until canvas.width) {
                val alpha = (canvas.getRGB(x, y) ushr 24) and 0xFF
                if (alpha > 0) nonTransparentPixels++
            }
        }
        assertThat(nonTransparentPixels)
            .withFailMessage("Tinted icon has no visible pixels — tinting may be broken")
            .isGreaterThan(0)
    }

    @Test
    fun `icon dimensions match requested size for all references`() {
        val refs = scanIconRefs()
        val wrongSize =
            refs.mapNotNull { ref ->
                val icon = RemixIcons.icon(ref.name, ref.size)
                if (icon.iconWidth != ref.size || icon.iconHeight != ref.size) {
                    "${ref.name}-${ref.size}: got ${icon.iconWidth}x${icon.iconHeight}"
                } else {
                    null
                }
            }
        assertThat(wrongSize)
            .withFailMessage("Icons with wrong size: %s", wrongSize.joinToString())
            .isEmpty()
    }
}
