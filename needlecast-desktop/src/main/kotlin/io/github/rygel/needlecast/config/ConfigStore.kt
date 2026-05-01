package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.AppConfig
import java.nio.file.Path

interface ConfigStore {
    fun load(): AppConfig
    fun save(config: AppConfig)
    fun import(path: Path): AppConfig
    fun export(config: AppConfig, path: Path)

    fun importWorkspace(path: Path, baseConfig: AppConfig): AppConfig =
        throw UnsupportedOperationException("Workspace import is not supported by this ConfigStore")

    fun exportWorkspace(config: AppConfig, path: Path): Unit {
        throw UnsupportedOperationException("Workspace export is not supported by this ConfigStore")
    }
}
