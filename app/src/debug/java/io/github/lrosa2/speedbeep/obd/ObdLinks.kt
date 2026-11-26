package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.ConnectAttempt
import io.github.lrosa2.speedbeep.engine.ObdException
import io.github.lrosa2.speedbeep.engine.ObdLink

/** Debug builds also offer simulated adapters, so the whole path runs on the emulator. */
object ObdLinks {
    const val SIMULATED = "SIMULATED"
    const val SIMULATED_UNPLUGGED = "SIMULATED-UNPLUGGED"

    val extraChoices: List<Pair<String, String>> = listOf(
        "Simulated adapter (debug)" to SIMULATED,
        "Simulated adapter, unplugged (debug)" to SIMULATED_UNPLUGGED,
    )

    /** [attempt] lets [ObdSource.stop] abort a connection in progress. */
    fun open(context: Context, address: String, attempt: ConnectAttempt = ConnectAttempt()): ObdLink = when (address) {
        SIMULATED -> SimulatedObdLink()
        SIMULATED_UNPLUGGED -> {
            Thread.sleep(2_000) // a real failed connection blocks for several seconds
            throw ObdException("cannot connect to the adapter (simulated: unplugged)")
        }
        else -> RfcommLink.open(context, address, attempt)
    }
}
