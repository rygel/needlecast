package io.github.rygel.needlecast.process

import io.github.rygel.needlecast.model.CommandDescriptor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class ProcessCommandRunner {
    private val logger = LoggerFactory.getLogger(ProcessCommandRunner::class.java)

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 300_000L
        private val NOOP_PROCESS =
            object : Process() {
                override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
                override fun getInputStream(): InputStream = InputStream.nullInputStream()
                override fun getErrorStream(): InputStream = InputStream.nullInputStream()
                override fun waitFor() = -1
                override fun exitValue() = -1
                override fun destroy() {}
            }
    }

    fun run(
        descriptor: CommandDescriptor,
        listener: ProcessOutputListener,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): RunningProcess {
        val pb =
            ProcessBuilder(descriptor.argv)
                .directory(File(descriptor.workingDirectory))
                .redirectErrorStream(true)
        if (descriptor.env.isNotEmpty()) pb.environment().putAll(descriptor.env)

        val process =
            try {
                pb.start()
            } catch (e: Exception) {
                logger.warn("Failed to start process: {}", descriptor.argv, e)
                listener.onLine("[Failed to start: ${e.message}]")
                listener.onExit(-1)
                return RunningProcess(NOOP_PROCESS, Thread.currentThread())
            }

        val readerThread =
            Thread({
                try {
                    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { line -> listener.onLine(line) }
                    }
                    listener.onExit(process.waitFor())
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    listener.onExit(-1)
                } catch (e: Exception) {
                    logger.warn("Error reading process output", e)
                    listener.onLine("[Error reading process output: ${e.message}]")
                    listener.onExit(-1)
                }
            }, "process-reader").apply {
                isDaemon = true
                start()
            }

        Thread({
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                logger.warn("Process timed out after {}ms, destroying: {}", timeoutMs, descriptor.argv)
                process.destroyForcibly()
                readerThread.interrupt()
            }
        }, "process-watchdog").apply {
            isDaemon = true
            start()
        }

        return RunningProcess(process, readerThread)
    }

}
