package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import java.nio.file.Path

interface ProjectScanner {
    fun scan(directory: ProjectDirectory): DetectedProject?
}

val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().contains("win")
val IS_MAC: Boolean = System.getProperty("os.name").lowercase().let { it.contains("mac") || it.contains("darwin") }
