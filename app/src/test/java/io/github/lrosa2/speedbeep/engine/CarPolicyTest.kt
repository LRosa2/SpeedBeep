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
        assertEquals(listOf(START_WATCH), policy.onCarAppeared())
    }

    @Test
    fun `a short drop-out changes nothing`() {
        policy.onCarAppeared()
        assertEquals(nothing, policy.onCarGone(0))
        assertEquals(nothing, policy.onCarAppeared())
        assertNull(policy.checkAtMs)
        assertEquals(nothing, policy.onTick(40_000))
    }

    @Test
    fun `the car gone for 30 s stops watching`() {
        policy.onCarAppeared()
        policy.onCarGone(1_000)
        assertEquals(31_000L, policy.checkAtMs)
        assertEquals(nothing, policy.onTick(30_999))
        assertEquals(listOf(STOP_WATCH), policy.onTick(31_000))
        assertFalse(policy.present)
        assertNull(policy.checkAtMs)
    }

    @Test
    fun `after Stop a drop-out does not restart watching`() {
        policy.onCarAppeared()
        assertEquals(listOf(SHOW_STOPPED_NOTE), policy.onOwnerStop())
        assertEquals(listOf(HIDE_STOPPED_NOTE), policy.onCarGone(0))
        assertEquals(listOf(SHOW_STOPPED_NOTE), policy.onCarAppeared())
    }

    @Test
    fun `after Stop the next real arrival starts watching again`() {
        policy.onCarAppeared()
        policy.onOwnerStop()
        policy.onCarGone(0)
        assertEquals(listOf(STOP_WATCH), policy.onTick(30_000))
        assertEquals(listOf(START_WATCH), policy.onCarAppeared())
    }

    @Test
    fun `Stop without the car shows no note`() {
        assertEquals(nothing, policy.onOwnerStop())
    }

    @Test
    fun `starting again hides the stopped note`() {
        policy.onCarAppeared()
        policy.onOwnerStop()
        assertEquals(listOf(HIDE_STOPPED_NOTE), policy.onWatchStarted())
        assertEquals(nothing, policy.onWatchStarted())
    }
}
