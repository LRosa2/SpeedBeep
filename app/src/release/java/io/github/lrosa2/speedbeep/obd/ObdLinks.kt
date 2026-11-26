package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.ConnectAttempt
import io.github.lrosa2.speedbeep.engine.ObdLink

/** Release builds talk to real adapters only. */
object ObdLinks {
    val extraChoices: List<Pair<String, String>> = emptyList()

    /** [attempt] lets [ObdSource.stop] abort a connection in progress. */
    fun open(context: Context, address: String, attempt: ConnectAttempt = ConnectAttempt()): ObdLink = RfcommLink.open(context, address, attempt)
}
