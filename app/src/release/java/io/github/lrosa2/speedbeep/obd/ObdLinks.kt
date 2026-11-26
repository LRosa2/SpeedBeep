package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.ObdLink

/** Release builds talk to real adapters only. */
object ObdLinks {
    val extraChoices: List<Pair<String, String>> = emptyList()

    fun open(context: Context, address: String): ObdLink = RfcommLink.open(context, address)
}
