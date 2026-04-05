package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

/**
 * Detects Ruby projects via `Gemfile`.
 *
 * Detects Rails projects (bin/rails or Rakefile with Rails references).
 */
class RubyProjectScanner : ProjectScanner {

    override fun scan(directory: ProjectDirectory): DetectedProject? {
        val dir = Path.of(directory.path)
        val gemfile = dir.resolve("Gemfile").toFile()
        if (!gemfile.exists()) return null

        val commands = mutableListOf<CommandDescriptor>()
        val hasRails = dir.resolve("bin/rails").toFile().exists() ||
            dir.resolve("bin\\rails").toFile().exists()
        val hasRakefile = dir.resolve("Rakefile").toFile().exists()

        commands += cmd("bundle install", directory, "bundle", "install")

        if (hasRails) {
            commands += cmd("rails server", directory, "bundle", "exec", "rails", "server")
            commands += cmd("rails console", directory, "bundle", "exec", "rails", "console")
            commands += cmd("rails test", directory, "bundle", "exec", "rails", "test")
            commands += cmd("rails db:migrate", directory, "bundle", "exec", "rails", "db:migrate")
            commands += cmd("rails routes", directory, "bundle", "exec", "rails", "routes")
        }

        if (hasRakefile) {
            commands += cmd("rake test", directory, "bundle", "exec", "rake", "test")
            commands += cmd("rake", directory, "bundle", "exec", "rake")
        }

        commands += cmd("bundle exec rspec", directory, "bundle", "exec", "rspec")
        commands += cmd("bundle update", directory, "bundle", "update")

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.BUNDLER),
            commands = commands,
        )
    }

    private fun cmd(label: String, dir: ProjectDirectory, vararg args: String): CommandDescriptor =
        CommandDescriptor(label, BuildTool.BUNDLER,
            if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
            dir.path)
}
