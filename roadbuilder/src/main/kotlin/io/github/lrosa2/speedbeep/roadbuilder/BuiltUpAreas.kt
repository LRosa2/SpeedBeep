package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.Geo
import kotlin.math.cos

enum class Placement { INSIDE, NEAR, OUTSIDE }

/** One closed outline (first point = last point), in micro-degrees. */
class Ring(val latE6: IntArray, val lonE6: IntArray) {
    init {
        require(latE6.size == lonE6.size) { "latitude and longitude counts differ" }
        require(latE6.size >= 4) { "a ring needs at least 3 distinct points" }
    }

    val minLatE6 = latE6.min()
    val maxLatE6 = latE6.max()
    val minLonE6 = lonE6.min()
    val maxLonE6 = lonE6.max()

    /** Crossing-number test in Double, since products of micro-degree Ints overflow. */
    fun contains(latE6: Double, lonE6: Double): Boolean {
        if (latE6 < minLatE6 || latE6 > maxLatE6 || lonE6 < minLonE6 || lonE6 > maxLonE6) return false
        var inside = false
        for (i in 0 until this.latE6.size - 1) {
            val aLat = this.latE6[i].toDouble()
            val aLon = this.lonE6[i].toDouble()
            val bLat = this.latE6[i + 1].toDouble()
            val bLon = this.lonE6[i + 1].toDouble()
            if ((aLat > latE6) != (bLat > latE6)) {
                val crossLon = aLon + (latE6 - aLat) / (bLat - aLat) * (bLon - aLon)
                if (lonE6 < crossLon) inside = !inside
            }
        }
        return inside
    }

    fun isWithinM(lat: Double, lon: Double, metres: Double): Boolean {
        for (i in 0 until latE6.size - 1) {
            val d = Geo.distanceToSegmentM(
                lat, lon,
                latE6[i] / 1e6, lonE6[i] / 1e6,
                latE6[i + 1] / 1e6, lonE6[i + 1] / 1e6,
            )
            if (d <= metres) return true
        }
        return false
    }
}

/**
 * Built-up areas (land-use outlines) with a grid index, for the build report only.
 * [Placement.NEAR] means outside every outline but within [bufferM] of one, which catches
 * roads drawn along or between the outlines of a village.
 */
class BuiltUpAreas(rings: List<Ring>, val bufferM: Double, val unclosedRings: Int = 0) {
    val ringCount = rings.size
    private val cells = HashMap<Long, MutableList<Ring>>()

    init {
        val dLatE6 = bufferM / Geo.M_PER_DEG * 1e6
        for (ring in rings) {
            val dLonE6 = bufferM / (Geo.M_PER_DEG * cos(Math.toRadians(ring.maxLatE6 / 1e6))) * 1e6
            val rowFrom = Math.floorDiv((ring.minLatE6 - dLatE6).toInt(), CELL_E6)
            val rowTo = Math.floorDiv((ring.maxLatE6 + dLatE6).toInt(), CELL_E6)
            val colFrom = Math.floorDiv((ring.minLonE6 - dLonE6).toInt(), CELL_E6)
            val colTo = Math.floorDiv((ring.maxLonE6 + dLonE6).toInt(), CELL_E6)
            for (row in rowFrom..rowTo) {
                for (col in colFrom..colTo) cells.getOrPut(key(row, col)) { ArrayList(2) }.add(ring)
            }
        }
    }

    fun locate(lat: Double, lon: Double): Placement {
        val latE6 = lat * 1e6
        val lonE6 = lon * 1e6
        val candidates = cells[key(Math.floorDiv(latE6.toInt(), CELL_E6), Math.floorDiv(lonE6.toInt(), CELL_E6))]
            ?: return Placement.OUTSIDE
        if (candidates.any { it.contains(latE6, lonE6) }) return Placement.INSIDE
        if (candidates.any { it.isWithinM(lat, lon, bufferM) }) return Placement.NEAR
        return Placement.OUTSIDE
    }

    private fun key(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFF_FFFFL)

    companion object {
        private const val CELL_E6 = 10_000
    }
}
