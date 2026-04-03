package io.github.rygel.needlecast.process

class RunningProcess(
    private val process: Process,
    private val readerThread: Thread? = null,
) {
    val isAlive: Boolean get() = process.isAlive

    fun cancel() {
        process.destroyForcibly()
        readerThread?.interrupt()
    }
}
