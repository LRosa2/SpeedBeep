package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class DriveRecorderTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `a drive is written as a replayable file named after its start`() {
        val zurich = ZoneId.of("Europe/Zurich")
        val wall = ZonedDateTime.of(2026, 11, 25, 14, 22, 5, 0, zurich).toInstant().toEpochMilli()
        val fixes = listOf(8.0, 8.0001, 8.0002).mapIndexed { i, lon -> Fix(9_000L + i * 1_000, 47.0, lon, 16.7f, 0.5f, 4f, 90f) }
        val recorder = DriveRecorder(dir, zurich)
        fixes.forEach { recorder.record(it, wall) }
        recorder.close()
        val file = recorder.file!!
        assertEquals("drive-20261125-142205.csv", file.name)
        assertEquals(fixes.map { it.copy(elapsedMs = it.elapsedMs - 9_000) }, DriveCsv.parse(file.readText()))
    }

    @Test
    fun `the latest drive is found by its name`() {
        File(dir, "drive-20261124-080000.csv").writeText("")
        File(dir, "drive-20261125-080000.csv").writeText("")
        File(dir, "notes.txt").writeText("")
        assertEquals("drive-20261125-080000.csv", DriveRecorder.latest(dir)!!.name)
    }
}
