package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandOverride
import javax.swing.SwingUtilities

class CommandOverrideManager(
    private val ctx: AppContext,
    private val currentProjectPath: () -> String?,
    private val updateModel: (Int, CommandDescriptor) -> Unit,
    private val selectedIndex: () -> Int,
) {
    fun findActiveOverride(cmd: CommandDescriptor): CommandOverride? {
        val workDir = currentProjectPath() ?: return null
        val overrides = ctx.config.commandOverrides[workDir] ?: return null
        return overrides.firstOrNull { it.argv == cmd.argv }
            ?: overrides.firstOrNull { it.originalArgv == cmd.argv }
    }

    fun editSelectedCommand(
        original: CommandDescriptor,
        parent: java.awt.Component,
    ) {
        val idx = selectedIndex().takeIf { it >= 0 } ?: return
        val workDir = currentProjectPath() ?: return
        val trueOriginalArgv =
            ctx.config.commandOverrides[workDir]
                ?.firstOrNull { it.argv == original.argv }
                ?.originalArgv
                ?: original.argv
        val owner = SwingUtilities.getWindowAncestor(parent)
        val dialog = EditCommandDialog(owner, original)
        dialog.isVisible = true
        val updated = dialog.result ?: return
        updateModel(idx, updated)
        val newOverride =
            CommandOverride(
                originalArgv = trueOriginalArgv,
                label = updated.label,
                argv = updated.argv,
            )
        val existing =
            ctx.config.commandOverrides[workDir]
                ?.filterNot { it.originalArgv == trueOriginalArgv }
                ?: emptyList()
        ctx.updateConfig(
            ctx.config.copy(
                commandOverrides = ctx.config.commandOverrides + (workDir to (existing + newOverride)),
            ),
        )
    }

    fun resetSelectedCommand(
        override: CommandOverride,
        currentBuildTool: BuildTool,
        currentWorkDir: String,
    ) {
        val idx = selectedIndex().takeIf { it >= 0 } ?: return
        val workDir = currentProjectPath() ?: return
        val restored =
            CommandDescriptor(
                label = override.originalArgv.joinToString(" "),
                buildTool = currentBuildTool,
                argv = override.originalArgv,
                workingDirectory = currentWorkDir,
            )
        updateModel(idx, restored)
        val remaining =
            ctx.config.commandOverrides[workDir]
                ?.filterNot { it.originalArgv == override.originalArgv }
                ?: emptyList()
        val newOverrides =
            if (remaining.isEmpty()) {
                ctx.config.commandOverrides - workDir
            } else {
                ctx.config.commandOverrides + (workDir to remaining)
            }
        ctx.updateConfig(ctx.config.copy(commandOverrides = newOverrides))
    }
}
