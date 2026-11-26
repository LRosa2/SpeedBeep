package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import java.util.Locale

/**
 * One GPS fix per line; times relative to the first fix. Empty field = not reported.
 * v2 adds the car's speed from the OBD adapter (spec §4.5); v1 files still read.
 */
object DriveCsv {
    const val HEADER = "# SpeedBeep drive v2"
    const val COLUMNS = "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg,obd_kmh"

    /** [obdKmh]: the car's speed when a fresh adapter reading exists, else null. */
    fun line(fix: Fix, startMs: Long, obdKmh: Int? = null): String = listOf(
        (fix.elapsedMs - startMs).toString(),
        "%.7f".format(Locale.ROOT, fix.lat),
        "%.7f".format(Locale.ROOT, fix.lon),
        fix.speedMps.field(),
        fix.speedAccuracyMps.field(),
        fix.horizontalAccuracyM.field(),
        fix.bearingDeg.field(),
        obdKmh?.toString() ?: "",
    ).joinToString(",")

    fun parse(text: String): List<Fix> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("t_ms") }
        .mapIndexed { i, l -> parseLine(l, i + 1) }
        .toList()

    private fun parseLine(line: String, record: Int): Fix {
        val f = line.split(',')
        require(f.size == 7 || f.size == 8) { "record $record: expected 7 or 8 fields, got ${f.size}" }
        return Fix(f[0].toLong(), f[1].toDouble(), f[2].toDouble(), f[3].optional(), f[4].optional(), f[5].optional(), f[6].optional())
    }

    private fun Float?.field() = this?.toString() ?: ""

    private fun String.optional() = trim().takeIf { it.isNotEmpty() }?.toFloat()
}
