package io.github.lrosa2.speedbeep.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.CarAction
import io.github.lrosa2.speedbeep.engine.CarPolicy
import io.github.lrosa2.speedbeep.watch.WatchNotifications
import io.github.lrosa2.speedbeep.watch.WatchService
import io.github.lrosa2.speedbeep.watch.WatchStarter

/** Runs [CarPolicy] on the main thread and carries out its actions. */
object CarController {
    private val policy = CarPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var autoStart = true
    private val check = Runnable { run(policy.onTick(now())) }

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** [autoStart] false: the §5.1 fallback, which asks with a notification instead of starting. */
    fun carAppeared(source: String, autoStart: Boolean = true) = onMain {
        Graph.events.log("car appeared ($source)")
        this.autoStart = autoStart
        run(policy.onCarAppeared())
    }

    fun carGone(source: String) = onMain {
        Graph.events.log("car gone ($source)")
        run(policy.onCarGone(now()))
    }

    fun ownerStopped() = onMain { run(policy.onOwnerStop()) }

    fun watchStarted() = onMain { run(policy.onWatchStarted()) }

    private fun run(actions: List<CarAction>) {
        for (action in actions) {
            when (action) {
                CarAction.START_WATCH ->
                    if (autoStart) WatchStarter.start(app, "car connected", fromBackground = true)
                    else WatchNotifications.showCarConnected(app)
                CarAction.STOP_WATCH -> {
                    WatchNotifications.hideCarConnected(app)
                    WatchService.stop(app, "car gone", byUser = false)
                }
                CarAction.SHOW_STOPPED_NOTE -> WatchNotifications.showStopped(app)
                CarAction.HIDE_STOPPED_NOTE -> WatchNotifications.hideStopped(app)
            }
        }
        handler.removeCallbacks(check)
        policy.checkAtMs?.let { handler.postDelayed(check, (it - now()).coerceAtLeast(0)) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    private fun now() = SystemClock.elapsedRealtime()
}
