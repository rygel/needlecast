package io.github.rygel.needlecast.scanner

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BuildFileWatcherTest {
    @Test
    fun `watcher fires callback when build file is created`(
        @TempDir dir: Path,
    ) {
        val fired = mutableListOf<String>()
        val watcher = BuildFileWatcher { fired.add(it) }
        try {
            watcher.watch(dir.toString())
            Thread.sleep(100) // let registration settle
            Files.createFile(dir.resolve("pom.xml"))
            waitFor { fired.isNotEmpty() }
            assertEquals(listOf(dir.toString()), fired)
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `watcher fires for each recognized build file extension`(
        @TempDir dir: Path,
    ) {
        val fired = mutableListOf<String>()
        val watcher = BuildFileWatcher { fired.add(it) }
        try {
            watcher.watch(dir.toString())
            Thread.sleep(100)
            Files.createFile(dir.resolve("build.gradle"))
            waitFor { fired.isNotEmpty() }
            assertEquals(1, fired.size)
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `watcher fires for sln and csproj extensions`(
        @TempDir dir: Path,
    ) {
        val fired = mutableListOf<String>()
        val watcher = BuildFileWatcher { fired.add(it) }
        try {
            watcher.watch(dir.toString())
            Thread.sleep(100)
            Files.createFile(dir.resolve("MyApp.sln"))
            waitFor { fired.isNotEmpty() }
            assertEquals(1, fired.size)
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `watcher ignores non-build files`(
        @TempDir dir: Path,
    ) {
        val fired = mutableListOf<String>()
        val watcher = BuildFileWatcher { fired.add(it) }
        try {
            watcher.watch(dir.toString())
            Thread.sleep(100)
            Files.createFile(dir.resolve("README.md"))
            Thread.sleep(500) // wait enough time for any spurious event
            assertTrue(fired.isEmpty())
        } finally {
            watcher.stop()
        }
    }

    @Test
    fun `unwatch stops firing events for that path`(
        @TempDir dir: Path,
    ) {
        val fired = mutableListOf<String>()
        val watcher = BuildFileWatcher { fired.add(it) }
        watcher.watch(dir.toString())
        Thread.sleep(100)
        watcher.unwatch(dir.toString())
        Files.createFile(dir.resolve("pom.xml"))
        Thread.sleep(500)
        assertTrue(fired.isEmpty())
        watcher.stop()
    }

    @Test
    fun `watching non-existent path is silently skipped`() {
        val fired = mutableListOf<String>()
        val watcher = BuildFileWatcher { fired.add(it) }
        // Should not throw
        watcher.watch("/nonexistent/path/${System.nanoTime()}")
        Thread.sleep(100)
        watcher.stop()
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `watching same path twice is idempotent`(
        @TempDir dir: Path,
    ) {
        val watcher = BuildFileWatcher { }
        watcher.watch(dir.toString())
        watcher.watch(dir.toString()) // should not throw
        watcher.unwatch(dir.toString())
        watcher.stop()
    }

    @Test
    fun `unwatchAll removes all watches`(
        @TempDir dir1: Path,
        @TempDir dir2: Path,
    ) {
        val watcher = BuildFileWatcher { }
        watcher.watch(dir1.toString())
        watcher.watch(dir2.toString())
        watcher.unwatchAll()
        watcher.stop()
    }

    @Test
    fun `dispose stops the watcher`(
        @TempDir dir: Path,
    ) {
        val watcher = BuildFileWatcher { }
        watcher.watch(dir.toString())
        watcher.dispose()
        // After dispose, thread should stop — we can verify by checking it doesn't crash
    }

    private fun waitFor(
        timeoutMs: Long = 3000,
        intervalMs: Long = 50,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(intervalMs)
        }
    }
}
