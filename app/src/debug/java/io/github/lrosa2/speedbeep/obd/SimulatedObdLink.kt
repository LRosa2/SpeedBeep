package io.github.lrosa2.speedbeep.obd

import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.ObdLink

/**
 * A pretend ELM327 for emulator checks. The speed follows a 90 s loop: 0 → 56 km/h over
 * 15 s, 60 until 45 s, down by 2 km/h a second to 30, then 30 until the loop restarts.
 */
class SimulatedObdLink(private val clock: () -> Long = SystemClock::elapsedRealtime) : ObdLink {
    private val startMs = clock()
    private var reply = ""

    override fun send(command: String) {
        reply = when (command) {
            "ATZ" -> "\r\rELM327 v1.5 (simulated)\r\r"
            "010D" -> "41 0D %02X\r\r".format(speedKmh((clock() - startMs) / 1_000 % 90))
            "ATDPN" -> "A6\r\r"
            else -> "OK\r\r"
        }
    }

    override fun readReply(timeoutMs: Long): String {
        Thread.sleep(50)
        return reply
    }

    override fun close() = Unit

    private fun speedKmh(s: Long): Int = when {
        s < 15 -> (s * 4).toInt()
        s < 45 -> 60
        s < 60 -> (60 - (s - 45) * 2).toInt()
        else -> 30
    }
}
