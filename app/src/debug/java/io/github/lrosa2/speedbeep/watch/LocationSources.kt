package io.github.lrosa2.speedbeep.watch

import android.content.Context
import io.github.lrosa2.speedbeep.engine.DriveCsv
import io.github.lrosa2.speedbeep.engine.EventLog
import java.io.File

/**
 * Debug builds replay `replay.csv` from the app's external files folder when it exists
 * (spec §3, ReplaySource). Push one with
 * `adb push town-over.csv /sdcard/Android/data/io.github.lrosa2.speedbeep.debug/files/replay.csv`.
 */
object LocationSources {
    fun create(context: Context, events: EventLog): LocationSource {
        val file = File(context.getExternalFilesDir(null), "replay.csv")
        if (!file.isFile) return GpsSource(context)
        return try {
            ReplaySource(DriveCsv.parse(file.readText())).also { events.log("replaying ${file.name}: ${it.size} fixes") }
        } catch (e: IllegalArgumentException) {
            events.log("replay.csv unreadable (${e.message}); using GPS")
            GpsSource(context)
        }
    }
}
