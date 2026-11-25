package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `report adds the built-up split when areas were measured`() {
        val stats = RoadStats()
        stats.add(RoadClass.TOWN, "primary", hasLimit = false, km = 300.0)
        stats.builtUpAreas = 1_234
        stats.unclosedRings = 5
        stats.addPosition("primary", RoadStats.Group.NO_LIMIT, PositionKm(inside = 100.0, near = 20.0, outside = 180.0))
        stats.addPosition("secondary", RoadStats.Group.LIMIT_50_OR_LESS, PositionKm(400.0, 50.0, 50.0))
        stats.addPosition("tertiary", RoadStats.Group.LIMIT_ABOVE_50, PositionKm(30.0, 20.0, 950.0))

        val lines = stats.format(fileBytes = 1_000_000, dataTimestampSec = 1_795_478_400).lines()
        fun tokens(prefix: String) = lines.single { it.trimStart().startsWith(prefix) }.trim().split(Regex("\\s+"))

        assertEquals(listOf("Built-up", "areas:", "1,234"), tokens("Built-up areas").take(3))
        assertTrue(lines.any { it.contains("5 unclosed rings dropped") })
        assertEquals(listOf("no", "speed", "information", "100", "20", "180", "60.0", "%"), tokens("no speed information"))
        assertEquals(listOf("tagged", "50", "or", "less", "400", "50", "50", "10.0", "%"), tokens("tagged 50 or less"))
        assertEquals(listOf("tagged", "above", "50", "30", "20", "950", "95.0", "%"), tokens("tagged above 50"))
        assertTrue(tokens("No speed information outside").joinToString(" ").endsWith("primary 180 km"))
    }

    @Test
    fun `report has no built-up section when areas were not measured`() {
        val stats = RoadStats()
        stats.add(RoadClass.TOWN, "primary", hasLimit = false, km = 300.0)
        assertFalse(stats.format(fileBytes = 1_000_000, dataTimestampSec = 0).contains("Built-up"))
    }
}
