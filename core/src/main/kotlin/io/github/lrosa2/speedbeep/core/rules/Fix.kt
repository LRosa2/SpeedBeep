package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch

/** One GPS fix as plain values. [elapsedMs] is the monotonic time it was taken; null fields were not reported. */
data class Fix(
    val elapsedMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val horizontalAccuracyM: Float?,
    val bearingDeg: Float?,
)

/** What the rules see for one fix (spec §4.1–4.2). [speedKmh] is null when the speed is unusable. */
data class Reading(val timeMs: Long, val speedKmh: Double?, val match: RoadMatch)
