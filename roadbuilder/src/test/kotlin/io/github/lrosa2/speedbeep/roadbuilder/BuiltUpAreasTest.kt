package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Geo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.cos

class BuiltUpAreasTest {
    private val lat0 = 47.0
    private val lon0 = 8.0
    private val metresPerDegLon = Geo.M_PER_DEG * cos(Math.toRadians(lat0))

    private fun lat(northM: Double) = lat0 + northM / Geo.M_PER_DEG
    private fun lon(eastM: Double) = lon0 + eastM / metresPerDegLon

    /** A closed ring through (north, east) corners given in metres from (lat0, lon0). */
    private fun ring(vararg corners: Pair<Double, Double>): Ring {
        val points = corners.toList() + corners.first()
        return Ring(
            IntArray(points.size) { Math.round(lat(points[it].first) * 1e6).toInt() },
            IntArray(points.size) { Math.round(lon(points[it].second) * 1e6).toInt() },
        )
    }

    private fun BuiltUpAreas.at(northM: Double, eastM: Double) = locate(lat(northM), lon(eastM))

    @Test
    fun `inside, within the edge band, and outside a square`() {
        val areas = BuiltUpAreas(listOf(ring(0.0 to 0.0, 0.0 to 500.0, 500.0 to 500.0, 500.0 to 0.0)), bufferM = 30.0)
        assertEquals(Placement.INSIDE, areas.at(250.0, 250.0))
        assertEquals(Placement.INSIDE, areas.at(5.0, 5.0))
        assertEquals(Placement.NEAR, areas.at(-20.0, 250.0))
        assertEquals(Placement.NEAR, areas.at(250.0, 525.0))
        assertEquals(Placement.OUTSIDE, areas.at(-40.0, 250.0))
        assertEquals(Placement.OUTSIDE, areas.at(250.0, 2_000.0))
    }

    @Test
    fun `the notch of an L-shaped area is outside`() {
        val l = ring(0.0 to 0.0, 0.0 to 1_000.0, 300.0 to 1_000.0, 300.0 to 300.0, 1_000.0 to 300.0, 1_000.0 to 0.0)
        val areas = BuiltUpAreas(listOf(l), bufferM = 30.0)
        assertEquals(Placement.INSIDE, areas.at(150.0, 800.0))
        assertEquals(Placement.INSIDE, areas.at(800.0, 150.0))
        assertEquals(Placement.OUTSIDE, areas.at(700.0, 700.0))
        assertEquals(Placement.NEAR, areas.at(700.0, 320.0))
    }

    @Test
    fun `a large area spanning many grid cells is found from any of them`() {
        val big = ring(0.0 to 0.0, 0.0 to 5_000.0, 5_000.0 to 5_000.0, 5_000.0 to 0.0)
        assertEquals(Placement.INSIDE, BuiltUpAreas(listOf(big), bufferM = 30.0).at(4_000.0, 4_000.0))
    }

    @Test
    fun `no areas means everything is outside`() {
        assertEquals(Placement.OUTSIDE, BuiltUpAreas(emptyList(), bufferM = 30.0).at(0.0, 0.0))
    }
}
