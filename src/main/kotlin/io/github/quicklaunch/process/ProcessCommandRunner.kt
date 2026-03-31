package io.github.quicklaunch.process

import io.github.quicklaunch.model.CommandDescriptor
import java.io.File

class ProcessCommandRunner : CommandRunner {

    override fun run(descriptor: CommandDescriptor, listener: ProcessOutputListener): RunningProcess {
        val process = ProcessBuilder(descriptor.argv)
            .directory(File(descriptor.workingDirectory))
            .redirectErrorStream(true)
            .start()

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
