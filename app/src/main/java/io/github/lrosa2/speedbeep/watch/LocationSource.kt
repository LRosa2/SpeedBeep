package io.github.lrosa2.speedbeep.watch

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import io.github.lrosa2.speedbeep.core.rules.Fix

/** Supplies fixes on the main thread (spec §3). */
interface LocationSource {
    val name: String
    fun start(onFix: (Fix) -> Unit)
    fun stop()
}

fun Location.toFix() = Fix(
    elapsedMs = elapsedRealtimeNanos / 1_000_000,
    lat = latitude,
    lon = longitude,
    speedMps = if (hasSpeed()) speed else null,
    speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    horizontalAccuracyM = if (hasAccuracy()) accuracy else null,
    bearingDeg = if (hasBearing()) bearing else null,
)

/** LocationManager GPS at 1 Hz, no Play Services. */
class GpsSource(context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = context.mainExecutor
    private var listener: LocationListener? = null

    override val name = "GPS"

    // WatchStarter checks the permission; WatchService catches the SecurityException anyway.
    @SuppressLint("MissingPermission")
    override fun start(onFix: (Fix) -> Unit) {
        val l = LocationListener { onFix(it.toFix()) }
        listener = l
        val request = LocationRequest.Builder(1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, executor, l)
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }
}
