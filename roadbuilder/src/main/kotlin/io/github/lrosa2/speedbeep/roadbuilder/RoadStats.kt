package io.github.lrosa2.speedbeep.roadbuilder

import io.github.lrosa2.speedbeep.core.road.RoadClass
import java.time.Instant
import java.util.Locale

/** Road length in km by position relative to built-up areas. */
data class PositionKm(val inside: Double, val near: Double, val outside: Double) {
    val total: Double get() = inside + near + outside

    operator fun plus(o: PositionKm) = PositionKm(inside + o.inside, near + o.near, outside + o.outside)

    companion object {
        val ZERO = PositionKm(0.0, 0.0, 0.0)
    }
}

/** Totals for the build report (spec §6.1). */
class RoadStats {
    /** Main roads grouped by what their tags say, for the built-up split and its calibration. */
    enum class Group(val label: String) {
        NO_LIMIT("no speed information"),
        LIMIT_50_OR_LESS("tagged 50 or less"),
        LIMIT_ABOVE_50("tagged above 50"),
    }

    /** Number of built-up outlines used, or null when the split was not measured. */
    var builtUpAreas: Int? = null
    var unclosedRings = 0
    private val positions = HashMap<Group, PositionKm>()
    private val noLimitOutsideByType = HashMap<String, Double>()

    fun addPosition(highway: String, group: Group, km: PositionKm) {
        positions[group] = position(group) + km
        if (group == Group.NO_LIMIT) noLimitOutsideByType[highway] = (noLimitOutsideByType[highway] ?: 0.0) + km.outside
    }

    fun position(group: Group): PositionKm = positions[group] ?: PositionKm.ZERO

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
            builtUpAreas?.let { appendBuiltUp(it) }
        }
    }

    private fun StringBuilder.appendBuiltUp(areas: Int) {
        appendLine(
            "Built-up areas: %,d from landuse=%s (%,d unclosed rings dropped)"
                .format(Locale.ROOT, areas, BuiltUpCollector.BUILT_UP_LANDUSE.sorted().joinToString("/"), unclosedRings),
        )
        val row = "  %-22s%10s%14s%10s%11s"
        appendLine(row.format(Locale.ROOT, "Main road km", "inside", "within %.0f m".format(Locale.ROOT, BuiltUpCollector.BUFFER_M), "outside", "outside %"))
        for (group in Group.entries) {
            val p = position(group)
            val pct = if (p.total == 0.0) 0.0 else p.outside / p.total * 100
            appendLine(row.format(Locale.ROOT, group.label, km(p.inside), km(p.near), km(p.outside), "%.1f %%".format(Locale.ROOT, pct)))
        }
        val byType = GAP_TYPE_ORDER.filter { (noLimitOutsideByType[it] ?: 0.0) > 0.0 }
            .joinToString(", ") { "$it ${km(noLimitOutsideByType.getValue(it))} km" }
        appendLine("  No speed information outside built-up areas, by type: $byType")
        appendLine("  Tagged roads show how well land use marks town limits: tagged 50 outside = would wrongly turn silent.")
    }

    private fun km(value: Double) = "%,.0f".format(Locale.ROOT, value)

    companion object {
        val GAP_TYPES = setOf("primary", "secondary", "tertiary", "unclassified")
        private val GAP_TYPE_ORDER = listOf("primary", "secondary", "tertiary", "unclassified")
    }
}
