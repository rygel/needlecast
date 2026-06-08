package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectTreeEntry

object ProjectTreeFilter {
    fun matches(
        entry: ProjectTreeEntry,
        filter: String,
    ): Boolean {
        val f = filter.lowercase()
        return when (entry) {
            is ProjectTreeEntry.Project -> {
                entry.directory
                    .label()
                    .lowercase()
                    .contains(f) ||
                    entry.tags.any { it.lowercase().contains(f) }
            }

            is ProjectTreeEntry.Folder -> {
                entry.children.any { matches(it, f) }
            }
        }
    }

    fun filterTree(
        entries: List<ProjectTreeEntry>,
        filter: String,
    ): List<ProjectTreeEntry> {
        if (filter.isBlank()) return entries
        return entries.mapNotNull { entry ->
            when (entry) {
                is ProjectTreeEntry.Project -> {
                    entry.takeIf { matches(entry, filter) }
                }

                is ProjectTreeEntry.Folder -> {
                    val filtered = filterTree(entry.children, filter)
                    if (filtered.isNotEmpty()) entry.copy(children = filtered) else null
                }
            }
        }
    }
}
