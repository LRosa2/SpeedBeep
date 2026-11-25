package io.github.lrosa2.speedbeep.watch

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.car.CarController
import io.github.lrosa2.speedbeep.core.road.NoRoadData
import io.github.lrosa2.speedbeep.core.rules.Fix
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.engine.DriveRecorder
import io.github.lrosa2.speedbeep.engine.NoteButton
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SoundCommand
import io.github.lrosa2.speedbeep.engine.SoundPolicy
import io.github.lrosa2.speedbeep.engine.StatusText
import io.github.lrosa2.speedbeep.engine.WatchSession
import io.github.lrosa2.speedbeep.engine.WatchStatus
import io.github.lrosa2.speedbeep.engine.WatchUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service of type location (spec §3): location source → session → beeper, plus
 * the notification and the optional drive recording. Everything runs on the main thread,
 * because SpeedRules, RoadMatcher and the beeper are single-threaded.
 */
class WatchService : Service() {
    companion object {
        const val ACTION_START = "io.github.lrosa2.speedbeep.action.START"
        const val ACTION_STOP = "io.github.lrosa2.speedbeep.action.STOP"
        const val ACTION_QUIET = "io.github.lrosa2.speedbeep.action.QUIET"
        const val ACTION_PAUSE = "io.github.lrosa2.speedbeep.action.PAUSE"
        const val ACTION_RESUME = "io.github.lrosa2.speedbeep.action.RESUME"
        const val EXTRA_REASON = "reason"
        private const val EXTRA_BY_USER = "byUser"
        private const val TICK_MS = 1_000L

        fun startIntent(context: Context, reason: String): Intent =
            Intent(context, WatchService::class.java).setAction(ACTION_START).putExtra(EXTRA_REASON, reason)

        fun actionIntent(context: Context, action: String, byUser: Boolean = true): Intent =
            Intent(context, WatchService::class.java).setAction(action).putExtra(EXTRA_BY_USER, byUser)

        /** Quiet, Pause or Resume while watching; ignored otherwise. */
        fun send(context: Context, action: String) {
            if (WatchBus.running) context.startService(actionIntent(context, action))
        }

        /** Stops watching if it runs. [byUser]: the owner pressed Stop (§5.3), as opposed to the car leaving. */
        fun stop(context: Context, reason: String, byUser: Boolean) {
            if (WatchBus.running) {
                context.startService(actionIntent(context, ACTION_STOP, byUser).putExtra(EXTRA_REASON, reason))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val events get() = Graph.events
    private val soundPolicy = SoundPolicy()
    private lateinit var beeper: Beeper

    private var started = false
    private var settings = Settings()
    private var session: WatchSession? = null
    private var source: LocationSource? = null
    private var recorder: DriveRecorder? = null
    private var lastPhase: Phase? = null
    private var lastLine: String? = null
    private var lastButtons: List<NoteButton>? = null

    private val tick = object : Runnable {
        override fun run() {
            session?.let { handle(it.onTick(now())) }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        beeper = Beeper(this, events)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_START) {
            if (!started) begin(intent?.getStringExtra(EXTRA_REASON) ?: "restarted by Android")
            return START_NOT_STICKY
        }
        if (!started) {
            stopSelf(startId) // a late button press after watching ended
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_STOP -> end(intent?.getStringExtra(EXTRA_REASON) ?: "stop", intent?.getBooleanExtra(EXTRA_BY_USER, false) == true)
            ACTION_QUIET -> session?.let { handle(it.quiet(now())) }
            ACTION_PAUSE -> session?.let { handle(it.pause(now())) }
            ACTION_RESUME -> session?.let { handle(it.resume(now())) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (started) end("service destroyed", byUser = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun begin(reason: String) {
        // Foreground first: Android allows only a few seconds after startForegroundService.
        try {
            ServiceCompat.startForeground(
                this,
                WatchNotifications.WATCH_ID,
                WatchNotifications.watching(this, null, "Starting"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) { // SecurityException without location permission, or a refused background start
            events.log("could not watch ($reason): ${e.javaClass.simpleName}: ${e.message}")
            WatchNotifications.showProblem(this, "SpeedBeep could not start watching. Open SpeedBeep and finish its setup.")
            stopSelf()
            return
        }
        started = true
        scope.launch {
            settings = Graph.settings.settings.first()
            startSession(reason)
            Graph.settings.settings.collect { applySettings(it) }
        }
    }

    private fun startSession(reason: String) {
        if (!started) return
        val s = WatchSession(settings, now(), Graph.roads.value?.lookup ?: NoRoadData)
        session = s
        scope.launch { Graph.roads.filterNotNull().collect { s.setRoads(it.lookup) } }
        val src = LocationSources.create(this, events)
        source = src
        try {
            src.start(::onFix)
        } catch (e: SecurityException) {
            events.log("GPS refused: ${e.message}")
            WatchNotifications.showProblem(this, "SpeedBeep has no location permission. Open SpeedBeep and finish its setup.")
            end("no location permission", byUser = false)
            return
        }
        events.log("watching started ($reason) with ${src.name}")
        StartStopTile.refresh(this)
        CarController.watchStarted()
        handle(s.onTick(now()))
        handler.postDelayed(tick, TICK_MS)
    }

    private fun applySettings(new: Settings) {
        settings = new
        if (!new.recordDrives) {
            recorder?.close()
            recorder = null
        }
        session?.let {
            it.updateSettings(new)
            publish(it.status)
        }
    }

    private fun onFix(fix: Fix) {
        val s = session ?: return
        if (settings.recordDrives) {
            val r = recorder ?: DriveRecorder(Graph.drivesDir(this)).also { recorder = it }
            r.record(fix, System.currentTimeMillis())
        }
        handle(s.onFix(fix, now()))
    }

    private fun handle(update: WatchUpdate) {
        val status = update.status
        when (soundPolicy.decide(update.sound, now(), settings.mainBeepMs)) {
            SoundCommand.PLAY_MAIN -> {
                beeper.playMain(settings)
                events.log("main beep at ${speed(status)}")
            }
            SoundCommand.PLAY_REMINDER -> {
                beeper.playReminder(settings)
                events.log("reminder at ${speed(status)}")
            }
            SoundCommand.STOP -> {
                beeper.stop()
                events.log("beep stopped")
            }
            SoundCommand.NONE -> Unit
        }
        if (status.phase != lastPhase) {
            val noData = if (status.noRoadData) " (no road data)" else ""
            events.log("state ${lastPhase ?: "start"} → ${status.phase} at ${speed(status)}, road ${status.roadContext}$noData")
            lastPhase = status.phase
        }
        publish(status)
    }

    private fun publish(status: WatchStatus) {
        WatchBus.publish(status)
        val line = StatusText.line(status, now(), System.currentTimeMillis(), ZoneId.systemDefault())
        val buttons = StatusText.buttons(status.phase)
        if (line != lastLine || buttons != lastButtons) {
            getSystemService(NotificationManager::class.java)
                .notify(WatchNotifications.WATCH_ID, WatchNotifications.watching(this, status, line))
            lastLine = line
            lastButtons = buttons
        }
    }

    private fun end(reason: String, byUser: Boolean) {
        events.log("watching stopped ($reason${if (byUser) ", by the owner" else ""})")
        started = false
        handler.removeCallbacks(tick)
        scope.coroutineContext.cancelChildren()
        source?.stop()
        source = null
        beeper.stop()
        recorder?.close()
        recorder = null
        session = null
        lastPhase = null
        lastLine = null
        lastButtons = null
        WatchBus.publish(null)
        StartStopTile.refresh(this)
        if (byUser) CarController.ownerStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun speed(status: WatchStatus) = status.speedKmh?.let { "${it.toInt()} km/h" } ?: "–"

    private fun now() = SystemClock.elapsedRealtime()
}
