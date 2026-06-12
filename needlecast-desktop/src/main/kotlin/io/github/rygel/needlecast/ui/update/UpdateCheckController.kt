package io.github.rygel.needlecast.ui.update

import io.github.rygel.needlecast.ui.StatusBar
import io.github.rygel.needlecast.ui.UpdateCheckErrors
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Desktop
import java.net.URI
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.Timer

private const val APPCAST_URL = "https://github.com/rygel/needlecast/releases/latest/download/appcast.xml"

internal fun buildSparkle4jInstance(
    version: String,
    intervalHours: Int,
    parentComponent: Component? = null,
): io.github.rygel.sparkle4j.Sparkle4jInstance {
    val builder =
        io.github.rygel.sparkle4j.Sparkle4j
            .builder()
            .appcastUrl(APPCAST_URL)
            .currentVersion(version)
            .allowUnsignedUpdates()
            .appName("Needlecast")
            .checkIntervalHours(intervalHours)
    if (parentComponent != null) builder.parentComponent(parentComponent)
    return builder.build()
}

internal class UpdateCheckController(
    private val parent: Component,
    private val statusBar: StatusBar,
    private val versionProvider: () -> String?,
) {
    private val logger = LoggerFactory.getLogger("needlecast.update")

    private var updateCheckFailures = 0
    private val updateCheckFailureThreshold = 3

    val updateTimer =
        Timer(15 * 60 * 1000) { checkForUpdates() }.apply {
            isRepeats = true
            initialDelay = 30_000
        }

    fun checkForUpdates() {
        Thread {
            try {
                logger.info("Periodic update check")
                val item = buildSparkle4j(0)?.checkNow()?.orElse(null)
                updateCheckFailures = 0
                SwingUtilities.invokeLater { statusBar.hideUpdateCheckWarning() }
                if (item != null) {
                    logger.info("Update available: {}", item.version())
                    SwingUtilities.invokeLater {
                        statusBar.showUpdateAvailable(item.version()) { openReleasesPage() }
                    }
                }
            } catch (e: Exception) {
                logUpdateCheckFailure("Periodic update check", e)
                updateCheckFailures++
                if (updateCheckFailures >= updateCheckFailureThreshold) {
                    logger.warn("Update checks have failed {} consecutive times", updateCheckFailures)
                    SwingUtilities.invokeLater { statusBar.showUpdateCheckWarning() }
                }
            }
        }.also {
            it.isDaemon = true
            it.name = "update-check"
        }.start()
    }

    fun checkForUpdatesManual() {
        val instance = buildSparkle4j(0)
        if (instance == null) {
            JOptionPane.showMessageDialog(
                parent,
                "Update checking is not available (version unknown).",
                "Check for Updates",
                JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        statusBar.setStatus("Checking for updates\u2026")
        Thread({
            try {
                logger.info("Manual update check")
                val item = instance.checkNow().orElse(null)
                SwingUtilities.invokeLater {
                    if (item == null) {
                        logger.info("No update found — already on latest version")
                        statusBar.setStatus("You are running the latest version.")
                        JOptionPane.showMessageDialog(
                            parent,
                            "You are running the latest version of Needlecast.",
                            "Check for Updates",
                            JOptionPane.INFORMATION_MESSAGE,
                        )
                    } else {
                        logger.info("Update found: {}", item.version())
                        statusBar.showUpdateAvailable(item.version()) { openReleasesPage() }
                        openReleasesPage()
                    }
                }
            } catch (e: Exception) {
                logUpdateCheckFailure("Manual update check", e)
                val details = UpdateCheckErrors.details(e)
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        parent,
                        details.userMessage,
                        "Check for Updates",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }, "update-check-manual").apply {
            isDaemon = true
            start()
        }
    }

    private fun buildSparkle4j(intervalHours: Int = 24): io.github.rygel.sparkle4j.Sparkle4jInstance? {
        val version =
            versionProvider() ?: run {
                logger.warn("Cannot determine app version — update check skipped")
                return null
            }
        logger.info("Building sparkle4j instance: version={}, interval={}h", version, intervalHours)
        return try {
            buildSparkle4jInstance(
                version = version,
                intervalHours = intervalHours,
                parentComponent = parent,
            )
        } catch (e: Exception) {
            logger.error("Failed to configure update checker", e)
            null
        }
    }

    private fun openReleasesPage() {
        try {
            Desktop.getDesktop().browse(URI("https://github.com/rygel/needlecast/releases/latest"))
        } catch (e: Exception) {
            logger.warn("Could not open releases page", e)
        }
    }

    private fun logUpdateCheckFailure(
        context: String,
        error: Throwable,
    ) {
        val details = UpdateCheckErrors.details(error)
        val root = details.root
        val category = details.category
        val appcastHost = runCatching { URI(APPCAST_URL).host }.getOrNull() ?: "unknown"
        logger.warn(
            "{} failed: category={}, appcastHost={}, exceptionType={}, message={}, rootType={}, rootMessage={}",
            context,
            category,
            appcastHost,
            error::class.java.name,
            UpdateCheckErrors.sanitizeLogField(error.message),
            root::class.java.name,
            UpdateCheckErrors.sanitizeLogField(root.message),
        )
        if (category.startsWith("tls")) {
            logger.warn(
                "{} TLS hint: verify corporate proxy/SSL interception trust chain and JVM trust store",
                context,
            )
        }
        logger.debug("{} stacktrace", context, error)
    }
}
