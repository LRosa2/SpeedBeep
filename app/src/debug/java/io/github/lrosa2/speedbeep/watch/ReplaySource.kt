package io.github.lrosa2.speedbeep.watch

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.engine.Replay

/** Plays a recorded drive once, on the live clock, then falls silent (the rules then see No GPS). */
class ReplaySource(private val recorded: List<Fix>) : LocationSource {
    private val handler = Handler(Looper.getMainLooper())

    override val name = "replay"
    val size: Int get() = recorded.size

    override fun start(onFix: (Fix) -> Unit) {
        val start = SystemClock.elapsedRealtime() + 1_000
        for (fix in Replay.rebase(recorded, start)) {
            handler.postDelayed({ onFix(fix) }, fix.elapsedMs - SystemClock.elapsedRealtime())
        }
    }

    override fun stop() = handler.removeCallbacksAndMessages(null)
}
