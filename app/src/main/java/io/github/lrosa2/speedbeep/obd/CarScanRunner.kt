package io.github.lrosa2.speedbeep.obd

import android.content.Context
import io.github.lrosa2.speedbeep.engine.CarScan
import io.github.lrosa2.speedbeep.engine.ConnectAttempt
import io.github.lrosa2.speedbeep.engine.EventLog
import io.github.lrosa2.speedbeep.engine.ObdException
import io.github.lrosa2.speedbeep.engine.ObdLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Runs [CarScan] on its own thread and writes it to a file in [dir], line by line, so a scan
 * cut short still keeps what it found (spec §4.6). The state lives here, not in the screen,
 * so it survives the settings screen being left and reopened.
 */
object CarScanRunner {
    sealed interface State {
        data object Idle : State

        data class Running(val progress: String) : State

        data class Done(val text: String) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState

    val running: Boolean get() = mutableState.value is State.Running

    @Volatile private var cancelReason: String? = null

    @Volatile private var attempt: ConnectAttempt? = null

    @Volatile private var link: ObdLink? = null

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "scans")

    fun latest(context: Context): File? =
        dir(context).listFiles { f -> f.name.startsWith("car-scan-") && f.name.endsWith(".txt") }?.maxByOrNull { it.name }

    /** Main thread. Ignored while a scan runs. */
    fun start(context: Context, address: String, events: EventLog) {
        if (running) return
        val app = context.applicationContext
        cancelReason = null
        mutableState.value = State.Running("Connecting to the adapter")
        events.log("car scan started")
        Thread({
            val text = scan(app, address)
            events.log("car scan: $text")
            mutableState.value = State.Done(text)
        }, "SpeedBeep-scan").apply { isDaemon = true }.start()
    }

    /** Any thread. Stops a running scan within a couple of seconds; what it found so far stays in the file. */
    fun cancel(reason: String) {
        if (!running) return
        cancelReason = reason
        attempt?.cancel()
        link?.close()
    }

    private fun scan(app: Context, address: String): String {
        val dir = dir(app)
        dir.mkdirs()
        val file = File(dir, "car-scan-${LocalDateTime.now().format(STAMP)}.txt")
        return try {
            val a = ConnectAttempt()
            attempt = a
            if (cancelReason != null) throw ObdException("stopped")
            val l = ObdLinks.open(app, address, a)
            link = l
            try {
                file.bufferedWriter().use { w ->
                    fun out(line: String) {
                        w.write(line)
                        w.newLine()
                        w.flush()
                    }
                    val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
                    out("# SpeedBeep car scan v1 · SpeedBeep $version · ${LocalDateTime.now()}")
                    val result = CarScan(
                        l,
                        ::out,
                        progress = { mutableState.value = State.Running(it) },
                        cancelled = { cancelReason },
                    ).run()
                    val units = "${result.responders.size} control units answered"
                    when {
                        cancelReason != null -> "stopped ($cancelReason) after $units; saved as ${file.name}"
                        result.stoppedBy != null -> "stopped: ${result.stoppedBy}; $units; saved as ${file.name}"
                        else -> "done: $units; saved as ${file.name}"
                    }
                }
            } finally {
                l.close()
            }
        } catch (e: Exception) {
            cancelReason?.let { "stopped ($it)" } ?: "failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            attempt = null
            link = null
        }
    }
}
