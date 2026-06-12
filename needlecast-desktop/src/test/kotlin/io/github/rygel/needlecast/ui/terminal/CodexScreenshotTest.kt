package io.github.rygel.needlecast.ui.terminal

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.ui.MainWindow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

class CodexScreenshotTest {
    private lateinit var window: MainWindow
    private lateinit var ctx: AppContext

    @BeforeEach
    fun setUp() {
        System.setProperty("awt.useSystemAAFontSettings", "lcd")
        System.setProperty("swing.aatext", "true")
        ImageIO.setUseCache(false)

        ctx = AppContext()
        ThemeRegistry.apply(ctx.config.theme)

        val latch = CountDownLatch(1)
        SwingUtilities.invokeLater {
            window = MainWindow(ctx)
            window.size = Dimension(1400, 900)
            window.isVisible = true
            latch.countDown()
        }
        assert(latch.await(10, TimeUnit.SECONDS))
        Thread.sleep(1500)

        resizeTerminalToFillUpperSplit(0.9)
        Thread.sleep(500)
    }

    @AfterEach
    fun tearDown() {
        SwingUtilities.invokeLater {
            window.dispose()
        }
    }

    @Test
    fun `launch codex inside needlecast and capture screenshot`() {
        val outputDir = File("target/codex-screenshots")
        outputDir.mkdirs()

        val manager = window.terminalPanel
        val projectDir = System.getProperty("user.dir")

        println("[TEST] Activating project terminal for: $projectDir")
        SwingUtilities.invokeLater {
            manager.activateProject(projectDir)
        }
        Thread.sleep(2000)

        val codexPath = resolveCodexCommand()

        println("[TEST] Sending codex command to Needlecast terminal...")
        manager.sendInput("cd /d \"$projectDir\"\r\n")
        Thread.sleep(1500)
        manager.sendInput("\"$codexPath\"\r\n")

        println("[TEST] Waiting 12s for Codex TUI to render inside Needlecast...")
        Thread.sleep(12000)

        val screenshotFile = File(outputDir, "needlecast-codex.png")
        saveWindowScreenshot(screenshotFile)
        println("[TEST] Screenshot saved to: ${screenshotFile.absolutePath}")

        val buffer = captureBuffer()
        val bufferFile = File(outputDir, "needlecast-codex-buffer.txt")
        bufferFile.writeText(buffer)
        println("[TEST] Terminal buffer saved to: ${bufferFile.absolutePath}")

        println("[TEST] === Terminal buffer (first 3000 chars) ===")
        println(buffer.take(3000))
        println("[TEST] === End ===")
    }

    private fun resizeTerminalToFillUpperSplit(proportion: Double) {
        val done = CountDownLatch(1)
        var result = "(no JSplitPane found containing the terminal)"
        SwingUtilities.invokeLater {
            val terminalAnchor: Component = window.terminalPanel
            val split = findInnermostSplitContaining(window.contentPane, terminalAnchor)
            if (split != null) {
                val (primary, _) = splitSides(split)
                val terminalIsPrimary = containsDescendant(primary, terminalAnchor)
                val totalSize = if (split.orientation == JSplitPane.HORIZONTAL_SPLIT) split.width else split.height
                val dividerLocation = if (terminalIsPrimary) {
                    (totalSize * proportion).toInt()
                } else {
                    (totalSize * (1.0 - proportion)).toInt()
                }
                split.setDividerLocation(dividerLocation)
                split.revalidate()
                split.repaint()
                val orient = if (split.orientation == JSplitPane.HORIZONTAL_SPLIT) "H(left|right)" else "V(top|bottom)"
                result = "Resized split=${split.width}x${split.height} [$orient] to divider=$dividerLocation (terminalIsPrimary=$terminalIsPrimary)"
            }
            done.countDown()
        }
        assert(done.await(5, TimeUnit.SECONDS))
        println("[TEST] $result")
    }

    private fun splitSides(split: JSplitPane): Pair<Component?, Component?> =
        if (split.orientation == JSplitPane.HORIZONTAL_SPLIT) split.leftComponent to split.rightComponent
        else split.topComponent to split.bottomComponent

    private fun findInnermostSplitContaining(root: Container, target: Component): JSplitPane? {
        if (root is JSplitPane) {
            val (a, b) = splitSides(root)
            if (containsDescendant(a, target) || containsDescendant(b, target)) {
                val deeper = findInnermostSplitInChildren(root, target)
                return deeper ?: root
            }
        }
        for (child in root.components) {
            if (child is Container) {
                val found = findInnermostSplitContaining(child, target)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findInnermostSplitInChildren(parent: JSplitPane, target: Component): JSplitPane? {
        val (a, b) = splitSides(parent)
        for (side in listOfNotNull(a, b)) {
            if (side is Container) {
                val found = findInnermostSplitContaining(side, target)
                if (found != null) return found
            }
        }
        return null
    }

    private fun containsDescendant(c: Component?, target: Component): Boolean {
        if (c == null) return false
        if (c === target) return true
        if (c !is Container) return false
        if (c is JTabbedPane) {
            for (i in 0 until c.tabCount) {
                if (containsDescendant(c.getComponentAt(i), target)) return true
            }
            return false
        }
        for (child in c.components) {
            if (containsDescendant(child, target)) return true
        }
        return false
    }

    private fun resolveCodexCommand(): String {
        val proc = ProcessBuilder("where", "codex").redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        if (output.isBlank() || proc.exitValue() != 0) {
            throw IllegalStateException("codex not found on PATH")
        }
        val path = output.lineSequence().first().trim()
        println("[TEST] Found codex at: $path")
        return path
    }

    private fun captureBuffer(): String {
        val pane = window.terminalPanel.activePane ?: return "(no active pane)"
        val tabCount = pane.realTabCount
        if (tabCount <= 0) return "(no tabs)"
        val terminalPanel = pane.tabs.getComponentAt(0) as? TerminalPanel ?: return "(tab is not TerminalPanel)"
        return terminalPanel.getTerminalText()
    }

    private fun saveWindowScreenshot(file: File) {
        val width = window.width
        val height = window.height
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2d = image.createGraphics()
        window.paint(g2d)
        g2d.dispose()
        ImageIO.write(image, "png", file)
    }
}

