package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Geo
import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadClassifier
import kotlin.math.ceil

private const val SAMPLE_M = 50.0

/**
 * Two-pass collection of drivable ways:
 * - pass 1 [acceptWay]
 * - then [finishWays]
 * - pass 2 [acceptNode]
 * - then [result]
 *
 * The parser calls the accept functions from several worker threads.
 */
class RoadCollector {
    private class PendingWay(
        val id: Long,
        val roadClass: RoadClass,
        val highway: String,
        val hasLimit: Boolean,
        val nodeIds: LongArray,
    )

    private val ways = ArrayList<PendingWay>()
    private var neededIds = LongArray(0)
    private var latE6 = IntArray(0)
    private var lonE6 = IntArray(0)
    private var found = BooleanArray(0)

    @Synchronized
    fun acceptWay(id: Long, tags: Map<String, String>, nodeIds: List<Long>) {
        if (nodeIds.size < 2) return
        val roadClass = RoadClassifier.classify(tags) ?: return
        ways += PendingWay(id, roadClass, tags.getValue("highway"), RoadClassifier.effectiveLimit(tags) != null, nodeIds.toLongArray())
    }

    /** Sorts the ways (so the file is reproducible) and builds the sorted table of needed node ids. */
    fun finishWays() {
        ways.sortBy { it.id }
        val all = LongArray(ways.sumOf { it.nodeIds.size })
        var i = 0
        for (way in ways) for (nodeId in way.nodeIds) all[i++] = nodeId
        all.sort()
        neededIds = distinctSorted(all)
        latE6 = IntArray(neededIds.size)
        lonE6 = IntArray(neededIds.size)
        found = BooleanArray(neededIds.size)
    }

    /** Each needed node id owns one array slot, so concurrent calls never touch the same slot. */
    fun acceptNode(id: Long, lat: Double, lon: Double) {
        val slot = neededIds.binarySearch(id)
        if (slot < 0) return
        latE6[slot] = Math.round(lat * 1e6).toInt()
        lonE6[slot] = Math.round(lon * 1e6).toInt()
        found[slot] = true
    }

    /** With [builtUp], main-road length is also split by position for the report (roads are unchanged). */
    fun result(builtUp: BuiltUpAreas? = null): CollectResult {
        val roads = ArrayList<Polyline>(ways.size)
        val stats = RoadStats()
        if (builtUp != null) {
            stats.builtUpAreas = builtUp.ringCount
            stats.unclosedRings = builtUp.unclosedRings
        }
        for (way in ways) {
            val slots = IntArray(way.nodeIds.size) { neededIds.binarySearch(way.nodeIds[it]) }
            if (slots.any { it < 0 || !found[it] }) {
                stats.droppedWays++
                continue
            }
            val road = Polyline(way.roadClass, IntArray(slots.size) { latE6[slots[it]] }, IntArray(slots.size) { lonE6[slots[it]] })
            roads += road
            stats.add(way.roadClass, way.highway, way.hasLimit, lengthKm(road))
            if (builtUp != null && way.highway in RoadStats.GAP_TYPES) {
                val group = when {
                    !way.hasLimit -> RoadStats.Group.NO_LIMIT
                    way.roadClass == RoadClass.TOWN -> RoadStats.Group.LIMIT_50_OR_LESS
                    else -> RoadStats.Group.LIMIT_ABOVE_50
                }
                stats.addPosition(way.highway, group, positionKm(road, builtUp))
            }
        }
        return CollectResult(roads, stats)
    }

    /** Samples every segment about every [SAMPLE_M] metres, since rural segments can cross a village edge. */
    private fun positionKm(p: Polyline, areas: BuiltUpAreas): PositionKm {
        var inside = 0.0
        var near = 0.0
        var outside = 0.0
        for (i in 0 until p.size - 1) {
            val aLat = p.latE6[i] / 1e6
            val aLon = p.lonE6[i] / 1e6
            val bLat = p.latE6[i + 1] / 1e6
            val bLon = p.lonE6[i + 1] / 1e6
            val metres = Geo.distanceM(aLat, aLon, bLat, bLon)
            val pieces = maxOf(1, ceil(metres / SAMPLE_M).toInt())
            val pieceM = metres / pieces
            for (k in 0 until pieces) {
                val t = (k + 0.5) / pieces
                when (areas.locate(aLat + (bLat - aLat) * t, aLon + (bLon - aLon) * t)) {
                    Placement.INSIDE -> inside += pieceM
                    Placement.NEAR -> near += pieceM
                    Placement.OUTSIDE -> outside += pieceM
                }
            }
        }
        return PositionKm(inside / 1000, near / 1000, outside / 1000)
    }

    private fun lengthKm(p: Polyline): Double {
        var metres = 0.0
        for (i in 0 until p.size - 1) {
            metres += Geo.distanceM(p.latE6[i] / 1e6, p.lonE6[i] / 1e6, p.latE6[i + 1] / 1e6, p.lonE6[i + 1] / 1e6)
        }
        return metres / 1000
    }

    private fun distinctSorted(sorted: LongArray): LongArray {
        if (sorted.isEmpty()) return sorted
        var n = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] != sorted[n - 1]) sorted[n++] = sorted[i]
        }
        return sorted.copyOf(n)
    }
}

data class CollectResult(val roads: List<Polyline>, val stats: RoadStats)
