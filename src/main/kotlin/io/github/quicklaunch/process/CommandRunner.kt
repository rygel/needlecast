package io.github.quicklaunch.process

import io.github.quicklaunch.model.CommandDescriptor

interface CommandRunner {
    fun run(descriptor: CommandDescriptor, listener: ProcessOutputListener): RunningProcess
}
