package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

internal interface ProjectTreePanelAccess {
    val ctx: AppContext
    val tree: JTree
    val treeModel: DefaultTreeModel
    val rootNode: DefaultMutableTreeNode
    val scanResults: MutableMap<String, DetectedProject>
    val missingPaths: MutableSet<String>
    val onProjectSelected: (DetectedProject?) -> Unit
    val onActivate: (DetectedProject) -> Unit
    val onDeactivate: (DetectedProject) -> Unit
    val component: Component

    fun persist()

    fun updateEmptyState()

    fun updateMissingPath(path: String): Boolean

    fun scanProject(dir: ProjectDirectory)

    fun treePath(node: DefaultMutableTreeNode): TreePath

    fun selectedProjectEntry(): ProjectTreeEntry.Project?

    fun isActivePath(path: String): Boolean

    fun addActivePath(path: String)

    fun removeActivePath(path: String)
}
