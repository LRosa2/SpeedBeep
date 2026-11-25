package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.FixEvaluator
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.RoadContext
import io.github.lrosa2.speedbeep.core.rules.RulesOutput
import io.github.lrosa2.speedbeep.core.rules.Sound
import io.github.lrosa2.speedbeep.core.rules.SpeedRules

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

    fun onFix(fix: Fix, nowMs: Long) = apply(rules.onReading(FixEvaluator.evaluate(fix, nowMs, settings.simpleMode, roads)))

    fun onTick(nowMs: Long) = apply(rules.onTick(nowMs))

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
            settings.thresholdKmh, settings.simpleMode, settings.pauseMin,
        )
        return WatchUpdate(status, out.sound)
    }
}
