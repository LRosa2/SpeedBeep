package io.github.lrosa2.speedbeep.core.road

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Small-distance geometry in metres and degrees. A local flat projection is exact enough
 * for the tens of metres the matcher works with.
 */
object Geo {
    /** Metres per degree of latitude (and of longitude at the equator). */
    const val M_PER_DEG = 111_320.0

    fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val metresPerDegLon = cos(Math.toRadians((aLat + bLat) / 2)) * M_PER_DEG
        return hypot((bLon - aLon) * metresPerDegLon, (bLat - aLat) * M_PER_DEG)
    }

    /** Distance from P to segment AB in metres, in a flat projection centred on P. */
    fun distanceToSegmentM(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double,
    ): Double {
        val metresPerDegLon = cos(Math.toRadians(pLat)) * M_PER_DEG
        val ax = (aLon - pLon) * metresPerDegLon
        val ay = (aLat - pLat) * M_PER_DEG
        val bx = (bLon - pLon) * metresPerDegLon
        val by = (bLat - pLat) * M_PER_DEG
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / lengthSquared
        val clamped = t.coerceIn(0.0, 1.0)
        return hypot(ax + clamped * dx, ay + clamped * dy)
    }

    /** Direction from A to B: 0 = north, 90 = east, in [0, 360). */
    fun bearingDeg(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dx = (bLon - aLon) * cos(Math.toRadians((aLat + bLat) / 2))
        val dy = bLat - aLat
        return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
    }

    /** Smallest angle between two undirected lines, in [0, 90]. */
    fun lineAngleDiff(aDeg: Double, bDeg: Double): Double {
        val d = abs(aDeg - bDeg) % 180.0
        return min(d, 180.0 - d)
    }
}
