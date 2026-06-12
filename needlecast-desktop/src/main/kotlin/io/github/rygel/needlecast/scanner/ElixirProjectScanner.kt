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
    private val logger = org.slf4j.LoggerFactory.getLogger(ElixirProjectScanner::class.java)

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val mixExs = dir.resolve("mix.exs").toFile()
        if (!mixExs.exists()) return null

        val content =
            try {
                mixExs.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                logger.warn("Failed to read {}", mixExs.name, e)
                ""
            }
        val isPhoenix = ":phoenix" in content

        val commands = mutableListOf<CommandDescriptor>()
        commands += scannerCmd("mix compile", directory, BuildTool.MIX, "mix", "compile")
        commands += scannerCmd("mix test", directory, BuildTool.MIX, "mix", "test")
        commands += scannerCmd("mix deps.get", directory, BuildTool.MIX, "mix", "deps.get")
        commands += scannerCmd("mix deps.update --all", directory, BuildTool.MIX, "mix", "deps.update", "--all")
        commands += scannerCmd("mix format", directory, BuildTool.MIX, "mix", "format")

        if (isPhoenix) {
            commands += scannerCmd("mix phx.server", directory, BuildTool.MIX, "mix", "phx.server")
            commands += scannerCmd("mix ecto.migrate", directory, BuildTool.MIX, "mix", "ecto.migrate")
            commands += scannerCmd("mix phx.routes", directory, BuildTool.MIX, "mix", "phx.routes")
        }

        commands += scannerCmd("mix clean", directory, BuildTool.MIX, "mix", "clean")
        commands += scannerCmd("iex -S mix", directory, BuildTool.MIX, "iex", "-S", "mix")

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.MIX),
            commands = commands,
        )
    }

}
