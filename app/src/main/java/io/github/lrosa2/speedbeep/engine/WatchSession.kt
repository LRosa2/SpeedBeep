package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.FixEvaluator
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.Reading
import io.github.lrosa2.speedbeep.core.rules.RoadContext
import io.github.lrosa2.speedbeep.core.rules.RulesOutput
import io.github.lrosa2.speedbeep.core.rules.Sound
import io.github.lrosa2.speedbeep.core.rules.SpeedRules

/** Where the speed in use comes from (spec §4.5). */
enum class SpeedSource { GPS, CAR }

/** What the notification, the main screen and the tile show. */
data class WatchStatus(
    val phase: Phase,
    val speedKmh: Double?,
    val roadContext: RoadContext,
    val noRoadData: Boolean,
    val pausedUntilMs: Long?,
    val thresholdKmh: Int,
    val simpleMode: Boolean,
    val pauseMin: Int,
    val speedSource: SpeedSource = SpeedSource.GPS,
)

data class WatchUpdate(val status: WatchStatus, val sound: Sound)

/**
 * One watching session: fix → [FixEvaluator] → [SpeedRules] (spec §4). Times are monotonic
 * milliseconds. Single-threaded, like SpeedRules and RoadMatcher.
 */
class WatchSession(settings: Settings, startMs: Long, private var roads: RoadLookup = NoRoadData) {
    private var settings = settings
    private val rules = SpeedRules(settings.toRulesConfig(), startMs)

    var status = WatchStatus(
        Phase.ARMED, null, RoadContext.NORMAL, false, null,
        settings.thresholdKmh, settings.simpleMode, settings.pauseMin,
    )
        private set

    companion object {
        /** The car's speed counts while its last reading is at most this old (spec §4.5). */
        const val CAR_FRESH_MS = 3_000L
    }

    private var lastCarMs: Long? = null
    private var source = SpeedSource.GPS

    /** The newest GPS fix ignored while the car's speed was fresh: GPS's first reading at the handover. */
    private var waitingGps: Fix? = null

    /** A GPS fix. In simple mode it is ignored while the car's speed is fresh (spec §4.5). */
    fun onFix(fix: Fix, nowMs: Long): WatchUpdate {
        if (carIsFresh(nowMs)) {
            waitingGps = fix
            return WatchUpdate(status, Sound.NONE)
        }
        source = SpeedSource.GPS
        val reading = FixEvaluator.evaluate(fix, nowMs, settings.simpleMode, roads)
        val waiting = waitingGps
        waitingGps = null
        // At the handover an unusable fix must not waste the last good one: No GPS comes only 5 s after the car's last reading.
        val use = if (reading.speedKmh == null && waiting != null) FixEvaluator.evaluate(waiting, nowMs, settings.simpleMode, roads) else reading
        return apply(rules.onReading(use))
    }

    /** One reading from the car's OBD adapter, in whole km/h. Smart mode ignores it (spec §4.5). */
    fun onCarSpeed(kmh: Int, nowMs: Long): WatchUpdate {
        if (!settings.simpleMode) return WatchUpdate(status, Sound.NONE)
        lastCarMs = nowMs
        source = SpeedSource.CAR
        // Straight to the rules in whole km/h, so 53 stays 53; simple mode needs no position.
        return apply(rules.onReading(Reading(nowMs, kmh.toDouble(), RoadMatch.UNKNOWN)))
    }

    private fun carIsFresh(nowMs: Long) = settings.simpleMode && lastCarMs?.let { nowMs - it <= CAR_FRESH_MS } == true

    fun onTick(nowMs: Long): WatchUpdate {
        val waiting = waitingGps
        if (waiting != null && !carIsFresh(nowMs)) {
            // The car just went silent: GPS takes over with the fix it already has (FixEvaluator drops it if too old).
            waitingGps = null
            source = SpeedSource.GPS
            return apply(rules.onReading(FixEvaluator.evaluate(waiting, nowMs, settings.simpleMode, roads)))
        }
        return apply(rules.onTick(nowMs))
    }

    fun quiet(nowMs: Long) = apply(rules.quietThisStretch(nowMs))

    fun pause(nowMs: Long) = apply(rules.pause(nowMs, settings.pauseMin * 60_000L))

    fun resume(nowMs: Long) = apply(rules.resume(nowMs))

    /** Threshold, mode and reminder interval apply from the next reading (SpeedRules.updateConfig). */
    fun updateSettings(newSettings: Settings) {
        settings = newSettings
        rules.updateConfig(newSettings.toRulesConfig())
        status = status.copy(
            thresholdKmh = newSettings.thresholdKmh,
            simpleMode = newSettings.simpleMode,
            pauseMin = newSettings.pauseMin,
        )
    }

    fun setRoads(lookup: RoadLookup) {
        roads = lookup
    }

    private fun apply(out: RulesOutput): WatchUpdate {
        status = WatchStatus(
            out.phase, out.speedKmh, out.roadContext, out.noRoadData, out.pausedUntilMs,
            settings.thresholdKmh, settings.simpleMode, settings.pauseMin, source,
        )
        return WatchUpdate(status, out.sound)
    }
}
