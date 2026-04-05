package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectGroup
import io.github.rygel.needlecast.model.PromptTemplate
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
        assertEquals("system", config.theme)
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

    @Test
    fun `tabsOnTop persists across save and load`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))

        store.save(AppConfig(tabsOnTop = true))
        assertTrue(store.load().tabsOnTop, "tabsOnTop=true should survive a round-trip")

        store.save(AppConfig(tabsOnTop = false))
        assertFalse(store.load().tabsOnTop, "tabsOnTop=false should survive a round-trip")
    }

    @Test
    fun `terminal colors persist across save and load`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val config = AppConfig(terminalBackground = "#1E1E1E", terminalForeground = "#D4D4D4")

        store.save(config)
        val loaded = store.load()

        assertEquals("#1E1E1E", loaded.terminalBackground)
        assertEquals("#D4D4D4", loaded.terminalForeground)
    }

    @Test
    fun `terminal colors default to null when not set`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        store.save(AppConfig())
        val loaded = store.load()
        assertNull(loaded.terminalBackground)
        assertNull(loaded.terminalForeground)
    }

    @Test
    fun `syntaxTheme persists across save and load`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        store.save(AppConfig(syntaxTheme = "monokai"))
        assertEquals("monokai", store.load().syntaxTheme)
    }

    @Test
    fun `syntaxTheme defaults to auto`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        store.save(AppConfig())
        assertEquals("auto", store.load().syntaxTheme)
    }

    @Test
    fun `dockingActiveHighlight persists across save and load`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        store.save(AppConfig(dockingActiveHighlight = true))
        assertTrue(store.load().dockingActiveHighlight)
        store.save(AppConfig(dockingActiveHighlight = false))
        assertFalse(store.load().dockingActiveHighlight)
    }

    @Test
    fun `dockingActiveHighlight defaults to false`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        store.save(AppConfig())
        assertFalse(store.load().dockingActiveHighlight)
    }

    @Test
    fun `promptLibrary round-trips correctly`(@TempDir dir: Path) {
        val store = JsonConfigStore(dir.resolve("config.json"))
        val template = PromptTemplate(
            id          = "test-id-1",
            name        = "Code Review",
            category    = "Review",
            description = "Ask the AI to review code",
            body        = "Please review {fileName} for {concern}.",
        )
        val config = AppConfig(promptLibrary = listOf(template))
        store.save(config)

        val loaded = store.load()
        assertEquals(1, loaded.promptLibrary.size)
        assertEquals("Code Review",  loaded.promptLibrary[0].name)
        assertEquals("test-id-1",    loaded.promptLibrary[0].id)
        assertEquals("Review",       loaded.promptLibrary[0].category)
        assertTrue(loaded.promptLibrary[0].body.contains("{fileName}"))
    }
}
