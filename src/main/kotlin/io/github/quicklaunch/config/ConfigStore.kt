package io.github.quicklaunch.config

import io.github.quicklaunch.model.AppConfig
import java.nio.file.Path

interface ConfigStore {
    fun load(): AppConfig
    fun save(config: AppConfig)
    fun import(path: Path): AppConfig
    fun export(config: AppConfig, path: Path)
}
