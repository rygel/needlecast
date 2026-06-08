package io.github.rygel.needlecast.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Verifies that every icon referenced from production code actually exists in the
 * resources directory and that RemixIcons returns a non-empty image for each.
 *
 * If a referenced icon is missing, RemixIcons silently returns a gray fallback
 * rectangle (see [RemixIcons.fallbackIcon]). This test fails fast on that
 * regression rather than letting "broken" icons ship to users.
 */
class IconLoadingTest {
    private data class IconRef(
        val name: String,
        val size: Int,
    )

    private val ICON_REFS =
        listOf(
            IconRef("ri-add-line", 16),
            IconRef("ri-arrow-down-line", 16),
            IconRef("ri-arrow-down-s-line", 12),
            IconRef("ri-arrow-up-circle-fill", 12),
            IconRef("ri-arrow-up-line", 16),
            IconRef("ri-arrow-up-s-line", 12),
            IconRef("ri-checkbox-blank-circle-fill", 10),
            IconRef("ri-checkbox-circle-fill", 12),
            IconRef("ri-checkbox-circle-fill", 16),
            IconRef("ri-close-circle-line", 16),
            IconRef("ri-close-line", 12),
            IconRef("ri-delete-bin-line", 16),
            IconRef("ri-edit-line", 16),
            IconRef("ri-error-warning-line", 10),
            IconRef("ri-external-link-line", 16),
            IconRef("ri-eye-line", 16),
            IconRef("ri-eye-off-line", 16),
            IconRef("ri-file-add-line", 16),
            IconRef("ri-folder-add-line", 16),
            IconRef("ri-history-line", 16),
            IconRef("ri-lock-line", 12),
            IconRef("ri-play-circle-line", 12),
            IconRef("ri-play-circle-line", 16),
            IconRef("ri-play-line", 12),
            IconRef("ri-play-line", 16),
            IconRef("ri-refresh-line", 16),
            IconRef("ri-stop-line", 16),
            IconRef("ri-subtract-line", 16),
        )

    @Test
    fun `all icon resource files exist on classpath`() {
        val missing =
            ICON_REFS.filter { ref ->
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
        ICON_REFS.forEach { ref ->
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
        // Build a new RemixIcons cache state by using a fresh invocation per call.
        // We can't reset the global cache, but every call returns a cached Icon,
        // so we just verify the icons are valid ImageIcons with the expected size.
        val invalid =
            ICON_REFS.mapNotNull { ref ->
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
        // Paint the icon into a fresh BufferedImage and count non-transparent pixels.
        // We can't read ImageIcon's internal `image` field (java.desktop module restriction),
        // so we paint it onto our own canvas and inspect the result.
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
        val wrongSize =
            ICON_REFS.mapNotNull { ref ->
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
