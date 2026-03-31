package io.github.quicklaunch.model

import io.github.quicklaunch.process.RunningProcess

sealed class ProcessResult {
    data object NotStarted : ProcessResult()
    data class Running(val process: RunningProcess) : ProcessResult()
    data class Finished(val exitCode: Int) : ProcessResult()
    data class Failed(val reason: String) : ProcessResult()
}
