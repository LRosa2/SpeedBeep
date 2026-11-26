package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Answers each command with [answer]; null is a timeout. */
private class FakeObdLink(private val answer: (String) -> String?) : ObdLink {
    val sent = mutableListOf<String>()
    private var reply: String? = null

    override fun send(command: String) {
        sent += command
        reply = answer(command)
    }

    override fun readReply(timeoutMs: Long) = reply ?: throw ObdException("no reply to ${sent.last()} within $timeoutMs ms")

    override fun close() = Unit
}

class Elm327Test {
    private fun adapter(speed: () -> String = { "41 0D 32\r\r" }, setup: (String) -> String = { "OK\r\r" }) = FakeObdLink { c ->
        when (c) {
            "ATZ" -> "ATZ\r\r\rELM327 v1.5\r\r"
            "010D" -> speed()
            "ATDPN" -> "A6\r\r"
            else -> setup(c)
        }
    }

    @Test
    fun `a plain speed reply is read as km per h`() = assertEquals(SpeedReply.Speed(58), Elm327.parseSpeed("410D3A"))

    @Test
    fun `echo, spaces, searching and line breaks are ignored`() =
        assertEquals(SpeedReply.Speed(50), Elm327.parseSpeed("010D\rSEARCHING...\r41 0D 32 \r\r"))

    @Test
    fun `with headers left on the speed is still found`() =
        assertEquals(SpeedReply.Speed(50), Elm327.parseSpeed("7E8 03 41 0D 32\r"))

    @Test
    fun `with several control units the highest speed counts`() =
        assertEquals(SpeedReply.Speed(52), Elm327.parseSpeed("41 0D 32\r41 0D 34\r"))

    @Test
    fun `stray NUL bytes from cheap copies are ignored`() =
        assertEquals(SpeedReply.Speed(50), Elm327.parseSpeed("\u000041 0D 32\r"))

    @Test
    fun `no data and errors are told apart`() {
        assertEquals(SpeedReply.NoData, Elm327.parseSpeed("NO DATA\r\r"))
        assertTrue(Elm327.parseSpeed("SEARCHING...\rUNABLE TO CONNECT\r") is SpeedReply.Failed)
        assertTrue(Elm327.parseSpeed("?\r") is SpeedReply.Failed)
        assertTrue(Elm327.parseSpeed("") is SpeedReply.Failed)
    }

    @Test
    fun `open sets the adapter up in order and returns its banner`() {
        val link = adapter()
        val log = mutableListOf<String>()
        assertEquals("ELM327 v1.5", Elm327(link, log::add).open())
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "010D", "ATDPN"), link.sent)
        assertTrue(log.contains("adapter: first speed reply 41 0D 32"), log.toString())
    }

    @Test
    fun `a copy that refuses a setting still opens, and the refusal is logged`() {
        val log = mutableListOf<String>()
        Elm327(adapter(setup = { if (it == "ATL0") "?\r\r" else "OK\r\r" }), log::add).open()
        assertTrue(log.contains("adapter: ATL0 → ?"), log.toString())
    }

    @Test
    fun `open fails when the car does not answer`() {
        val e = assertThrows<ObdException> { Elm327(adapter(speed = { "SEARCHING...\rUNABLE TO CONNECT\r\r" })) {}.open() }
        assertTrue(e.message!!.contains("UNABLE TO CONNECT"), e.message)
    }

    @Test
    fun `open fails when the adapter is silent`() {
        assertThrows<ObdException> { Elm327(FakeObdLink { null }) {}.open() }
    }

    @Test
    fun `speed requests give km per h, null when missing, and fail after three errors in a row`() {
        val replies = ArrayDeque(listOf("41 0D 32\r", "41 0D 35\r", "NO DATA\r", "?\r", "?\r", "41 0D 36\r", "?\r", "?\r", "?\r"))
        val elm = Elm327(adapter(speed = { replies.removeFirst() })) {}
        elm.open() // takes the first reply
        assertEquals(53, elm.speedKmh())
        assertNull(elm.speedKmh()) // NO DATA
        assertNull(elm.speedKmh()) // first failure
        assertNull(elm.speedKmh()) // second failure
        assertEquals(54, elm.speedKmh()) // a reading resets the count
        assertNull(elm.speedKmh())
        assertNull(elm.speedKmh())
        assertThrows<ObdException> { elm.speedKmh() }
    }
}
