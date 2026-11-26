package io.github.lrosa2.speedbeep.obd

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.lrosa2.speedbeep.engine.Elm327
import io.github.lrosa2.speedbeep.engine.EventLog

/** "Test adapter" in settings (spec §7.3): connect, set up, read one speed. [onDone] runs on the main thread. */
object ObdTest {
    fun run(context: Context, address: String, events: EventLog, onDone: (String) -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        fun log(text: String) {
            main.post { events.log(text) }
        }
        Thread({
            val result = try {
                val link = ObdLinks.open(app, address)
                try {
                    val elm = Elm327(link, ::log)
                    val banner = elm.open()
                    val speed = elm.speedKmh()
                    "Connected to $banner. " +
                        (speed?.let { "Car speed: $it km/h." } ?: "The car sent no speed: is the ignition on?")
                } finally {
                    link.close()
                }
            } catch (e: Exception) {
                "Adapter test failed: ${e.message ?: e.javaClass.simpleName}"
            }
            log("adapter test: $result")
            main.post { onDone(result) }
        }, "SpeedBeep-OBD-test").apply { isDaemon = true }.start()
    }
}
