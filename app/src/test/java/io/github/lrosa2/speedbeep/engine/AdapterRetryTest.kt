package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdapterRetryTest {
    @Test
    fun `an adapter out of reach is tried for 5 minutes from the first failure, then no more`() {
        val retry = AdapterRetry()
        val answers = listOf(10_000L, 200_000L, 309_999L, 310_000L).map { retry.keepTrying(it) }
        assertEquals(listOf(true, true, true, false), answers)
    }

    @Test
    fun `reaching the adapter starts the 5 minutes again at the next failure`() {
        val retry = AdapterRetry()
        retry.keepTrying(0)
        retry.keepTrying(250_000)
        retry.reached()
        val answers = listOf(400_000L, 699_999L, 700_000L).map { retry.keepTrying(it) }
        assertEquals(listOf(true, true, false), answers)
    }
}
