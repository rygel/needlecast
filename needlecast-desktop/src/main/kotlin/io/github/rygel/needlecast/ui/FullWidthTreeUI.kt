package io.github.rygel.needlecast.ui

import java.awt.Insets
import java.awt.Rectangle
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreePath

internal class FullWidthTreeUI : BasicTreeUI() {
    override fun installDefaults() {
        super.installDefaults()
        tree.rowHeight = 0
    }

    override fun paintRow(
        g: java.awt.Graphics,
        clipBounds: Rectangle?,
        insets: Insets?,
        bounds: Rectangle?,
        path: TreePath?,
        row: Int,
        isExpanded: Boolean,
        hasBeenExpanded: Boolean,
        isLeaf: Boolean,
    ) {
        val t = tree ?: return
        if (bounds == null || path == null) return
        if (t.isEditing && editingRow == row) return
        val vp = t.parent as? javax.swing.JViewport
        val vpWidth = vp?.width ?: t.width
        val rightInset = t.insets?.right ?: 0
        val fullWidth = (vpWidth - bounds.x - rightInset).coerceAtLeast(1)
        val fullBounds = Rectangle(bounds.x, bounds.y, fullWidth, bounds.height)
        val leadIndex = t.leadSelectionRow
        val selected = t.isRowSelected(row)
        val renderer =
            currentCellRenderer?.getTreeCellRendererComponent(
                t,
                path.lastPathComponent,
                selected,
                isExpanded,
                isLeaf,
                row,
                leadIndex == row,
            )
        if (renderer != null) {
            rendererPane.paintComponent(g, renderer, t, fullBounds.x, fullBounds.y, fullBounds.width, fullBounds.height, true)
        }
    }
}
