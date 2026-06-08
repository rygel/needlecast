package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectTreeEntry

/**
 * Pure, side-effect-free filter for [ProjectTreeEntry] trees.
 *
 * Matches a project entry if the filter text (case-insensitive) is contained in
 * the project label, its full directory path, or any tag. Folder entries are
 * kept only if they contain at least one matching child.
 *
 * Extracted from [ProjectTreePanel] for testability — the panel's filter
 * function is tightly coupled to Swing tree mutation, which is hard to test.
 */
object ProjectTreeFilter {

    /**
     * @param entries the source tree
     * @param query the raw filter text from the text field (whitespace is trimmed)
     * @return a filtered tree containing only matching entries; empty query returns entries unchanged
     */
    fun filter(entries: List<ProjectTreeEntry>, query: String): List<ProjectTreeEntry> {
        val filter = query.trim().lowercase()
        if (filter.isEmpty()) return entries
        return entries.mapNotNull { filterEntry(it, filter) }
    }

    /**
     * @return true if the [entry] matches the [query] (already lowercased + trimmed)
     */
    fun matches(entry: ProjectTreeEntry, query: String): Boolean {
        val filter = query.trim().lowercase()
        if (filter.isEmpty()) return true
        return when (entry) {
            is ProjectTreeEntry.Project -> matchesProject(entry, filter)
            is ProjectTreeEntry.Folder  -> entry.children.any { matches(it, filter) }
        }
    }

    private fun filterEntry(entry: ProjectTreeEntry, filter: String): ProjectTreeEntry? = when (entry) {
        is ProjectTreeEntry.Project -> if (matchesProject(entry, filter)) entry else null
        is ProjectTreeEntry.Folder -> {
            val children = entry.children.mapNotNull { filterEntry(it, filter) }
            if (children.isEmpty()) null else entry.copy(children = children)
        }
    }

    private fun matchesProject(entry: ProjectTreeEntry.Project, filter: String): Boolean {
        if (entry.directory.label().lowercase().contains(filter)) return true
        if (entry.directory.path.lowercase().contains(filter)) return true
        return entry.tags.any { it.lowercase().contains(filter) }
    }
}
