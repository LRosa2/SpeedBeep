package io.github.lrosa2.speedbeep.watch

import android.content.Context
import io.github.lrosa2.speedbeep.engine.EventLog

object LocationSources {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, events: EventLog): LocationSource = GpsSource(context)
}
