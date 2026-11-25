package io.github.lrosa2.speedbeep.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.lrosa2.speedbeep.R
import io.github.lrosa2.speedbeep.engine.NoteButton
import io.github.lrosa2.speedbeep.engine.StatusText
import io.github.lrosa2.speedbeep.engine.WatchStatus
import io.github.lrosa2.speedbeep.ui.MainActivity

/** All notifications (spec §5.3). Actions work from the lock screen: none requires unlocking. */
object WatchNotifications {
    const val WATCH_ID = 1
    private const val PROBLEM_ID = 2
    private const val STOPPED_ID = 3
    private const val CAR_ID = 4

    // "watching" and "stopped_note" replace the first channels ("watch", "stopped"), whose importance was
    // too low for the lock screen; a channel's importance cannot change once it exists.
    private const val WATCH = "watching"
    private const val PROBLEMS = "problems"
    private const val STOPPED = "stopped_note"
    private const val CAR = "car"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun createChannels(context: Context) {
        val manager = manager(context)
        manager.deleteNotificationChannel("watch")
        manager.deleteNotificationChannel("stopped")
        manager.createNotificationChannels(
            listOf(
                // Default importance, so phones that hide silent notifications still show it on the lock
                // screen (spec 5.3). No sound or vibration of its own: the beeper does the beeping.
                NotificationChannel(WATCH, "Watching", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(PROBLEMS, "Problems", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(STOPPED, "Stopped while the car is connected", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CAR, "Car connected", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    /** The ongoing notification of the running service; [status] null while starting. */
    fun watching(context: Context, status: WatchStatus?, line: String): Notification {
        val builder = Notification.Builder(context, WATCH)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(line)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
        if (status != null) {
            for (button in StatusText.buttons(status.phase)) builder.addAction(action(context, button, status.pauseMin))
        }
        return builder.build()
    }

    fun showProblem(context: Context, text: String) {
        val n = Notification.Builder(context, PROBLEMS)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        manager(context).notify(PROBLEM_ID, n)
    }

    /** "SpeedBeep stopped · [Start]" while the car is still there (§5.3). */
    fun showStopped(context: Context) {
        val n = Notification.Builder(context, STOPPED)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("SpeedBeep stopped")
            .setContentText("Tap Start to watch again")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(icon(context), "Start", startService(context, 30, "stopped note")).build())
            .build()
        manager(context).notify(STOPPED_ID, n)
    }

    fun hideStopped(context: Context) = manager(context).cancel(STOPPED_ID)

    /** §5.1 fallback: "Car connected · Start". A notification tap may start the service from the background. */
    fun showCarConnected(context: Context) {
        val start = startService(context, 31, "car connected (notification)")
        val n = Notification.Builder(context, CAR)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setContentTitle("Car connected")
            .setContentText("Start SpeedBeep?")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(start)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(icon(context), "Start", start).build())
            .build()
        manager(context).notify(CAR_ID, n)
    }

    fun hideCarConnected(context: Context) = manager(context).cancel(CAR_ID)

    private fun action(context: Context, button: NoteButton, pauseMin: Int): Notification.Action {
        val (label, action) = when (button) {
            NoteButton.QUIET -> "Quiet this stretch" to WatchService.ACTION_QUIET
            NoteButton.PAUSE -> "Pause $pauseMin min" to WatchService.ACTION_PAUSE
            NoteButton.RESUME -> "Resume" to WatchService.ACTION_RESUME
            NoteButton.STOP -> "Stop" to WatchService.ACTION_STOP
        }
        val intent = WatchService.actionIntent(context, action).putExtra(WatchService.EXTRA_REASON, "notification")
        val pending = PendingIntent.getService(context, 10 + button.ordinal, intent, FLAGS)
        return Notification.Action.Builder(icon(context), label, pending).build()
    }

    private fun startService(context: Context, requestCode: Int, reason: String): PendingIntent =
        PendingIntent.getForegroundService(context, requestCode, WatchService.startIntent(context, reason), FLAGS)

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), FLAGS,
    )

    private fun icon(context: Context) = Icon.createWithResource(context, R.drawable.ic_stat_speed)

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
