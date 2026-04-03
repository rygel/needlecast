package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.AppConfig
import java.nio.file.Path

interface ConfigStore {
    fun load(): AppConfig
    fun save(config: AppConfig)
    fun import(path: Path): AppConfig
    fun export(config: AppConfig, path: Path)
}
