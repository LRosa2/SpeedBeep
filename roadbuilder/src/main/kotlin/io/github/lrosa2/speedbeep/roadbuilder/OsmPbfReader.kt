package io.github.lrosa2.speedbeep.roadbuilder

import com.wolt.osm.parallelpbf.ParallelBinaryParser
import java.io.File

/** Thin adapter over parallelpbf. Callbacks run on the parser's worker threads. */
object OsmPbfReader {
    // Each parser thread can hold a few hundred MB of blob data; 4 threads fit in -Xmx4g.
    private val threads = minOf(4, Runtime.getRuntime().availableProcessors())

    fun readWays(pbf: File, onWay: (id: Long, tags: Map<String, String>, nodeIds: List<Long>) -> Unit) =
        parse(pbf) { parser -> parser.onWay { way -> onWay(way.id, way.tags, way.nodes) } }

    fun readNodes(pbf: File, onNode: (id: Long, lat: Double, lon: Double) -> Unit) =
        parse(pbf) { parser -> parser.onNode { node -> onNode(node.id, node.lat, node.lon) } }

    private fun parse(pbf: File, configure: (ParallelBinaryParser) -> Unit) {
        var completed = false
        pbf.inputStream().buffered(1 shl 20).use { input ->
            val parser = ParallelBinaryParser(input, threads)
            configure(parser)
            parser.onComplete { completed = true }
            parser.parse()
        }
        // On failure parallelpbf logs the error and returns without calling onComplete.
        check(completed) { "reading $pbf failed (see the log above)" }
    }
}
