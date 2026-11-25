package io.github.lrosa2.speedbeep.core.road

/** One road, or one piece of a road inside a grid cell. Coordinates in micro-degrees (1e-6°). */
class Polyline(val roadClass: RoadClass, val latE6: IntArray, val lonE6: IntArray) {
    init {
        require(latE6.size == lonE6.size) { "latitude and longitude counts differ" }
        require(latE6.size >= 2) { "a polyline needs at least 2 points" }
    }

    val size: Int get() = latE6.size

    /** Points [from, toExclusive) as a new polyline of the same class. */
    fun slice(from: Int, toExclusive: Int) =
        Polyline(roadClass, latE6.copyOfRange(from, toExclusive), lonE6.copyOfRange(from, toExclusive))

    override fun equals(other: Any?) =
        other is Polyline && other.roadClass == roadClass &&
            other.latE6.contentEquals(latE6) && other.lonE6.contentEquals(lonE6)

    override fun hashCode() = 31 * (31 * roadClass.hashCode() + latE6.contentHashCode()) + lonE6.contentHashCode()

    override fun toString() = "Polyline($roadClass, ${(0 until size).joinToString { "${latE6[it]}/${lonE6[it]}" }})"
}
