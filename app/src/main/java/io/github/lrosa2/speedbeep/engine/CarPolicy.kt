package io.github.lrosa2.speedbeep.engine

enum class CarAction { START_WATCH, STOP_WATCH, SHOW_STOPPED_NOTE, HIDE_STOPPED_NOTE }

/**
 * When the linked car comes and goes (spec §5.1, §5.3). Times are monotonic milliseconds;
 * call [onTick] at [checkAtMs]. Transport-free: the companion route and the §5.1 fallback
 * both feed it.
 */
class CarPolicy {
    companion object {
        const val GRACE_MS = 30_000L
    }

    var present = false
        private set
    private var goneSinceMs: Long? = null
    private var stoppedByOwner = false

    /** When [onTick] must next run, or null. */
    val checkAtMs: Long? get() = goneSinceMs?.plus(GRACE_MS)

    fun onCarAppeared(): List<CarAction> {
        if (goneSinceMs != null) { // back within the grace period
            goneSinceMs = null
            return if (stoppedByOwner) listOf(CarAction.SHOW_STOPPED_NOTE) else emptyList()
        }
        if (present) return emptyList()
        present = true
        stoppedByOwner = false
        return listOf(CarAction.START_WATCH)
    }

    fun onCarGone(nowMs: Long): List<CarAction> {
        if (!present || goneSinceMs != null) return emptyList()
        goneSinceMs = nowMs
        return if (stoppedByOwner) listOf(CarAction.HIDE_STOPPED_NOTE) else emptyList()
    }

    fun onTick(nowMs: Long): List<CarAction> {
        val since = goneSinceMs ?: return emptyList()
        if (nowMs - since < GRACE_MS) return emptyList()
        present = false
        goneSinceMs = null
        stoppedByOwner = false
        return listOf(CarAction.STOP_WATCH)
    }

    fun onOwnerStop(): List<CarAction> {
        if (!present) return emptyList()
        stoppedByOwner = true
        return if (goneSinceMs == null) listOf(CarAction.SHOW_STOPPED_NOTE) else emptyList()
    }

    fun onWatchStarted(): List<CarAction> {
        if (!stoppedByOwner) return emptyList()
        stoppedByOwner = false
        return listOf(CarAction.HIDE_STOPPED_NOTE)
    }
}
