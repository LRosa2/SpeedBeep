package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Phase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class NoteButton { QUIET, PAUSE, RESUME, STOP }

/** The status line and the notification buttons of spec §5.3. */
object StatusText {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    /** [monoNowMs] and [wallNowMs] are the same moment on both clocks; they turn the pause end into a clock time. */
    fun line(s: WatchStatus, monoNowMs: Long, wallNowMs: Long, zone: ZoneId): String {
        // Truncated, so 52.9 never shows as "53 km/h · town" while still under a threshold of 53.
        val speed = s.speedKmh?.let { "${it.toInt()} km/h" }
        return when {
            s.phase == Phase.PAUSED -> "Paused until " + clock(s.pausedUntilMs ?: monoNowMs, monoNowMs, wallNowMs, zone)
            s.phase == Phase.NO_GPS -> "No GPS"
            speed == null -> "Waiting for GPS"
            s.phase == Phase.OVER -> "$speed · OVER ${s.thresholdKmh}"
            s.phase == Phase.FAST_ROAD -> "$speed · fast road, silent"
            s.phase == Phase.QUIET -> "$speed · quiet this stretch"
            s.simpleMode -> if (s.speedSource == SpeedSource.CAR) "$speed · simple mode · car" else "$speed · simple mode"
            s.noRoadData -> "$speed · simple mode · no road data"
            else -> "$speed · town"
        }
    }

    fun buttons(phase: Phase): List<NoteButton> = when (phase) {
        Phase.OVER -> listOf(NoteButton.QUIET, NoteButton.PAUSE, NoteButton.STOP)
        Phase.PAUSED -> listOf(NoteButton.RESUME, NoteButton.STOP)
        else -> listOf(NoteButton.PAUSE, NoteButton.STOP)
    }

    private fun clock(monoMs: Long, monoNowMs: Long, wallNowMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(wallNowMs + (monoMs - monoNowMs)).atZone(zone).format(CLOCK)
}
