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
        val hasRails =
            dir.resolve("bin/rails").toFile().exists() ||
                dir.resolve("bin\\rails").toFile().exists()
        val hasRakefile = dir.resolve("Rakefile").toFile().exists()

        commands += scannerCmd("bundle install", directory, BuildTool.BUNDLER, "bundle", "install")

        if (hasRails) {
            commands += scannerCmd("rails server", directory, BuildTool.BUNDLER, "bundle", "exec", "rails", "server")
            commands += scannerCmd("rails console", directory, BuildTool.BUNDLER, "bundle", "exec", "rails", "console")
            commands += scannerCmd("rails test", directory, BuildTool.BUNDLER, "bundle", "exec", "rails", "test")
            commands += scannerCmd("rails db:migrate", directory, BuildTool.BUNDLER, "bundle", "exec", "rails", "db:migrate")
            commands += scannerCmd("rails routes", directory, BuildTool.BUNDLER, "bundle", "exec", "rails", "routes")
        }

        if (hasRakefile) {
            commands += scannerCmd("rake test", directory, BuildTool.BUNDLER, "bundle", "exec", "rake", "test")
            commands += scannerCmd("rake", directory, BuildTool.BUNDLER, "bundle", "exec", "rake")
        }

        commands += scannerCmd("bundle exec rspec", directory, BuildTool.BUNDLER, "bundle", "exec", "rspec")
        commands += scannerCmd("bundle update", directory, BuildTool.BUNDLER, "bundle", "update")

        return DetectedProject(
            directory = directory,
            buildTools = setOf(BuildTool.BUNDLER),
            commands = commands,
        )
    }
}
