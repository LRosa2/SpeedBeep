package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadClass
import java.time.Instant
import java.util.Locale

/** Totals for the build report (spec §6.1). */
class RoadStats {
    var ways = 0
        private set
    var droppedWays = 0
    var fastKm = 0.0
        private set
    var townKm = 0.0
        private set
    var mainRoadKm = 0.0
        private set
    var mainRoadNoLimitKm = 0.0
        private set

    fun add(roadClass: RoadClass, highway: String, hasLimit: Boolean, km: Double) {
        ways++
        if (roadClass == RoadClass.FAST) fastKm += km else townKm += km
        if (highway in GAP_TYPES) {
            mainRoadKm += km
            if (!hasLimit) mainRoadNoLimitKm += km
        }
    }

    fun format(fileBytes: Long, dataTimestampSec: Long): String {
        val gapPercent = if (mainRoadKm == 0.0) 0.0 else mainRoadNoLimitKm / mainRoadKm * 100
        val dataDate = Instant.ofEpochSecond(dataTimestampSec).toString().take(10)
        return buildString {
            appendLine("Roads written: %,d (dropped for missing nodes: %,d)".format(Locale.ROOT, ways, droppedWays))
            appendLine("  FAST: %,.0f km".format(Locale.ROOT, fastKm))
            appendLine("  TOWN: %,.0f km".format(Locale.ROOT, townKm))
            appendLine(
                "Known gap: %,.0f of %,.0f km of primary/secondary/tertiary/unclassified road (%.1f %%) has no speed information."
                    .format(Locale.ROOT, mainRoadNoLimitKm, mainRoadKm, gapPercent),
            )
            appendLine("  Upper bound: it includes roads inside towns, where 50 is right anyway.")
            appendLine("File: %.1f MB, OSM data from %s".format(Locale.ROOT, fileBytes / 1_000_000.0, dataDate))
        }
    }

    companion object {
        val GAP_TYPES = setOf("primary", "secondary", "tertiary", "unclassified")
    }
}
