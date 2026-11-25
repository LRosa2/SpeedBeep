package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Sound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SoundPolicyTest {
    @Test
    fun `a reminder never cuts into a main beep still playing`() {
        val policy = SoundPolicy()
        assertEquals(SoundCommand.PLAY_MAIN, policy.decide(Sound.MAIN, 0, 10_000))
        assertEquals(SoundCommand.NONE, policy.decide(Sound.REMINDER, 3_000, 10_000))
        assertEquals(SoundCommand.PLAY_REMINDER, policy.decide(Sound.REMINDER, 12_000, 10_000))
    }

    @Test
    fun `stop ends the main beep at once`() {
        val policy = SoundPolicy()
        policy.decide(Sound.MAIN, 0, 10_000)
        assertEquals(SoundCommand.STOP, policy.decide(Sound.STOP, 1_000, 10_000))
        assertEquals(SoundCommand.PLAY_REMINDER, policy.decide(Sound.REMINDER, 2_000, 10_000))
    }

    @Test
    fun `nothing to do stays nothing`() {
        assertEquals(SoundCommand.NONE, SoundPolicy().decide(Sound.NONE, 0, 2_000))
    }
}
