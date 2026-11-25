package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadFile
import io.github.lrosa2.speedbeep.core.road.RoadFileWriter
import io.github.lrosa2.speedbeep.core.road.RoadMatcher
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.system.exitProcess

private val USAGE = """
    Usage:
      build <switzerland-latest.osm.pbf> <roads-ch.bin>
      probe <roads-ch.bin> <lat> <lon> [bearingDeg]
""".trimIndent()

fun main(args: Array<String>) {
    when {
        args.size == 3 && args[0] == "build" -> build(File(args[1]), File(args[2]))
        args.size in 4..5 && args[0] == "probe" ->
            probe(File(args[1]), args[2].toDouble(), args[3].toDouble(), args.getOrNull(4)?.toDouble())
        else -> {
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}

private fun build(pbf: File, out: File) {
    require(pbf.isFile) { "no such file: $pbf" }
    val collector = RoadCollector()
    println("Pass 1/2: ways")
    OsmPbfReader.readWays(pbf, collector::acceptWay)
    collector.finishWays()
    println("Pass 2/2: nodes")
    OsmPbfReader.readNodes(pbf, collector::acceptNode)
    val (roads, stats) = collector.result()
    // curl -R keeps Geofabrik's Last-Modified, so the file date is the OSM data date.
    val dataTimestampSec = pbf.lastModified() / 1000
    out.absoluteFile.parentFile.mkdirs()
    out.outputStream().buffered().use { RoadFileWriter.write(roads, dataTimestampSec, System.currentTimeMillis() / 1000, it) }
    print(stats.format(out.length(), dataTimestampSec))
}

private fun probe(bin: File, lat: Double, lon: Double, bearingDeg: Double?) {
    val file = RoadFile.parse(ByteBuffer.wrap(bin.readBytes()))
    // With a bearing, probe as a car at 50 km/h, so the heading check applies.
    val match = RoadMatcher(file).classify(lat, lon, bearingDeg, if (bearingDeg != null) 50.0 else 0.0)
    println("match: $match")
    for (road in Probe.nearest(file, lat, lon, radiusM = 300.0, limit = 5)) {
        println("  %s at %.1f m, running %.0f°".format(Locale.ROOT, road.roadClass, road.distanceM, road.bearingDeg))
    }
}
