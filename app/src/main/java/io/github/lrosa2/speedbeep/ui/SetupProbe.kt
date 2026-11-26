package io.github.lrosa2.speedbeep.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import io.github.lrosa2.speedbeep.engine.CarStart
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SetupItem
import io.github.lrosa2.speedbeep.engine.SetupState

/** Reads the phone's state for the checklist, and runs each item's fix. */
object SetupProbe {
    fun state(context: Context, s: Settings) = SetupState(
        notifications = granted(context, Manifest.permission.POST_NOTIFICATIONS),
        location = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        carLinked = s.carAddress != null,
        bluetoothNeeded = s.carStart == CarStart.NOTIFICATION || s.obdAddress != null,
        bluetoothAllowed = granted(context, Manifest.permission.BLUETOOTH_CONNECT),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        alarmVolumeOn = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) > 0,
        output = s.output,
    )

    fun fix(item: SetupItem, context: Context, request: (Array<String>) -> Unit, openSettings: () -> Unit) {
        when (item) {
            SetupItem.NOTIFICATIONS -> request(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            SetupItem.LOCATION -> request(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            // Android shows its settings page here, where the owner picks "Allow all the time".
            SetupItem.BACKGROUND_LOCATION -> request(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            SetupItem.CAR -> openSettings()
            SetupItem.NEARBY_DEVICES -> request(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            SetupItem.BATTERY -> context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            )
            SetupItem.ALARM_VOLUME -> context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
