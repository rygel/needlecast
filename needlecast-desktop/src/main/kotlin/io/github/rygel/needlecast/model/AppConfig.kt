package io.github.rygel.needlecast.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

// CommandHistoryEntry is defined in CommandDescriptor.kt (same package)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ProjectTreeEntry.Folder::class, name = "folder"),
    JsonSubTypes.Type(value = ProjectTreeEntry.Project::class, name = "project"),
)
sealed class ProjectTreeEntry {
    abstract val id: String

    data class Folder(
        override val id: String = UUID.randomUUID().toString(),
        val name: String,
        val color: String? = null,
        val children: List<ProjectTreeEntry> = emptyList(),
    ) : ProjectTreeEntry()

    data class Project(
        override val id: String = UUID.randomUUID().toString(),
        val directory: ProjectDirectory,
        val tags: List<String> = emptyList(),
    ) : ProjectTreeEntry()
}

data class PromptTemplate(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val body: String = "",
)

data class ExternalEditor(
    val name: String,
    val executable: String,
)

/** Persisted edit to a scanner-generated [CommandDescriptor], keyed by the original argv. */
data class CommandOverride(
    val originalArgv: List<String>,
    val label: String,
    val argv: List<String>,
)

data class AppConfig(
    val configVersion: Int = 7,
    val groups: List<ProjectGroup> = emptyList(),
    val windowWidth: Int = 1200,
    val windowHeight: Int = 800,
    val lastSelectedGroupId: String? = null,
    val lastSelectedProjectPath: String? = null,
    val theme: String = "dark-purple",
    val language: String = "en",
    val externalEditors: List<ExternalEditor> =
        listOf(
            ExternalEditor("VS Code", "code"),
            ExternalEditor("Zed", "zed"),
            ExternalEditor("IntelliJ IDEA", "idea"),
        ),
    val commandHistory: Map<String, List<CommandHistoryEntry>> = emptyMap(),
    val shortcuts: Map<String, String> = emptyMap(),
    val projectTree: List<ProjectTreeEntry> = emptyList(),
    val showConsole: Boolean = true,
    val showExplorer: Boolean = true,
    val aiCliEnabled: Map<String, Boolean> = emptyMap(),
    val customAiClis: List<AiCliDefinition> = emptyList(),
    val tabsOnTop: Boolean = true,
    val panelHoverHighlight: Boolean = false,
    val dockingActiveHighlight: Boolean = false,
    val treeClickTraceEnabled: Boolean = false,
    val edtStallTraceEnabled: Boolean = false,
    val defaultShell: String? = null,
    val syntaxTheme: String = "auto",
    val terminalBackground: String? = null,
    val terminalForeground: String? = null,
    val terminalFontSize: Int = 13,
    val uiFontFamily: String? = null,
    val uiFontSize: Int? = null,
    val editorFontFamily: String? = null,
    val editorFontSize: Int = 12,
    val terminalFontFamily: String? = null,
    val claudeHooksEnabled: Boolean = false,
    val claudeQuotaEnabled: Boolean = true,
    val commandOverrides: Map<String, List<CommandOverride>> = emptyMap(),
    val mediaAutoplay: Boolean = true,
    val privacyModeEnabled: Boolean = false,
    val editorBackground: String? = null,
    val editorForeground: String? = null,
    val gitAutoFetch: Boolean = true,
    val gitAutoFetchIntervalMinutes: Int = 5,
    val showContextualHints: Boolean = true,
    val showHelpPopups: Boolean = true,
    val dismissedHints: Set<String> = emptySet(),
    val shownHints: Set<String> = emptySet(),
    val diffLegendDismissed: Boolean = false,
    val tourCompleted: Boolean = false,
    val terminalEncoding: String = "UTF-8",
    val activeProjectPaths: List<String> = emptyList(),
)

data class AiCliDefinition(
    val name: String,
    val command: String,
    val description: String = "",
)

data class ProjectGroup(
    val id: String,
    val name: String,
    val directories: List<ProjectDirectory> = emptyList(),
    val color: String? = null,
)

data class ProjectDirectory(
    val path: String,
    val displayName: String? = null,
    val color: String? = null,
    val env: Map<String, String> = emptyMap(),
    val shellExecutable: String? = null,
    val startupCommand: String? = null,
    val extraScanDirs: List<String> = emptyList(),
    val skillTargetDir: String? = null,
    @JsonProperty("private")
    val isPrivate: Boolean = false,
) {
    fun label(): String = displayName ?: path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

    fun label(privacyModeEnabled: Boolean): String = if (isPrivate && privacyModeEnabled) "\u2022\u2022\u2022\u2022\u2022\u2022" else label()

    fun redactedPath(privacyModeEnabled: Boolean): String = if (isPrivate && privacyModeEnabled) "\u2022\u2022\u2022\u2022\u2022\u2022" else path
}
