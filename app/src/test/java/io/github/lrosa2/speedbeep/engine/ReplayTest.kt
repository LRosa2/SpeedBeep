package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.FixEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReplayTest {
    /** Recorded on another boot: its monotonic times mean nothing on today's clock. */
    private val recorded = listOf(5_000_000L, 5_001_000L, 5_003_000L).map { Fix(it, 47.0, 8.0, 16.7f, null, 5f, 90f) }

    @Test
    fun `replayed fixes move onto the live clock and keep their spacing`() {
        assertEquals(listOf(200_000L, 201_000L, 203_000L), Replay.rebase(recorded, 200_000).map { it.elapsedMs })
    }

    @Test
    fun `only rebased fixes count as fresh`() {
        val live = 200_000L
        assertNull(FixEvaluator.evaluate(recorded[0], live, true, NoRoadData).speedKmh)
        assertNotNull(FixEvaluator.evaluate(Replay.rebase(recorded, live)[0], live, true, NoRoadData).speedKmh)
    }

    @Test
    fun `an empty recording replays nothing`() {
        assertEquals(emptyList<Fix>(), Replay.rebase(emptyList(), 0))
    }
}
