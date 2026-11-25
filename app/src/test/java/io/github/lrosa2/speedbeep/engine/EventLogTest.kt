package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class EventLogTest {
    @TempDir
    lateinit var dir: File

    private val zurich = ZoneId.of("Europe/Zurich")
    private val at = ZonedDateTime.of(2026, 11, 25, 14, 22, 5, 0, zurich).toInstant().toEpochMilli()

    private fun log(maxBytes: Long = EventLog.MAX_BYTES) = EventLog(File(dir, "events.log"), maxBytes, zurich) { at }

    @Test
    fun `each event is one line with date and time`() {
        val log = log()
        log.log("watching started (app)")
        assertEquals("11-25 14:22:05 watching started (app)\n", log.read())
    }

    @Test
    fun `a missing log reads as empty`() {
        assertEquals("", log().read())
    }

    @Test
    fun `a full log keeps its newest whole lines`() {
        val log = log(maxBytes = 1_000)
        repeat(100) { log.log("event number $it") }
        val text = log.read()
        assertTrue(text.length <= 1_000)
        assertTrue(text.endsWith("event number 99\n"))
        assertTrue(text.startsWith("11-25 14:22:05 event number "))
    }

    @Test
    fun `clear empties the log`() {
        val log = log()
        log.log("x")
        log.clear()
        assertEquals("", log.read())
    }
}
