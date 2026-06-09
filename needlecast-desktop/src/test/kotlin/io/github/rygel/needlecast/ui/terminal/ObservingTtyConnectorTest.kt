package io.github.rygel.needlecast.ui.terminal

import com.jediterm.terminal.Questioner
import com.jediterm.terminal.TtyConnector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Dimension

class ObservingTtyConnectorTest {
    private class FakeTtyConnector(
        private val chunks: List<String>,
        private val eofAtIndex: Int = chunks.size,
    ) : TtyConnector {
        private var callIndex = 0
        var resizeCalled = false
            private set

        override fun read(
            buf: CharArray,
            offset: Int,
            length: Int,
        ): Int {
            if (callIndex >= eofAtIndex) return -1
            val chunk = chunks[callIndex]
            chunk.toCharArray().copyInto(buf, offset)
            callIndex++
            return chunk.length
        }

        override fun resize(termWinSize: Dimension) {
            resizeCalled = true
        }

        override fun write(buffer: ByteArray?) {}

        override fun write(string: String?) {}

        override fun close() {}

        override fun isConnected(): Boolean = callIndex < eofAtIndex

        override fun getName(): String = "FakeTty"

        override fun init(questioner: Questioner?): Boolean = true

        override fun waitFor(): Int = 0

        override fun ready(): Boolean = callIndex < eofAtIndex
    }

    @Test
    fun `onOutput receives chunks`() {
        val received = mutableListOf<String>()
        val fake = FakeTtyConnector(listOf("hello", "world"))
        val connector = ObservingTtyConnector(fake, onOutput = { received.add(it) })

        val buf = CharArray(256)
        connector.read(buf, 0, buf.size)
        connector.read(buf, 0, buf.size)

        assertThat(received).containsExactly("hello", "world")
    }

    @Test
    fun `onEof called when read returns negative`() {
        var eofCalled = false
        val fake = FakeTtyConnector(listOf("data"), eofAtIndex = 1)
        val connector =
            ObservingTtyConnector(
                fake,
                onOutput = {},
                onEof = { eofCalled = true },
            )

        val buf = CharArray(256)
        connector.read(buf, 0, buf.size)
        assertThat(eofCalled).isFalse()

        connector.read(buf, 0, buf.size)
        assertThat(eofCalled).isTrue()
    }

    @Test
    fun `onEof not called when no callback provided`() {
        val fake = FakeTtyConnector(listOf("data"), eofAtIndex = 1)
        val connector = ObservingTtyConnector(fake, onOutput = {})

        val buf = CharArray(256)
        connector.read(buf, 0, buf.size)
        connector.read(buf, 0, buf.size)

        assertThat(connector.isConnected).isFalse()
    }

    @Test
    fun `delegates resize to inner connector`() {
        val fake = FakeTtyConnector(emptyList())
        val connector = ObservingTtyConnector(fake, onOutput = {})

        connector.resize(Dimension(80, 24))

        assertThat(fake.resizeCalled).isTrue()
    }

    @Test
    fun `onEof called on every consecutive EOF read`() {
        var eofCount = 0
        val fake = FakeTtyConnector(emptyList(), eofAtIndex = 0)
        val connector = ObservingTtyConnector(fake, onOutput = {}, onEof = { eofCount++ })

        val buf = CharArray(256)
        repeat(3) { connector.read(buf, 0, buf.size) }

        assertThat(eofCount).isEqualTo(3)
    }

    @Test
    fun `onOutput not called on EOF`() {
        val received = mutableListOf<String>()
        val fake = FakeTtyConnector(emptyList(), eofAtIndex = 0)
        val connector = ObservingTtyConnector(fake, onOutput = { received.add(it) })

        val buf = CharArray(256)
        val result = connector.read(buf, 0, buf.size)

        assertThat(result).isEqualTo(-1)
        assertThat(received).isEmpty()
    }
}
