package io.github.quicklaunch.scanner

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap

private val BUILD_FILE_NAMES = setOf(
    "pom.xml",
    "build.gradle",
    "build.gradle.kts",
    "package.json",
)

private val BUILD_FILE_EXTENSIONS = setOf("sln", "csproj")

private fun isBuildFile(fileName: String): Boolean {
    if (fileName in BUILD_FILE_NAMES) return true
    val ext = fileName.substringAfterLast('.', "")
    return ext in BUILD_FILE_EXTENSIONS
}

/**
 * Watches registered project directories for changes to recognized build files and invokes
 * [onChanged] with the directory path when a build file changes. Events are debounced
 * (≥500 ms) so rapid saves do not fire multiple rescans.
 *
 * Call [watch] to register a directory, [unwatch] to deregister one, and [stop] to shut
 * down the background thread entirely.
 */
class BuildFileWatcher(private val onChanged: (String) -> Unit) {

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()

    /** Maps WatchKey → directory path string. */
    private val keyToPath = ConcurrentHashMap<WatchKey, String>()

    /** Maps directory path string → WatchKey, for deregistration. */
    private val pathToKey = ConcurrentHashMap<String, WatchKey>()

    /** Last-fire timestamp per path, used for debouncing. */
    private val lastFired = ConcurrentHashMap<String, Long>()

    private val thread = Thread(::pollLoop, "build-file-watcher").apply {
        isDaemon = true
        start()
    }

    @Volatile private var running = true

    /** Register [path] for watching. Safe to call from any thread. */
    fun watch(path: String) {
        if (pathToKey.containsKey(path)) return
        try {
            val dir: Path = Paths.get(path)
            val key: WatchKey = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY)
            keyToPath[key] = path
            pathToKey[path] = key
        } catch (_: Exception) {
            // Directory may not exist yet or may not be accessible; silently skip.
        }
    }

    /** Deregister [path]. Safe to call from any thread. */
    fun unwatch(path: String) {
        val key = pathToKey.remove(path) ?: return
        keyToPath.remove(key)
        lastFired.remove(path)
        try { key.cancel() } catch (_: Exception) {}
    }

    /** Deregister all watched paths. */
    fun unwatchAll() {
        pathToKey.keys.toList().forEach { unwatch(it) }
    }

    /** Stop the watcher thread and release resources. */
    fun stop() {
        running = false
        try { watchService.close() } catch (_: Exception) {}
    }

    private fun pollLoop() {
        while (running) {
            val key: WatchKey = try {
                watchService.take()
            } catch (_: Exception) {
                break
            }

            val dirPath = keyToPath[key]
            if (dirPath != null) {
                val buildFileChanged = key.pollEvents().any { event ->
                    val context = event.context()
                    context is Path && isBuildFile(context.fileName.toString())
                }
                if (buildFileChanged) {
                    val now = System.currentTimeMillis()
                    val last = lastFired[dirPath] ?: 0L
                    if (now - last >= 500L) {
                        lastFired[dirPath] = now
                        onChanged(dirPath)
                    }
                }
            }

            if (!key.reset()) {
                // Directory no longer accessible — remove it.
                keyToPath.remove(key)?.let { pathToKey.remove(it) }
            }
        }
    }
}
