package io.github.rygel.needlecast.model

data class DocTarget(
    val label: String,
    val relativePath: String,
    val buildTool: BuildTool,
    val category: DocCategory,
    val hint: String,
)
