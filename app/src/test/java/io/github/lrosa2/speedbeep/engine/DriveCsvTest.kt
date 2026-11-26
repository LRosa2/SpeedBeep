package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DriveCsvTest {
    private val full = Fix(1_000, 47.3563251, 8.5558482, 16.7f, 0.5f, 4.0f, 147.0f)
    private val bare = Fix(2_000, 47.0, 8.0, null, null, null, null)

    @Test
    fun `fixes survive writing and reading`() {
        val text = listOf(DriveCsv.HEADER, DriveCsv.COLUMNS, DriveCsv.line(full, 1_000), DriveCsv.line(bare, 1_000))
            .joinToString("\n")
        assertEquals(listOf(full.copy(elapsedMs = 0), bare.copy(elapsedMs = 1_000)), DriveCsv.parse(text))
    }

    @Test
    fun `missing values are empty fields`() {
        assertEquals("1000,47.0000000,8.0000000,,,,,", DriveCsv.line(bare, 1_000))
    }

    @Test
    fun `a broken line is reported`() {
        val e = assertThrows<IllegalArgumentException> { DriveCsv.parse("0,47.0,8.0") }
        assertTrue(e.message!!.contains("7 or 8 fields"))
    }

    @Test
    fun `the car's speed is the last field`() {
        val line = DriveCsv.line(full, 1_000, obdKmh = 52)
        assertTrue(line.endsWith(",52"), line)
        assertEquals(listOf(full.copy(elapsedMs = 0)), DriveCsv.parse(line))
    }

    @Test
    fun `version 1 drives still read`() {
        val v1 = listOf(
            "# SpeedBeep drive v1",
            "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg",
            "0,47.3563251,8.5558482,16.7,0.5,4.0,147.0",
        ).joinToString("\n")
        assertEquals(listOf(full.copy(elapsedMs = 0)), DriveCsv.parse(v1))
    }
}
