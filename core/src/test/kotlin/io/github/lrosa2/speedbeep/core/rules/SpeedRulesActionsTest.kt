package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedRulesActionsTest {

    @Test
    fun `quiet this stretch silences until back at 50`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val quiet = d.quiet()
        assertEquals(Sound.STOP, quiet.sound)
        assertEquals(Phase.QUIET, quiet.phase)
        assertTrue(d.readings(15, 60.0).all { it == Sound.NONE })
        assertEquals(Phase.QUIET, d.at(52.0).phase)
        assertEquals(Phase.ARMED, d.at(50.0).phase)
        d.at(60.0)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `quiet does nothing unless over`() {
        val d = TestDrive()
        d.at(40.0)
        val out = d.quiet()
        assertEquals(Sound.NONE, out.sound)
        assertEquals(Phase.ARMED, out.phase)
    }

    @Test
    fun `a pause silences for its length, then re-arms`() {
        val d = TestDrive()
        d.at(40.0)
        val paused = d.pause(10)
        assertEquals(Phase.PAUSED, paused.phase)
        assertEquals(d.now + 600_000, paused.pausedUntilMs)
        val during = List(59) { d.at(60.0, stepMs = 10_000).sound }           // up to 590 s
        assertTrue(during.all { it == Sound.NONE })
        assertEquals(Phase.PAUSED, d.at(60.0, stepMs = 9_000).phase)        // 599 s
        val ended = d.at(60.0, stepMs = 1_000)                               // 600 s
        assertEquals(Phase.ARMED, ended.phase)
        assertEquals(Sound.NONE, ended.sound)
        assertNull(ended.pausedUntilMs)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `pausing while over stops the beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val out = d.pause(10)
        assertEquals(Sound.STOP, out.sound)
        assertEquals(Phase.PAUSED, out.phase)
    }

    @Test
    fun `resume ends the pause at once and the debounce starts over`() {
        val d = TestDrive()
        d.at(60.0)
        d.pause(10)
        d.at(60.0)
        assertEquals(Phase.ARMED, d.resume().phase)
        assertEquals(Sound.NONE, d.at(60.0).sound)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `a pause ending on the motorway goes straight to the fast road`() {
        val d = TestDrive()
        d.readings(5, 120.0, RoadMatch.FAST)
        d.pause(10)
        d.readings(3, 120.0, RoadMatch.FAST)
        assertEquals(Phase.FAST_ROAD, d.resume().phase)
    }

    @Test
    fun `a pause ending with no GPS gives NO_GPS, not a stale speed`() {
        val d = TestDrive()
        d.at(30.0)
        d.pause(1)                                    // t = 1 s, until 61 s
        assertEquals(Phase.PAUSED, d.tick(30_000).phase) // no NO_GPS during a pause
        val after = d.tick(30_000)                    // t = 61 s
        assertEquals(Phase.NO_GPS, after.phase)
        assertNull(after.speedKmh)
        assertNull(after.pausedUntilMs)
    }

    @Test
    fun `pause during NO_GPS, then resume, is still NO_GPS`() {
        val d = TestDrive()
        d.tick(5_000)
        assertEquals(Phase.PAUSED, d.pause(5).phase)
        assertEquals(Phase.NO_GPS, d.resume().phase)
    }
}
