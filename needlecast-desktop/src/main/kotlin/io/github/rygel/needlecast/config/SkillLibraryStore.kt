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

    fun save(entry: SkillEntry, body: String) {
        val dir = skillsDir.resolve(entry.name)
        Files.createDirectories(dir)
        val content = buildString {
            appendLine("---")
            appendLine("name: ${entry.name}")
            appendLine("description: ${entry.description}")
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

    fun isDeployed(skillName: String, projectPath: String, targetDir: String): Boolean {
        val link = Path.of(projectPath).resolve(targetDir).resolve(skillName)
        if (!Files.exists(link)) return false
        return try {
            val realTarget = Files.readSymbolicLink(link)
            realTarget == skillsDir.resolve(skillName)
        } catch (_: Exception) {
            isJunction(link, skillsDir.resolve(skillName))
        }
    }

    fun deploy(skillName: String, projectPath: String, targetDir: String) {
        val target = Path.of(projectPath).resolve(targetDir)
        Files.createDirectories(target)
        val link = target.resolve(skillName)
        if (Files.exists(link)) return
        val skillTarget = skillsDir.resolve(skillName)
        try {
            Files.createSymbolicLink(link, skillTarget)
        } catch (_: Exception) {
            createJunction(link, skillTarget)
        }
    }

    fun undeploy(skillName: String, projectPath: String, targetDir: String) {
        val link = Path.of(projectPath).resolve(targetDir).resolve(skillName)
        if (!Files.exists(link)) return
        try {
            Files.delete(link)
        } catch (_: Exception) {
            deleteJunction(link)
        }
        cleanupEmptyTarget(Path.of(projectPath).resolve(targetDir))
    }

    fun deployedSkills(projectPath: String, targetDir: String): List<String> {
        val target = Path.of(projectPath).resolve(targetDir)
        if (!Files.isDirectory(target)) return emptyList()
        val result = mutableListOf<String>()
        val entries = Files.list(target).use { it.toList() }
        for (entry in entries) {
            if (!Files.isDirectory(entry)) continue
            val name = entry.name
            val pointsTo = try {
                Files.readSymbolicLink(entry)
            } catch (_: Exception) {
                if (isJunction(entry, skillsDir.resolve(name))) result.add(name)
                continue
            }
            if (pointsTo == skillsDir.resolve(name)) {
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

    private fun parseSkillFile(dir: Path, file: Path): SkillEntry? {
        val raw = Files.readString(file)
        val (frontmatter, _) = splitFrontmatter(raw)
        val name = frontmatter["name"] ?: dir.name
        val description = frontmatter["description"] ?: ""
        return SkillEntry(name = name, description = description, skillDir = dir)
    }

    private fun splitFrontmatter(raw: String): Pair<Map<String, String>, String> {
        val lines = raw.lines()
        if (lines.isEmpty() || lines[0].trim() != "---") return emptyMap<String, String>() to raw
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end == -1) return emptyMap<String, String>() to raw
        val yamlLines = lines.subList(1, end + 1)
        val body = lines.subList(end + 2, lines.size).joinToString("\n").trim()
        val map = mutableMapOf<String, String>()
        for (line in yamlLines) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                map[key] = value
            }
        }
        return map to body
    }

    private fun isJunction(link: Path, expectedTarget: Path): Boolean {
        return try {
            val attr = Files.readAttributes(link, java.nio.file.attribute.BasicFileAttributes::class.java)
            if (!attr.isOther) return false
            val proc = ProcessBuilder("cmd", "/c", "fsutil", "reparsepoint", "query", link.toString())
                .redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.contains(expectedTarget.toString().replace('/', '\\'))
        } catch (_: Exception) {
            false
        }
    }

    private fun createJunction(link: Path, target: Path) {
        val proc = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
            .redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        if (exit != 0) throw RuntimeException("Failed to create junction: $link -> $target (exit=$exit)")
    }

    private fun deleteJunction(link: Path) {
        val proc = ProcessBuilder("cmd", "/c", "rmdir", link.toString())
            .redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().readText()
        proc.waitFor()
    }

    private fun cleanupEmptyTarget(target: Path) {
        if (!Files.isDirectory(target)) return
        try {
            val remaining = Files.list(target).use { it.toList() }
            if (remaining.isEmpty()) Files.deleteIfExists(target)
        } catch (_: Exception) {}
    }
}
