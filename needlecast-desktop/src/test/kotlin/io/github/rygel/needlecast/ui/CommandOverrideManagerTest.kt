package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.config.PromptLibraryStore
import io.github.rygel.needlecast.config.SkillLibraryStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandOverride
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CommandOverrideManagerTest {
    @Test
    fun `findActiveOverride returns null when no overrides exist`(
        @TempDir dir: Path,
    ) {
        val ctx = newTestContext(dir)
        val manager =
            CommandOverrideManager(
                ctx = ctx,
                currentProjectPath = { "/project" },
                updateModel = { _, _ -> },
                selectedIndex = { 0 },
            )
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("mvn", "clean"), "/project")

        assertNull(manager.findActiveOverride(cmd))
    }

    @Test
    fun `findActiveOverride returns null when project path is null`(
        @TempDir dir: Path,
    ) {
        val override =
            CommandOverride(
                originalArgv = listOf("mvn", "clean"),
                label = "Build",
                argv = listOf("mvn", "clean", "-DskipTests"),
            )
        val ctx =
            newTestContext(
                dir,
                AppConfig(
                    commandOverrides = mapOf("/project" to listOf(override)),
                ),
            )
        val manager =
            CommandOverrideManager(
                ctx = ctx,
                currentProjectPath = { null },
                updateModel = { _, _ -> },
                selectedIndex = { 0 },
            )
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("mvn", "clean"), "/project")

        assertNull(manager.findActiveOverride(cmd))
    }

    @Test
    fun `findActiveOverride matches by argv`(
        @TempDir dir: Path,
    ) {
        val override =
            CommandOverride(
                originalArgv = listOf("mvn", "clean"),
                label = "Fast Build",
                argv = listOf("mvn", "clean", "-DskipTests"),
            )
        val ctx =
            newTestContext(
                dir,
                AppConfig(
                    commandOverrides = mapOf("/project" to listOf(override)),
                ),
            )
        val manager =
            CommandOverrideManager(
                ctx = ctx,
                currentProjectPath = { "/project" },
                updateModel = { _, _ -> },
                selectedIndex = { 0 },
            )
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("mvn", "clean", "-DskipTests"), "/project")

        val result = manager.findActiveOverride(cmd)
        assertNotNull(result)
        assertEquals("Fast Build", result!!.label)
        assertEquals(listOf("mvn", "clean"), result.originalArgv)
    }

    @Test
    fun `findActiveOverride falls back to originalArgv match`(
        @TempDir dir: Path,
    ) {
        val override =
            CommandOverride(
                originalArgv = listOf("mvn", "clean"),
                label = "Fast Build",
                argv = listOf("mvn", "clean", "-DskipTests"),
            )
        val ctx =
            newTestContext(
                dir,
                AppConfig(
                    commandOverrides = mapOf("/project" to listOf(override)),
                ),
            )
        val manager =
            CommandOverrideManager(
                ctx = ctx,
                currentProjectPath = { "/project" },
                updateModel = { _, _ -> },
                selectedIndex = { 0 },
            )
        val cmd = CommandDescriptor("build", BuildTool.MAVEN, listOf("mvn", "clean"), "/project")

        val result = manager.findActiveOverride(cmd)
        assertNotNull(result)
        assertEquals("Fast Build", result!!.label)
    }

    @Test
    fun `resetSelectedCommand removes override from config`(
        @TempDir dir: Path,
    ) {
        val override =
            CommandOverride(
                originalArgv = listOf("mvn", "clean"),
                label = "Fast Build",
                argv = listOf("mvn", "clean", "-DskipTests"),
            )
        val ctx =
            newTestContext(
                dir,
                AppConfig(
                    commandOverrides = mapOf("/project" to listOf(override)),
                ),
            )
        val updates = mutableListOf<CommandDescriptor>()
        val manager =
            CommandOverrideManager(
                ctx = ctx,
                currentProjectPath = { "/project" },
                updateModel = { _, d -> updates.add(d) },
                selectedIndex = { 0 },
            )

        manager.resetSelectedCommand(override, BuildTool.MAVEN, "/project")

        assertNull(ctx.config.commandOverrides["/project"])
        assertEquals(1, updates.size)
        assertEquals(listOf("mvn", "clean"), updates[0].argv)
    }

    @Test
    fun `resetSelectedCommand keeps other overrides`(
        @TempDir dir: Path,
    ) {
        val override1 =
            CommandOverride(
                originalArgv = listOf("mvn", "clean"),
                label = "Fast Build",
                argv = listOf("mvn", "clean", "-DskipTests"),
            )
        val override2 =
            CommandOverride(
                originalArgv = listOf("mvn", "test"),
                label = "Verbose Test",
                argv = listOf("mvn", "test", "--verbose"),
            )
        val ctx =
            newTestContext(
                dir,
                AppConfig(
                    commandOverrides = mapOf("/project" to listOf(override1, override2)),
                ),
            )
        val manager =
            CommandOverrideManager(
                ctx = ctx,
                currentProjectPath = { "/project" },
                updateModel = { _, _ -> },
                selectedIndex = { 0 },
            )

        manager.resetSelectedCommand(override1, BuildTool.MAVEN, "/project")

        val remaining = ctx.config.commandOverrides["/project"]
        assertNotNull(remaining)
        assertEquals(1, remaining!!.size)
        assertEquals("Verbose Test", remaining[0].label)
    }

    private fun newTestContext(
        dir: Path,
        initial: AppConfig = AppConfig(),
    ): AppContext {
        val configStore = JsonConfigStore(dir.resolve("config.json"))
        val promptStore =
            PromptLibraryStore(
                dir.resolve("prompts"),
                dir.resolve("commands"),
            )
        val skillStore = SkillLibraryStore(dir.resolve("skills"))
        val ctx =
            AppContext(
                configStore = configStore,
                promptLibraryStore = promptStore,
                skillLibraryStore = skillStore,
            )
        if (initial != AppConfig()) {
            ctx.updateConfig(initial)
        }
        return ctx
    }
}
