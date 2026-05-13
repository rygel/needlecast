package io.github.rygel.needlecast.ui.diff

import javax.swing.JScrollPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class SynchronizedScrollListener(
    private val source: JScrollPane,
    private val target: JScrollPane,
) : ChangeListener {

    @Volatile
    private var isSyncing = false

    private val targetListener = ChangeListener { e ->
        if (isSyncing) return@ChangeListener
        isSyncing = true
        try {
            val targetBar = target.verticalScrollBar
            val sourceBar = source.verticalScrollBar
            if (targetBar.maximum == targetBar.minimum) return@ChangeListener
            val ratio = targetBar.value.toDouble() / (targetBar.maximum - targetBar.visibleAmount).coerceAtLeast(1)
            sourceBar.value = (ratio * (sourceBar.maximum - sourceBar.visibleAmount)).toInt()
        } finally {
            isSyncing = false
        }
    }

    override fun stateChanged(e: ChangeEvent?) {
        if (isSyncing) return
        isSyncing = true
        try {
            val sourceBar = source.verticalScrollBar
            val targetBar = target.verticalScrollBar
            if (sourceBar.maximum == sourceBar.minimum) return
            val ratio = sourceBar.value.toDouble() / (sourceBar.maximum - sourceBar.visibleAmount).coerceAtLeast(1)
            targetBar.value = (ratio * (targetBar.maximum - targetBar.visibleAmount)).toInt()
        } finally {
            isSyncing = false
        }
    }

    fun install() {
        source.verticalScrollBar.model.addChangeListener(this)
        target.verticalScrollBar.model.addChangeListener(targetListener)
    }
}
