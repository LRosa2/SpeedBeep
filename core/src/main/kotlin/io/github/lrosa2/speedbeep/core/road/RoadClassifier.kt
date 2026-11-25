package io.github.lrosa2.speedbeep.core.road

/** Turns OSM way tags into a [RoadClass] (spec §6.2). Used by the road-file builder. */
object RoadClassifier {

    val DRIVABLE: Set<String> = setOf(
        "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
        "residential", "living_street", "road",
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    )

    private val FAST_WITHOUT_LIMIT = setOf("motorway", "motorway_link", "trunk", "trunk_link")

    /** Swiss implicit values (lower-case). "none", "signals" and "variable" are deliberately absent: no usable limit. */
    private val NAMED_LIMITS: Map<String, Int> = mapOf(
        "ch:urban" to 50,
        "ch:rural" to 80,
        "ch:trunk" to 100,
        "ch:motorway" to 120,
        "ch:zone30" to 30,
        "ch:30" to 30,
        "ch:zone20" to 20,
        "walk" to 20,
    )

    private val KMH = Regex("""^(\d{1,3})\s*(km/h|kmh|kph)?$""")
    private val MPH = Regex("""^(\d{1,3})\s*mph$""")

    /** One maxspeed-like value in km/h, or null for "no usable limit". Several values separated by ';' give the lowest. */
    fun parseMaxspeed(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return raw.split(';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseSingle)
            .minOrNull()
    }

    private fun parseSingle(value: String): Int? {
        KMH.matchEntire(value)?.let { return it.groupValues[1].toInt() }
        MPH.matchEntire(value)?.let { return Math.round(it.groupValues[1].toInt() * 1.609344).toInt() }
        return NAMED_LIMITS[value]
    }

    /** Highest limit that still counts as a town road. */
    private const val TOWN_MAX_KMH = 50

    /**
     * Effective limit in km/h, or null when the tags give none. The order follows spec §6.2.
     * A directional value only ever lowers the limit, never raises it (D10): the road is
     * stored once for both directions.
     */
    fun effectiveLimit(tags: Map<String, String>): Int? {
        val forward = parseMaxspeed(tags["maxspeed:forward"])
        val backward = parseMaxspeed(tags["maxspeed:backward"])
        val lowestDirectional = listOfNotNull(forward, backward).minOrNull()
        val base = parseMaxspeed(tags["maxspeed"])
            ?: (if (forward != null && backward != null) minOf(forward, backward) else null)
            ?: listOf("maxspeed:type", "zone:maxspeed", "source:maxspeed").firstNotNullOfOrNull { parseMaxspeed(tags[it]) }
        return when {
            lowestDirectional == null -> base
            base == null -> lowestDirectional.takeIf { it <= TOWN_MAX_KMH }
            else -> minOf(base, lowestDirectional)
        }
    }

    fun isDrivable(tags: Map<String, String>): Boolean {
        if (tags["highway"] !in DRIVABLE) return false
        if (tags["area"] == "yes") return false
        val closed = tags["access"] == "no" || tags["motor_vehicle"] == "no"
        return !closed || tags["motorcar"] == "yes"
    }

    /** The class of a drivable way, or null when the way does not belong in the road file. */
    fun classify(tags: Map<String, String>): RoadClass? {
        if (!isDrivable(tags)) return null
        val limit = effectiveLimit(tags)
        return when {
            limit != null && limit > TOWN_MAX_KMH -> RoadClass.FAST
            limit == null && tags["highway"] in FAST_WITHOUT_LIMIT -> RoadClass.FAST
            else -> RoadClass.TOWN
        }
    }
}
