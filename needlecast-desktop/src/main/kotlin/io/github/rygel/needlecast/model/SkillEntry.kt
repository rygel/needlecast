package io.github.rygel.needlecast.model

import java.nio.file.Path

data class SkillEntry(
    val name: String,
    val description: String,
    val skillDir: Path,
)
