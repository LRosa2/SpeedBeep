package io.github.lrosa2.speedbeep.engine

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A plain-text log the owner can share after a drive (spec §9 step 5). It records what the
 * app decided, never positions. Thread-safe. When it grows past [maxBytes] the older half is
 * dropped, at a line boundary.
 */
class EventLog(
    private val file: File,
    private val maxBytes: Long = MAX_BYTES,
    zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_BYTES = 256L * 1024
    }

    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT).withZone(zone)

    @Synchronized
    fun log(message: String) {
        file.parentFile?.mkdirs()
        file.appendText("${time.format(Instant.ofEpochMilli(clock()))} $message\n")
        if (file.length() > maxBytes) trim()
    }

    @Synchronized
    fun read(): String = if (file.isFile) file.readText() else ""

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun trim() {
        val text = file.readText()
        val from = (text.length - (maxBytes / 2).toInt()).coerceAtLeast(0)
        val cut = text.indexOf('\n', from)
        file.writeText(if (cut < 0) "" else text.substring(cut + 1))
    }
}
