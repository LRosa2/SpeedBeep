package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.Sound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarSpeedTest {
    private val simple = Settings(mode = Mode.SIMPLE)

    private fun gps(t: Long, kmh: Double) = Fix(t, 47.0, 8.0, (kmh / 3.6).toFloat(), 0.5f, 5f, 90f)

    @Test
    fun `in simple mode the car's speed drives the beep, and the threshold itself counts`() {
        val s = WatchSession(simple, 0)
        val sounds = listOf(45, 53, 53).mapIndexed { i, kmh -> s.onCarSpeed(kmh, i * 1_000L).sound }
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.MAIN), sounds)
        assertEquals(53.0, s.status.speedKmh)
        assertEquals(SpeedSource.CAR, s.status.speedSource)
    }

    @Test
    fun `GPS is ignored while the car's speed is fresh`() {
        val s = WatchSession(simple, 0)
        s.onCarSpeed(40, 0)
        val sounds = listOf(500L, 1_500L, 2_500L).map { t -> s.onFix(gps(t, 80.0), t).sound }
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.NONE), sounds)
        assertEquals(40.0, s.status.speedKmh)
        assertEquals(Phase.ARMED, s.status.phase)
    }

    @Test
    fun `when the adapter falls silent GPS takes over within 4 s, still OVER and without a second main beep`() {
        val s = WatchSession(simple, 0)
        val carSounds = listOf(60, 60).mapIndexed { i, kmh -> s.onCarSpeed(kmh, i * 1_000L).sound }
        assertEquals(listOf(Sound.NONE, Sound.MAIN), carSounds)
        // The adapter stops after t = 1 s; the service ticks and GPS keeps sending once a second.
        val updates = (2..6).flatMap { sec ->
            val t = sec * 1_000L
            listOf(s.onTick(t), s.onFix(gps(t, 60.0), t))
        }
        assertTrue(updates.none { it.sound == Sound.MAIN })
        assertTrue(updates.none { it.status.phase == Phase.NO_GPS })
        assertEquals(Phase.OVER, s.status.phase)
        assertEquals(SpeedSource.GPS, s.status.speedSource)
    }

    @Test
    fun `the car's speed takes over again as soon as the adapter answers`() {
        val s = WatchSession(simple, 0)
        s.onFix(gps(0, 30.0), 0)
        assertEquals(SpeedSource.GPS, s.status.speedSource)
        s.onCarSpeed(31, 500)
        assertEquals(SpeedSource.CAR, s.status.speedSource)
        assertEquals(31.0, s.status.speedKmh)
    }

    @Test
    fun `smart mode ignores the car's speed`() {
        val s = WatchSession(Settings(), 0)
        assertEquals(Sound.NONE, s.onCarSpeed(80, 0).sound)
        assertNull(s.status.speedKmh)
        s.onFix(gps(500, 30.0), 500)
        assertEquals(SpeedSource.GPS, s.status.speedSource)
    }
}
