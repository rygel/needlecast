package io.github.rygel.needlecast.ui

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
        }
    }
}
