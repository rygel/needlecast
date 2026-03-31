package io.github.quicklaunch.process

class RunningProcess(private val process: Process) {
    val isAlive: Boolean get() = process.isAlive
    fun cancel() { process.destroyForcibly() }
}
