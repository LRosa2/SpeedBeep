package io.github.lrosa2.speedbeep.engine

/** A connection to an ELM327 adapter: classic Bluetooth, or simulated in debug builds. Blocking: never on the main thread. */
interface ObdLink {
    /** Sends one command; the link adds the carriage return. */
    fun send(command: String)

    /** The reply up to the adapter's `>` prompt, without it. Throws [ObdException] after [timeoutMs]. */
    fun readReply(timeoutMs: Long): String

    fun close()
}

class ObdException(message: String) : Exception(message)

/** What one vehicle-speed request ("010D") returned. */
sealed interface SpeedReply {
    data class Speed(val kmh: Int) : SpeedReply

    /** The car sent no speed this time. */
    data object NoData : SpeedReply

    data class Failed(val text: String) : SpeedReply
}

/**
 * The part of the ELM327 protocol SpeedBeep needs (spec §4.5): reset and set up the adapter,
 * then ask for the vehicle speed, mode 01 PID 0D, one byte in km/h. [log] gets the adapter's
 * replies during setup, for the event log.
 */
class Elm327(private val link: ObdLink, private val log: (String) -> Unit) {
    companion object {
        const val RESET_TIMEOUT_MS = 5_000L
        const val COMMAND_TIMEOUT_MS = 2_000L

        /** The first request makes the adapter search for the car's protocol, which can take several seconds. */
        const val SEARCH_TIMEOUT_MS = 15_000L
        const val MAX_FAILURES_IN_A_ROW = 3
        private val SETUP = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")

        /**
         * Reads a reply to "010D". Tolerates the echo, spaces, NUL bytes, line breaks,
         * "SEARCHING...", headers and several control units; with several speeds the highest counts (D10).
         */
        fun parseSpeed(reply: String): SpeedReply {
            val lines = reply.uppercase().split('\r', '\n')
                .map { line -> line.filter { it > ' ' } }
                .filter { it.isNotEmpty() && it != "SEARCHING..." && it != "010D" }
            val speeds = lines.mapNotNull { line ->
                val at = line.indexOf("410D")
                if (at >= 0 && line.length >= at + 6) line.substring(at + 4, at + 6).toIntOrNull(16) else null
            }
            return when {
                speeds.isNotEmpty() -> SpeedReply.Speed(speeds.max())
                "NODATA" in lines -> SpeedReply.NoData
                else -> SpeedReply.Failed(lines.joinToString(" ").ifEmpty { "empty reply" })
            }
        }

        private fun String.oneLine() =
            lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" | ").ifEmpty { "(empty)" }
    }

    private var failuresInARow = 0

    /** Resets and sets up the adapter and wakes the car's bus. Returns the adapter's banner, such as "ELM327 v1.5". */
    fun open(): String {
        val banner = command("ATZ", RESET_TIMEOUT_MS).lines().map { it.trim() }
            .lastOrNull { it.isNotEmpty() && it != "ATZ" } ?: "unknown adapter"
        log("adapter: $banner")
        for (c in SETUP) {
            val reply = command(c, COMMAND_TIMEOUT_MS)
            // Cheap copies skip some settings; the speed parser copes with what they leave on.
            if ("OK" !in reply.uppercase()) log("adapter: $c → ${reply.oneLine()}")
        }
        val first = command("010D", SEARCH_TIMEOUT_MS)
        log("adapter: first speed reply ${first.oneLine()}")
        if (parseSpeed(first) is SpeedReply.Failed) throw ObdException("the car does not answer: ${first.oneLine()}")
        log("adapter: protocol ${command("ATDPN", COMMAND_TIMEOUT_MS).oneLine()}")
        return banner
    }

    /** One speed request: km/h, or null when there is no reading this time. Throws [ObdException] after [MAX_FAILURES_IN_A_ROW] failures. */
    fun speedKmh(): Int? = when (val reply = parseSpeed(command("010D", COMMAND_TIMEOUT_MS))) {
        is SpeedReply.Speed -> {
            failuresInARow = 0
            reply.kmh
        }
        SpeedReply.NoData -> {
            failuresInARow = 0
            null
        }
        is SpeedReply.Failed -> {
            failuresInARow++
            if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                throw ObdException("speed request failed $failuresInARow times: ${reply.text}")
            }
            null
        }
    }

    private fun command(c: String, timeoutMs: Long): String {
        link.send(c)
        return link.readReply(timeoutMs)
    }
}
