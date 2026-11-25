package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.cos

class RoadMatcherTest {
    // 47.37 N / 8.54 E is a cell corner, so these layouts also exercise cell edges.
    private val lat0 = 47.37
    private val lon0 = 8.54
    private val metresPerDegLon = Geo.M_PER_DEG * cos(Math.toRadians(lat0))

    private fun latOf(northM: Double) = lat0 + northM / Geo.M_PER_DEG
    private fun lonOf(eastM: Double) = lon0 + eastM / metresPerDegLon

    /** A road through (north, east) points given in metres from (lat0, lon0). */
    private fun road(roadClass: RoadClass, vararg points: Pair<Double, Double>) = Polyline(
        roadClass,
        IntArray(points.size) { Math.round(latOf(points[it].first) * 1e6).toInt() },
        IntArray(points.size) { Math.round(lonOf(points[it].second) * 1e6).toInt() },
    )

    private fun matcherOf(vararg roads: Polyline): RoadMatcher {
        val out = ByteArrayOutputStream()
        RoadFileWriter.write(roads.toList(), 0, 0, out)
        return RoadMatcher(RoadFile.parse(ByteBuffer.wrap(out.toByteArray())))
    }

    private fun RoadMatcher.at(northM: Double, eastM: Double, bearingDeg: Double?, speedKmh: Double = 60.0) =
        classify(latOf(northM), lonOf(eastM), bearingDeg, speedKmh)

    private val townEastWest = road(RoadClass.TOWN, 0.0 to -500.0, 0.0 to 500.0)
    private val motorwayEastWest = road(RoadClass.FAST, 0.0 to -500.0, 0.0 to 500.0)
    private val motorwayNorthSouth = road(RoadClass.FAST, -500.0 to 0.0, 500.0 to 0.0)

    @Test
    fun `on a town road`() {
        assertEquals(RoadMatch.TOWN, matcherOf(townEastWest).at(0.0, 0.0, 90.0))
    }

    @Test
    fun `on a motorway`() {
        assertEquals(RoadMatch.FAST, matcherOf(motorwayEastWest).at(0.0, 0.0, 90.0))
    }

    @Test
    fun `beside a parallel motorway 25 m away, the nearer road wins`() {
        val matcher = matcherOf(townEastWest, road(RoadClass.FAST, 25.0 to -500.0, 25.0 to 500.0))
        assertEquals(RoadMatch.TOWN, matcher.at(0.0, 0.0, 90.0))
        assertEquals(RoadMatch.TOWN, matcher.at(11.0, 0.0, 90.0))
        assertEquals(RoadMatch.FAST, matcher.at(14.0, 0.0, 90.0))
        assertEquals(RoadMatch.FAST, matcher.at(25.0, 0.0, 90.0))
    }

    @Test
    fun `under a motorway bridge, the heading decides`() {
        val matcher = matcherOf(townEastWest, motorwayNorthSouth)
        assertEquals(RoadMatch.TOWN, matcher.at(0.0, 0.0, 90.0))
        assertEquals(RoadMatch.TOWN, matcher.at(0.0, 0.0, 270.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, 0.0, 0.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, 0.0, 180.0))
    }

    @Test
    fun `below 15 km per h or without a bearing the heading is not checked`() {
        val matcher = matcherOf(townEastWest, motorwayNorthSouth)
        // On the motorway 5 m north of the crossing, heading east (across it).
        assertEquals(RoadMatch.TOWN, matcher.at(5.0, 0.0, 90.0, speedKmh = 60.0))
        assertEquals(RoadMatch.FAST, matcher.at(5.0, 0.0, 90.0, speedKmh = 10.0))
        assertEquals(RoadMatch.FAST, matcher.at(5.0, 0.0, null))
    }

    @Test
    fun `on an exit ramp and then on the town road it leads to`() {
        val matcher = matcherOf(
            road(RoadClass.FAST, -1000.0 to 0.0, 1000.0 to 0.0),
            road(RoadClass.FAST, 0.0 to 0.0, 300.0 to 60.0),
            road(RoadClass.TOWN, 300.0 to 60.0, 300.0 to 600.0),
        )
        assertEquals(RoadMatch.FAST, matcher.at(150.0, 30.0, 11.0))
        assertEquals(RoadMatch.TOWN, matcher.at(300.0, 300.0, 90.0))
    }

    @Test
    fun `a road crossing a cell edge matches on both sides`() {
        val matcher = matcherOf(road(RoadClass.FAST, 0.0 to -300.0, 0.0 to 300.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, -5.0, 90.0))
        assertEquals(RoadMatch.FAST, matcher.at(0.0, 5.0, 90.0))
    }

    @Test
    fun `at equal distance the town road wins (when in doubt, beep)`() {
        assertEquals(RoadMatch.TOWN, matcherOf(townEastWest, motorwayEastWest).at(0.0, 0.0, 90.0))
        assertEquals(RoadMatch.TOWN, matcherOf(motorwayEastWest, townEastWest).at(0.0, 0.0, 90.0))
    }

    @Test
    fun `no road within 20 m in a covered cell is UNKNOWN`() {
        assertEquals(RoadMatch.UNKNOWN, matcherOf(townEastWest).at(50.0, 0.0, 90.0))
    }

    @Test
    fun `outside the grid, or south-west of its edge, is NOT_COVERED`() {
        val matcher = matcherOf(townEastWest)
        assertEquals(RoadMatch.NOT_COVERED, matcher.at(-40.0, 0.0, 90.0))
        assertEquals(RoadMatch.NOT_COVERED, matcher.classify(46.0, 7.0, 90.0, 60.0))
        assertEquals(RoadMatch.NOT_COVERED, matcher.classify(-33.9, 151.2, null, 0.0))
    }

    @Test
    fun `without a road file everything is NOT_COVERED`() {
        assertEquals(RoadMatch.NOT_COVERED, NoRoadData.classify(lat0, lon0, 90.0, 60.0))
    }
}
