package io.github.rygel.needlecast.ui

import javax.swing.Timer
import javax.swing.text.JTextComponent

/**
 * Incrementally updates large text blobs on the EDT to avoid long UI stalls.
 *
 * Uses a Swing Timer to insert chunks into the document. If a new update starts,
 * any previous chunking timer on the same component is cancelled.
 */
object TextChunker {

    private const val TIMER_PROP = "needlecast.textChunkTimer"

    fun setTextChunked(
        target: JTextComponent,
        text: String,
        chunkSize: Int = 32_000,
        delayMs: Int = 5,
        onDone: (() -> Unit)? = null,
    ) {
        cancel(target)

        if (text.isEmpty()) {
            target.text = ""
            onDone?.invoke()
            return
        }
        if (text.length <= chunkSize) {
            target.text = text
            onDone?.invoke()
            return
        }

        val doc = target.document
        try { doc.remove(0, doc.length) } catch (_: Exception) {}

        var index = 0
        val timer = Timer(delayMs) {
            val end = (index + chunkSize).coerceAtMost(text.length)
            try {
                doc.insertString(doc.length, text.substring(index, end), null)
            } catch (_: Exception) {
                // If insert fails, stop to avoid a tight loop.
                cancel(target)
                onDone?.invoke()
                return@Timer
            }
            index = end
            if (index >= text.length) {
                cancel(target)
                onDone?.invoke()
            }
        }.apply { isRepeats = true }

        target.putClientProperty(TIMER_PROP, timer)
        timer.start()
    }

    fun cancel(target: JTextComponent) {
        val timer = target.getClientProperty(TIMER_PROP) as? Timer
        timer?.stop()
        target.putClientProperty(TIMER_PROP, null)
    }
}
