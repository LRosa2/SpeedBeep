package io.github.lrosa2.speedbeep.core.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpeedRulesSpeedTest {

    @Test
    fun `below the threshold it never beeps`() {
        val d = TestDrive()
        for (speed in listOf(40.0, 50.0, 52.9, 52.9, 52.9)) assertEquals(Sound.NONE, d.at(speed).sound)
        assertEquals(Phase.ARMED, d.at(52.0).phase)
    }

    @Test
    fun `two readings at 53 or more give the main beep`() {
        val d = TestDrive()
        assertEquals(Sound.NONE, d.at(53.0).sound)
        val out = d.at(53.0)
        assertEquals(Sound.MAIN, out.sound)
        assertEquals(Phase.OVER, out.phase)
    }

    @Test
    fun `single spikes do not beep`() {
        val d = TestDrive()
        for (speed in listOf(60.0, 45.0, 60.0, 45.0)) assertEquals(Sound.NONE, d.at(speed).sound)
    }

    @Test
    fun `a reminder sounds every 10 s while over`() {
        val d = TestDrive()
        d.at(60.0)
        assertEquals(Sound.MAIN, d.at(60.0).sound)                  // t = 2 s
        assertEquals(List(9) { Sound.NONE }, d.readings(9, 60.0))   // t = 3..11 s
        assertEquals(Sound.REMINDER, d.at(60.0).sound)              // t = 12 s
        assertEquals(List(9) { Sound.NONE }, d.readings(9, 60.0))
        assertEquals(Sound.REMINDER, d.at(60.0).sound)              // t = 22 s
    }

    @Test
    fun `reminder interval 0 means no reminders`() {
        val d = TestDrive(RulesConfig(reminderIntervalMs = 0))
        d.at(60.0)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
        assertEquals(List(30) { Sound.NONE }, d.readings(30, 60.0))
    }

    @Test
    fun `dipping between 50 and 53 stops the sound but keeps the warning`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)                                                  // main beep at 2 s
        assertEquals(Sound.STOP, d.at(52.0).sound)
        assertEquals(List(10) { Sound.NONE }, d.readings(10, 51.0)) // up to 13 s
        val back = d.at(60.0)                                       // 14 s: 12 s after the main beep
        assertEquals(Sound.REMINDER, back.sound)
        assertEquals(Phase.OVER, back.phase)
    }

    @Test
    fun `hovering around 53 after a warning never repeats the main beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val sounds = (1..8).map { d.at(if (it % 2 == 0) 54.0 else 52.0).sound }
        assertFalse(Sound.MAIN in sounds, sounds.toString())
    }

    @Test
    fun `dropping to 50 re-arms, and the next crossing gives a new main beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0)
        val down = d.at(50.0)
        assertEquals(Sound.STOP, down.sound)
        assertEquals(Phase.ARMED, down.phase)
        assertEquals(Sound.NONE, d.at(60.0).sound)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `5 s without a usable speed gives NO_GPS and stops a running beep`() {
        val d = TestDrive()
        d.at(60.0)
        d.at(60.0) // last usable speed at 2 s
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.NONE, Sound.NONE, Sound.STOP), d.readings(5, null))
        val out = d.at(null)
        assertEquals(Phase.NO_GPS, out.phase)
        assertNull(out.speedKmh)
    }

    @Test
    fun `NO_GPS also comes from the clock when no fixes arrive`() {
        val d = TestDrive()
        assertEquals(Phase.ARMED, d.tick(4_999).phase)
        assertEquals(Phase.NO_GPS, d.tick(1).phase)
    }

    @Test
    fun `after NO_GPS the debounce starts over`() {
        val d = TestDrive()
        d.tick(5_000)
        val first = d.at(60.0)
        assertEquals(Sound.NONE, first.sound)
        assertEquals(Phase.ARMED, first.phase)
        assertEquals(Sound.MAIN, d.at(60.0).sound)
    }

    @Test
    fun `raising the threshold while over stops the warning without a new main beep`() {
        val d = TestDrive()
        d.at(57.0)
        d.at(57.0) // main beep at T = 53
        d.rules.updateConfig(RulesConfig(thresholdKmh = 60))
        val atNewThreshold = d.at(58.0)
        assertEquals(Sound.STOP, atNewThreshold.sound)
        assertEquals(Phase.OVER, atNewThreshold.phase)
        assertEquals(List(15) { Sound.NONE }, d.readings(15, 58.0)) // no reminders below the new T
        assertEquals(Phase.ARMED, d.at(57.0).phase)                 // re-arms at the new T − R
    }

    @Test
    fun `the output carries the speed`() {
        assertEquals(47.5, TestDrive().at(47.5).speedKmh!!, 1e-9)
    }
}
