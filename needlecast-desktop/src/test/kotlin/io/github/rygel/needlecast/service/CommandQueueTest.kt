package io.github.rygel.needlecast.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommandQueueTest {

    private lateinit var queue: CommandQueue

    @BeforeEach
    fun setUp() {
        queue = CommandQueue()
    }

    private fun cmd(label: String) = QueuedCommand(
        label = label,
        argv = listOf("mvn", label),
        workingDir = "/project",
    )

    @Test
    fun `new queue is empty`() {
        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
    }

    @Test
    fun `drain on empty queue returns null`() {
        assertNull(queue.drain())
    }

    @Test
    fun `enqueue increases size`() {
        queue.enqueue(cmd("clean"))
        assertEquals(1, queue.size)
        assertFalse(queue.isEmpty)
    }

    @Test
    fun `drain returns items in FIFO order`() {
        queue.enqueue(cmd("clean"))
        queue.enqueue(cmd("build"))
        queue.enqueue(cmd("test"))

        assertEquals("clean", queue.drain()!!.label)
        assertEquals("build", queue.drain()!!.label)
        assertEquals("test",  queue.drain()!!.label)
    }

    @Test
    fun `drain decreases size`() {
        queue.enqueue(cmd("clean"))
        queue.enqueue(cmd("build"))

        queue.drain()
        assertEquals(1, queue.size)

        queue.drain()
        assertEquals(0, queue.size)
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `drain returns null after last item is removed`() {
        queue.enqueue(cmd("clean"))
        queue.drain()
        assertNull(queue.drain())
    }

    @Test
    fun `clear empties the queue`() {
        queue.enqueue(cmd("clean"))
        queue.enqueue(cmd("build"))
        queue.clear()

        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
        assertNull(queue.drain())
    }

    @Test
    fun `snapshot returns items in FIFO order without mutating queue`() {
        queue.enqueue(cmd("clean"))
        queue.enqueue(cmd("build"))

        val snap = queue.snapshot()
        assertEquals(listOf("clean", "build"), snap.map { it.label })
        // Queue itself is unaffected
        assertEquals(2, queue.size)
    }

    @Test
    fun `snapshot is isolated from subsequent mutations`() {
        queue.enqueue(cmd("clean"))
        val snap = queue.snapshot()

        queue.enqueue(cmd("build"))
        assertEquals(1, snap.size)   // snapshot does not grow
        assertEquals(2, queue.size)  // queue grew
    }

    @Test
    fun `enqueue after drain works correctly`() {
        queue.enqueue(cmd("clean"))
        queue.drain()
        queue.enqueue(cmd("build"))

        assertEquals(1, queue.size)
        assertEquals("build", queue.drain()!!.label)
    }
}
