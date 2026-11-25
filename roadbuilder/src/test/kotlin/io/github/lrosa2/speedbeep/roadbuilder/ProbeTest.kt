package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadFile
import io.github.lrosa2.speedbeep.core.road.RoadFileWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ProbeTest {

    @Test
    fun `lists the nearest roads once each, nearest first`() {
        // Town road along 47.3750 N; motorway along 47.3755 N (about 56 m further north).
        val town = Polyline(RoadClass.TOWN, intArrayOf(47_375_000, 47_375_000), intArrayOf(8_535_000, 8_545_000))
        val motorway = Polyline(RoadClass.FAST, intArrayOf(47_375_500, 47_375_500), intArrayOf(8_535_000, 8_545_000))
        val out = ByteArrayOutputStream()
        RoadFileWriter.write(listOf(town, motorway), 0, 0, out)
        val file = RoadFile.parse(ByteBuffer.wrap(out.toByteArray()))

        val nearby = Probe.nearest(file, 47.3751, 8.5395, radiusM = 300.0, limit = 5)

        assertEquals(listOf(RoadClass.TOWN, RoadClass.FAST), nearby.map { it.roadClass })
        assertEquals(11.1, nearby[0].distanceM, 0.2)
        assertEquals(44.5, nearby[1].distanceM, 0.2)
        assertEquals(90.0, nearby[0].bearingDeg, 0.5)
    }
}
