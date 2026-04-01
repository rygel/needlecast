package io.github.quicklaunch.config

import io.github.quicklaunch.model.AppConfig
import io.github.quicklaunch.model.ProjectDirectory
import io.github.quicklaunch.model.ProjectGroup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class ConfigRoundTripTest {

    @Test
    fun `save and load preserves all groups and directories`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val group = ProjectGroup(
            id = UUID.randomUUID().toString(),
            name = "Work",
            directories = listOf(
                ProjectDirectory("/home/user/project-a", "Project A"),
                ProjectDirectory("/home/user/project-b"),
            ),
        )
        val config = AppConfig(groups = listOf(group), theme = "dark")

        store.save(config)
        val loaded = store.load()

        assertEquals(1, loaded.groups.size)
        assertEquals("Work", loaded.groups[0].name)
        assertEquals(2, loaded.groups[0].directories.size)
        assertEquals("Project A", loaded.groups[0].directories[0].displayName)
        assertEquals("/home/user/project-b", loaded.groups[0].directories[1].path)
        assertEquals("dark", loaded.theme)
    }

    @Test
    fun `load returns default config when file does not exist`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("nonexistent.json"))
        val config = store.load()
        assertTrue(config.groups.isEmpty())
        assertEquals("dark", config.theme)
    }

    @Test
    fun `export and import round-trips config correctly`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val group = ProjectGroup(UUID.randomUUID().toString(), "Export Test")
        val original = AppConfig(groups = listOf(group), theme = "light")

        store.save(original)

        val exportPath = dir.resolve("export.json")
        store.export(original, exportPath)
        val imported = store.import(exportPath)

        assertEquals(original.groups.size, imported.groups.size)
        assertEquals(original.groups[0].name, imported.groups[0].name)
        assertEquals("light", imported.theme)
    }

    @Test
    fun `window dimensions are persisted`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val config = AppConfig(windowWidth = 1920, windowHeight = 1080)
        store.save(config)
        val loaded = store.load()
        assertEquals(1920, loaded.windowWidth)
        assertEquals(1080, loaded.windowHeight)
    }
}
