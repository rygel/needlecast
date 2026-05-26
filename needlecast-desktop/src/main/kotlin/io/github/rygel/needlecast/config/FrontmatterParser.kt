package io.github.rygel.needlecast.config

internal object FrontmatterParser {
    fun split(raw: String): Pair<Map<String, String>, String> {
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
}
