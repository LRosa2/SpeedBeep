package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.Polyline
import io.github.lrosa2.speedbeep.core.road.RoadClass
import io.github.lrosa2.speedbeep.core.road.RoadFileWriter
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class RoadStoreTest {
    @TempDir
    lateinit var dir: File

    /** One fast road running east from 47.0 / 8.0. */
    private val roadBytes: ByteArray = ByteArrayOutputStream().also {
        val road = Polyline(RoadClass.FAST, intArrayOf(47_000_000, 47_000_000), intArrayOf(8_000_000, 8_001_000))
        RoadFileWriter.write(listOf(road), 1_700_000_000L, 1_700_000_100L, it)
    }.toByteArray()

    private var opened = 0

    private fun store(key: String, bytes: ByteArray? = roadBytes) = RoadStore(dir, key) {
        opened++
        if (bytes == null) throw FileNotFoundException("roads-ch.bin") else ByteArrayInputStream(bytes)
    }

    @Test
    fun `the first start copies the road file and matches roads`() {
        val roads = store("1-100").load()
        assertNull(roads.problem)
        assertEquals(1_700_000_000L, roads.dataTimestampSec)
        assertEquals(RoadMatch.FAST, roads.lookup.classify(47.0, 8.0005, 90.0, 50.0))
        assertEquals(1, opened)
    }

    @Test
    fun `the same version does not copy again`() {
        store("1-100").load()
        store("1-100").load()
        assertEquals(1, opened)
    }

    @Test
    fun `a new version copies again`() {
        store("1-100").load()
        store("2-200").load()
        assertEquals(2, opened)
    }

    @Test
    fun `a damaged file gives simple mode with a reason`() {
        val roads = store("1-100", "not a road file".toByteArray()).load()
        assertTrue(roads.problem!!.startsWith("road file damaged"))
        assertEquals(RoadMatch.NOT_COVERED, roads.lookup.classify(47.0, 8.0005, 90.0, 50.0))
    }

    @Test
    fun `a missing asset gives simple mode with a reason`() {
        val roads = store("1-100", bytes = null).load()
        assertTrue(roads.problem!!.startsWith("road file unavailable"))
        assertEquals(RoadMatch.NOT_COVERED, roads.lookup.classify(47.0, 8.0005, 90.0, 50.0))
    }

    @Test
    fun `an interrupted copy is not used and the next start copies again`() {
        // The asset stream fails half-way, as when the phone runs out of space.
        val failing = RoadStore(dir, "1-100") {
            object : InputStream() {
                var n = 0
                override fun read(): Int = if (n++ < 100) 1 else throw IOException("disk full")
            }
        }
        assertTrue(failing.load().problem!!.startsWith("road file unavailable"))
        assertNull(store("1-100").load().problem)
    }
}
