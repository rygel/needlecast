package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.Component
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

internal class ProjectTreeDndHandler(
    private val tree: JTree,
    private val treeModel: DefaultTreeModel,
    private val rootNode: DefaultMutableTreeNode,
    private val missingPaths: MutableSet<String>,
    private val dragPressedPath: () -> TreePath?,
    private val parentComponent: Component,
    private val persist: () -> Unit,
    private val updateMissingPath: (String) -> Boolean,
    private val scanProject: (ProjectDirectory) -> Unit,
    private val onProjectSelected: (DetectedProject?) -> Unit,
    private val onExternalFilesDropped: (List<File>) -> Unit,
) {
    val transferHandler: TransferHandler = TreeTransferHandler()

    // ── Test hooks ─────────────────────────────────────────────────────────

    fun simulateExternalDropForTest(items: List<File>, targetFolder: String? = null): Boolean {
        val dirs = items.filter { it.isDirectory }
        val files = items.filter { it.isFile }
        val (parent, idx) = if (targetFolder == null) {
            rootNode to rootNode.childCount
        } else {
            val node = findFolderNodeByName(rootNode, targetFolder)
                ?: error("Folder '$targetFolder' not found in tree")
            node to node.childCount
        }
        return doImportExternal(dirs, files, parent, idx)
    }

    fun findMissingMatch(droppedName: String): DefaultMutableTreeNode? {
        fun walk(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            val e = node.userObject
            if (e is ProjectTreeEntry.Project && e.directory.path in missingPaths) {
                if (namesMatch(File(e.directory.path).name, droppedName)) return node
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i) as DefaultMutableTreeNode)?.let { return it }
            }
            return null
        }
        return walk(rootNode)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun doImportExternal(
        dirs: List<File>,
        files: List<File>,
        newParent: DefaultMutableTreeNode,
        startIndex: Int,
    ): Boolean {
        val existingPaths = collectAllPaths(rootNode)
        var insertIdx = startIndex.coerceAtMost(newParent.childCount)
        var lastNode: DefaultMutableTreeNode? = null

        for (dir in dirs) {
            val absPath = dir.absolutePath
            if (absPath in existingPaths) continue
            val directory = ProjectDirectory(path = absPath)
            val node = DefaultMutableTreeNode(ProjectTreeEntry.Project(directory = directory))
            treeModel.insertNodeInto(node, newParent, insertIdx)
            existingPaths += absPath
            insertIdx++
            lastNode = node
            val missing = updateMissingPath(directory.path)
            if (!missing) scanProject(directory)
        }

        if (files.isNotEmpty()) {
            onExternalFilesDropped(files)
        }

        if (lastNode != null) {
            if (newParent !== rootNode) tree.expandPath(TreePath(newParent.path))
            val tp = treePath(lastNode)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
            persist()
        }
        return lastNode != null || files.isNotEmpty()
    }

    private fun collectAllPaths(root: DefaultMutableTreeNode): MutableSet<String> {
        val set = mutableSetOf<String>()
        fun walk(n: DefaultMutableTreeNode) {
            val e = n.userObject
            if (e is ProjectTreeEntry.Project) set += e.directory.path
            for (i in 0 until n.childCount) walk(n.getChildAt(i) as DefaultMutableTreeNode)
        }
        walk(root)
        return set
    }

    private fun namesMatch(a: String, b: String): Boolean =
        if (IS_WINDOWS) a.equals(b, ignoreCase = true) else a == b

    private fun confirmRepairPath(missingNode: DefaultMutableTreeNode, newPath: String): Boolean {
        val entry = missingNode.userObject as ProjectTreeEntry.Project
        val oldPath = entry.directory.path
        val projectName = File(oldPath).name
        val choice = JOptionPane.showOptionDialog(
            parentComponent,
            "<html>Project:&nbsp;&nbsp;${projectName.replace("&", "&amp;").replace("<", "&lt;")}<br>" +
                "Old path: ${oldPath.replace("&", "&amp;").replace("<", "&lt;")}<br>" +
                "New path: ${newPath.replace("&", "&amp;").replace("<", "&lt;")}</html>",
            "Replace missing project path?",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            arrayOf("Replace", "Add as new project"),
            "Replace",
        )
        if (choice != 0) return false

        val updatedDirectory = entry.directory.copy(path = newPath)
        missingNode.userObject = entry.copy(directory = updatedDirectory)
        missingPaths.remove(oldPath)
        val nowMissing = updateMissingPath(newPath)
        treeModel.nodeChanged(missingNode)
        persist()
        if (!nowMissing) scanProject(updatedDirectory)
        return true
    }

    private fun findFolderNodeByName(n: DefaultMutableTreeNode, name: String): DefaultMutableTreeNode? {
        for (i in 0 until n.childCount) {
            val c = n.getChildAt(i) as DefaultMutableTreeNode
            if ((c.userObject as? ProjectTreeEntry.Folder)?.name == name) return c
            findFolderNodeByName(c, name)?.let { return it }
        }
        return null
    }

    private fun treePath(node: DefaultMutableTreeNode) = TreePath(node.path)

    // ── TransferHandler ────────────────────────────────────────────────────

    private inner class TreeTransferHandler : TransferHandler() {

        private val flavor: DataFlavor = run {
            val mime = DataFlavor.javaJVMLocalObjectMimeType + ";class=" + DefaultMutableTreeNode::class.java.name
            try {
                DataFlavor(mime)
            } catch (_: ClassNotFoundException) {
                DataFlavor(DefaultMutableTreeNode::class.java, "TreeNode")
            }
        }

        private val uriListFlavor: DataFlavor? = try {
            DataFlavor("text/uri-list;class=java.lang.String")
        } catch (_: Exception) { null }
        private val uriListReaderFlavor: DataFlavor? = try {
            DataFlavor("text/uri-list;class=java.io.Reader")
        } catch (_: Exception) { null }
        private val uriListInputFlavor: DataFlavor? = try {
            DataFlavor("text/uri-list;class=java.io.InputStream")
        } catch (_: Exception) { null }
        private val urlFlavor: DataFlavor? = try {
            DataFlavor("application/x-java-url;class=java.net.URL")
        } catch (_: Exception) { null }

        override fun getSourceActions(c: JComponent) = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val path = dragPressedPath() ?: (c as? JTree)?.selectionPath ?: return null
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
            return object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(flavor)
                override fun isDataFlavorSupported(f: DataFlavor) = f == flavor
                override fun getTransferData(f: DataFlavor): Any = node
            }
        }

        private fun nodeFrom(support: TransferSupport): DefaultMutableTreeNode? =
            try { support.transferable.getTransferData(flavor) as? DefaultMutableTreeNode }
            catch (_: Exception) { null }

        private fun isExternalDrop(support: TransferSupport): Boolean =
            support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                    (urlFlavor != null && support.isDataFlavorSupported(urlFlavor)) ||
                    (uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor)) ||
                    (uriListReaderFlavor != null && support.isDataFlavorSupported(uriListReaderFlavor)) ||
                    (uriListInputFlavor != null && support.isDataFlavorSupported(uriListInputFlavor))

        private fun centeredFolderDrop(dl: JTree.DropLocation): DefaultMutableTreeNode? {
            val p = dl.dropPoint ?: return null
            val rowPath = tree.getClosestPathForLocation(p.x, p.y) ?: return null
            val rowNode = rowPath.lastPathComponent as? DefaultMutableTreeNode ?: return null
            if (rowNode.userObject !is ProjectTreeEntry.Folder) return null
            val bounds = tree.getPathBounds(rowPath) ?: return null
            val top = bounds.y + (bounds.height * 0.25).toInt()
            val bottom = bounds.y + (bounds.height * 0.75).toInt()
            return if (p.y in top..bottom) rowNode else null
        }

        private fun resolveDropTarget(
            dl: JTree.DropLocation,
            overrideFolder: DefaultMutableTreeNode?,
        ): Pair<DefaultMutableTreeNode, Int>? {
            if (overrideFolder != null) return Pair(overrideFolder, overrideFolder.childCount)
            if (dl.path == null) return Pair(rootNode, rootNode.childCount)
            val targetNode = dl.path.lastPathComponent as? DefaultMutableTreeNode ?: return null
            return if (dl.childIndex == -1) {
                when (targetNode.userObject) {
                    is ProjectTreeEntry.Folder -> Pair(targetNode, targetNode.childCount)
                    else -> {
                        val parent = targetNode.parent as? DefaultMutableTreeNode ?: rootNode
                        Pair(parent, parent.getIndex(targetNode) + 1)
                    }
                }
            } else {
                val parent = when (targetNode.userObject) {
                    is ProjectTreeEntry.Folder -> targetNode
                    else -> targetNode.parent as? DefaultMutableTreeNode ?: rootNode
                }
                val idx = if (parent === targetNode) dl.childIndex else parent.getIndex(targetNode).coerceAtLeast(0)
                Pair(parent, idx)
            }
        }

        override fun canImport(support: TransferSupport): Boolean {
            if (!support.isDrop) return false
            return when {
                support.isDataFlavorSupported(flavor) -> {
                    try {
                        support.dropAction = MOVE
                        support.setShowDropLocation(true)
                    } catch (_: Exception) {}
                    val dl = support.dropLocation as? JTree.DropLocation ?: return false
                    val overrideTarget = centeredFolderDrop(dl)
                    val targetPath = overrideTarget?.let { TreePath(it.path) } ?: dl.path
                        ?: return true
                    val targetNode = targetPath.lastPathComponent as? DefaultMutableTreeNode ?: return false
                    val src = nodeFrom(support) ?: return false
                    var n: TreeNode? = targetNode
                    while (n != null) {
                        if (n === src) return false
                        n = n.parent
                    }
                    true
                }
                isExternalDrop(support) -> {
                    support.setShowDropLocation(true)
                    true
                }
                else -> false
            }
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            return if (isExternalDrop(support)) importExternal(support) else importInternal(support)
        }

        private fun importInternal(support: TransferSupport): Boolean {
            val node = nodeFrom(support) ?: return false
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val (newParent, rawIndex) = resolveDropTarget(dl, centeredFolderDrop(dl)) ?: return false

            val oldParent = node.parent as? DefaultMutableTreeNode ?: return false
            val oldIndex  = oldParent.getIndex(node)
            treeModel.removeNodeFromParent(node)

            val insertIndex = if (newParent === oldParent && rawIndex > oldIndex)
                (rawIndex - 1).coerceAtMost(newParent.childCount)
            else
                rawIndex.coerceAtMost(newParent.childCount)

            treeModel.insertNodeInto(node, newParent, insertIndex)
            if (newParent !== rootNode) tree.expandPath(TreePath(newParent.path))
            val tp = treePath(node)
            tree.selectionPath = tp
            tree.scrollPathToVisible(tp)
            persist()
            return true
        }

        private fun importExternal(support: TransferSupport): Boolean {
            val (dirs, files) = entriesFromExternal(support)
            if (dirs.isEmpty() && files.isEmpty()) return false
            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            val (newParent, startIndex) = resolveDropTarget(dl, centeredFolderDrop(dl)) ?: return false

            var anyRepaired = false
            val remainingDirs = mutableListOf<File>()
            for (dir in dirs) {
                val match = findMissingMatch(dir.name)
                if (match != null) {
                    val consumed = confirmRepairPath(match, dir.absolutePath)
                    if (consumed) anyRepaired = true else remainingDirs += dir
                } else {
                    remainingDirs += dir
                }
            }
            return doImportExternal(remainingDirs, files, newParent, startIndex) || anyRepaired
        }

        @Suppress("UNCHECKED_CAST")
        private fun entriesFromExternal(support: TransferSupport): Pair<List<File>, List<File>> {
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                val items = try {
                    (support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        ?.filterIsInstance<File>()
                        ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val dirs = items.filter { it.isDirectory }
                val files = items.filter { it.isFile }
                return dirs to files
            }
            if (urlFlavor != null && support.isDataFlavorSupported(urlFlavor)) {
                return try {
                    val url = support.transferable.getTransferData(urlFlavor) as? java.net.URL
                    val file = url?.toURI()?.let { File(it) }
                    val dirs = if (file != null && file.isDirectory) listOf(file) else emptyList()
                    val files = if (file != null && file.isFile) listOf(file) else emptyList()
                    dirs to files
                } catch (_: Exception) { emptyList<File>() to emptyList() }
            }
            val text = readUriListText(support) ?: return emptyList<File>() to emptyList()
            val items = parseUriList(text)
            val dirs = items.filter { it.isDirectory }
            val files = items.filter { it.isFile }
            return dirs to files
        }

        private fun readUriListText(support: TransferSupport): String? {
            return try {
                when {
                    uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor) ->
                        support.transferable.getTransferData(uriListFlavor) as? String
                    uriListReaderFlavor != null && support.isDataFlavorSupported(uriListReaderFlavor) -> {
                        val reader = support.transferable.getTransferData(uriListReaderFlavor) as? java.io.Reader
                        reader?.readText()
                    }
                    uriListInputFlavor != null && support.isDataFlavorSupported(uriListInputFlavor) -> {
                        val stream = support.transferable.getTransferData(uriListInputFlavor) as? java.io.InputStream
                        stream?.bufferedReader()?.readText()
                    }
                    else -> null
                }
            } catch (_: Exception) { null }
        }

        private fun parseUriList(text: String): List<File> =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    if (!line.startsWith("file:/")) return@mapNotNull null
                    runCatching { File(URI(line)) }.getOrNull()
                }
                .toList()
    }
}
