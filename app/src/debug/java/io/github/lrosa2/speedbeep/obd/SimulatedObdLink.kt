package io.github.lrosa2.speedbeep.obd

import android.os.SystemClock
import io.github.lrosa2.speedbeep.engine.ObdLink

/**
 * A pretend ELM327 for emulator checks. The speed follows a 90 s loop: 0 → 56 km/h over
 * 15 s, 60 until 45 s, down by 2 km/h a second to 30, then 30 until the loop restarts.
 * For the car scan it has an engine unit at 7E0 and a tyre unit behind the gateway at 750/A7.
 */
class SimulatedObdLink(private val clock: () -> Long = SystemClock::elapsedRealtime) : ObdLink {
    private val startMs = clock()
    private var reply = ""
    private var header = "7DF"
    private var ext: String? = null

    override fun send(command: String) {
        reply = when {
            command == "ATZ" || command == "ATI" -> "\r\rELM327 v1.5 (simulated)\r\r"
            command == "010D" -> "41 0D %02X\r\r".format(speedKmh((clock() - startMs) / 1_000 % 90))
            command == "ATDPN" -> "A6\r\r"
            command == "ATRV" -> "12.4V\r\r"
            command.startsWith("ATSH ") -> "OK\r\r".also { header = command.substringAfter(' ') }
            command == "ATCEA" -> "OK\r\r".also { ext = null }
            command.startsWith("ATCEA ") -> "OK\r\r".also { ext = command.substringAfter(' ') }
            command == "01 00" -> "7E8 06 41 00 BE 3E B8 11 \r\r"
            command.startsWith("21 ") || command.startsWith("22 ") -> unit(ext?.let { "$header/$it" } ?: header, command)
            else -> "OK\r\r"
        }
    }

    override fun readReply(timeoutMs: Long): String {
        Thread.sleep(50)
        return reply
    }

    override fun close() = Unit

    private fun unit(target: String, request: String): String = when (target) {
        "7E0" -> when {
            request == "21 01" -> "7E8 03 61 01 00 \r\r"
            request.startsWith("21") -> "7E8 03 7F 21 31 \r\r"
            else -> "7E8 03 7F 22 11 \r\r"
        }
        "750/A7" -> when (request) {
            "21 30" -> "758 A7 10 0B 61 30 00 9C 00 \r758 A7 21 90 00 8D 00 9A \r\r"
            else -> "758 A7 03 7F ${request.take(2)} 12 \r\r"
        }
        else -> "NO DATA\r\r"
    }

    private fun speedKmh(s: Long): Int = when {
        s < 15 -> (s * 4).toInt()
        s < 45 -> 60
        s < 60 -> (60 - (s - 45) * 2).toInt()
        else -> 30
    }
}
