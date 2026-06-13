package io.github.rygel.needlecast.ui

import org.slf4j.LoggerFactory
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

private val logger = LoggerFactory.getLogger(TrayNotifier::class.java)

internal object TrayNotifier {
    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        try {
            val img =
                TrayNotifier::class.java
                    .getResource("/icons/needlecast.png")
                    ?.let {
                        javax.imageio.ImageIO
                            .read(it)
                            .getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH)
                    }
                    ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val icon = TrayIcon(img, "Needlecast")
            SystemTray.getSystemTray().add(icon)
            icon
        } catch (e: Exception) {
            logger.debug("Failed to create system tray icon", e)
            null
        }
    }

    fun notify(
        caption: String,
        text: String,
        type: TrayIcon.MessageType,
    ) {
        try {
            trayIcon?.displayMessage(caption, text, type)
        } catch (e: Exception) {
            logger.debug("Failed to display tray notification", e)
        }
    }
}
