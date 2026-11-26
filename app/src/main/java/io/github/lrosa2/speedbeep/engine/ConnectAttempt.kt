package io.github.lrosa2.speedbeep.engine

/**
 * Lets another thread abort a connection attempt in progress: Bluetooth's connect() ignores
 * interrupts, but closing its socket makes it throw. Thread-safe.
 */
class ConnectAttempt {
    @Volatile
    var cancelled = false
        private set

    @Volatile
    private var closer: (() -> Unit)? = null

    /** Registers how to close the socket being connected; runs [close] at once if already cancelled. */
    fun onClose(close: () -> Unit) {
        closer = close
        if (cancelled) close()
    }

    fun cancel() {
        cancelled = true
        closer?.invoke()
    }
}
