package io.github.rygel.needlecast.ui.explorer

import org.slf4j.LoggerFactory
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.TransferHandler.TransferSupport

class ExplorerDropHandler(
    private val openFileInTab: (File) -> Unit,
    private val setRootDirectory: (File) -> Unit,
    private val table: JTable,
    private val tabs: JTabbedPane,
) : TransferHandler() {
    private val logger = LoggerFactory.getLogger(ExplorerDropHandler::class.java)
    private val uriListFlavor: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.lang.String")
        } catch (e: Exception) {
            logger.warn("Failed to create URI list flavor (string)", e)
            null
        }
    private val uriListReaderFlavor: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.io.Reader")
        } catch (e: Exception) {
            logger.warn("Failed to create URI list flavor (reader)", e)
            null
        }
    private val uriListInputFlavor: DataFlavor? =
        try {
            DataFlavor("text/uri-list;class=java.io.InputStream")
        } catch (e: Exception) {
            logger.warn("Failed to create URI list flavor (input stream)", e)
            null
        }
    private val urlFlavor: DataFlavor? =
        try {
            DataFlavor("application/x-java-url;class=java.net.URL")
        } catch (e: Exception) {
            logger.warn("Failed to create URL flavor", e)
            null
        }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDrop) return false
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            (urlFlavor != null && support.isDataFlavorSupported(urlFlavor)) ||
            (uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor)) ||
            (uriListReaderFlavor != null && support.isDataFlavorSupported(uriListReaderFlavor)) ||
            (uriListInputFlavor != null && support.isDataFlavorSupported(uriListInputFlavor))
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        val (dirs, files) = entriesFromExternal(support)
        if (dirs.isEmpty() && files.isEmpty()) return false

        val isOverTable = SwingUtilities.isDescendingFrom(support.component, table)
        val isOverTabs = SwingUtilities.isDescendingFrom(support.component, tabs)

        if (files.isNotEmpty()) {
            files.forEach { openFileInTab(it) }
            return true
        }
        if (dirs.isNotEmpty() && (isOverTable || isOverTabs)) {
            setRootDirectory(dirs.first())
            return true
        }
        return false
    }

    private fun entriesFromExternal(support: TransferSupport): Pair<List<File>, List<File>> {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val items =
                try {
                    (support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        ?.filterIsInstance<File>()
                        ?: emptyList()
                } catch (e: Exception) {
                    logger.warn("Failed to read file list from transferable", e)
                    emptyList()
                }
            return items.filter { it.isDirectory } to items.filter { it.isFile }
        }
        if (urlFlavor != null && support.isDataFlavorSupported(urlFlavor)) {
            return try {
                val url = support.transferable.getTransferData(urlFlavor) as? java.net.URL
                val file = url?.toURI()?.let { File(it) }
                val dirs = if (file != null && file.isDirectory) listOf(file) else emptyList()
                val files = if (file != null && file.isFile) listOf(file) else emptyList()
                dirs to files
            } catch (e: Exception) {
                logger.warn("Failed to extract URL from transferable", e)
                emptyList<File>() to emptyList()
            }
        }
        val text = readUriListText(support) ?: return emptyList<File>() to emptyList()
        val items = parseUriList(text)
        return items.filter { it.isDirectory } to items.filter { it.isFile }
    }

    private fun readUriListText(support: TransferSupport): String? =
        try {
            when {
                uriListFlavor != null && support.isDataFlavorSupported(uriListFlavor) -> {
                    support.transferable.getTransferData(uriListFlavor) as? String
                }

                uriListReaderFlavor != null && support.isDataFlavorSupported(uriListReaderFlavor) -> {
                    val reader = support.transferable.getTransferData(uriListReaderFlavor) as? java.io.Reader
                    reader?.readText()
                }

                uriListInputFlavor != null && support.isDataFlavorSupported(uriListInputFlavor) -> {
                    val stream = support.transferable.getTransferData(uriListInputFlavor) as? java.io.InputStream
                    stream?.bufferedReader()?.readText()
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to read URI list text from transferable", e)
            null
        }
}

internal fun parseUriList(text: String): List<File> =
    text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            if (!line.startsWith("file:/")) return@mapNotNull null
            runCatching { File(URI(line)) }.getOrNull()
        }.toList()
