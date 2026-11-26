package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
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

    /** Why recording stopped (storage full or missing), or null. Recording never fails the drive. */
    var error: String? = null
        private set

    /** [obdKmh]: the car's speed next to this GPS fix, when the adapter has a fresh reading (spec §4.5). */
    fun record(fix: Fix, wallNowMs: Long, obdKmh: Int? = null) {
        if (error != null) return
        try {
            val w = writer ?: open(fix, wallNowMs)
            w.write(DriveCsv.line(fix, startMs, obdKmh))
            w.newLine()
            if (++lines % 10 == 0) w.flush()
        } catch (e: IOException) {
            error = e.message ?: e.javaClass.simpleName
            close()
        }
    }

    fun close() {
        runCatching { writer?.close() }
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
