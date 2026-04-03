package io.github.rygel.needlecast.service

data class QueuedCommand(val label: String, val argv: List<String>, val workingDir: String)

/**
 * Thread-safe FIFO queue of [QueuedCommand] items.
 * Extracted from [io.github.rygel.needlecast.ui.CommandPanel] to be independently testable.
 */
class CommandQueue {

    private val deque = ArrayDeque<QueuedCommand>()

    val size: Int get() = deque.size
    val isEmpty: Boolean get() = deque.isEmpty()

    fun enqueue(command: QueuedCommand) {
        deque.addLast(command)
    }

    /** Removes and returns the next command, or null if the queue is empty. */
    fun drain(): QueuedCommand? = if (deque.isEmpty()) null else deque.removeFirst()

    fun clear() {
        deque.clear()
    }

    /** Returns an immutable snapshot of the current queue contents in FIFO order. */
    fun snapshot(): List<QueuedCommand> = deque.toList()
}
