package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoadCollectorTest {

    @Test
    fun `collects drivable ways with their node coordinates, ordered by way id`() {
        val collector = RoadCollector()
        collector.acceptWay(20, mapOf("highway" to "motorway"), listOf(3L, 4L))
        collector.acceptWay(10, mapOf("highway" to "residential"), listOf(1L, 2L, 3L))
        collector.acceptWay(30, mapOf("highway" to "footway"), listOf(1L, 4L))
        collector.finishWays()
        collector.acceptNode(1, 47.0, 8.0)
        collector.acceptNode(2, 47.001, 8.0)
        collector.acceptNode(3, 47.002, 8.0)
        collector.acceptNode(4, 47.003, 8.0000004)
        collector.acceptNode(99, 1.0, 1.0) // not needed by any road
        val result = collector.result()
        assertEquals(
            listOf(
                Polyline(RoadClass.TOWN, intArrayOf(47_000_000, 47_001_000, 47_002_000), intArrayOf(8_000_000, 8_000_000, 8_000_000)),
                Polyline(RoadClass.FAST, intArrayOf(47_002_000, 47_003_000), intArrayOf(8_000_000, 8_000_000)),
            ),
            result.roads,
        )
        assertEquals(2, result.stats.ways)
        assertEquals(0, result.stats.droppedWays)
    }

    @Test
    fun `a way with a node missing from the extract is dropped and counted`() {
        val collector = RoadCollector()
        collector.acceptWay(1, mapOf("highway" to "primary"), listOf(1L, 2L))
        collector.finishWays()
        collector.acceptNode(1, 47.0, 8.0)
        val result = collector.result()
        assertEquals(emptyList<Polyline>(), result.roads)
        assertEquals(1, result.stats.droppedWays)
    }

    @Test
    fun `ways with fewer than two nodes are ignored`() {
        val collector = RoadCollector()
        collector.acceptWay(1, mapOf("highway" to "primary"), listOf(1L))
        collector.finishWays()
        collector.acceptNode(1, 47.0, 8.0)
        assertEquals(0, collector.result().stats.ways)
    }

    @Test
    fun `main roads without speed information count toward the known gap`() {
        val collector = RoadCollector()
        collector.acceptWay(1, mapOf("highway" to "primary"), listOf(1L, 2L))
        collector.acceptWay(2, mapOf("highway" to "secondary", "maxspeed" to "80"), listOf(3L, 4L))
        collector.acceptWay(3, mapOf("highway" to "residential"), listOf(5L, 6L))
        collector.finishWays()
        // Each way is 0.009° of latitude long, about 1.002 km.
        listOf(1L to 47.0, 2L to 47.009, 3L to 46.0, 4L to 46.009, 5L to 45.0, 6L to 45.009)
            .forEach { (id, lat) -> collector.acceptNode(id, lat, 8.0) }
        val stats = collector.result().stats
        assertEquals(2.004, stats.mainRoadKm, 0.01)
        assertEquals(1.002, stats.mainRoadNoLimitKm, 0.01)
        assertEquals(1.002, stats.fastKm, 0.01)
        assertEquals(2.004, stats.townKm, 0.01)
    }
}
