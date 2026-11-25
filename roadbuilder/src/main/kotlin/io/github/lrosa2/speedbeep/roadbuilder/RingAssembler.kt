package io.github.lrosa2.speedbeep.roadbuilder

/** Joins the member ways of a multipolygon end to end into closed rings of node ids. */
object RingAssembler {

    class Result(val rings: List<LongArray>, val unclosed: Int)

    fun assemble(members: List<LongArray>): Result {
        val rings = ArrayList<LongArray>()
        var unclosed = 0
        val used = BooleanArray(members.size)
        // Endpoint node id -> indices of the members that start or end there.
        val byEnd = HashMap<Long, MutableList<Int>>()
        for ((i, m) in members.withIndex()) {
            if (m.size < 2) {
                used[i] = true
                continue
            }
            byEnd.getOrPut(m.first()) { ArrayList(2) }.add(i)
            byEnd.getOrPut(m.last()) { ArrayList(2) }.add(i)
        }
        for (start in members.indices) {
            if (used[start]) continue
            used[start] = true
            val chain = ArrayList<Long>()
            members[start].forEach { chain += it }
            while (chain.first() != chain.last()) {
                val next = byEnd[chain.last()]?.firstOrNull { !used[it] } ?: break
                used[next] = true
                val m = members[next]
                val oriented = if (m.first() == chain.last()) m else m.reversedArray()
                for (k in 1 until oriented.size) chain += oriented[k]
            }
            if (chain.size >= 4 && chain.first() == chain.last()) rings += chain.toLongArray() else unclosed++
        }
        return Result(rings, unclosed)
    }
}
