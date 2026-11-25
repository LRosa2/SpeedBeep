package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TonesTest {
    private fun at(ms: Int) = Tones.samples(ms)

    private fun silent(pcm: ShortArray, fromMs: Int, toMs: Int) = (at(fromMs) until at(toMs)).all { pcm[it].toInt() == 0 }

    private fun loud(pcm: ShortArray, fromMs: Int, toMs: Int) = (at(fromMs) until at(toMs)).maxOf { abs(pcm[it].toInt()) } > 20_000

    /** Sign changes; a pure tone of f Hz has 2·f per second. */
    private fun crossings(pcm: ShortArray, fromMs: Int, toMs: Int) =
        (at(fromMs) + 1 until at(toMs)).count { (pcm[it - 1] < 0) != (pcm[it] < 0) }.toDouble()

    @Test
    fun `the main beep has exactly the set length`() {
        assertEquals(at(2_000), Tones.main(SoundKind.BEEP, 2_000).size)
        assertEquals(at(500), Tones.main(SoundKind.CHIME, 500).size)
    }

    @Test
    fun `the beep is a repeated 1 kHz tone with gaps`() {
        val pcm = Tones.main(SoundKind.BEEP, 2_000)
        assertTrue(loud(pcm, 20, 230))
        assertTrue(silent(pcm, 255, 345))
        assertTrue(loud(pcm, 370, 580))
        assertEquals(400.0, crossings(pcm, 20, 220), 6.0)
    }

    @Test
    fun `the double beep has two tones before its pause`() {
        val pcm = Tones.main(SoundKind.DOUBLE_BEEP, 600)
        assertTrue(loud(pcm, 10, 110))
        assertTrue(silent(pcm, 125, 195))
        assertTrue(loud(pcm, 210, 310))
        assertTrue(silent(pcm, 325, 595))
    }

    @Test
    fun `the chime goes from low to high`() {
        val pcm = Tones.main(SoundKind.CHIME, 800)
        assertEquals(2 * 660 * 0.2, crossings(pcm, 50, 250), 6.0)
        assertEquals(2 * 990 * 0.2, crossings(pcm, 350, 550), 6.0)
    }

    @Test
    fun `the reminder is one short tone`() {
        val pcm = Tones.reminder()
        assertEquals(at(Tones.REMINDER_MS), pcm.size)
        assertTrue(loud(pcm, 20, 380))
    }

    @Test
    fun `tones start and end softly`() {
        val pcm = Tones.main(SoundKind.BEEP, 1_234)
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 2_000)
    }
}
