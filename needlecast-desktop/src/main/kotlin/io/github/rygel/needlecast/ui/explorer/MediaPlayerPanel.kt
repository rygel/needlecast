package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import org.slf4j.LoggerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.Timer

class MediaPlayerPanel(
    private val file: File,
    private val ctx: AppContext,
) : JPanel(BorderLayout()) {

    private val logger = LoggerFactory.getLogger(MediaPlayerPanel::class.java)

    private val statusLabel = JLabel("Loading...")
    private val timeLabel = JLabel("00:00 / 00:00")
    private val playButton = JButton("Play")
    private val stopButton = JButton("Stop")
    private val seekSlider = JSlider(0, 1000, 0)
    private val volumeSlider = JSlider(0, 100, 80)
    private val loopCheck = JCheckBox("Loop")
    private val autoplayCheck = JCheckBox("Autoplay")
    // Per-session only: speed resets to 1× on each new file open (not persisted to AppConfig by design)
    private val speedCombo = JComboBox(arrayOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×")).apply {
        selectedItem = "1×"
    }

    private var mediaPlayerComponent: EmbeddedMediaPlayerComponent? = null
    private var mediaPlayer: MediaPlayer? = null
    private var userSeeking = false
    private var updateTimer: Timer? = null

    init {
        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(JLabel(file.name), BorderLayout.WEST)
            add(statusLabel, BorderLayout.CENTER)
        }
        add(toolbar, BorderLayout.NORTH)

        if (!ensureVlcAvailable()) {
            add(buildMissingVlcPanel(), BorderLayout.CENTER)
        } else {
            val component = EmbeddedMediaPlayerComponent().also { mediaPlayerComponent = it }
            val player = component.mediaPlayer().also { mediaPlayer = it }

            component.background = Color.BLACK
            add(component, BorderLayout.CENTER)

            val controls = JPanel(BorderLayout(6, 0)).apply {
                border = BorderFactory.createEmptyBorder(4, 6, 6, 6)
                val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    add(playButton)
                    add(stopButton)
                    add(loopCheck)
                    add(autoplayCheck)
                }
                val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    add(JLabel("Volume"))
                    add(volumeSlider)
                    add(speedCombo)
                }
                add(buttons, BorderLayout.WEST)
                add(seekSlider, BorderLayout.CENTER)
                add(right, BorderLayout.EAST)
            }
            val south = JPanel(BorderLayout()).apply {
                add(controls, BorderLayout.CENTER)
                add(timeLabel, BorderLayout.SOUTH)
            }
            add(south, BorderLayout.SOUTH)

            autoplayCheck.isSelected = ctx.config.mediaAutoplay
            player.audio().setVolume(volumeSlider.value)
            playButton.addActionListener { togglePlayPause() }
            stopButton.addActionListener { player.controls().stop() }
            volumeSlider.addChangeListener {
                if (!volumeSlider.valueIsAdjusting) {
                    player.audio().setVolume(volumeSlider.value)
                }
            }
            autoplayCheck.addActionListener {
                ctx.updateConfig(ctx.config.copy(mediaAutoplay = autoplayCheck.isSelected))
            }
            speedCombo.addActionListener {
                val item = (speedCombo.selectedItem as? String) ?: "1×"
                val rate = item.removeSuffix("×").trim().toFloatOrNull() ?: 1.0f
                player.controls().setRate(rate)
            }
            seekSlider.addChangeListener {
                if (seekSlider.valueIsAdjusting) {
                    userSeeking = true
                } else if (userSeeking) {
                    userSeeking = false
                    val duration = player.status().length()
                    if (duration > 0) {
                        val pos = seekSlider.value / 1000.0
                        player.controls().setTime((duration * pos).toLong())
                    }
                }
            }

            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    SwingUtilities.invokeLater { updatePlayState(isPlaying = true) }
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    SwingUtilities.invokeLater { updatePlayState(isPlaying = false) }
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    SwingUtilities.invokeLater {
                        updatePlayState(isPlaying = false)
                        seekSlider.value = 0
                    }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    SwingUtilities.invokeLater {
                        if (loopCheck.isSelected) {
                            playFile()
                        } else {
                            updatePlayState(isPlaying = false)
                            seekSlider.value = seekSlider.maximum
                        }
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "Playback error"
                    }
                }
            })

            updateTimer = Timer(250) { refreshTime() }.apply { isRepeats = true; start() }
            statusLabel.text = "Ready"
            if (ctx.config.mediaAutoplay) {
                SwingUtilities.invokeLater { playFile() }
            }
        }
    }

    fun dispose() {
        try { updateTimer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.controls()?.stop() } catch (_: Exception) {}
        try { mediaPlayerComponent?.release() } catch (_: Exception) {}
    }

    private fun playFile() {
        val player = mediaPlayer ?: return
        statusLabel.text = "Playing..."
        val ok = player.media().play(file.absolutePath)
        if (!ok) statusLabel.text = "Could not play media"
    }

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.status().isPlaying) {
            player.controls().pause()
        } else {
            if (player.status().time() <= 0L) playFile()
            else player.controls().play()
        }
    }

    private fun updatePlayState(isPlaying: Boolean) {
        playButton.text = if (isPlaying) "Pause" else "Play"
        statusLabel.text = if (isPlaying) "Playing..." else "Paused"
    }

    private fun refreshTime() {
        val player = mediaPlayer ?: return
        if (userSeeking) return
        val duration = player.status().length()
        val current = player.status().time()
        if (duration > 0) {
            val pos = (current.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
            seekSlider.value = (pos * 1000).toInt()
            timeLabel.text = "${fmtTime(current)} / ${fmtTime(duration)}"
        } else {
            timeLabel.text = "${fmtTime(current)} / 00:00"
        }
    }

    private fun fmtTime(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val s = totalSec % 60
        val m = (totalSec / 60) % 60
        val h = totalSec / 3600
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun ensureVlcAvailable(): Boolean = VlcSupport.available

    private fun buildMissingVlcPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            background = Color(0x2B2B2B)
            add(JLabel(
                "<html><body style='color:#ddd; font-family:sans-serif;'>" +
                    "VLC is required to play media files.<br/>" +
                    "Install VLC (3.x) and restart Needlecast." +
                    "</body></html>"
            ).apply { border = BorderFactory.createEmptyBorder(16, 16, 16, 16) }, BorderLayout.NORTH)
        }

    private object VlcSupport {
        val available: Boolean by lazy {
            try { NativeDiscovery().discover() } catch (_: Exception) { false }
        }
    }
}
