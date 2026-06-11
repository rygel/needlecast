package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.TtyConnector
import org.slf4j.LoggerFactory
import java.awt.Dimension

class ObservingTtyConnector(
    private val delegate: TtyConnector,
    private val onOutput: (chunk: String) -> Unit,
    private val onEof: (() -> Unit)? = null,
    private val onBell: (() -> Unit)? = null,
) : TtyConnector by delegate {
    private val logger = LoggerFactory.getLogger(ObservingTtyConnector::class.java)

    override fun read(
        buf: CharArray,
        offset: Int,
        length: Int,
    ): Int {
        val n = delegate.read(buf, offset, length)
        if (n > 0) {
            if (onBell != null) {
                val s = String(buf, offset, n)
                if ('\u0007' in s) {
                    var i = 0
                    while (i < s.length) {
                        val idx = s.indexOf('\u0007', i)
                        if (idx < 0) break
                        onBell.invoke()
                        i = idx + 1
                    }
                }
                onOutput(s)
            } else {
                onOutput(String(buf, offset, n))
            }
        } else if (n < 0) {
            logger.info("TtyConnector.read returned {} (EOF) — child process exited", n)
            onEof?.invoke()
        }
        return n
    }

    override fun resize(termWinSize: Dimension) {
        logger.info("ObservingTtyConnector.resize: cols={}, rows={}", termWinSize.width, termWinSize.height)
        delegate.resize(termWinSize)
    }
}
