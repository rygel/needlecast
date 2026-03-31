package io.github.quicklaunch

import io.github.quicklaunch.config.ConfigStore
import io.github.quicklaunch.config.JsonConfigStore
import io.github.quicklaunch.model.AppConfig
import io.github.quicklaunch.process.CommandRunner
import io.github.quicklaunch.process.ProcessCommandRunner
import io.github.quicklaunch.scanner.CompositeProjectScanner
import io.github.quicklaunch.scanner.ProjectScanner

class AppContext(
    val configStore: ConfigStore = JsonConfigStore(),
    val scanner: ProjectScanner = CompositeProjectScanner(),
    val commandRunner: CommandRunner = ProcessCommandRunner(),
) {
    var config: AppConfig = configStore.load()
        private set

    fun saveConfig() = configStore.save(config)

    fun updateConfig(updated: AppConfig) {
        config = updated
        saveConfig()
    }
}
