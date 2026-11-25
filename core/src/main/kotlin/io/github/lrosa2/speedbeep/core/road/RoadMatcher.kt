package io.github.lrosa2.speedbeep.core.road

/** What the road data says about one GPS reading (spec §4.2). */
enum class RoadMatch {
    FAST,
    TOWN,

    /** Inside the covered area, but no road within 20 m running the same way. */
    UNKNOWN,

    /** Outside the road file, or no road file at all. */
    NOT_COVERED,

    /** Position too inaccurate to match. Set by FixEvaluator, never returned by a RoadLookup. */
    NEUTRAL,
}

fun interface RoadLookup {
    /** [bearingDeg] is null when unknown. The heading is only compared at [speedKmh] ≥ 15. */
    fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double): RoadMatch
}

/** Stand-in when the road file is missing or unreadable: everything is NOT_COVERED, so the rules act as in simple mode. */
object NoRoadData : RoadLookup {
    override fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double) = RoadMatch.NOT_COVERED
}

/** Matches a position to the nearest road segment (spec §6.4). Not thread-safe, like [RoadFile]. */
class RoadMatcher(private val file: RoadFile) : RoadLookup {

    companion object {
        const val MAX_DISTANCE_M = 20.0
        const val MAX_ANGLE_DEG = 30.0
        const val HEADING_MIN_KMH = 15.0
        const val SEARCH_RADIUS_M = 30.0
    }

    override fun classify(lat: Double, lon: Double, bearingDeg: Double?, speedKmh: Double): RoadMatch {
        val cells = file.cellsAround(lat, lon, SEARCH_RADIUS_M)
        if (cells.isEmpty()) return RoadMatch.NOT_COVERED
        val heading = if (bearingDeg != null && speedKmh >= HEADING_MIN_KMH) bearingDeg else null
        var best: RoadClass? = null
        var bestDistance = MAX_DISTANCE_M
        for (cell in cells) {
            for (p in file.polylines(cell)) {
                for (i in 0 until p.size - 1) {
                    val aLat = p.latE6[i] / 1e6
                    val aLon = p.lonE6[i] / 1e6
                    val bLat = p.latE6[i + 1] / 1e6
                    val bLon = p.lonE6[i + 1] / 1e6
                    if (aLat == bLat && aLon == bLon) continue // no direction; its neighbours cover the point
                    val d = Geo.distanceToSegmentM(lat, lon, aLat, aLon, bLat, bLon)
                    if (d > bestDistance) continue
                    if (heading != null && Geo.lineAngleDiff(Geo.bearingDeg(aLat, aLon, bLat, bLon), heading) > MAX_ANGLE_DEG) continue
                    // On a tie the town road wins: when in doubt, beep (spec D10).
                    if (best == null || d < bestDistance || p.roadClass == RoadClass.TOWN) {
                        best = p.roadClass
                        bestDistance = d
                    }
                }
            }
        }
        return when (best) {
            RoadClass.FAST -> RoadMatch.FAST
            RoadClass.TOWN -> RoadMatch.TOWN
            null -> RoadMatch.UNKNOWN
        }
    }
}
