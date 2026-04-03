package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.CommandHistoryEntry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CommandHistoryManagerTest {

    private fun makeCtx(dir: Path): AppContext {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        ctx.updateConfig(AppConfig())
        return ctx
    }

    private fun entry(label: String) = CommandHistoryEntry(
        label = label,
        argv = listOf("mvn", label),
        workingDirectory = "/project",
        exitCode = 0,
    )

    @Test
    fun `record stores entry and persists`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val mgr = CommandHistoryManager(ctx)

        mgr.record("/project", entry("clean"))

        val history = mgr.getHistory("/project")
        assertEquals(1, history.size)
        assertEquals("clean", history[0].label)
        // Verify persisted
        assertEquals(1, ctx.config.commandHistory["/project"]?.size)
    }

    @Test
    fun `record prepends newest entry`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val mgr = CommandHistoryManager(ctx)

        mgr.record("/project", entry("clean"))
        mgr.record("/project", entry("build"))

        val history = mgr.getHistory("/project")
        assertEquals("build", history[0].label)
        assertEquals("clean", history[1].label)
    }

    @Test
    fun `record caps history at MAX entries`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val mgr = CommandHistoryManager(ctx)

        repeat(CommandHistoryManager.MAX + 5) { i -> mgr.record("/project", entry("cmd-$i")) }

        assertEquals(CommandHistoryManager.MAX, mgr.getHistory("/project").size)
    }

    @Test
    fun `getHistory returns empty list for unknown project`(@TempDir dir: Path) {
        val mgr = CommandHistoryManager(makeCtx(dir))
        assertTrue(mgr.getHistory("/no/such/path").isEmpty())
    }

    @Test
    fun `histories for different projects are independent`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val mgr = CommandHistoryManager(ctx)

        mgr.record("/project/a", entry("build-a"))
        mgr.record("/project/b", entry("build-b"))

        assertEquals("build-a", mgr.getHistory("/project/a")[0].label)
        assertEquals("build-b", mgr.getHistory("/project/b")[0].label)
    }
}
