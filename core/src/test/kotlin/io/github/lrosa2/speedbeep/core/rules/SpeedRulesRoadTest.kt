package io.github.lrosa2.speedbeep.core.rules

import io.github.lrosa2.speedbeep.core.road.RoadMatch.FAST
import io.github.lrosa2.speedbeep.core.road.RoadMatch.NEUTRAL
import io.github.lrosa2.speedbeep.core.road.RoadMatch.NOT_COVERED
import io.github.lrosa2.speedbeep.core.road.RoadMatch.TOWN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedRulesRoadTest {

    private fun allSilent(sounds: List<Sound>) = assertTrue(sounds.all { it == Sound.NONE }, sounds.toString())

    @Test
    fun `five FAST readings confirm the fast road, silently`() {
        val d = TestDrive()
        val outs = List(5) { d.at(120.0, FAST) }
        allSilent(outs.map { it.sound })
        assertEquals(Phase.ARMED, outs[3].phase)
        assertEquals(Phase.FAST_ROAD, outs[4].phase)
        assertEquals(RoadContext.FAST, outs[4].roadContext)
    }

    @Test
    fun `accelerating past an 80 sign out of town does not beep`() {
        val d = TestDrive()
        val drive = listOf(45.0 to TOWN, 50.0 to TOWN, 54.0 to TOWN, 58.0 to FAST, 63.0 to FAST, 68.0 to FAST, 72.0 to FAST, 76.0 to FAST)
        allSilent(drive.map { (speed, match) -> d.at(speed, match).sound })
        assertEquals(Phase.FAST_ROAD, d.at(80.0, FAST).phase)
    }

    @Test
    fun `speeding on the town road before the sign still beeps`() {
        val d = TestDrive()
        d.at(54.0, TOWN)
        assertEquals(Sound.MAIN, d.at(56.0, TOWN).sound)
    }

    @Test
    fun `a motorway on-ramp from a town road does not beep`() {
        val d = TestDrive()
        d.at(50.0, TOWN)
        allSilent(listOf(60.0, 70.0, 80.0, 90.0, 100.0).map { d.at(it, FAST).sound })
        assertEquals(Phase.FAST_ROAD, d.at(105.0, FAST).phase)
    }

    @Test
    fun `GPS returning on the motorway after a tunnel does not beep`() {
        val d = TestDrive()
        d.readings(5, 110.0, FAST)
        d.readings(8, null)
        assertEquals(Phase.NO_GPS, d.at(null).phase)
        allSilent(d.readings(3, 110.0, FAST))
        assertEquals(Phase.FAST_ROAD, d.at(110.0, FAST).phase)
    }

    @Test
    fun `GPS returning with weak positions after a tunnel does not beep either`() {
        val d = TestDrive()
        d.readings(5, 110.0, FAST)
        d.readings(9, null)
        allSilent(d.readings(3, 110.0, NEUTRAL))
        assertEquals(Phase.FAST_ROAD, d.at(110.0, FAST).phase)
    }

    @Test
    fun `leaving the motorway onto a town road at 70 beeps on the second town reading`() {
        val d = TestDrive()
        d.readings(5, 100.0, FAST)
        val first = d.at(70.0, TOWN)
        assertEquals(Sound.NONE, first.sound)
        assertEquals(Phase.FAST_ROAD, first.phase)
        val second = d.at(70.0, TOWN)
        assertEquals(Sound.MAIN, second.sound)
        assertEquals(Phase.OVER, second.phase)
    }

    @Test
    fun `one stray TOWN reading on the motorway does not beep`() {
        val d = TestDrive()
        d.readings(5, 120.0, FAST)
        val sounds = listOf(TOWN, FAST, TOWN, FAST, FAST).map { d.at(120.0, it).sound }
        allSilent(sounds)
    }

    @Test
    fun `a stray FAST reading does not cut off a running warning`() {
        val d = TestDrive()
        d.at(60.0, TOWN)
        assertEquals(Sound.MAIN, d.at(60.0, TOWN).sound) // t = 2 s
        val stray = d.at(60.0, FAST)
        assertEquals(Sound.NONE, stray.sound)
        assertEquals(Phase.OVER, stray.phase)
        allSilent(d.readings(8, 60.0, TOWN))              // t = 4..11 s
        assertEquals(Sound.REMINDER, d.at(60.0, TOWN).sound)
    }

    @Test
    fun `weak positions in town still beep`() {
        val d = TestDrive()
        assertEquals(listOf(Sound.NONE, Sound.MAIN), d.readings(2, 60.0, NEUTRAL))
    }

    @Test
    fun `a weak position does not break a run of FAST readings`() {
        val d = TestDrive()
        val outs = listOf(FAST, FAST, NEUTRAL, FAST, FAST, FAST).map { d.at(120.0, it) }
        allSilent(outs.map { it.sound })
        assertEquals(Phase.FAST_ROAD, outs.last().phase)
    }

    @Test
    fun `10 s of only weak positions on the motorway falls back to beeping`() {
        val d = TestDrive()
        d.readings(5, 120.0, FAST)
        allSilent(d.readings(10, 120.0, NEUTRAL))  // neutral streak 0..9 s
        assertEquals(Sound.MAIN, d.at(120.0, NEUTRAL).sound) // 10 s
    }

    @Test
    fun `simple mode ignores road data`() {
        val d = TestDrive(RulesConfig(simpleMode = true))
        assertEquals(listOf(Sound.NONE, Sound.MAIN), d.readings(2, 60.0, FAST))
    }

    @Test
    fun `no road data behaves like simple mode and says so`() {
        val d = TestDrive()
        d.at(60.0, NOT_COVERED)
        val out = d.at(60.0, NOT_COVERED)
        assertEquals(Sound.MAIN, out.sound)
        assertTrue(out.noRoadData)
        assertFalse(d.at(60.0, TOWN).noRoadData)
    }

    @Test
    fun `simple mode does not claim missing road data`() {
        assertFalse(TestDrive(RulesConfig(simpleMode = true)).at(60.0, NOT_COVERED).noRoadData)
    }

    @Test
    fun `switching to simple mode on the motorway beeps at once`() {
        val d = TestDrive()
        d.readings(5, 120.0, FAST)
        d.rules.updateConfig(RulesConfig(simpleMode = true))
        assertEquals(Sound.MAIN, d.at(120.0, FAST).sound)
    }
}
