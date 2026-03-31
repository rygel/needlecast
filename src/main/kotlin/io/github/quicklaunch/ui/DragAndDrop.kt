package io.github.quicklaunch.ui

import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProjectGroup
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import javax.swing.JComponent
import javax.swing.TransferHandler

val DIRECTORY_FLAVOR = DataFlavor(DirectoryTransfer::class.java, "Project Directory")

data class DirectoryTransfer(val sourceGroupId: String, val project: DetectedProject)

class DirectoryDragHandler(
    private val getSourceGroupId: () -> String?,
    private val getSelectedProject: () -> DetectedProject?,
) : TransferHandler() {

    override fun getSourceActions(c: JComponent): Int = MOVE

    override fun createTransferable(c: JComponent): Transferable? {
        val groupId = getSourceGroupId() ?: return null
        val project = getSelectedProject() ?: return null
        val transfer = DirectoryTransfer(groupId, project)
        return object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DIRECTORY_FLAVOR)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DIRECTORY_FLAVOR
            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor != DIRECTORY_FLAVOR) throw UnsupportedFlavorException(flavor)
                return transfer
            }
        }
    }

    override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
        // handled by drop target
    }
}

class GroupDropHandler(
    private val getGroup: () -> ProjectGroup?,
    private val onDrop: (DirectoryTransfer, ProjectGroup) -> Unit,
) : TransferHandler() {

    override fun canImport(support: TransferSupport): Boolean =
        support.isDataFlavorSupported(DIRECTORY_FLAVOR)

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        val targetGroup = getGroup() ?: return false
        return try {
            val transfer = support.transferable.getTransferData(DIRECTORY_FLAVOR) as DirectoryTransfer
            onDrop(transfer, targetGroup)
            true
        } catch (e: Exception) {
            false
        }
    }
}
