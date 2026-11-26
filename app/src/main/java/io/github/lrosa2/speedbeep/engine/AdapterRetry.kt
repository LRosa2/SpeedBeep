package io.github.lrosa2.speedbeep.engine

/** Where the OBD adapter's Bluetooth link stands during one watch (spec §4.5). */
enum class AdapterState {
    /** No adapter chosen. */
    NONE,

    /** The first connection of this watch is under way. */
    CONNECTING,

    /** The phone reached the adapter over Bluetooth; the car may or may not answer. */
    REACHED,

    /** The last Bluetooth connection failed; SpeedBeep keeps trying. */
    UNREACHABLE,

    /** Out of reach for [AdapterRetry.WINDOW_MS]: no more tries until the next watch. */
    GAVE_UP,
}

/**
 * How long SpeedBeep keeps trying to reach an adapter that is out of Bluetooth reach (spec §4.5):
 * [windowMs] from the first failed connection, and again from the next failure after the adapter
 * was reached. Failures once the adapter is reached (the car does not answer) do not count.
 */
class AdapterRetry(private val windowMs: Long = WINDOW_MS) {
    companion object {
        const val WINDOW_MS = 5 * 60_000L
    }

    private var unreachableSinceMs: Long? = null

    fun reached() {
        unreachableSinceMs = null
    }

    /** One failed Bluetooth connection at [nowMs]. False once the adapter has been out of reach for the whole window. */
    fun keepTrying(nowMs: Long): Boolean {
        val since = unreachableSinceMs ?: nowMs.also { unreachableSinceMs = it }
        return nowMs - since < windowMs
    }
}
