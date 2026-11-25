package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch

/** Fix → Reading (spec §4.1). Speed and position are judged separately. */
object FixEvaluator {
    const val MAX_AGE_MS = 3_000L

    /** The fix clock may run slightly ahead of the reading clock. */
    const val MAX_FUTURE_MS = 1_000L
    const val MAX_SPEED_ACCURACY_MPS = 2.0f
    const val MAX_POSITION_ACCURACY_M = 25.0f

    fun evaluate(fix: Fix, nowMs: Long, simpleMode: Boolean, roads: RoadLookup): Reading {
        val age = nowMs - fix.elapsedMs
        val fresh = age in -MAX_FUTURE_MS..MAX_AGE_MS
        val speedMps = fix.speedMps
        val speedAccuracy = fix.speedAccuracyMps
        val speedKmh = if (fresh && speedMps != null && (speedAccuracy == null || speedAccuracy <= MAX_SPEED_ACCURACY_MPS)) {
            speedMps.toDouble() * 3.6
        } else {
            null
        }
        val positionAccuracy = fix.horizontalAccuracyM
        val positionUsable = fresh && positionAccuracy != null && positionAccuracy <= MAX_POSITION_ACCURACY_M
        val match = when {
            simpleMode -> RoadMatch.UNKNOWN // ignored by the rules in simple mode
            !positionUsable -> RoadMatch.NEUTRAL
            else -> roads.classify(fix.lat, fix.lon, fix.bearingDeg?.toDouble(), speedKmh ?: 0.0)
        }
        return Reading(nowMs, speedKmh, match)
    }
}
