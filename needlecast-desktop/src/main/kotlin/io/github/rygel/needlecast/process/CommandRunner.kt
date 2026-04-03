package io.github.rygel.needlecast.process

import io.github.rygel.needlecast.model.CommandDescriptor

interface CommandRunner {
    fun run(descriptor: CommandDescriptor, listener: ProcessOutputListener): RunningProcess
}
