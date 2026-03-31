package io.github.quicklaunch.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.quicklaunch.model.AppConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

class JsonConfigStore(
    private val configPath: Path = defaultConfigPath(),
) : ConfigStore {

    private val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    override fun load(): AppConfig {
        if (!Files.exists(configPath)) return AppConfig()
        return try {
            mapper.readValue(configPath.toFile(), AppConfig::class.java)
        } catch (e: Exception) {
            // Preserve the unreadable file before returning defaults so user data isn't silently lost.
            preserveCorruptConfig()
            AppConfig()
        }
    }

    /**
     * Copies the current config file to a timestamped backup (e.g. config.json.corrupt.1714000000)
     * so the user can inspect or recover it. The next [save] will write a fresh valid config.
     */
    private fun preserveCorruptConfig() {
        try {
            val ts = Instant.now().epochSecond
            val backup = configPath.resolveSibling("${configPath.fileName}.corrupt.$ts")
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Best-effort; ignore if backup fails
        }
    }

    override fun save(config: AppConfig) {
        Files.createDirectories(configPath.parent)
        val tmp = configPath.resolveSibling(configPath.fileName.toString() + ".tmp")
        mapper.writeValue(tmp.toFile(), config)
        Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun import(path: Path): AppConfig =
        mapper.readValue(path.toFile(), AppConfig::class.java)

    override fun export(config: AppConfig, path: Path) {
        mapper.writeValue(path.toFile(), config)
    }

    companion object {
        fun defaultConfigPath(): Path =
            Path.of(System.getProperty("user.home"), ".quicklaunch", "config.json")
    }
}
