package io.github.rygel.needlecast.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.config.ConfigMigrator
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
            val raw = mapper.readValue(configPath.toFile(), AppConfig::class.java)
            ConfigMigrator.migrate(raw)
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
        rotateBackups()
        val tmp = configPath.resolveSibling(configPath.fileName.toString() + ".tmp")
        mapper.writeValue(tmp.toFile(), config)
        Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /**
     * Rotates up to [MAX_BACKUPS] numbered backup files before each save.
     * The oldest slot is dropped, every existing backup shifts up by one,
     * and the current config becomes backup #1. For example:
     *   config.json.bak.5 → deleted
     *   config.json.bak.4 → config.json.bak.5
     *   …
     *   config.json.bak.1 → config.json.bak.2
     *   config.json       → config.json.bak.1
     */
    private fun rotateBackups() {
        if (!Files.exists(configPath)) return
        try {
            val name = configPath.fileName.toString()
            val dir = configPath.parent
            // Drop the oldest slot
            val oldest = dir.resolve("$name.bak.$MAX_BACKUPS")
            Files.deleteIfExists(oldest)
            // Shift each backup up one slot
            for (i in MAX_BACKUPS - 1 downTo 1) {
                val src = dir.resolve("$name.bak.$i")
                val dst = dir.resolve("$name.bak.${i + 1}")
                if (Files.exists(src)) Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING)
            }
            // Current config becomes backup #1
            Files.copy(configPath, dir.resolve("$name.bak.1"), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Best-effort; a backup failure must never prevent saving
        }
    }

    override fun import(path: Path): AppConfig =
        mapper.readValue(path.toFile(), AppConfig::class.java)

    override fun export(config: AppConfig, path: Path) {
        mapper.writeValue(path.toFile(), config)
    }

    companion object {
        const val MAX_BACKUPS = 5

        fun defaultConfigPath(): Path =
            Path.of(System.getProperty("user.home"), ".quicklaunch", "config.json")
    }
}
