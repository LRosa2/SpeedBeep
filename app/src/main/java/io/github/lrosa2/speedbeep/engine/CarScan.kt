package io.github.lrosa2.speedbeep.engine

/**
 * "Scan car data" (spec §4.6): finds the car's control units through the ELM327 adapter, asks each
 * for its data, and writes every command and raw reply to [out]. The first aim is the tyre
 * pressures, whose request is not known for the owner's car.
 *
 * Read-only by construction: [send] refuses anything [isAllowed] does not list, which is only
 * adapter settings and the read services 01, 21 and 22. Never 09 either: the VIN would end up in
 * a file the owner shares.
 */
class CarScan(
    private val link: ObdLink,
    private val out: (String) -> Unit,
    private val progress: (String) -> Unit = {},
    private val cancelled: () -> Boolean = { false },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    companion object {
        /** Read services: current data (01), Toyota's local identifiers (21), data by identifier (22). */
        val READ_SERVICES = setOf("01", "21", "22")

        /** Adapter settings and questions; the last group takes a hex value (header, filter, sub-address, timeout). */
        private val ADAPTER_COMMANDS = setOf("ATZ", "ATI", "AT@1", "ATE0", "ATL0", "ATS1", "ATH1", "ATSP0", "ATDPN", "ATRV", "ATCEA")
        private val ADAPTER_SETTING = Regex("AT(ST|SH|CRA|CEA) [0-9A-F]{2,3}")
        private val HEX_REQUEST = Regex("[0-9A-F]{2}( [0-9A-F]{2})*")

        const val TIME_LIMIT_MS = 20 * 60_000L
        const val MAX_TIMEOUTS_IN_A_ROW = 3
        private const val RESET_TIMEOUT_MS = 5_000L
        private const val COMMAND_TIMEOUT_MS = 2_000L
        private const val SEARCH_TIMEOUT_MS = 15_000L

        /** Toyota's gateway: the body control units sit behind it, each at its own sub-address. */
        private const val GATEWAY = "750"

        /** Sub-address and request worth trying first: a GR Yaris owner's reading, and the older Toyota guess. */
        private val GUESSES = listOf("2A" to "22 10 05", "A7" to "21 30")

        fun isAllowed(command: String): Boolean =
            if (command.startsWith("AT")) {
                command in ADAPTER_COMMANDS || ADAPTER_SETTING.matches(command)
            } else {
                HEX_REQUEST.matches(command) && command.take(2) in READ_SERVICES
            }

        /** What a reply to [request] says, with or without headers, spaces and several frames. */
        fun classify(reply: String, request: String): Answer {
            val s = reply.uppercase().filter { it > ' ' }
            val req = request.uppercase().filter { it > ' ' }
            val service = req.take(2)
            val positive = "%02X".format(service.toInt(16) + 0x40) + req.drop(2)
            return when {
                "7F${service}11" in s -> Answer.NOT_SUPPORTED
                positive in s -> Answer.POSITIVE
                "7F$service" in s -> Answer.REJECTED
                else -> Answer.NONE
            }
        }
    }

    enum class Answer { NONE, POSITIVE, REJECTED, NOT_SUPPORTED }

    /** [responders]: control units that answered, as "7E0" or "750/A7". [stoppedBy]: why the scan ended early, or null. */
    data class Result(val responders: List<String>, val stoppedBy: String?)

    private class Stop(val reason: String) : Exception(reason)

    private data class ControlUnit(val header: String, val ext: String?, val answer: Answer) {
        val label get() = ext?.let { "$header/$it" } ?: header
    }

    private val startMs = clock()
    private var timeoutsInARow = 0
    private val found = mutableListOf<ControlUnit>()

    fun run(): Result {
        val stoppedBy = try {
            setup()
            gateway()
            elevenBit()
            for ((i, u) in found.toList().withIndex()) dump(u, i + 1)
            out("## End")
            command("ATRV")
            null
        } catch (e: Stop) {
            out("## Stopped: ${e.reason}")
            e.reason
        }
        out("# Control units that answered: ${found.joinToString { "${it.label} (${it.answer})" }.ifEmpty { "none" }}")
        return Result(found.map { it.label }, stoppedBy)
    }

    private fun setup() {
        out("## Adapter and car")
        progress("Setting up the adapter")
        command("ATZ", RESET_TIMEOUT_MS)
        for (c in listOf("ATE0", "ATL0", "ATS1", "ATH1", "ATSP0", "ATI", "AT@1", "ATRV")) command(c)
        val first = command("01 00", SEARCH_TIMEOUT_MS)
        if (classify(first, "01 00") == Answer.NONE) throw Stop("the car does not answer; is the power on?")
        command("ATDPN")
        command("ATST 32") // 200 ms for a control unit that is not there
    }

    /** The guesses, then every sub-address behind the gateway. */
    private fun gateway() {
        out("## Gateway $GATEWAY, sub-addresses")
        command("ATSH $GATEWAY")
        command("ATCRA 758")
        var first = true
        for ((ext, request) in GUESSES) {
            val ok = setExt(ext)
            if (first && !ok) {
                out("# The adapter refuses CAN extended addressing (ATCEA): the sub-addresses behind $GATEWAY are skipped.")
                return
            }
            first = false
            command(request)
        }
        for (n in 0..0xFF) {
            progress("Gateway sub-address ${n + 1} of 256")
            val ext = "%02X".format(n)
            setExt(ext)
            discover(GATEWAY, ext)
        }
        command("ATCEA")
    }

    /** Every 11-bit request address 700–7F7 but the broadcast 7DF; replies come from the address + 8. */
    private fun elevenBit() {
        out("## 11-bit addresses")
        for (id in 0x700..0x7F7) {
            if (id == 0x7DF) continue
            progress("Address %03X".format(id))
            command("ATSH %03X".format(id))
            command("ATCRA %03X".format(id + 8))
            discover("%03X".format(id), null)
        }
    }

    private fun discover(header: String, ext: String?) {
        val answer = classify(command("21 01"), "21 01")
        if (answer != Answer.NONE) found += ControlUnit(header, ext, answer)
    }

    /** All local identifiers of a unit that knows service 21; a block of service 22 identifiers otherwise. */
    private fun dump(u: ControlUnit, n: Int) {
        out("## Data of ${u.label}")
        progress("Reading control unit $n of ${found.size} (${u.label})")
        command("ATSH ${u.header}")
        command("ATCRA %03X".format(u.header.toInt(16) + 8))
        if (u.ext != null) setExt(u.ext) else command("ATCEA")
        if (u.answer == Answer.NOT_SUPPORTED) {
            for (id in 0x1000..0x103F) command("22 %02X %02X".format(id shr 8, id and 0xFF))
        } else {
            for (id in 0x02..0xFF) command("21 %02X".format(id))
        }
    }

    private fun setExt(ext: String) = "OK" in command("ATCEA $ext").uppercase()

    /** Sends [c] and writes it and its reply; a timeout is written too. */
    private fun command(c: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): String {
        check(isAllowed(c)) { "not a read-only command: $c" }
        if (cancelled()) throw Stop("stopped by the owner")
        if (clock() - startMs >= TIME_LIMIT_MS) throw Stop("time limit of ${TIME_LIMIT_MS / 60_000} min")
        out("> $c")
        val reply = try {
            link.send(c)
            link.readReply(timeoutMs).also { timeoutsInARow = 0 }
        } catch (e: ObdException) {
            out("< (no reply: ${e.message})")
            if (++timeoutsInARow >= MAX_TIMEOUTS_IN_A_ROW) throw Stop("the adapter stopped answering")
            return ""
        }
        val lines = reply.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) out("< (empty)") else lines.forEach { out("< $it") }
        return reply
    }
}
