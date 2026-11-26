package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.engine.CarScan.Answer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A pretend ELM327 on a pretend car. [ecus] maps "header" or "header/ext" to a control unit
 * that answers a request, or null for silence ("NO DATA").
 */
private class FakeCar(
    private val ecus: Map<String, (String) -> String?>,
    private val extendedAddressing: Boolean = true,
    private val carOn: Boolean = true,
    private val timeoutsAfter: Int = Int.MAX_VALUE,
) : ObdLink {
    /** Every command with the control unit it went to ("-" for adapter commands). */
    val sent = mutableListOf<Pair<String, String>>()
    private var header = "7DF"
    private var ext: String? = null
    private var reply = ""

    override fun send(command: String) {
        val target = if (command.startsWith("AT")) "-" else ext?.let { "$header/$it" } ?: header
        sent += target to command
        reply = when {
            command == "ATZ" || command == "ATI" -> "ELM327 v1.5\r\r"
            command == "ATRV" -> "12.4V\r\r"
            command == "ATDPN" -> "A6\r\r"
            command.startsWith("ATSH ") -> "OK\r\r".also { header = command.substringAfter(' ') }
            command == "ATCEA" -> "OK\r\r".also { ext = null }
            command.startsWith("ATCEA ") -> if (extendedAddressing) "OK\r\r".also { ext = command.substringAfter(' ') } else "?\r\r"
            command.startsWith("AT") -> "OK\r\r"
            command == "01 00" -> if (carOn) "7E8 06 41 00 BE 3E B8 11 \r\r" else "NO DATA\r\r"
            else -> ecus[target]?.invoke(command) ?: "NO DATA\r\r"
        }
    }

    override fun readReply(timeoutMs: Long): String {
        if (sent.size > timeoutsAfter) throw ObdException("no reply within $timeoutMs ms")
        return reply
    }

    override fun close() = Unit
}

class CarScanTest {
    /** Engine ECU speaking Toyota's mode 21; only local identifier 01 holds data. */
    private val engine: (String) -> String? = { r -> if (r == "21 01") "7E8 03 61 01 00 \r\r" else "7E8 03 7F 21 31 \r\r" }

    /** A tyre-pressure ECU behind the gateway at 750, sub-address A7, with its pressures at 21 30. */
    private val tyres: (String) -> String? = { r ->
        if (r == "21 30") "758 A7 10 0B 61 30 00 9C 00 \r758 A7 21 90 00 8D 00 9A \r\r" else "758 A7 03 7F 21 12 \r\r"
    }

    /** A control unit that knows only the newer service 22. */
    private val udsOnly: (String) -> String? = { r ->
        when {
            r.startsWith("21") -> "7B8 03 7F 21 11 \r\r"
            r == "22 10 05" -> "7B8 05 62 10 05 9C 90 \r\r"
            else -> "7B8 03 7F 22 31 \r\r"
        }
    }

    private val car = mapOf("7E0" to engine, "750/A7" to tyres, "7B0" to udsOnly)

    private fun scan(link: ObdLink, cancelled: () -> Boolean = { false }, clock: () -> Long = { 0L }): Pair<CarScan.Result, List<String>> {
        val out = mutableListOf<String>()
        val result = CarScan(link, out::add, cancelled = cancelled, clock = clock).run()
        return result to out
    }

    @Test
    fun `only read-only requests are ever sent`() {
        val link = FakeCar(car)
        scan(link)
        val requests = link.sent.map { it.second }.filterNot { it.startsWith("AT") }
        assertTrue(requests.size > 500)
        assertEquals(setOf("01", "21", "22"), requests.map { it.take(2) }.toSet())
        assertTrue(link.sent.all { CarScan.isAllowed(it.second) })
    }

    @Test
    fun `commands that could change the car are refused`() {
        for (c in listOf("04", "14 FF FF FF", "10 03", "11 01", "27 01", "2E 10 05 00", "2F 01", "31 01", "3B 01", "09 02", "ATMA", "ATPP 00 SV 01")) {
            assertFalse(CarScan.isAllowed(c), c)
        }
        for (c in listOf("01 00", "21 01", "22 10 05", "ATSH 750", "ATCEA A7", "ATCEA", "ATCRA 758", "ATST 32", "ATZ")) {
            assertTrue(CarScan.isAllowed(c), c)
        }
    }

    @Test
    fun `the scan finds the control units that answer and reads all their data`() {
        val link = FakeCar(car)
        val (result, out) = scan(link)
        assertNull(result.stoppedBy)
        assertEquals(setOf("7E0", "750/A7", "7B0"), result.responders.toSet())
        val to = link.sent.groupBy({ it.first }, { it.second })
        assertEquals((1..0xFF).map { "21 %02X".format(it) }, to.getValue("7E0"))
        assertTrue(to.getValue("750/A7").containsAll((1..0xFF).map { "21 %02X".format(it) }))
        assertEquals(listOf("21 01") + (0..0x3F).map { "22 10 %02X".format(it) }, to.getValue("7B0"))
        assertEquals(listOf("21 01"), to.getValue("7C0"))
        // The raw reply is kept, line by line, right after its request.
        val section = out.indexOf("## Data of 750/A7")
        val at = section + out.drop(section).indexOf("> 21 30")
        assertEquals("< 758 A7 10 0B 61 30 00 9C 00", out[at + 1])
        assertEquals("< 758 A7 21 90 00 8D 00 9A", out[at + 2])
    }

    @Test
    fun `an adapter without extended addressing skips the sub-addresses and still scans the rest`() {
        val link = FakeCar(car, extendedAddressing = false)
        val (result, out) = scan(link)
        assertEquals(setOf("7E0", "7B0"), result.responders.toSet())
        assertTrue(link.sent.none { it.first.contains('/') })
        assertTrue(out.any { "extended addressing" in it })
    }

    @Test
    fun `a car that does not answer ends the scan after the setup`() {
        val link = FakeCar(car, carOn = false)
        val (result, _) = scan(link)
        assertTrue(result.stoppedBy!!.contains("does not answer"))
        assertTrue(link.sent.none { it.second.startsWith("ATSH") })
    }

    @Test
    fun `three timeouts in a row end the scan`() {
        val link = FakeCar(car, timeoutsAfter = 40)
        val (result, _) = scan(link)
        assertTrue(result.stoppedBy!!.contains("stopped answering"))
        assertEquals(43, link.sent.size)
    }

    @Test
    fun `the owner can stop the scan`() {
        val link = FakeCar(car)
        val (result, _) = scan(link, cancelled = { link.sent.size >= 30 })
        assertEquals("stopped by the owner", result.stoppedBy)
        assertEquals(30, link.sent.size)
    }

    @Test
    fun `the scan stops at its time limit`() {
        val link = FakeCar(car)
        val (result, _) = scan(link, clock = { link.sent.size * 1_000L })
        assertTrue(result.stoppedBy!!.contains("time limit"))
        assertEquals(CarScan.TIME_LIMIT_MS / 1_000, link.sent.size.toLong())
    }

    @Test
    fun `replies are sorted into data, refusals and silence`() {
        assertEquals(Answer.POSITIVE, CarScan.classify("7E8 10 14 61 01 00 00\r7E8 21 00 00\r", "21 01"))
        assertEquals(Answer.POSITIVE, CarScan.classify("758 2A 05 62 10 05 9C 90", "22 10 05"))
        assertEquals(Answer.NOT_SUPPORTED, CarScan.classify("7B8 03 7F 21 11", "21 01"))
        assertEquals(Answer.REJECTED, CarScan.classify("7E8 03 7F 21 31", "21 01"))
        assertEquals(Answer.NONE, CarScan.classify("NO DATA", "21 01"))
        assertEquals(Answer.NONE, CarScan.classify("?", "21 01"))
        assertEquals(Answer.NONE, CarScan.classify("CAN ERROR", "21 01"))
    }
}
