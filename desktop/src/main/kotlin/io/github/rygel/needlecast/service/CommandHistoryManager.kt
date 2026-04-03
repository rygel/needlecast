package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.CommandHistoryEntry

/**
 * Manages per-project command history persistence, extracted from [CommandPanel].
 *
 * History is stored in [AppContext.config] and persisted on every [record] call.
 * The list is capped at [MAX] entries per project (newest first).
 */
class CommandHistoryManager(private val ctx: AppContext) {

    companion object {
        const val MAX = 20
    }

    /** Prepends [entry] to [projectPath]'s history and persists immediately. */
    fun record(projectPath: String, entry: CommandHistoryEntry) {
        val current = ctx.config.commandHistory[projectPath].orEmpty()
        val updated = (listOf(entry) + current).take(MAX)
        ctx.updateConfig(
            ctx.config.copy(commandHistory = ctx.config.commandHistory + (projectPath to updated))
        )
    }

    /** Returns the stored history for [projectPath], newest first. */
    fun getHistory(projectPath: String): List<CommandHistoryEntry> =
        ctx.config.commandHistory[projectPath].orEmpty()
}
