package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import java.util.Locale

/** One raw fix per line; times relative to the first fix. Empty field = not reported. */
object DriveCsv {
    const val HEADER = "# SpeedBeep drive v1"
    const val COLUMNS = "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg"

    fun line(fix: Fix, startMs: Long): String = listOf(
        (fix.elapsedMs - startMs).toString(),
        "%.7f".format(Locale.ROOT, fix.lat),
        "%.7f".format(Locale.ROOT, fix.lon),
        fix.speedMps.field(),
        fix.speedAccuracyMps.field(),
        fix.horizontalAccuracyM.field(),
        fix.bearingDeg.field(),
    ).joinToString(",")

    fun parse(text: String): List<Fix> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("t_ms") }
        .mapIndexed { i, l -> parseLine(l, i + 1) }
        .toList()

    private fun parseLine(line: String, record: Int): Fix {
        val f = line.split(',')
        require(f.size == 7) { "record $record: expected 7 fields, got ${f.size}" }
        return Fix(f[0].toLong(), f[1].toDouble(), f[2].toDouble(), f[3].optional(), f[4].optional(), f[5].optional(), f[6].optional())
    }

    private fun Float?.field() = this?.toString() ?: ""

    private fun String.optional() = trim().takeIf { it.isNotEmpty() }?.toFloat()
}
