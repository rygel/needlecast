package io.github.quicklaunch.config

import io.github.quicklaunch.model.AppConfig
import io.github.quicklaunch.model.ProjectDirectory
import io.github.quicklaunch.model.ProjectGroup
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonConfigStoreTest {

    @Test
    fun `round-trips AppConfig to file and back`(@TempDir dir: Path) {
        val configPath = dir.resolve("config.json")
        val store = JsonConfigStore(configPath)

        val config = AppConfig(
            groups = listOf(
                ProjectGroup(
                    id = "group-1",
                    name = "Work Projects",
                    directories = listOf(
                        ProjectDirectory(path = "/home/user/work/project-a"),
                        ProjectDirectory(path = "/home/user/work/project-b", displayName = "Project B"),
                    ),
                ),
                ProjectGroup(
                    id = "group-2",
                    name = "Personal",
                    directories = emptyList(),
                ),
            ),
            theme = "dark",
        )

        store.save(config)
        val loaded = store.load()

        assertEquals(config, loaded)
    }

    @Test
    fun `returns default config when file does not exist`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("nonexistent.json"))
        val config = store.load()
        assertEquals(AppConfig(), config)
    }

    @Test
    fun `import and export round-trip`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val exportPath = dir.resolve("export.json")

        val config = AppConfig(
            groups = listOf(ProjectGroup("id-1", "Test Group")),
        )

        store.export(config, exportPath)
        val imported = store.import(exportPath)

        assertEquals(config, imported)
    }

    @Test
    fun `save is atomic (tmp file is cleaned up)`(@TempDir dir: Path) {
        val configPath = dir.resolve("config.json")
        val store = JsonConfigStore(configPath)

        store.save(AppConfig())

        assertFalse(dir.resolve("config.json.tmp").toFile().exists())
        assertTrue(configPath.toFile().exists())
    }
}
