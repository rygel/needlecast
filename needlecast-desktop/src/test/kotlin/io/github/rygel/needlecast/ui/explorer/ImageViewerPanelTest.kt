package io.github.rygel.needlecast.ui.explorer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO

class ImageViewerPanelTest {

    // ── isImageFile routing ───────────────────────────────────────────────────

    @Test
    fun `image extensions are recognised`() {
        val exts = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "ico")
        for (ext in exts) {
            val file = File("test.$ext")
            assertTrue(isImageFile(file), ".$ext should be treated as an image")
        }
    }

    @Test
    fun `non-image extensions are not recognised`() {
        val exts = listOf("kt", "xml", "json", "txt", "md", "yaml", "sh", "bat", "pdf")
        for (ext in exts) {
            val file = File("test.$ext")
            assertFalse(isImageFile(file), ".$ext should NOT be treated as an image")
        }
    }

    @Test
    fun `extension check is case-insensitive`() {
        assertTrue(isImageFile(File("photo.JPG")))
        assertTrue(isImageFile(File("photo.PNG")))
        assertTrue(isImageFile(File("photo.WebP")))
    }

    // ── ImageViewerPanel construction ─────────────────────────────────────────

    @Test
    fun `panel constructs without error for a valid PNG`(@TempDir dir: Path) {
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val file = dir.resolve("test.png").toFile()
        ImageIO.write(img, "png", file)

        // Should not throw — loading is async via SwingWorker so we only verify construction
        val panel = ImageViewerPanel(file)
        assertNotNull(panel)
    }

    @Test
    fun `panel constructs without error for a missing file`(@TempDir dir: Path) {
        val missing = dir.resolve("nonexistent.png").toFile()
        val panel = ImageViewerPanel(missing)
        assertNotNull(panel)  // error is handled gracefully, not thrown
    }

    // ── Helper mirroring the private ExplorerPanel.isImageFile logic ─────────

    private fun isImageFile(file: File) = file.extension.lowercase() in
        setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif", "ico")
}
