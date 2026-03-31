package io.github.quicklaunch.process

interface ProcessOutputListener {
    fun onLine(line: String)
    fun onExit(exitCode: Int)
}
