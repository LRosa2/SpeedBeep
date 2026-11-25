package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch

enum class Phase { ARMED, OVER, QUIET, PAUSED, FAST_ROAD, NO_GPS }

enum class RoadContext { NORMAL, FAST }

/** What the beeper should do now. STOP cuts any sound still playing. */
enum class Sound { NONE, MAIN, REMINDER, STOP }

data class RulesConfig(
    val thresholdKmh: Int = 53,
    /** 0 = no reminders. */
    val reminderIntervalMs: Long = 10_000,
    val simpleMode: Boolean = false,
)

data class RulesOutput(
    val phase: Phase,
    val sound: Sound,
    /** Last usable speed; null while NO_GPS. */
    val speedKmh: Double?,
    val roadContext: RoadContext,
    /** Smart mode, but the last reading was outside the road data. */
    val noRoadData: Boolean,
    /** End of the running pause, or null. */
    val pausedUntilMs: Long?,
)

/**
 * The beep state machine of spec §4.3–4.4. Feed it every reading ([onReading]) and a clock
 * tick about once a second ([onTick]), and act on [RulesOutput.sound]. Times are monotonic
 * milliseconds. Not thread-safe.
 */
class SpeedRules(config: RulesConfig, startMs: Long) {

    companion object {
        const val REARM_MARGIN_KMH = 3
        const val DEBOUNCE_READINGS = 2
        const val FAST_ENTER_READINGS = 5
        const val FAST_LEAVE_READINGS = 2
        const val NEUTRAL_TIMEOUT_MS = 10_000L
        const val NO_GPS_AFTER_MS = 5_000L
    }

    var config: RulesConfig = config
        private set

    private var phase = Phase.ARMED
    private var context = RoadContext.NORMAL
    private var fastRun = 0
    private var normalRun = 0
    private var neutralSinceMs: Long? = null
    private var noRoadData = false
    private var overRun = 0
    private var wasAbove = false
    private var lastBeepMs = 0L
    private var lastUsableMs = startMs
    private var lastSpeed: Double? = null
    private var pausedUntilMs: Long? = null

    /** QUIET was active when GPS was lost, so the same stretch stays quiet when it returns. */
    private var quietBeforeNoGps = false

    /** Takes effect from the next reading. */
    fun updateConfig(newConfig: RulesConfig) {
        config = newConfig
    }

    fun onReading(reading: Reading): RulesOutput {
        val speed = reading.speedKmh ?: return onTick(reading.timeMs)
        val now = reading.timeMs
        // A GPS gap ends any weak-position streak: the 10 s timeout must not count the tunnel.
        if (now - lastUsableMs >= NO_GPS_AFTER_MS) neutralSinceMs = null
        lastUsableMs = now
        lastSpeed = speed
        updateRoadContext(reading)

        if (phase == Phase.PAUSED) {
            if (now < pausedUntilMs!!) return output(Sound.NONE)
            endPause()
        }
        if (phase == Phase.NO_GPS) {
            val stillFast = speed > config.thresholdKmh - REARM_MARGIN_KMH
            phase = if (quietBeforeNoGps && stillFast) Phase.QUIET else Phase.ARMED
        }

        val threshold = config.thresholdKmh
        val above = speed >= threshold
        val aboveBefore = wasAbove
        wasAbove = above
        // Counted on the fast road too, so leaving it while over beeps without a new debounce.
        overRun = if (above) overRun + 1 else 0

        if (context == RoadContext.FAST) {
            val sound = if (phase == Phase.OVER) Sound.STOP else Sound.NONE
            phase = Phase.FAST_ROAD
            return output(sound)
        }
        if (phase == Phase.FAST_ROAD) phase = Phase.ARMED

        val sound = when (phase) {
            Phase.ARMED ->
                if (overRun >= DEBOUNCE_READINGS && !holdForFastRoad(reading)) {
                    phase = Phase.OVER
                    lastBeepMs = now
                    Sound.MAIN
                } else {
                    Sound.NONE
                }
            Phase.OVER -> when {
                speed <= threshold - REARM_MARGIN_KMH -> {
                    phase = Phase.ARMED
                    Sound.STOP
                }
                !above -> if (aboveBefore) Sound.STOP else Sound.NONE
                config.reminderIntervalMs > 0 && now - lastBeepMs >= config.reminderIntervalMs -> {
                    lastBeepMs = now
                    Sound.REMINDER
                }
                else -> Sound.NONE
            }
            Phase.QUIET -> {
                if (speed <= threshold - REARM_MARGIN_KMH) phase = Phase.ARMED
                Sound.NONE
            }
            else -> Sound.NONE
        }
        return output(sound)
    }

    fun onTick(nowMs: Long): RulesOutput {
        if (phase == Phase.PAUSED) {
            if (nowMs < pausedUntilMs!!) return output(Sound.NONE)
            endPause()
        }
        if (phase != Phase.NO_GPS && nowMs - lastUsableMs >= NO_GPS_AFTER_MS) {
            val sound = if (phase == Phase.OVER) Sound.STOP else Sound.NONE
            quietBeforeNoGps = phase == Phase.QUIET
            phase = Phase.NO_GPS
            overRun = 0
            wasAbove = false
            lastSpeed = null
            return output(sound)
        }
        return output(Sound.NONE)
    }

    fun quietThisStretch(nowMs: Long): RulesOutput {
        if (phase != Phase.OVER) return onTick(nowMs)
        phase = Phase.QUIET
        return output(Sound.STOP)
    }

    fun pause(nowMs: Long, durationMs: Long): RulesOutput {
        val sound = if (phase == Phase.OVER) Sound.STOP else Sound.NONE
        phase = Phase.PAUSED
        pausedUntilMs = nowMs + durationMs
        overRun = 0
        return output(sound)
    }

    fun resume(nowMs: Long): RulesOutput {
        if (phase == Phase.PAUSED) endPause()
        return onTick(nowMs)
    }

    private fun endPause() {
        pausedUntilMs = null
        overRun = 0
        wasAbove = false
        phase = if (context == RoadContext.FAST) Phase.FAST_ROAD else Phase.ARMED
    }

    /** §4.3 fast-road hold: this reading itself points at a fast road that is not confirmed yet. */
    private fun holdForFastRoad(reading: Reading): Boolean =
        !config.simpleMode &&
            (reading.match == RoadMatch.FAST || (reading.match == RoadMatch.NEUTRAL && fastRun > 0))

    private fun updateRoadContext(reading: Reading) {
        if (config.simpleMode || reading.match == RoadMatch.NOT_COVERED) {
            noRoadData = !config.simpleMode
            context = RoadContext.NORMAL
            fastRun = 0
            normalRun = 0
            neutralSinceMs = null
            return
        }
        noRoadData = false
        when (reading.match) {
            RoadMatch.FAST -> {
                neutralSinceMs = null
                normalRun = 0
                fastRun++
                if (fastRun >= FAST_ENTER_READINGS) context = RoadContext.FAST
            }
            RoadMatch.TOWN, RoadMatch.UNKNOWN -> {
                neutralSinceMs = null
                fastRun = 0
                normalRun++
                if (normalRun >= FAST_LEAVE_READINGS) context = RoadContext.NORMAL
            }
            RoadMatch.NEUTRAL -> {
                // Neither counts toward a switch nor breaks a run; only a long weak-position streak resets.
                val since = neutralSinceMs ?: reading.timeMs.also { neutralSinceMs = it }
                if (reading.timeMs - since >= NEUTRAL_TIMEOUT_MS) {
                    context = RoadContext.NORMAL
                    fastRun = 0
                }
            }
            RoadMatch.NOT_COVERED -> Unit // handled above
        }
    }

    private fun output(sound: Sound) = RulesOutput(
        phase = phase,
        sound = sound,
        speedKmh = if (phase == Phase.NO_GPS) null else lastSpeed,
        roadContext = context,
        noRoadData = noRoadData,
        pausedUntilMs = pausedUntilMs,
    )
}
