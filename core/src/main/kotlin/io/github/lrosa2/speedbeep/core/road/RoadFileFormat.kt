package io.github.lrosa2.speedbeep.core.road

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** The road file is unreadable: wrong magic or version, truncated, or inconsistent. */
class RoadFileFormatException(message: String) : RuntimeException(message)

/**
 * Road file version 1 (spec §6.3), little-endian:
 *
 * ```
 * header (48 bytes): magic i32 "SBRD" | version u16 | reserved u16 | dataTimestampSec i64 |
 *                    buildTimestampSec i64 | originLatE6 i32 | originLonE6 i32 | cellSizeE6 i32 |
 *                    gridWidth i32 | gridHeight i32 | cellCount i32
 * index: cellCount × (cellId i32 | offset i32 from file start | length i32), sorted by cellId
 * cell payload: varint polylineCount, then per polyline:
 *               varint classCode | varint pointCount | pointCount × (zigzag-varint dLatE6, zigzag-varint dLonE6)
 *               deltas start from the cell's south-west corner, then from the previous point
 * ```
 */
object RoadFileFormat {
    /** "SBRD" in file byte order, read as a little-endian Int. */
    const val MAGIC = 0x44524253
    const val VERSION = 1
    const val HEADER_SIZE = 48
    const val INDEX_ENTRY_SIZE = 12
    const val DEFAULT_CELL_SIZE_E6 = 10_000
}

/** Cell grid: row 0 at the south edge, column 0 at the west edge, ids row-major. */
class Grid(
    val originLatE6: Int,
    val originLonE6: Int,
    val cellSizeE6: Int,
    val width: Int,
    val height: Int,
) {
    fun row(latE6: Int): Int = Math.floorDiv(latE6 - originLatE6, cellSizeE6)
    fun col(lonE6: Int): Int = Math.floorDiv(lonE6 - originLonE6, cellSizeE6)
    fun contains(row: Int, col: Int): Boolean = row in 0 until height && col in 0 until width
    fun cellId(row: Int, col: Int): Int = row * width + col
    fun cellOriginLatE6(cellId: Int): Int = originLatE6 + (cellId / width) * cellSizeE6
    fun cellOriginLonE6(cellId: Int): Int = originLonE6 + (cellId % width) * cellSizeE6
}

/** Unsigned LEB128 varints plus zigzag for signed deltas. */
internal object Varint {
    fun zigzag(v: Int): Int = (v shl 1) xor (v shr 31)
    fun unzigzag(v: Int): Int = (v ushr 1) xor -(v and 1)

    fun write(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while ((v and 0x7F.inv()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }

    fun read(buf: ByteBuffer): Int {
        var result = 0
        var shift = 0
        while (true) {
            if (shift > 28) throw RoadFileFormatException("varint longer than 5 bytes")
            if (!buf.hasRemaining()) throw RoadFileFormatException("truncated varint")
            val b = buf.get().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
    }
}
