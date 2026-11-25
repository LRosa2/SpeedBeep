package io.github.lrosa2.speedbeep.core.road

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes road file version 1 (layout in [RoadFileFormat]). */
object RoadFileWriter {

    fun write(
        roads: List<Polyline>,
        dataTimestampSec: Long,
        buildTimestampSec: Long,
        out: OutputStream,
        cellSizeE6: Int = RoadFileFormat.DEFAULT_CELL_SIZE_E6,
    ) {
        require(roads.isNotEmpty()) { "no roads to write" }
        val grid = gridFor(roads, cellSizeE6)
        val pieces = splitIntoCells(roads, grid)
        val cellIds = pieces.keys.sorted()
        val payloads = cellIds.map { encodeCell(grid, it, pieces.getValue(it)) }

        val indexEnd = RoadFileFormat.HEADER_SIZE + cellIds.size * RoadFileFormat.INDEX_ENTRY_SIZE
        val head = ByteBuffer.allocate(indexEnd).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(RoadFileFormat.MAGIC)
        head.putShort(RoadFileFormat.VERSION.toShort())
        head.putShort(0)
        head.putLong(dataTimestampSec)
        head.putLong(buildTimestampSec)
        head.putInt(grid.originLatE6)
        head.putInt(grid.originLonE6)
        head.putInt(grid.cellSizeE6)
        head.putInt(grid.width)
        head.putInt(grid.height)
        head.putInt(cellIds.size)
        var offset = indexEnd.toLong()
        for ((i, id) in cellIds.withIndex()) {
            check(offset + payloads[i].size <= Int.MAX_VALUE) { "road file would exceed 2 GB" }
            head.putInt(id)
            head.putInt(offset.toInt())
            head.putInt(payloads[i].size)
            offset += payloads[i].size
        }
        out.write(head.array())
        payloads.forEach(out::write)
    }

    private fun gridFor(roads: List<Polyline>, cellSizeE6: Int): Grid {
        var minLat = Int.MAX_VALUE
        var minLon = Int.MAX_VALUE
        var maxLat = Int.MIN_VALUE
        var maxLon = Int.MIN_VALUE
        for (road in roads) {
            for (i in 0 until road.size) {
                minLat = minOf(minLat, road.latE6[i])
                maxLat = maxOf(maxLat, road.latE6[i])
                minLon = minOf(minLon, road.lonE6[i])
                maxLon = maxOf(maxLon, road.lonE6[i])
            }
        }
        val originLat = Math.floorDiv(minLat, cellSizeE6) * cellSizeE6
        val originLon = Math.floorDiv(minLon, cellSizeE6) * cellSizeE6
        return Grid(
            originLatE6 = originLat,
            originLonE6 = originLon,
            cellSizeE6 = cellSizeE6,
            width = Math.floorDiv(maxLon - originLon, cellSizeE6) + 1,
            height = Math.floorDiv(maxLat - originLat, cellSizeE6) + 1,
        )
    }

    /**
     * Cuts every road into runs of consecutive segments per cell. A segment goes to every
     * cell its bounding box touches, so a cell may hold a segment that only passes near it.
     */
    private fun splitIntoCells(roads: List<Polyline>, grid: Grid): Map<Int, List<Polyline>> {
        val cells = HashMap<Int, MutableList<Polyline>>()
        for (road in roads) {
            val segmentsByCell = LinkedHashMap<Int, MutableList<Int>>()
            for (s in 0 until road.size - 1) {
                val rows = grid.row(minOf(road.latE6[s], road.latE6[s + 1]))..grid.row(maxOf(road.latE6[s], road.latE6[s + 1]))
                val cols = grid.col(minOf(road.lonE6[s], road.lonE6[s + 1]))..grid.col(maxOf(road.lonE6[s], road.lonE6[s + 1]))
                for (row in rows) {
                    for (col in cols) {
                        segmentsByCell.getOrPut(grid.cellId(row, col)) { mutableListOf() }.add(s)
                    }
                }
            }
            for ((cell, segments) in segmentsByCell) {
                var runStart = segments[0]
                for (k in 1..segments.size) {
                    val runEnds = k == segments.size || segments[k] != segments[k - 1] + 1
                    if (runEnds) {
                        // Segments runStart..segments[k-1] are points runStart..segments[k-1]+1.
                        cells.getOrPut(cell) { mutableListOf() }.add(road.slice(runStart, segments[k - 1] + 2))
                        if (k < segments.size) runStart = segments[k]
                    }
                }
            }
        }
        return cells
    }

    private fun encodeCell(grid: Grid, cellId: Int, polylines: List<Polyline>): ByteArray {
        val out = ByteArrayOutputStream()
        Varint.write(out, polylines.size)
        val originLat = grid.cellOriginLatE6(cellId)
        val originLon = grid.cellOriginLonE6(cellId)
        for (p in polylines) {
            Varint.write(out, p.roadClass.code)
            Varint.write(out, p.size)
            var prevLat = originLat
            var prevLon = originLon
            for (i in 0 until p.size) {
                Varint.write(out, Varint.zigzag(p.latE6[i] - prevLat))
                Varint.write(out, Varint.zigzag(p.lonE6[i] - prevLon))
                prevLat = p.latE6[i]
                prevLon = p.lonE6[i]
            }
        }
        return out.toByteArray()
    }
}
