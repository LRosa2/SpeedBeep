package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch

/** Feeds readings into [SpeedRules], one second apart by default, starting at t = 0. */
class TestDrive(config: RulesConfig = RulesConfig()) {
    var now = 0L
        private set
    val rules = SpeedRules(config, startMs = 0L)

    fun at(speedKmh: Double?, match: RoadMatch = RoadMatch.TOWN, stepMs: Long = 1_000): RulesOutput {
        now += stepMs
        return rules.onReading(Reading(now, speedKmh, match))
    }

    /** [count] readings at the same speed and match; returns their sounds. */
    fun readings(count: Int, speedKmh: Double?, match: RoadMatch = RoadMatch.TOWN): List<Sound> =
        List(count) { at(speedKmh, match).sound }

    fun tick(stepMs: Long): RulesOutput {
        now += stepMs
        return rules.onTick(now)
    }

    fun quiet() = rules.quietThisStretch(now)
    fun pause(minutes: Int) = rules.pause(now, minutes * 60_000L)
    fun resume() = rules.resume(now)
}
