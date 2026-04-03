package io.github.rygel.needlecast.process

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

class ProcessExecutorTest {

    @Test
    fun `run returns output and exit code for a successful command`() {
        val argv = if (isWindows()) listOf("cmd", "/c", "echo", "hello") else listOf("echo", "hello")
        val result = ProcessExecutor.run(argv)
        assertNotNull(result)
        assertEquals(0, result!!.exitCode)
        assertTrue(result.output.contains("hello"))
    }

    @Test
    fun `run returns non-zero exit code for a failing command`() {
        // 'false' on Unix / 'exit 1' via cmd on Windows always exits with code 1
        val argv = if (isWindows()) listOf("cmd", "/c", "exit", "1") else listOf("false")
        val result = ProcessExecutor.run(argv)
        assertNotNull(result)
        assertNotEquals(0, result!!.exitCode)
    }

    @Test
    fun `run returns null when process exceeds timeout`() {
        // ping loops ~10 s on both platforms; avoids Git-for-Windows 'timeout' collision on Windows
        val argv = if (isWindows()) listOf("ping", "-n", "11", "127.0.0.1")
                   else listOf("sleep", "10")
        val result = ProcessExecutor.run(argv, timeoutMs = 100L)
        assertNull(result)
    }

    @Test
    fun `run returns null for a non-existent command`() {
        val result = ProcessExecutor.run(listOf("this-command-does-not-exist-xyz"))
        assertNull(result)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `isOnPath returns true for a command that exists on Unix`() {
        assertTrue(ProcessExecutor.isOnPath("sh"))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `isOnPath returns true for a command that exists on Windows`() {
        assertTrue(ProcessExecutor.isOnPath("cmd"))
    }

    @Test
    fun `isOnPath returns false for a command that does not exist`() {
        assertFalse(ProcessExecutor.isOnPath("this-command-does-not-exist-xyz"))
    }

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
}
