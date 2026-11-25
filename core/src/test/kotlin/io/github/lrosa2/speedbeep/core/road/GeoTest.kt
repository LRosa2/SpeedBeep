package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoTest {
    private val lat = 47.37
    private val lon = 8.54

    @Test
    fun `a thousandth of a degree of latitude is about 111 m`() {
        assertEquals(111.32, Geo.distanceM(lat, lon, lat + 0.001, lon), 0.01)
    }

    @Test
    fun `distance to a segment is perpendicular inside it and to the nearest end outside it`() {
        val east = 0.002 // a segment running east from (lat, lon)
        val tenMetresNorth = 10.0 / Geo.M_PER_DEG
        assertEquals(10.0, Geo.distanceToSegmentM(lat + tenMetresNorth, lon + east / 2, lat, lon, lat, lon + east), 0.01)
        val beyondEnd = Geo.distanceM(lat, lon + east * 2, lat, lon + east)
        assertEquals(beyondEnd, Geo.distanceToSegmentM(lat, lon + east * 2, lat, lon, lat, lon + east), 0.01)
    }

    @Test
    fun `a zero-length segment is a point`() {
        assertEquals(
            Geo.distanceM(lat + 0.0001, lon, lat, lon),
            Geo.distanceToSegmentM(lat + 0.0001, lon, lat, lon, lat, lon),
            0.001,
        )
    }

    @Test
    fun `bearings run clockwise from north`() {
        assertEquals(0.0, Geo.bearingDeg(lat, lon, lat + 0.01, lon), 0.01)
        assertEquals(90.0, Geo.bearingDeg(lat, lon, lat, lon + 0.01), 0.01)
        assertEquals(180.0, Geo.bearingDeg(lat, lon, lat - 0.01, lon), 0.01)
        assertEquals(270.0, Geo.bearingDeg(lat, lon, lat, lon - 0.01), 0.01)
    }

    @Test
    fun `line angles ignore direction`() {
        assertEquals(20.0, Geo.lineAngleDiff(10.0, 350.0), 1e-9)
        assertEquals(0.0, Geo.lineAngleDiff(0.0, 180.0), 1e-9)
        assertEquals(90.0, Geo.lineAngleDiff(90.0, 0.0), 1e-9)
        assertEquals(20.0, Geo.lineAngleDiff(170.0, 10.0), 1e-9)
    }
}
