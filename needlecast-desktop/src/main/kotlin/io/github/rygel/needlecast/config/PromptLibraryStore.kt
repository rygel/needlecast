package io.github.rygel.needlecast.config

import io.github.rygel.needlecast.model.PromptTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString

class PromptLibraryStore(
    private val promptsDir: Path,
    private val commandsDir: Path,
) {
    fun loadPrompts(): List<PromptTemplate> = loadFromDirectory(promptsDir)

    fun loadCommands(): List<PromptTemplate> = loadFromDirectory(commandsDir)

    fun save(template: PromptTemplate, isCommand: Boolean) {
        val baseDir = if (isCommand) commandsDir else promptsDir
        val category = template.category.ifBlank { "Uncategorized" }
        val categoryDir = baseDir.resolve(category)
        Files.createDirectories(categoryDir)
        val slug = slugify(template.name)
        val targetFile = categoryDir.resolve("$slug.md")

        val existing = findExistingFile(baseDir, template.id)
        val oldFile = if (existing != null && existing != targetFile) existing else null

        val content = buildString {
            appendLine("---")
            appendLine("name: ${template.name}")
            appendLine("description: ${template.description}")
            appendLine("---")
            appendLine(template.body)
        }
        val tmp = targetFile.resolveSibling(targetFile.name + ".tmp")
        Files.writeString(tmp, content)
        Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

        if (oldFile != null) {
            Files.deleteIfExists(oldFile)
            cleanupEmptyDirectories(baseDir)
        }
    }

    fun delete(template: PromptTemplate, isCommand: Boolean) {
        val baseDir = if (isCommand) commandsDir else promptsDir
        val existing = findExistingFile(baseDir, template.id)
        if (existing != null) {
            Files.deleteIfExists(existing)
            cleanupEmptyDirectories(baseDir)
        }
    }

    fun seedDefaults(prompts: List<PromptTemplate>, commands: List<PromptTemplate>) {
        if (Files.exists(promptsDir) && loadFromDirectory(promptsDir).isNotEmpty()) return
        Files.createDirectories(promptsDir)
        prompts.forEach { save(it, isCommand = false) }

        if (Files.exists(commandsDir) && loadFromDirectory(commandsDir).isNotEmpty()) return
        Files.createDirectories(commandsDir)
        commands.forEach { save(it, isCommand = true) }
    }

    private fun loadFromDirectory(dir: Path): List<PromptTemplate> {
        if (!Files.exists(dir)) return emptyList()
        val result = mutableListOf<PromptTemplate>()
        val categories = Files.list(dir).use { it.toList() }
            .filter { it.isDirectory() }
            .sortedBy { it.name }
        for (categoryDir in categories) {
            val category = categoryDir.name
            val files = Files.list(categoryDir).use { it.toList() }
                .filter { it.isRegularFile() && it.extension == "md" }
                .sortedBy { it.name }
            for (file in files) {
                result.add(parseFile(file, category, dir))
            }
        }
        return result
    }

    private fun parseFile(file: Path, category: String, baseDir: Path): PromptTemplate {
        val raw = Files.readString(file)
        val relativePath = baseDir.relativize(file).pathString.replace('\\', '/')
        val id = deterministicId(relativePath)

        val (frontmatter, body) = splitFrontmatter(raw)
        val name = frontmatter["name"] ?: file.name.removeSuffix(".md")
        val description = frontmatter["description"] ?: ""

        return PromptTemplate(id = id, name = name, category = category, description = description, body = body)
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

    private fun findExistingFile(baseDir: Path, id: String): Path? {
        if (!Files.exists(baseDir)) return null
        val categories = Files.list(baseDir).use { it.toList() }
            .filter { it.isDirectory() }
        for (categoryDir in categories) {
            val files = Files.list(categoryDir).use { it.toList() }
                .filter { it.isRegularFile() && it.extension == "md" }
            for (file in files) {
                val relativePath = baseDir.relativize(file).pathString.replace('\\', '/')
                if (deterministicId(relativePath) == id) return file
            }
        }
        return null
    }

    private fun cleanupEmptyDirectories(baseDir: Path) {
        if (!Files.exists(baseDir)) return
        try {
            val emptyDirs = Files.list(baseDir).use { it.toList() }
                .filter { it.isDirectory() }
                .filter { dir -> Files.list(dir).use { inner -> inner.findAny().isEmpty } }
            emptyDirs.forEach { Files.deleteIfExists(it) }
        } catch (_: Exception) {}
    }

    companion object {
        fun deterministicId(relativePath: String): String =
            UUID.nameUUIDFromBytes(relativePath.toByteArray(Charsets.UTF_8)).toString()

        fun slugify(name: String): String =
            name.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("[\\s]+"), "-")
                .replace(Regex("-+"), "-")
                .trim('-')
                .ifEmpty { "untitled" }
    }
}
