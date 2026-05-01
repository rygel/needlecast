package io.github.rygel.needlecast.model

data class WorkspaceSnapshot(
    val snapshotVersion: Int = 1,
    val groups: List<ProjectGroup> = emptyList(),
    val projectTree: List<ProjectTreeEntry> = emptyList(),
    val lastSelectedGroupId: String? = null,
    val lastSelectedProjectPath: String? = null,
)

fun AppConfig.toWorkspaceSnapshot(): WorkspaceSnapshot = WorkspaceSnapshot(
    groups = groups,
    projectTree = projectTree,
    lastSelectedGroupId = lastSelectedGroupId,
    lastSelectedProjectPath = lastSelectedProjectPath,
)

fun AppConfig.withWorkspaceSnapshot(snapshot: WorkspaceSnapshot): AppConfig = copy(
    groups = snapshot.groups,
    projectTree = snapshot.projectTree,
    lastSelectedGroupId = snapshot.lastSelectedGroupId,
    lastSelectedProjectPath = snapshot.lastSelectedProjectPath,
)
