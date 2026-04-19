package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.TtyConnector
import org.slf4j.LoggerFactory
import java.awt.Dimension

class ObservingTtyConnector(
    private val delegate: TtyConnector,
    private val onOutput: (chunk: String) -> Unit,
) : TtyConnector by delegate {

    private val logger = LoggerFactory.getLogger(ObservingTtyConnector::class.java)

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val n = delegate.read(buf, offset, length)
        if (n > 0) onOutput(String(buf, offset, n))
        return n
    }

    override fun resize(termWinSize: Dimension) {
        logger.info("ObservingTtyConnector.resize: cols={}, rows={}", termWinSize.width, termWinSize.height)
        delegate.resize(termWinSize)
    }
}
