package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FixEvaluatorTest {
    private val now = 100_000L
    private var lookups = 0
    private var lastBearing: Double? = null
    private var lastSpeed: Double? = null
    private val roads = RoadLookup { _, _, bearing, speed ->
        lookups++
        lastBearing = bearing
        lastSpeed = speed
        RoadMatch.TOWN
    }

    private fun fix(
        ageMs: Long = 500,
        speedMps: Float? = 15f,
        speedAccuracy: Float? = 0.5f,
        positionAccuracy: Float? = 5f,
        bearing: Float? = 90f,
    ) = Fix(now - ageMs, 47.37, 8.54, speedMps, speedAccuracy, positionAccuracy, bearing)

    private fun evaluate(fix: Fix, simpleMode: Boolean = false) = FixEvaluator.evaluate(fix, now, simpleMode, roads)

    @Test
    fun `a good fix gives km per h, the road match and the reading time`() {
        val r = evaluate(fix())
        assertEquals(54.0, r.speedKmh!!, 1e-9)
        assertEquals(RoadMatch.TOWN, r.match)
        assertEquals(now, r.timeMs)
        assertEquals(90.0, lastBearing!!, 1e-9)
        assertEquals(54.0, lastSpeed!!, 1e-9)
    }

    @Test
    fun `a fix older than 3 s is ignored for speed and road`() {
        val r = evaluate(fix(ageMs = 3_001))
        assertNull(r.speedKmh)
        assertEquals(RoadMatch.NEUTRAL, r.match)
        assertEquals(0, lookups)
    }

    @Test
    fun `a fix exactly 3 s old is still used`() {
        assertEquals(54.0, evaluate(fix(ageMs = 3_000)).speedKmh!!, 1e-9)
    }

    @Test
    fun `a fix stamped slightly ahead of the clock is used, far ahead is not`() {
        assertEquals(54.0, evaluate(fix(ageMs = -500)).speedKmh!!, 1e-9)
        assertNull(evaluate(fix(ageMs = -1_500)).speedKmh)
    }

    @Test
    fun `poor speed accuracy drops only the speed`() {
        val r = evaluate(fix(speedAccuracy = 2.5f))
        assertNull(r.speedKmh)
        assertEquals(RoadMatch.TOWN, r.match)
    }

    @Test
    fun `an unreported speed accuracy is accepted`() {
        assertEquals(54.0, evaluate(fix(speedAccuracy = null)).speedKmh!!, 1e-9)
    }

    @Test
    fun `poor position accuracy keeps the speed but makes the match NEUTRAL`() {
        val r = evaluate(fix(positionAccuracy = 30f))
        assertEquals(54.0, r.speedKmh!!, 1e-9)
        assertEquals(RoadMatch.NEUTRAL, r.match)
        assertEquals(0, lookups)
    }

    @Test
    fun `an unreported position accuracy is NEUTRAL`() {
        assertEquals(RoadMatch.NEUTRAL, evaluate(fix(positionAccuracy = null)).match)
    }

    @Test
    fun `no speed gives no speed`() {
        assertNull(evaluate(fix(speedMps = null)).speedKmh)
    }

    @Test
    fun `simple mode never looks at roads`() {
        val r = evaluate(fix(), simpleMode = true)
        assertEquals(RoadMatch.UNKNOWN, r.match)
        assertEquals(54.0, r.speedKmh!!, 1e-9)
        assertEquals(0, lookups)
    }

    @Test
    fun `without a bearing the lookup gets none`() {
        evaluate(fix(bearing = null))
        assertNull(lastBearing)
    }
}
