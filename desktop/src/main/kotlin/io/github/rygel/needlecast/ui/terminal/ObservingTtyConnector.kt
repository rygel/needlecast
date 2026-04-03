package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.TtyConnector

/**
 * Wraps a [TtyConnector] and fires [onOutput] on the JediTerm reader thread whenever
 * [read] returns at least one character. The decoded string slice is passed so callers
 * can inspect content (e.g. for spinner-char detection) without re-decoding.
 *
 * Callers must not touch Swing state inside [onOutput] — use [javax.swing.SwingUtilities.invokeLater].
 */
class ObservingTtyConnector(
    private val delegate: TtyConnector,
    private val onOutput: (chunk: String) -> Unit,
) : TtyConnector by delegate {

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val n = delegate.read(buf, offset, length)
        if (n > 0) onOutput(String(buf, offset, n))
        return n
    }
}
