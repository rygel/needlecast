package io.github.rygel.needlecast.ui.util

import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.File

object DesktopUtils {
    private val logger = LoggerFactory.getLogger(DesktopUtils::class.java)

    val openInFileManagerLabel: String =
        when {
            IS_MAC -> "Open in Finder"
            IS_WINDOWS -> "Open in Explorer"
            else -> "Open in File Manager"
        }

    val revealInFileManagerLabel: String =
        when {
            IS_MAC -> "Reveal in Finder"
            IS_WINDOWS -> "Reveal in Explorer"
            else -> "Open Containing Folder"
        }

    fun openInFileManager(dir: File) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir)
            } else if (IS_WINDOWS) {
                ProcessBuilder("explorer.exe", dir.absolutePath).start()
            } else if (IS_MAC) {
                ProcessBuilder("open", dir.absolutePath).start()
            } else {
                ProcessBuilder("xdg-open", dir.absolutePath).start()
            }
        } catch (e: Exception) {
            logger.warn("Failed to open {} in file manager", dir, e)
        }
    }

    fun revealInFileManager(file: File) {
        try {
            when {
                IS_WINDOWS -> ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                IS_MAC -> ProcessBuilder("open", "-R", file.absolutePath).start()
                else -> openInFileManager(file.parentFile ?: return)
            }
        } catch (e: Exception) {
            logger.warn("Failed to reveal {} in file manager", file, e)
        }
    }
}
