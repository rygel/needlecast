package io.github.quicklaunch.model

// CommandHistoryEntry is defined in CommandDescriptor.kt (same package)

data class ExternalEditor(
    val name: String,
    val executable: String,
)

private fun defaultEditors() = listOf(
    ExternalEditor("VS Code", "code"),
    ExternalEditor("Zed", "zed"),
    ExternalEditor("IntelliJ IDEA", "idea"),
)

data class AppConfig(
    val groups: List<ProjectGroup> = emptyList(),
    val windowWidth: Int = 1200,
    val windowHeight: Int = 800,
    val lastSelectedGroupId: String? = null,
    val theme: String = "dark",
    val externalEditors: List<ExternalEditor> = defaultEditors(),
    // Per-project command history keyed by working directory path (max 20 per project)
    val commandHistory: Map<String, List<CommandHistoryEntry>> = emptyMap(),
    // Split pane divider positions — null means use defaults
    val dividerLeft: Int? = null,
    val dividerMiddle: Int? = null,
    val dividerRight: Int? = null,
    val dividerMain: Int? = null,
    val dividerMiddleRight: Int? = null,
)

data class ProjectGroup(
    val id: String,
    val name: String,
    val directories: List<ProjectDirectory> = emptyList(),
)

data class ProjectDirectory(
    val path: String,
    val displayName: String? = null,
) {
    fun label(): String = displayName ?: path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
}
