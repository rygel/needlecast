package io.github.rygel.needlecast.process

interface ProcessOutputListener {
    fun onLine(line: String)
    fun onExit(exitCode: Int)
}
