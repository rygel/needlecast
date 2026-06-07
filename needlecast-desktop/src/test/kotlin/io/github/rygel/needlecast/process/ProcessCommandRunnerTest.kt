package io.github.rygel.needlecast.process

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProcessCommandRunnerTest {

    private val runner = ProcessCommandRunner()

    @Test
    fun `run executes command and captures stdout`(@TempDir dir: Path) {
        val lines = mutableListOf<String>()
        val exitLatch = CountDownLatch(1)
        val listener = object : ProcessOutputListener {
            override fun onLine(line: String) { lines.add(line) }
            override fun onExit(exitCode: Int) { exitLatch.countDown() }
        }

        val argv = if (isWindows()) listOf("cmd", "/c", "echo", "hello world") else listOf("echo", "hello world")
        val descriptor = CommandDescriptor("test", BuildTool.MAVEN, argv, dir.toString())
        runner.run(descriptor, listener)

        assertTrue(exitLatch.await(5, TimeUnit.SECONDS), "Process should exit within timeout")
        assertTrue(lines.any { it.contains("hello world") }, "Should capture stdout: $lines")
    }

    @Test
    fun `run reports non-zero exit code`(@TempDir dir: Path) {
        var exitCode = Int.MIN_VALUE
        val exitLatch = CountDownLatch(1)
        val listener = object : ProcessOutputListener {
            override fun onLine(line: String) {}
            override fun onExit(code: Int) { exitCode = code; exitLatch.countDown() }
        }

        val argv = if (isWindows()) listOf("cmd", "/c", "exit", "42") else listOf("sh", "-c", "exit 42")
        val descriptor = CommandDescriptor("test", BuildTool.MAVEN, argv, dir.toString())
        runner.run(descriptor, listener)

        assertTrue(exitLatch.await(5, TimeUnit.SECONDS))
        assertEquals(42, exitCode)
    }

    @Test
    fun `run respects working directory`(@TempDir dir: Path) {
        val lines = mutableListOf<String>()
        val exitLatch = CountDownLatch(1)
        val listener = object : ProcessOutputListener {
            override fun onLine(line: String) { lines.add(line) }
            override fun onExit(exitCode: Int) { exitLatch.countDown() }
        }

        val argv = if (isWindows()) listOf("cmd", "/c", "cd") else listOf("pwd")
        val descriptor = CommandDescriptor("test", BuildTool.MAVEN, argv, dir.toString())
        runner.run(descriptor, listener)

        assertTrue(exitLatch.await(5, TimeUnit.SECONDS))
        assertTrue(lines.any { it.contains(dir.toString()) || it.contains(dir.toRealPath().toString()) },
            "Working directory should be $dir but got: $lines")
    }

    @Test
    fun `run passes environment variables`(@TempDir dir: Path) {
        val lines = mutableListOf<String>()
        val exitLatch = CountDownLatch(1)
        val listener = object : ProcessOutputListener {
            override fun onLine(line: String) { lines.add(line) }
            override fun onExit(exitCode: Int) { exitLatch.countDown() }
        }

        val argv = if (isWindows()) listOf("cmd", "/c", "echo", "%NEEDLECAST_TEST_VAR%")
                   else listOf("sh", "-c", "echo \$NEEDLECAST_TEST_VAR")
        val descriptor = CommandDescriptor(
            "test", BuildTool.MAVEN, argv, dir.toString(),
            env = mapOf("NEEDLECAST_TEST_VAR" to "test-value-123")
        )
        runner.run(descriptor, listener)

        assertTrue(exitLatch.await(5, TimeUnit.SECONDS))
        assertTrue(lines.any { it.contains("test-value-123") },
            "Should see env variable value: $lines")
    }

    @Test
    fun `run captures multi-line output`(@TempDir dir: Path) {
        val lines = mutableListOf<String>()
        val exitLatch = CountDownLatch(1)
        val listener = object : ProcessOutputListener {
            override fun onLine(line: String) { lines.add(line) }
            override fun onExit(exitCode: Int) { exitLatch.countDown() }
        }

        val argv = if (isWindows()) listOf("cmd", "/c", "echo line1&& echo line2&& echo line3")
                   else listOf("sh", "-c", "echo line1; echo line2; echo line3")
        val descriptor = CommandDescriptor("test", BuildTool.MAVEN, argv, dir.toString())
        runner.run(descriptor, listener)

        assertTrue(exitLatch.await(5, TimeUnit.SECONDS))
        assertTrue(lines.size >= 3, "Should capture at least 3 lines: $lines")
        assertTrue(lines.any { it.contains("line1") })
        assertTrue(lines.any { it.contains("line2") })
        assertTrue(lines.any { it.contains("line3") })
    }

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
}
