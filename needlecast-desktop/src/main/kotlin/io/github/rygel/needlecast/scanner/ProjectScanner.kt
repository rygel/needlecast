package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory

interface ProjectScanner {
    fun scan(directory: ProjectDirectory): DetectedProject?
}

val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().contains("win")
val IS_MAC: Boolean = System.getProperty("os.name").lowercase().let { it.contains("mac") || it.contains("darwin") }

fun scannerCmd(
    label: String,
    dir: ProjectDirectory,
    buildTool: BuildTool,
    vararg args: String,
): CommandDescriptor =
    CommandDescriptor(
        label = label,
        buildTool = buildTool,
        argv = if (IS_WINDOWS) listOf("cmd", "/c") + args else args.toList(),
        workingDirectory = dir.path,
    )
