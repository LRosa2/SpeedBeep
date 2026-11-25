package io.github.lrosa2.speedbeep.watch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import io.github.lrosa2.speedbeep.Graph

/** The one way to start watching. Explains a refusal in a notification instead of failing silently. */
object WatchStarter {
    /** [fromBackground]: started by the car or the tile, so location must be allowed all the time (§5.1, §8). */
    fun start(context: Context, reason: String, fromBackground: Boolean): Boolean {
        val missing = missingPermission(context, fromBackground)
        if (missing != null) {
            Graph.events.log("not started ($reason): $missing")
            WatchNotifications.showProblem(context, "SpeedBeep could not start: $missing. Open SpeedBeep to fix it.")
            return false
        }
        return try {
            context.startForegroundService(WatchService.startIntent(context, reason))
            true
        } catch (e: Exception) { // ForegroundServiceStartNotAllowedException and friends
            Graph.events.log("start refused ($reason): ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun missingPermission(context: Context, fromBackground: Boolean): String? = when {
        !granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> "location is not allowed"
        fromBackground && !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
            "location is not allowed all the time"
        else -> null
    }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
