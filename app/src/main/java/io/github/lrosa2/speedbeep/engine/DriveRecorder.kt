package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import java.io.BufferedWriter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes every raw fix of one drive to `drive-<start time>.csv` in [dir] ("Record drives", spec §7.3). */
class DriveRecorder(private val dir: File, private val zone: ZoneId = ZoneId.systemDefault()) {
    companion object {
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun latest(dir: File): File? =
            dir.listFiles { f -> f.name.startsWith("drive-") && f.name.endsWith(".csv") }?.maxByOrNull { it.name }
    }

    private var writer: BufferedWriter? = null
    private var startMs = 0L
    private var lines = 0

    var file: File? = null
        private set

    fun record(fix: Fix, wallNowMs: Long) {
        val w = writer ?: open(fix, wallNowMs)
        w.write(DriveCsv.line(fix, startMs))
        w.newLine()
        if (++lines % 10 == 0) w.flush()
    }

    fun close() {
        writer?.close()
        writer = null
    }

    private fun open(first: Fix, wallNowMs: Long): BufferedWriter {
        dir.mkdirs()
        val f = File(dir, "drive-" + NAME_TIME.format(Instant.ofEpochMilli(wallNowMs).atZone(zone)) + ".csv")
        file = f
        startMs = first.elapsedMs
        return f.bufferedWriter().also {
            it.write(DriveCsv.HEADER)
            it.newLine()
            it.write(DriveCsv.COLUMNS)
            it.newLine()
            writer = it
        }
    }
}
