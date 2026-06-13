package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.SkillEntry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.toList

class SkillLibraryStore(
    private val skillsDir: Path,
) {
    private companion object {
        const val DEPLOY_MARKER = ".needlecast-skill-source"
    }

    fun loadLibrary(): List<SkillEntry> {
        if (!Files.exists(skillsDir)) return emptyList()
        val result = mutableListOf<SkillEntry>()
        val entries = Files.list(skillsDir).use { it.toList() }
        entries.filter { it.isDirectory() }.sortedBy { it.name }.forEach { dir ->
            val skillFile = findSkillFile(dir) ?: return@forEach
            val entry = parseSkillFile(dir, skillFile)
            if (entry != null) result.add(entry)
        }
        return result
    }

    fun loadSkill(name: String): SkillEntry? {
        val dir = skillsDir.resolve(name)
        if (!Files.isDirectory(dir)) return null
        val skillFile = findSkillFile(dir) ?: return null
        return parseSkillFile(dir, skillFile)
    }

    fun save(
        entry: SkillEntry,
        body: String,
    ) {
        val dir = skillsDir.resolve(entry.name)
        Files.createDirectories(dir)
        val content =
            buildString {
                appendLine("---")
                appendLine("name: ${entry.name}")
                appendLine("description: ${entry.description}")
                if (entry.category.isNotBlank() && entry.category != "General") {
                    appendLine("category: ${entry.category}")
                }
                appendLine("---")
                appendLine(body)
            }
        val target = dir.resolve("SKILL.md")
        val tmp = target.resolveSibling(target.name + ".tmp")
        Files.writeString(tmp, content)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun delete(name: String) {
        val dir = skillsDir.resolve(name)
        if (!Files.exists(dir)) return
        dir.toFile().walkBottomUp().forEach { it.delete() }
    }

    fun isDeployed(
        skillName: String,
        projectPath: String,
        targetDir: String,
    ): Boolean {
        val deployed = Path.of(projectPath).resolve(targetDir).resolve(skillName)
        if (!Files.isDirectory(deployed)) return false
        return readMarker(deployed) == skillsDir.resolve(skillName)
    }

    fun deploy(
        skillName: String,
        projectPath: String,
        targetDir: String,
    ) {
        val target = Path.of(projectPath).resolve(targetDir)
        Files.createDirectories(target)
        val deployed = target.resolve(skillName)
        if (Files.exists(deployed)) return
        val skillSource = skillsDir.resolve(skillName)
        copyDir(skillSource, deployed)
        writeMarker(deployed, skillSource)
    }

    fun undeploy(
        skillName: String,
        projectPath: String,
        targetDir: String,
    ) {
        val deployed = Path.of(projectPath).resolve(targetDir).resolve(skillName)
        if (!Files.exists(deployed)) return
        deployed.toFile().walkBottomUp().forEach { it.delete() }
        cleanupEmptyTarget(Path.of(projectPath).resolve(targetDir))
    }

    fun deployedSkills(
        projectPath: String,
        targetDir: String,
    ): List<String> {
        val target = Path.of(projectPath).resolve(targetDir)
        if (!Files.isDirectory(target)) return emptyList()
        val result = mutableListOf<String>()
        val entries = Files.list(target).use { it.toList() }
        for (entry in entries) {
            if (!Files.isDirectory(entry)) continue
            val name = entry.name
            if (readMarker(entry) == skillsDir.resolve(name)) {
                result.add(name)
            }
        }
        return result.sorted()
    }

    private fun findSkillFile(dir: Path): Path? {
        val candidates = listOf("SKILL.md", "skill.md")
        for (c in candidates) {
            val f = dir.resolve(c)
            if (Files.isRegularFile(f)) return f
        }
        return null
    }

    private fun parseSkillFile(
        dir: Path,
        file: Path,
    ): SkillEntry? {
        val raw = Files.readString(file)
        val (frontmatter, _) = FrontmatterParser.split(raw)
        val name = frontmatter["name"] ?: dir.name
        val description = frontmatter["description"] ?: ""
        val category = frontmatter["category"] ?: "General"
        return SkillEntry(name = name, description = description, skillDir = dir, category = category)
    }

    private fun copyDir(
        source: Path,
        target: Path,
    ) {
        Files.createDirectories(target)
        Files.walk(source).use { stream ->
            stream.forEach { src ->
                val dst = target.resolve(source.relativize(src))
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst)
                } else {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun writeMarker(
        deployed: Path,
        source: Path,
    ) {
        Files.writeString(deployed.resolve(DEPLOY_MARKER), source.toAbsolutePath().toString())
    }

    private fun readMarker(deployed: Path): Path? {
        val marker = deployed.resolve(DEPLOY_MARKER)
        if (!Files.isRegularFile(marker)) return null
        return try {
            Path.of(Files.readString(marker).trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupEmptyTarget(target: Path) {
        if (!Files.isDirectory(target)) return
        try {
            val remaining = Files.list(target).use { it.toList() }
            if (remaining.isEmpty()) Files.deleteIfExists(target)
        } catch (_: Exception) {
        }
    }
}
