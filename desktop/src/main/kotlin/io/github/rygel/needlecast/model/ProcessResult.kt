package io.github.rygel.needlecast.model

import io.github.rygel.needlecast.process.RunningProcess

sealed class ProcessResult {
    data object NotStarted : ProcessResult()
    data class Running(val process: RunningProcess) : ProcessResult()
    data class Finished(val exitCode: Int) : ProcessResult()
    data class Failed(val reason: String) : ProcessResult()
}
