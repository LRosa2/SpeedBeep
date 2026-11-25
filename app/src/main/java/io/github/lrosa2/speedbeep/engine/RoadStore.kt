package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.road.RoadFile
import io.github.lrosa2.speedbeep.core.road.RoadFileFormatException
import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatcher
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** The app's road data. [problem] is set when the app must run in simple mode everywhere (spec §6.3). */
class LoadedRoads(val lookup: RoadLookup, val dataTimestampSec: Long?, val problem: String?)

/**
 * Copies the bundled road file into [dir] once per [versionKey], then memory-maps it
 * read-only (spec §6.3). Each version gets its own file name, so a mapped file is never
 * overwritten. Never throws: any failure gives [NoRoadData] and a [LoadedRoads.problem].
 * The first load copies 16.8 MB, so call it off the main thread.
 */
class RoadStore(private val dir: File, versionKey: String, private val openAsset: () -> InputStream) {
    companion object {
        /** Asset name in the APK. */
        const val FILE_NAME = "roads-ch.bin"
        private const val PREFIX = "roads-ch-"
    }

    private val target = File(dir, PREFIX + versionKey.filter { it.isLetterOrDigit() || it == '-' } + ".bin")

    fun load(): LoadedRoads = try {
        if (!target.isFile) copy()
        val bytes = FileChannel.open(target.toPath(), StandardOpenOption.READ).use {
            it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
        }
        val file = RoadFile.parse(bytes)
        LoadedRoads(RoadMatcher(file), file.dataTimestampSec, null)
    } catch (e: RoadFileFormatException) {
        target.delete() // copy again on the next start; best effort while still mapped
        LoadedRoads(NoRoadData, null, "road file damaged (${e.message})")
    } catch (e: IOException) {
        LoadedRoads(NoRoadData, null, "road file unavailable (${e.message})")
    }

    private fun copy() {
        dir.mkdirs()
        // Copy to a temporary name first, so an interrupted copy is never taken for the real file.
        val tmp = File(dir, "$PREFIX.tmp")
        openAsset().use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        if (!tmp.renameTo(target)) throw IOException("cannot rename ${tmp.name}")
        // Older versions' copies. One still mapped elsewhere may refuse, which is harmless.
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".bin") && f != target }
            ?.forEach { it.delete() }
    }
}
