package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectGroup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class ProjectServiceTest {

    private fun makeCtx(dir: Path): AppContext {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        val group = ProjectGroup(
            id = "g1",
            name = "Test Group",
            directories = listOf(ProjectDirectory("/existing/path", "Existing")),
        )
        ctx.updateConfig(AppConfig(groups = listOf(group)))
        return ctx
    }

    @Test
    fun `addDirectory appends directory and persists`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val service = ProjectService(ctx)
        val group = ctx.config.groups.first()
        val newDir = ProjectDirectory("/new/path", "New Project")

        val updated = service.addDirectory(group, newDir)

        assertEquals(2, updated.directories.size)
        assertEquals("/new/path", updated.directories.last().path)
        // Verify persisted
        assertEquals(2, ctx.config.groups.first().directories.size)
    }

    @Test
    fun `removeDirectory removes matching path and persists`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val service = ProjectService(ctx)
        val group = ctx.config.groups.first()

        val updated = service.removeDirectory(group, "/existing/path")

        assertTrue(updated.directories.isEmpty())
        assertTrue(ctx.config.groups.first().directories.isEmpty())
    }

    @Test
    fun `removeDirectory is a no-op for unknown path`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val service = ProjectService(ctx)
        val group = ctx.config.groups.first()

        val updated = service.removeDirectory(group, "/does/not/exist")

        assertEquals(1, updated.directories.size)
    }

    @Test
    fun `updateDirectory applies transform and persists`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val service = ProjectService(ctx)
        val group = ctx.config.groups.first()

        val updated = service.updateDirectory(group, "/existing/path") {
            it.copy(displayName = "Renamed")
        }

        assertEquals("Renamed", updated.directories.first().displayName)
        assertEquals("Renamed", ctx.config.groups.first().directories.first().displayName)
    }

    @Test
    fun `updateDirectory leaves other directories unchanged`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val service = ProjectService(ctx)
        val groupWithTwo = ctx.config.groups.first().copy(
            directories = listOf(
                ProjectDirectory("/path/a", "A"),
                ProjectDirectory("/path/b", "B"),
            )
        )
        ctx.updateConfig(ctx.config.copy(groups = listOf(groupWithTwo)))

        val updated = service.updateDirectory(groupWithTwo, "/path/a") { it.copy(displayName = "A-updated") }

        assertEquals("A-updated", updated.directories[0].displayName)
        assertEquals("B",         updated.directories[1].displayName)
    }

    @Test
    fun `persist replaces only the matching group`(@TempDir dir: Path) {
        val ctx = makeCtx(dir)
        val service = ProjectService(ctx)
        val second = ProjectGroup(id = "g2", name = "Second")
        ctx.updateConfig(ctx.config.copy(groups = ctx.config.groups + second))

        val updated = ctx.config.groups.first().copy(name = "Renamed Group")
        service.persist(updated)

        assertEquals("Renamed Group", ctx.config.groups.first().name)
        assertEquals("Second",        ctx.config.groups.last().name)
    }
}
