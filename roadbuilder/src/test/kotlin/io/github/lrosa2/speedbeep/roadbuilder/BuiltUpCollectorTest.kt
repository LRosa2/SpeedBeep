package io.github.lrosa2.speedbeep.roadbuilder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuiltUpCollectorTest {

    @Test
    fun `closed land-use ways and land-use multipolygons become areas, others are ignored`() {
        val c = BuiltUpCollector()
        c.acceptRelation(100, mapOf("type" to "multipolygon", "landuse" to "residential"), listOf(21L to "outer", 22L to ""))
        c.acceptRelation(101, mapOf("type" to "multipolygon", "landuse" to "forest"), listOf(23L to "outer"))
        c.finishRelations()
        c.acceptWay(10, mapOf("landuse" to "industrial"), listOf(1L, 2L, 3L, 4L, 1L)) // square A
        c.acceptWay(11, mapOf("landuse" to "farmland"), listOf(1L, 2L, 3L, 1L))
        c.acceptWay(12, mapOf("landuse" to "retail"), listOf(1L, 2L, 3L)) // not closed
        c.acceptWay(21, emptyMap(), listOf(5L, 6L, 7L)) // square B, first half
        c.acceptWay(22, emptyMap(), listOf(7L, 8L, 5L)) // square B, second half
        c.acceptWay(23, emptyMap(), listOf(1L, 2L, 3L, 1L))
        c.finishWays()
        mapOf(
            1L to (47.0 to 8.0), 2L to (47.0 to 8.01), 3L to (47.01 to 8.01), 4L to (47.01 to 8.0),
            5L to (47.1 to 8.1), 6L to (47.1 to 8.11), 7L to (47.11 to 8.11), 8L to (47.11 to 8.1),
            99L to (1.0 to 1.0),
        ).forEach { (id, p) -> c.acceptNode(id, p.first, p.second) }

        val areas = c.result()

        assertEquals(2, areas.ringCount)
        assertEquals(0, areas.unclosedRings)
        assertEquals(Placement.INSIDE, areas.locate(47.005, 8.005))
        assertEquals(Placement.INSIDE, areas.locate(47.105, 8.105))
        assertEquals(Placement.OUTSIDE, areas.locate(47.05, 8.05))
    }

    @Test
    fun `a ring with a node missing from the extract is dropped and counted`() {
        val c = BuiltUpCollector()
        c.finishRelations()
        c.acceptWay(10, mapOf("landuse" to "residential"), listOf(1L, 2L, 3L, 4L, 1L))
        c.finishWays()
        c.acceptNode(1, 47.0, 8.0)
        c.acceptNode(2, 47.0, 8.01)
        c.acceptNode(3, 47.01, 8.01)
        val areas = c.result()
        assertEquals(0, areas.ringCount)
        assertEquals(1, areas.unclosedRings)
    }
}
