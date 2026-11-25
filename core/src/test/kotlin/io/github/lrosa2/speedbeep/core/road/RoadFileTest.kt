package io.github.lrosa2.speedbeep.core.road

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class RoadFileTest {

    // Town road running east along 47.375 N, crossing the cell boundary at 8.540 E.
    private val town = Polyline(
        RoadClass.TOWN,
        intArrayOf(47_375_000, 47_375_000, 47_375_000, 47_375_000),
        intArrayOf(8_535_000, 8_538_000, 8_541_000, 8_545_000),
    )

    // Motorway running north along 8.5505 E, crossing the boundaries at 47.37 and 47.38 N.
    private val motorway = Polyline(
        RoadClass.FAST,
        intArrayOf(47_360_000, 47_372_000, 47_381_000),
        intArrayOf(8_550_500, 8_550_500, 8_550_500),
    )

    private fun bytesOf(vararg roads: Polyline): ByteArray {
        val out = ByteArrayOutputStream()
        RoadFileWriter.write(roads.toList(), dataTimestampSec = 1_764_000_000, buildTimestampSec = 1_764_100_000, out = out)
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray) = RoadFile.parse(ByteBuffer.wrap(bytes))

    @Test
    fun `header round-trips`() {
        val file = parse(bytesOf(town, motorway))
        assertEquals(1_764_000_000L, file.dataTimestampSec)
        assertEquals(1_764_100_000L, file.buildTimestampSec)
        assertEquals(47_360_000, file.grid.originLatE6)
        assertEquals(8_530_000, file.grid.originLonE6)
        assertEquals(3, file.grid.width)
        assertEquals(3, file.grid.height)
        assertEquals(5, file.cellCount)
    }

    @Test
    fun `roads are cut into one piece per cell with every touching segment`() {
        val file = parse(bytesOf(town, motorway))
        // Cell ids are row * 3 + col. Town road: row 1, cols 0 and 1. Motorway: col 2, rows 0..2.
        assertEquals(listOf(town.slice(0, 3)), file.polylines(3))
        assertEquals(listOf(town.slice(1, 4)), file.polylines(4))
        assertEquals(listOf(motorway.slice(0, 2)), file.polylines(2))
        assertEquals(listOf(motorway.slice(0, 3)), file.polylines(5))
        assertEquals(listOf(motorway.slice(1, 3)), file.polylines(8))
        assertEquals(emptyList<Polyline>(), file.polylines(0))
    }

    @Test
    fun `cellsAround finds only existing cells in the search box`() {
        val file = parse(bytesOf(town, motorway))
        assertArrayEquals(intArrayOf(3), file.cellsAround(47.375, 8.5395, 30.0))
        assertArrayEquals(intArrayOf(3, 4), file.cellsAround(47.375, 8.5399, 30.0))
    }

    @Test
    fun `positions outside or at the south-west edge of the grid are not covered`() {
        val file = parse(bytesOf(town, motorway))
        assertArrayEquals(intArrayOf(), file.cellsAround(46.0, 7.0, 30.0))
        assertArrayEquals(intArrayOf(), file.cellsAround(47.3599, 8.5299, 30.0))
        assertArrayEquals(intArrayOf(), file.cellsAround(89.99, 179.9, 30.0))
    }

    @Test
    fun `writing nothing is refused`() {
        assertThrows<IllegalArgumentException> { RoadFileWriter.write(emptyList(), 0, 0, ByteArrayOutputStream()) }
    }

    @Test
    fun `a wrong magic is refused`() {
        val bytes = bytesOf(town).also { it[0] = 0 }
        assertThrows<RoadFileFormatException> { parse(bytes) }
    }

    @Test
    fun `another version is refused`() {
        val bytes = bytesOf(town).also { it[4] = 2 }
        assertThrows<RoadFileFormatException> { parse(bytes) }
    }

    @Test
    fun `truncated files are refused, never an index error`() {
        val bytes = bytesOf(town, motorway)
        for (length in listOf(0, 10, RoadFileFormat.HEADER_SIZE - 1, RoadFileFormat.HEADER_SIZE + 5, bytes.size - 1)) {
            assertThrows<RoadFileFormatException>("length $length") { parse(bytes.copyOf(length)) }
        }
    }

    @Test
    fun `a corrupt cell payload is a format error when the cell is read`() {
        val bytes = bytesOf(town, motorway)
        val firstPayload = RoadFileFormat.HEADER_SIZE + 5 * RoadFileFormat.INDEX_ENTRY_SIZE
        bytes[firstPayload] = 0x7F // claims 127 polylines in a cell of a few bytes
        val file = parse(bytes)
        assertThrows<RoadFileFormatException> { file.polylines(2) }
    }
}
