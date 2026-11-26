package io.github.lrosa2.speedbeep.obd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.AdapterRetry
import io.github.lrosa2.speedbeep.engine.AdapterState
import io.github.lrosa2.speedbeep.engine.ConnectAttempt
import io.github.lrosa2.speedbeep.engine.Elm327
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.ObdLink

/**
 * Reads the car's speed from the OBD adapter on its own thread, once a second (spec §4.5), and
 * hands each reading to [onSpeed] and each change of the Bluetooth link to [onState], both on the
 * main thread. Retries every 5 s after a failure; an adapter out of Bluetooth reach is tried for
 * [AdapterRetry.WINDOW_MS], then not again during this watch.
 */
class ObdSource(
    context: Context,
    private val address: String,
    private val events: EventLog,
    private val onSpeed: (Int) -> Unit,
    private val onState: (AdapterState) -> Unit,
) {
    companion object {
        const val POLL_MS = 1_000L
        const val RETRY_MS = 5_000L
    }

    /** Opening the Bluetooth link failed: the adapter is unplugged, out of reach or refused us. */
    private class Unreachable(message: String) : Exception(message)

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false

    @Volatile private var link: ObdLink? = null

    @Volatile private var attempt: ConnectAttempt? = null
    private var thread: Thread? = null

    // OBD thread only.
    private val setupLines = HashSet<String>()
    private var lastProblem: String? = null
    private val retry = AdapterRetry()

    fun start() {
        running = true
        thread = Thread(::loop, "SpeedBeep-OBD").apply {
            isDaemon = true
            start()
        }
    }

    /** Main thread. No reading or state reaches the callbacks after this returns. */
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
            } catch (e: Unreachable) {
                if (!running) return
                if (!retry.keepTrying(SystemClock.elapsedRealtime())) {
                    post("adapter: out of reach for ${AdapterRetry.WINDOW_MS / 60_000} min (${e.message}); no more tries until the next watch")
                    state(AdapterState.GAVE_UP)
                    return
                }
                problem(e.message ?: "cannot connect")
                state(AdapterState.UNREACHABLE)
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
        val l = try {
            ObdLinks.open(context, address, a)
        } catch (e: Exception) {
            throw Unreachable(e.message ?: e.javaClass.simpleName)
        }
        link = l
        if (!running) return
        retry.reached()
        state(AdapterState.REACHED)
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

    private fun state(s: AdapterState) {
        main.post { if (running) onState(s) }
    }

    private fun post(text: String) {
        main.post { events.log(text) }
    }
}
