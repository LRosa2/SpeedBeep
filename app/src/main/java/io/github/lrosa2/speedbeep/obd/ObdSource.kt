package io.github.lrosa2.speedbeep.obd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.ConnectAttempt
import io.github.lrosa2.speedbeep.engine.Elm327
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.ObdLink

/**
 * Reads the car's speed from the OBD adapter on its own thread, once a second (spec §4.5), and
 * hands each reading to [onSpeed] on the main thread. Reconnects every 5 s after a failure.
 */
class ObdSource(
    context: Context,
    private val address: String,
    private val events: EventLog,
    private val onSpeed: (Int) -> Unit,
) {
    companion object {
        const val POLL_MS = 1_000L
        const val RETRY_MS = 5_000L
    }

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false

    @Volatile private var link: ObdLink? = null

    @Volatile private var attempt: ConnectAttempt? = null
    private var thread: Thread? = null

    // OBD thread only.
    private val setupLines = HashSet<String>()
    private var lastProblem: String? = null

    fun start() {
        running = true
        thread = Thread(::loop, "SpeedBeep-OBD").apply {
            isDaemon = true
            start()
        }
    }

    /** Main thread. No reading reaches [onSpeed] after this returns. */
    fun stop() {
        running = false
        attempt?.cancel() // aborts a connect in progress
        link?.close() // unblocks a read
        thread?.interrupt()
        thread = null
        main.removeCallbacksAndMessages(null)
    }

    private fun loop() {
        while (running) {
            try {
                connectAndRead()
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) { // ObdException, or SecurityException / IllegalArgumentException from Bluetooth
                if (running) problem(e.message ?: e.javaClass.simpleName)
            } finally {
                link?.close()
                link = null
            }
            try {
                Thread.sleep(RETRY_MS)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun connectAndRead() {
        val a = ConnectAttempt()
        attempt = a
        if (!running) return
        val l = ObdLinks.open(context, address, a)
        link = l
        if (!running) return
        // Setup replies are logged once per watch, not on every reconnect.
        val elm = Elm327(l) { if (setupLines.add(it)) post(it) }
        val banner = elm.open()
        post("adapter connected ($banner)")
        lastProblem = null
        while (running) {
            val started = SystemClock.elapsedRealtime()
            elm.speedKmh()?.let { kmh -> main.post { if (running) onSpeed(kmh) } }
            Thread.sleep((POLL_MS - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
        }
    }

    /** Logged once per kind of problem, not every 5 s while the adapter stays out of reach. */
    private fun problem(text: String) {
        if (text != lastProblem) post("adapter: $text; retrying every ${RETRY_MS / 1_000} s")
        lastProblem = text
    }

    private fun post(text: String) {
        main.post { events.log(text) }
    }
}
