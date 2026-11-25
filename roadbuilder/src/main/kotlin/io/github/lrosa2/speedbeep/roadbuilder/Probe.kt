package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Geo
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadFile

data class NearbyRoad(val roadClass: RoadClass, val distanceM: Double, val bearingDeg: Double)

/** Debug helper for spot checks: the nearest road pieces around a point. */
object Probe {
    fun nearest(file: RoadFile, lat: Double, lon: Double, radiusM: Double, limit: Int): List<NearbyRoad> {
        val found = ArrayList<NearbyRoad>()
        for (cell in file.cellsAround(lat, lon, radiusM)) {
            for (p in file.polylines(cell)) {
                var best: NearbyRoad? = null
                for (i in 0 until p.size - 1) {
                    val aLat = p.latE6[i] / 1e6
                    val aLon = p.lonE6[i] / 1e6
                    val bLat = p.latE6[i + 1] / 1e6
                    val bLon = p.lonE6[i + 1] / 1e6
                    val d = Geo.distanceToSegmentM(lat, lon, aLat, aLon, bLat, bLon)
                    if (best == null || d < best.distanceM) best = NearbyRoad(p.roadClass, d, Geo.bearingDeg(aLat, aLon, bLat, bLon))
                }
                if (best != null && best.distanceM <= radiusM) found += best
            }
        }
        // A road split over several cells shows up once per cell; keep one of each.
        return found
            .sortedBy { it.distanceM }
            .distinctBy { Triple(it.roadClass, Math.round(it.distanceM * 10), Math.round(it.bearingDeg)) }
            .take(limit)
    }
}
