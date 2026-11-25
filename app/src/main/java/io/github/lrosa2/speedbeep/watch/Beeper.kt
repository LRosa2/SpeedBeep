package io.github.lrosa2.speedbeep.watch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.Output
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SoundKind
import io.github.lrosa2.speedbeep.engine.Tones

/**
 * Plays the main beep and the reminder with the chosen sound, output and volume (spec §7.1,
 * §7.2). Phone speaker = alarm usage; car speakers = navigation usage with transient,
 * ducking audio focus, like a Maps prompt. Main thread only.
 */
class Beeper(private val context: Context, private val events: EventLog) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val finish = Runnable { stop() }

    fun playMain(s: Settings) {
        stop()
        val attributes = attributes(s.output)
        requestFocus(s.output, attributes)
        val alarmPlaying = s.sound == SoundKind.PHONE_ALARM && s.alarmUri != null && playAlarm(s, attributes)
        if (!alarmPlaying) playPcm(Tones.main(s.sound, s.mainBeepMs), s, attributes, "main beep")
        handler.postDelayed(finish, s.mainBeepMs + END_MARGIN_MS)
    }

    fun playReminder(s: Settings) {
        stop()
        val attributes = attributes(s.output)
        requestFocus(s.output, attributes)
        playPcm(Tones.reminder(), s, attributes, "reminder")
        handler.postDelayed(finish, Tones.REMINDER_MS + END_MARGIN_MS)
    }

    /** Cuts any sound at once and gives the audio focus back. */
    fun stop() {
        handler.removeCallbacks(finish)
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun attributes(output: Output): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            if (output == Output.CAR_SPEAKERS) AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            else AudioAttributes.USAGE_ALARM,
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun requestFocus(output: Output, attributes: AudioAttributes) {
        if (output != Output.CAR_SPEAKERS) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = request
    }

    /** Never throws: a failed beep is logged, and the next beep tries again (watching must go on). */
    private fun playPcm(pcm: ShortArray, s: Settings, attributes: AudioAttributes, what: String) {
        try {
            startPcm(pcm, s, attributes)
        } catch (e: Exception) {
            events.log("$what failed (${e.javaClass.simpleName}: ${e.message})")
            track?.release()
            track = null
        }
    }

    private fun startPcm(pcm: ShortArray, s: Settings, attributes: AudioAttributes) {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(Tones.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track = t
        t.write(pcm, 0, pcm.size)
        t.setVolume(s.volumePercent / 100f)
        t.play()
    }

    /** Loops the chosen phone alarm sound until [finish] cuts it. False if it cannot play. */
    private fun playAlarm(s: Settings, attributes: AudioAttributes): Boolean = try {
        val volume = s.volumePercent / 100f
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(context, Uri.parse(s.alarmUri))
            isLooping = true
            prepare()
            setVolume(volume, volume)
            start()
        }
        true
    } catch (e: Exception) {
        events.log("alarm sound failed (${e.javaClass.simpleName}); using the built-in beep")
        player?.release()
        player = null
        false
    }

    private companion object {
        const val END_MARGIN_MS = 150L
    }
}
