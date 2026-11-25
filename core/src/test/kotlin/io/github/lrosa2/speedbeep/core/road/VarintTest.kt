package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class VarintTest {

    @Test
    fun `signed values survive zigzag varint round trips`() {
        val values = intArrayOf(0, 1, -1, 63, -64, 64, 127, 128, 300, -300, Int.MAX_VALUE, Int.MIN_VALUE)
        val out = ByteArrayOutputStream()
        values.forEach { Varint.write(out, Varint.zigzag(it)) }
        val buf = ByteBuffer.wrap(out.toByteArray())
        values.forEach { assertEquals(it, Varint.unzigzag(Varint.read(buf))) }
        assertEquals(0, buf.remaining())
    }

    @Test
    fun `a truncated varint is a format error`() {
        val out = ByteArrayOutputStream()
        Varint.write(out, 300) // two bytes
        val firstByteOnly = ByteBuffer.wrap(out.toByteArray(), 0, 1)
        assertThrows<RoadFileFormatException> { Varint.read(firstByteOnly) }
    }

    @Test
    fun `an over-long varint is a format error`() {
        val sixContinuationBytes = ByteBuffer.wrap(ByteArray(6) { 0x80.toByte() })
        assertThrows<RoadFileFormatException> { Varint.read(sixContinuationBytes) }
    }
}
