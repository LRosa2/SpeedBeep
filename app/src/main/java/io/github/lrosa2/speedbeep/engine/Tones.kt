package io.github.lrosa2.speedbeep.engine

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The built-in sounds as 16-bit mono PCM (spec §7.2). No asset files. */
object Tones {
    const val SAMPLE_RATE = 22_050
    const val REMINDER_MS = 400
    private const val AMPLITUDE = 0.8 * Short.MAX_VALUE
    private const val FADE_MS = 5

    /** [hz] = 0 is silence. */
    private class Segment(val hz: Int, val ms: Int)

    private fun pattern(kind: SoundKind): List<Segment> = when (kind) {
        // PHONE_ALARM falls back to the beep when the alarm sound cannot play.
        SoundKind.BEEP, SoundKind.PHONE_ALARM -> listOf(Segment(1_000, 250), Segment(0, 100))
        SoundKind.DOUBLE_BEEP -> listOf(Segment(1_000, 120), Segment(0, 80), Segment(1_000, 120), Segment(0, 280))
        SoundKind.CHIME -> listOf(Segment(660, 300), Segment(990, 300), Segment(0, 200))
    }

    fun samples(ms: Int): Int = (ms.toLong() * SAMPLE_RATE / 1_000).toInt()

    /** The main beep: [kind]'s pattern repeated, then cut to exactly [lengthMs]. */
    fun main(kind: SoundKind, lengthMs: Int): ShortArray = render(pattern(kind), lengthMs)

    /** One short 1 kHz tone, whatever sound is chosen. */
    fun reminder(): ShortArray = render(listOf(Segment(1_000, REMINDER_MS)), REMINDER_MS)

    private fun render(pattern: List<Segment>, lengthMs: Int): ShortArray {
        val out = ShortArray(samples(lengthMs))
        var at = 0
        while (at < out.size) {
            for (segment in pattern) {
                val n = samples(segment.ms)
                if (segment.hz > 0) tone(out, at, n, segment.hz)
                at += n
                if (at >= out.size) break
            }
        }
        fadeOutEnd(out)
        return out
    }

    /** A sine tone with short fades at both ends, so the edges do not click. */
    private fun tone(out: ShortArray, start: Int, n: Int, hz: Int) {
        val fade = samples(FADE_MS)
        for (i in 0 until n) {
            val index = start + i
            if (index >= out.size) return
            val edge = minOf(i, n - 1 - i)
            val envelope = if (edge < fade) edge.toDouble() / fade else 1.0
            out[index] = (AMPLITUDE * envelope * sin(2 * PI * hz * i / SAMPLE_RATE)).roundToInt().toShort()
        }
    }

    /** A tone cut by the set length also ends softly. */
    private fun fadeOutEnd(out: ShortArray) {
        val fade = minOf(samples(FADE_MS), out.size)
        for (k in 0 until fade) {
            val index = out.size - 1 - k
            out[index] = (out[index] * (k.toDouble() / fade)).roundToInt().toShort()
        }
    }
}
