package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.RoadLookup
import io.github.lrosa2.speedbeep.core.road.RoadMatch
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.Sound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchSessionTest {
    private val town = RoadLookup { _, _, _, _ -> RoadMatch.TOWN }
    private val fast = RoadLookup { _, _, _, _ -> RoadMatch.FAST }

    private fun fix(t: Long, kmh: Double) = Fix(t, 47.0, 8.0, (kmh / 3.6).toFloat(), 0.5f, 5f, 90f)

    /** One fresh fix per second from [from], at the given speeds. */
    private fun drive(session: WatchSession, from: Long, kmh: List<Double>): List<WatchUpdate> =
        kmh.mapIndexed { i, v ->
            val t = from + i * 1_000L
            session.onFix(fix(t, v), t)
        }

    @Test
    fun `going over in town gives a main beep on the second reading above`() {
        val s = WatchSession(Settings(), 0, town)
        val sounds = drive(s, 0, listOf(45.0, 60.0, 60.0, 60.0)).map { it.sound }
        assertEquals(listOf(Sound.NONE, Sound.NONE, Sound.MAIN, Sound.NONE), sounds)
        assertEquals(Phase.OVER, s.status.phase)
    }

    @Test
    fun `a fast road stays silent`() {
        val s = WatchSession(Settings(), 0, fast)
        val updates = drive(s, 0, List(6) { 110.0 })
        assertTrue(updates.none { it.sound == Sound.MAIN })
        assertEquals(Phase.FAST_ROAD, s.status.phase)
    }

    @Test
    fun `simple mode beeps on a fast road`() {
        val s = WatchSession(Settings(mode = Mode.SIMPLE), 0, fast)
        assertEquals(Sound.MAIN, drive(s, 0, listOf(110.0, 110.0)).last().sound)
    }

    @Test
    fun `road data that arrives later is used`() {
        val s = WatchSession(Settings(), 0) // NoRoadData until the file is loaded
        drive(s, 0, listOf(40.0))
        assertTrue(s.status.noRoadData)
        s.setRoads(town)
        drive(s, 1_000, listOf(40.0))
        assertFalse(s.status.noRoadData)
    }

    @Test
    fun `a pause lasts the set number of minutes`() {
        val s = WatchSession(Settings(pauseMin = 5), 0, town)
        assertEquals(2_000L + 5 * 60_000, s.pause(2_000).status.pausedUntilMs)
    }

    @Test
    fun `a new threshold shows at once and applies from the next reading`() {
        val s = WatchSession(Settings(), 0, town)
        drive(s, 0, listOf(55.0))
        s.updateSettings(Settings(thresholdKmh = 60))
        assertEquals(60, s.status.thresholdKmh)
        assertEquals(Sound.NONE, drive(s, 1_000, listOf(55.0, 55.0)).last().sound)
    }

    @Test
    fun `no fix for 5 s means No GPS`() {
        val s = WatchSession(Settings(), 0, town)
        assertEquals(Phase.NO_GPS, s.onTick(5_000).status.phase)
    }

    @Test
    fun `quiet this stretch stops the beep`() {
        val s = WatchSession(Settings(), 0, town)
        drive(s, 0, listOf(60.0, 60.0))
        val update = s.quiet(2_000)
        assertEquals(Sound.STOP, update.sound)
        assertEquals(Phase.QUIET, update.status.phase)
    }
}
