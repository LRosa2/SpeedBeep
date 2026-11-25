package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.engine.CarAction.HIDE_STOPPED_NOTE
import io.github.lrosa2.speedbeep.engine.CarAction.SHOW_STOPPED_NOTE
import io.github.lrosa2.speedbeep.engine.CarAction.START_WATCH
import io.github.lrosa2.speedbeep.engine.CarAction.STOP_WATCH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarPolicyTest {
    private val policy = CarPolicy()
    private val nothing = emptyList<CarAction>()

    @Test
    fun `the car arriving starts watching`() {
        assertEquals(listOf(START_WATCH), policy.onCarAppeared(0))
    }

    @Test
    fun `a short drop-out changes nothing`() {
        policy.onCarAppeared(0)
        assertEquals(nothing, policy.onCarGone(0))
        assertEquals(nothing, policy.onCarAppeared(10_000))
        assertNull(policy.checkAtMs)
        assertEquals(nothing, policy.onTick(40_000))
    }

    @Test
    fun `the car gone for 30 s stops watching`() {
        policy.onCarAppeared(0)
        policy.onCarGone(1_000)
        assertEquals(31_000L, policy.checkAtMs)
        assertEquals(nothing, policy.onTick(30_999))
        assertEquals(listOf(STOP_WATCH), policy.onTick(31_000))
        assertFalse(policy.present)
        assertNull(policy.checkAtMs)
    }

    @Test
    fun `after Stop a drop-out does not restart watching`() {
        policy.onCarAppeared(0)
        assertEquals(listOf(SHOW_STOPPED_NOTE), policy.onOwnerStop())
        assertEquals(listOf(HIDE_STOPPED_NOTE), policy.onCarGone(0))
        assertEquals(listOf(SHOW_STOPPED_NOTE), policy.onCarAppeared(5_000))
    }

    @Test
    fun `after Stop the next real arrival starts watching again`() {
        policy.onCarAppeared(0)
        policy.onOwnerStop()
        policy.onCarGone(0)
        assertEquals(listOf(STOP_WATCH), policy.onTick(30_000))
        assertEquals(listOf(START_WATCH), policy.onCarAppeared(40_000))
    }

    @Test
    fun `after Stop a return long after the car left starts watching even if the timer never ran`() {
        // The phone slept or the process was frozen, so onTick never came.
        policy.onCarAppeared(0)
        policy.onOwnerStop()
        policy.onCarGone(1_000)
        assertEquals(listOf(START_WATCH), policy.onCarAppeared(301_000))
        assertNull(policy.checkAtMs)
    }

    @Test
    fun `a return just after the grace counts as a new arrival`() {
        policy.onCarAppeared(0)
        policy.onCarGone(0)
        assertEquals(listOf(START_WATCH), policy.onCarAppeared(30_000))
    }

    @Test
    fun `Stop without the car shows no note`() {
        assertEquals(nothing, policy.onOwnerStop())
    }

    @Test
    fun `starting again hides the stopped note`() {
        policy.onCarAppeared(0)
        policy.onOwnerStop()
        assertEquals(listOf(HIDE_STOPPED_NOTE), policy.onWatchStarted())
        assertEquals(nothing, policy.onWatchStarted())
    }
}
