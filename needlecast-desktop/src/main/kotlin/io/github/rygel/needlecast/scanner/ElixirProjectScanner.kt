package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Elixir projects via `mix.exs`.
 *
 * Detects Phoenix framework via presence of `phoenix` in mix.exs.
 */
class ElixirProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val mixExs = dir.resolve("mix.exs").toFile()
        if (!mixExs.exists()) return null

        val content = try { mixExs.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val isPhoenix = ":phoenix" in content

        val commands = mutableListOf<CommandDescriptor>()
        commands += cmd("mix compile", directory, "mix", "compile")
        commands += cmd("mix test", directory, "mix", "test")
        commands += cmd("mix deps.get", directory, "mix", "deps.get")
        commands += cmd("mix deps.update --all", directory, "mix", "deps.update", "--all")
        commands += cmd("mix format", directory, "mix", "format")

        if (isPhoenix) {
            commands += cmd("mix phx.server", directory, "mix", "phx.server")
            commands += cmd("mix ecto.migrate", directory, "mix", "ecto.migrate")
            commands += cmd("mix phx.routes", directory, "mix", "phx.routes")
        }

        commands += cmd("mix clean", directory, "mix", "clean")
        commands += cmd("iex -S mix", directory, "iex", "-S", "mix")

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.ELIXIR),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.ELIXIR,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
