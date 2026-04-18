package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.TtyConnector

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
