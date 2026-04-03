package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectGroup

/**
 * Encapsulates all mutations to [ProjectGroup] and [ProjectDirectory] state,
 * keeping this logic out of UI components.
 *
 * Every method that modifies state immediately persists the change via [AppContext.updateConfig].
 * The return value is the updated [ProjectGroup] that the caller should use going forward.
 */
class ProjectService(private val ctx: AppContext) {

    /** Appends [dir] to [group] and persists. */
    fun addDirectory(group: ProjectGroup, dir: ProjectDirectory): ProjectGroup {
        val updated = group.copy(directories = group.directories + dir)
        persist(updated)
        return updated
    }

    /** Removes the directory at [path] from [group] and persists. */
    fun removeDirectory(group: ProjectGroup, path: String): ProjectGroup {
        val updated = group.copy(directories = group.directories.filter { it.path != path })
        persist(updated)
        return updated
    }

    /**
     * Applies [transform] to the directory matching [path] within [group] and persists.
     * No-ops (and returns [group] unchanged) if no directory with [path] exists.
     */
    fun updateDirectory(
        group: ProjectGroup,
        path: String,
        transform: (ProjectDirectory) -> ProjectDirectory,
    ): ProjectGroup {
        val updated = group.copy(
            directories = group.directories.map { if (it.path == path) transform(it) else it },
        )
        persist(updated)
        return updated
    }

    /** Replaces [group] in the full config and saves. */
    fun persist(group: ProjectGroup) {
        val updatedGroups = ctx.config.groups.map { if (it.id == group.id) group else it }
        ctx.updateConfig(ctx.config.copy(groups = updatedGroups))
    }
}
