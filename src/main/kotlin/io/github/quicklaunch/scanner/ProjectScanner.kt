package io.github.quicklaunch.scanner

import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProjectDirectory
import java.nio.file.Path

interface ProjectScanner {
    fun scan(directory: ProjectDirectory): DetectedProject?
}

val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().contains("win")
