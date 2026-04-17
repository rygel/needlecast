package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.scanner.IS_MAC
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.swing.JTextArea
import javax.swing.SwingWorker
import javax.swing.UIManager

fun monoFont(): String {
    val available = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .availableFontFamilyNames.toHashSet()
    val preferred = when {
        IS_WINDOWS -> listOf("Cascadia Code", "Cascadia Mono", "JetBrains Mono", "Consolas")
        IS_MAC     -> listOf("SF Mono", "Menlo", "JetBrains Mono", "Monaco")
        else       -> listOf("JetBrains Mono", "Fira Code", "DejaVu Sans Mono", "Liberation Mono")
    }
    return preferred.firstOrNull { it in available } ?: Font.MONOSPACED
}

fun uiBaseFont(): Font =
    UIManager.getFont("defaultFont")
        ?: UIManager.getFont("Label.font")
        ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)

fun availableFontFamilies(): List<String> =
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .availableFontFamilyNames.toList().sorted()

fun availableMonospaceFamilies(): List<String> =
    availableFontFamilies().filter { isMonospaced(it) }

fun isMonospaced(name: String): Boolean {
    val font = Font(name, Font.PLAIN, 12)
    val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    val fm = g.getFontMetrics(font)
    val w1 = fm.charWidth('i')
    val w2 = fm.charWidth('W')
    val w3 = fm.charWidth('m')
    g.dispose()
    return w1 > 0 && w1 == w2 && w2 == w3
}

fun buildOutputArea(): JTextArea = JTextArea().apply {
    isEditable = false
    font = Font(monoFont(), Font.PLAIN, 11)
    lineWrap = true
    wrapStyleWord = false
    rows = 8
}

/**
 * Runs [command] via the OS shell and streams stdout+stderr line-by-line into [outputArea].
 * Call on the EDT; the actual process runs in a background thread.
 * [onFinished] is called on the EDT when the process exits.
 */
fun runCommandStreaming(
    command: String,
    outputArea: JTextArea,
    env: Map<String, String> = emptyMap(),
    onFinished: () -> Unit,
) {
    outputArea.text = ""
    outputArea.append("$ $command\n")

    object : SwingWorker<Int, String>() {
        override fun doInBackground(): Int {
            val argv = if (IS_WINDOWS) listOf("powershell", "-NoProfile", "-Command", command) else listOf("sh", "-c", command)
            val pb = ProcessBuilder(argv).redirectErrorStream(true)
            pb.environment()["PATH"] = System.getenv("PATH") ?: ""
            env.forEach { (k, v) -> pb.environment()[k] = v }
            val proc = pb.start()
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) publish(line)
            }
            return proc.waitFor()
        }

        override fun process(chunks: List<String>) {
            for (line in chunks) {
                outputArea.append("$line\n")
                outputArea.caretPosition = outputArea.document.length
            }
        }

        override fun done() {
            val exitCode = try { get() } catch (e: Exception) {
                outputArea.append("\nError: ${e.cause?.message ?: e.message}\n")
                -1
            }
            if (exitCode == 0) outputArea.append("\nCompleted successfully.\n")
            else if (exitCode > 0) outputArea.append("\nCommand failed (exit code $exitCode).\n")
            outputArea.caretPosition = outputArea.document.length
            onFinished()
        }
    }.execute()
}
