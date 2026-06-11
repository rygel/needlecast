package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectTreeEntry

data class FilterState(
    val lastFilter: String = "",
    val lastActiveOnly: Boolean = false,
    val cachedEntries: List<ProjectTreeEntry>? = null,
) {
    fun needsReapply(
        filter: String,
        activeOnly: Boolean,
    ): Boolean = filter != lastFilter || activeOnly != lastActiveOnly
}

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
    ): List<ProjectTreeEntry> = filterTree(entries, filter, false, emptySet())

    fun filterTree(
        entries: List<ProjectTreeEntry>,
        filter: String,
        activeOnly: Boolean,
        activePaths: Set<String>,
    ): List<ProjectTreeEntry> {
        if (filter.isBlank() && !activeOnly) return entries
        return entries.mapNotNull { filterEntry(it, filter, activeOnly, activePaths) }
    }

    private fun filterEntry(
        entry: ProjectTreeEntry,
        textFilter: String,
        activeOnly: Boolean,
        activePaths: Set<String>,
    ): ProjectTreeEntry? {
        val f = textFilter.lowercase()
        return when (entry) {
            is ProjectTreeEntry.Project -> {
                val matchesText =
                    textFilter.isEmpty() ||
                        entry.directory
                            .label()
                            .lowercase()
                            .contains(f) ||
                        entry.tags.any { it.lowercase().contains(f) }
                val matchesActive = !activeOnly || entry.directory.path in activePaths
                if (matchesText && matchesActive) entry else null
            }

            is ProjectTreeEntry.Folder -> {
                val filteredChildren =
                    entry.children.mapNotNull { filterEntry(it, textFilter, activeOnly, activePaths) }
                if (filteredChildren.isNotEmpty()) entry.copy(children = filteredChildren) else null
            }
        }
    }
}
