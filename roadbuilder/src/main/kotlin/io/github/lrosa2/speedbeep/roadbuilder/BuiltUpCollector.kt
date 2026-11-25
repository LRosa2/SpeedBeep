package io.github.lrosa2.speedbeep.roadbuilder

/**
 * Collects built-up land-use outlines for the report's inside/outside split. Three passes:
 * - [acceptRelation], then [finishRelations]
 * - [acceptWay], then [finishWays]
 * - [acceptNode], then [result]
 *
 * Inner rings (holes) are ignored, which counts a little more road as inside.
 * The parser calls the accept functions from several worker threads.
 */
class BuiltUpCollector(private val bufferM: Double = BUFFER_M) {

    companion object {
        val BUILT_UP_LANDUSE = setOf("residential", "commercial", "industrial", "retail")

        /** Edge band: land-use outlines often stop at the roadside, so a village road can lie between them. */
        const val BUFFER_M = 30.0
    }

    private val relations = ArrayList<LongArray>() // member way ids per relation
    private var memberWayIds = LongArray(0)
    private val memberWays = HashMap<Long, LongArray>()
    private val closedWays = ArrayList<LongArray>()

    private var nodeRings = emptyList<LongArray>()
    private var unclosed = 0
    private var neededIds = LongArray(0)
    private var latE6 = IntArray(0)
    private var lonE6 = IntArray(0)
    private var found = BooleanArray(0)

    @Synchronized
    fun acceptRelation(id: Long, tags: Map<String, String>, wayMembers: List<Pair<Long, String>>) {
        if (tags["type"] != "multipolygon" || tags["landuse"] !in BUILT_UP_LANDUSE) return
        val outer = wayMembers.filter { (_, role) -> role == "outer" || role.isEmpty() }.map { it.first }
        if (outer.isNotEmpty()) relations += outer.toLongArray()
    }

    fun finishRelations() {
        memberWayIds = relations.flatMap { it.asList() }.distinct().sorted().toLongArray()
    }

    fun acceptWay(id: Long, tags: Map<String, String>, nodeIds: List<Long>) {
        val isMember = memberWayIds.binarySearch(id) >= 0
        val isClosedArea = tags["landuse"] in BUILT_UP_LANDUSE && nodeIds.size >= 4 && nodeIds.first() == nodeIds.last()
        if (!isMember && !isClosedArea) return
        synchronized(this) {
            if (isMember) memberWays[id] = nodeIds.toLongArray()
            if (isClosedArea) closedWays += nodeIds.toLongArray()
        }
    }

    fun finishWays() {
        val rings = ArrayList<LongArray>(closedWays)
        for (members in relations) {
            // A member missing from the extract (cut at the border) leaves its ring open.
            val assembled = RingAssembler.assemble(members.asList().mapNotNull { memberWays[it] })
            rings += assembled.rings
            unclosed += assembled.unclosed
        }
        nodeRings = rings
        neededIds = rings.flatMap { it.asList() }.distinct().sorted().toLongArray()
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

    fun result(): BuiltUpAreas {
        val rings = ArrayList<Ring>(nodeRings.size)
        var dropped = unclosed
        for (ids in nodeRings) {
            val slots = IntArray(ids.size) { neededIds.binarySearch(ids[it]) }
            if (slots.any { it < 0 || !found[it] }) {
                dropped++
                continue
            }
            rings += Ring(IntArray(slots.size) { latE6[slots[it]] }, IntArray(slots.size) { lonE6[slots[it]] })
        }
        return BuiltUpAreas(rings, bufferM, dropped)
    }
}
