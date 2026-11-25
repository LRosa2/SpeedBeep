package io.github.lrosa2.speedbeep.core.road

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/**
 * Read-only view of a road file. [parse] checks the header and index. A cell's payload is
 * checked when it is first decoded. The 32 most recently used cells stay decoded.
 * Not thread-safe: use one instance from one thread.
 */
class RoadFile private constructor(
    private val buf: ByteBuffer,
    val dataTimestampSec: Long,
    val buildTimestampSec: Long,
    val grid: Grid,
    private val cellIds: IntArray,
    private val offsets: IntArray,
    private val lengths: IntArray,
) {
    val cellCount: Int get() = cellIds.size

    private val cache = object : LinkedHashMap<Int, List<Polyline>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<Polyline>>?) = size > 32
    }

    fun hasCell(cellId: Int): Boolean = cellIds.binarySearch(cellId) >= 0

    /** Existing cells overlapping the box of ±[radiusM] around the point. Empty means not covered. */
    fun cellsAround(lat: Double, lon: Double, radiusM: Double): IntArray {
        val dLatE6 = radiusM / Geo.M_PER_DEG * 1e6
        val dLonE6 = radiusM / (Geo.M_PER_DEG * cos(Math.toRadians(lat))) * 1e6
        val latE6 = lat * 1e6
        val lonE6 = lon * 1e6
        // Clamp to the grid first, so far-away positions cost nothing and never overflow.
        val rowFrom = maxOf(grid.row(floor(latE6 - dLatE6).toInt()), 0)
        val rowTo = minOf(grid.row(ceil(latE6 + dLatE6).toInt()), grid.height - 1)
        val colFrom = maxOf(grid.col(floor(lonE6 - dLonE6).toInt()), 0)
        val colTo = minOf(grid.col(ceil(lonE6 + dLonE6).toInt()), grid.width - 1)
        val found = ArrayList<Int>(4)
        for (row in rowFrom..rowTo) {
            for (col in colFrom..colTo) {
                val id = grid.cellId(row, col)
                if (hasCell(id)) found += id
            }
        }
        return found.toIntArray()
    }

    fun polylines(cellId: Int): List<Polyline> {
        cache[cellId]?.let { return it }
        val index = cellIds.binarySearch(cellId)
        if (index < 0) return emptyList()
        val decoded = decodeCell(cellId, offsets[index], lengths[index])
        cache[cellId] = decoded
        return decoded
    }

    private fun decodeCell(cellId: Int, offset: Int, length: Int): List<Polyline> {
        val b = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        b.limit(offset + length)
        b.position(offset)
        val count = Varint.read(b)
        if (count < 0 || count > length) throw RoadFileFormatException("cell $cellId: bad polyline count $count")
        val originLat = grid.cellOriginLatE6(cellId)
        val originLon = grid.cellOriginLonE6(cellId)
        val result = ArrayList<Polyline>(count)
        repeat(count) {
            val roadClass = RoadClass.fromCode(Varint.read(b))
            val points = Varint.read(b)
            if (points < 2 || points.toLong() * 2 > b.remaining()) {
                throw RoadFileFormatException("cell $cellId: bad point count $points")
            }
            val lat = IntArray(points)
            val lon = IntArray(points)
            var prevLat = originLat
            var prevLon = originLon
            for (i in 0 until points) {
                prevLat += Varint.unzigzag(Varint.read(b))
                prevLon += Varint.unzigzag(Varint.read(b))
                lat[i] = prevLat
                lon[i] = prevLon
            }
            result += Polyline(roadClass, lat, lon)
        }
        if (b.hasRemaining()) throw RoadFileFormatException("cell $cellId: ${b.remaining()} trailing bytes")
        return result
    }

    companion object {
        fun parse(bytes: ByteBuffer): RoadFile {
            val b = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val size = b.limit()
            if (size < RoadFileFormat.HEADER_SIZE) throw RoadFileFormatException("file too short: $size bytes")
            if (b.getInt(0) != RoadFileFormat.MAGIC) throw RoadFileFormatException("not a road file (bad magic)")
            val version = b.getShort(4).toInt()
            if (version != RoadFileFormat.VERSION) throw RoadFileFormatException("unsupported road file version $version")
            val grid = Grid(b.getInt(24), b.getInt(28), b.getInt(32), b.getInt(36), b.getInt(40))
            if (grid.cellSizeE6 <= 0 || grid.width <= 0 || grid.height <= 0) throw RoadFileFormatException("bad grid")
            val cellLimit = grid.width.toLong() * grid.height
            if (cellLimit > Int.MAX_VALUE) throw RoadFileFormatException("grid too large")
            val count = b.getInt(44)
            val indexEnd = RoadFileFormat.HEADER_SIZE.toLong() + count.toLong() * RoadFileFormat.INDEX_ENTRY_SIZE
            if (count < 0 || indexEnd > size) throw RoadFileFormatException("index truncated")
            val ids = IntArray(count)
            val offsets = IntArray(count)
            val lengths = IntArray(count)
            for (i in 0 until count) {
                val at = RoadFileFormat.HEADER_SIZE + i * RoadFileFormat.INDEX_ENTRY_SIZE
                val id = b.getInt(at)
                val offset = b.getInt(at + 4)
                val length = b.getInt(at + 8)
                if (id < 0 || id >= cellLimit) throw RoadFileFormatException("cell id $id outside the grid")
                if (i > 0 && id <= ids[i - 1]) throw RoadFileFormatException("index not sorted at entry $i")
                if (offset < indexEnd || length <= 0 || offset.toLong() + length > size) {
                    throw RoadFileFormatException("cell $id data lies outside the file")
                }
                ids[i] = id
                offsets[i] = offset
                lengths[i] = length
            }
            return RoadFile(b, b.getLong(8), b.getLong(16), grid, ids, offsets, lengths)
        }
    }
}
