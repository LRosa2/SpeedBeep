package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Fix

object Replay {
    /**
     * Moves recorded fixes onto the live monotonic clock, first fix at [startMs], spacing kept.
     * Without this, FixEvaluator's 3 s age check drops every replayed fix.
     */
    fun rebase(fixes: List<Fix>, startMs: Long): List<Fix> {
        val first = fixes.firstOrNull()?.elapsedMs ?: return emptyList()
        return fixes.map { it.copy(elapsedMs = startMs + (it.elapsedMs - first)) }
    }
}
