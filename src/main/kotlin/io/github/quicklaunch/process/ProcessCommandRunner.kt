package io.github.quicklaunch.process

import io.github.quicklaunch.model.CommandDescriptor
import java.io.File

class ProcessCommandRunner : CommandRunner {

    override fun run(descriptor: CommandDescriptor, listener: ProcessOutputListener): RunningProcess {
        val pb = ProcessBuilder(descriptor.argv)
            .directory(File(descriptor.workingDirectory))
            .redirectErrorStream(true)
        if (descriptor.env.isNotEmpty()) pb.environment().putAll(descriptor.env)
        val process = pb.start()

        val runningProcess = RunningProcess(process)

        Thread({
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> listener.onLine(line) }
                }
                listener.onExit(process.waitFor())
            } catch (e: Exception) {
                listener.onLine("[Error reading process output: ${e.message}]")
                listener.onExit(-1)
            }
        }, "process-reader").apply {
            isDaemon = true
            start()
        }

        return runningProcess
    }
}
