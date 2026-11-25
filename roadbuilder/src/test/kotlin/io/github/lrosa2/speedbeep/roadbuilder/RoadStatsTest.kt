package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoadStatsTest {

    @Test
    fun `report shows totals, the known gap and the file`() {
        val stats = RoadStats()
        stats.add(RoadClass.FAST, "motorway", hasLimit = true, km = 1500.0)
        stats.add(RoadClass.TOWN, "primary", hasLimit = false, km = 300.0)
        stats.add(RoadClass.FAST, "secondary", hasLimit = true, km = 700.0)
        stats.droppedWays = 2
        val expected = """
            Roads written: 3 (dropped for missing nodes: 2)
              FAST: 2,200 km
              TOWN: 300 km
            Known gap: 300 of 1,000 km of primary/secondary/tertiary/unclassified road (30.0 %) has no speed information.
              Upper bound: it includes roads inside towns, where 50 is right anyway.
            File: 21.5 MB, OSM data from 2026-11-24
        """.trimIndent() + "\n"
        assertEquals(expected, stats.format(fileBytes = 21_500_000, dataTimestampSec = 1_795_478_400))
    }
}
