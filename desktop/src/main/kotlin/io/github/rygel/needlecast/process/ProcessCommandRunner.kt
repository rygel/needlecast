package io.github.rygel.needlecast.process

import io.github.rygel.needlecast.model.CommandDescriptor
import java.io.File

class ProcessCommandRunner : CommandRunner {

    override fun run(descriptor: CommandDescriptor, listener: ProcessOutputListener): RunningProcess {
        val pb = ProcessBuilder(descriptor.argv)
            .directory(File(descriptor.workingDirectory))
            .redirectErrorStream(true)
        if (descriptor.env.isNotEmpty()) pb.environment().putAll(descriptor.env)
        val process = pb.start()

        val readerThread = Thread({
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> listener.onLine(line) }
                }
                listener.onExit(process.waitFor())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                listener.onExit(-1)
            } catch (e: Exception) {
                listener.onLine("[Error reading process output: ${e.message}]")
                listener.onExit(-1)
            }
        }, "process-reader").apply {
            isDaemon = true
            start()
        }

        return RunningProcess(process, readerThread)
    }
}
