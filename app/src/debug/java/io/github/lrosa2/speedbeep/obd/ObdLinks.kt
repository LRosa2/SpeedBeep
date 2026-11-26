package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.ConnectAttempt
import io.github.lrosa2.speedbeep.engine.ObdLink

/** Debug builds also offer a simulated adapter, so the whole path runs on the emulator. */
object ObdLinks {
    const val SIMULATED = "SIMULATED"

    val extraChoices: List<Pair<String, String>> = listOf("Simulated adapter (debug)" to SIMULATED)

    /** [attempt] lets [ObdSource.stop] abort a connection in progress. */
    fun open(context: Context, address: String, attempt: ConnectAttempt = ConnectAttempt()): ObdLink =
        if (address == SIMULATED) SimulatedObdLink() else RfcommLink.open(context, address, attempt)
}
