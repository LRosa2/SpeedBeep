package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.RulesConfig

enum class Mode { SMART, SIMPLE }

enum class Output { PHONE_SPEAKER, CAR_SPEAKERS }

enum class SoundKind { BEEP, DOUBLE_BEEP, CHIME, PHONE_ALARM }

/** How the linked car starts watching: the companion route (D12) or the §5.1 notification fallback (D16). */
enum class CarStart { AUTOMATIC, NOTIFICATION }

private val RULE_DEFAULTS = RulesConfig()

/** The owner's settings (spec §7.3). Always use [clamped] values. */
data class Settings(
    val thresholdKmh: Int = RULE_DEFAULTS.thresholdKmh,
    val mode: Mode = Mode.SMART,
    val mainBeepMs: Int = 2_000,
    /** 0 = no reminders. */
    val reminderIntervalS: Int = (RULE_DEFAULTS.reminderIntervalMs / 1_000).toInt(),
    val sound: SoundKind = SoundKind.BEEP,
    /** Only used with [SoundKind.PHONE_ALARM]. */
    val alarmUri: String? = null,
    val alarmTitle: String? = null,
    val volumePercent: Int = 80,
    val output: Output = Output.PHONE_SPEAKER,
    val pauseMin: Int = 10,
    val keepScreenOn: Boolean = true,
    val recordDrives: Boolean = false,
    val carStart: CarStart = CarStart.AUTOMATIC,
    /** Bluetooth address of the linked car, upper case; null when no car is linked. */
    val carAddress: String? = null,
    val carName: String? = null,
    /** The paired ELM327 adapter (spec §4.5), upper case; null when none is chosen. */
    val obdAddress: String? = null,
    val obdName: String? = null,
) {
    companion object {
        val THRESHOLD_KMH = 20..150
        val MAIN_BEEP_MS = 500..10_000
        val REMINDER_S = 3..60
        val VOLUME_PERCENT = 0..100
        val PAUSE_MIN = 1..60
    }

    val simpleMode: Boolean get() = mode == Mode.SIMPLE

    fun clamped(): Settings = copy(
        thresholdKmh = thresholdKmh.coerceIn(THRESHOLD_KMH),
        mainBeepMs = mainBeepMs.coerceIn(MAIN_BEEP_MS),
        reminderIntervalS = if (reminderIntervalS <= 0) 0 else reminderIntervalS.coerceIn(REMINDER_S),
        volumePercent = volumePercent.coerceIn(VOLUME_PERCENT),
        pauseMin = pauseMin.coerceIn(PAUSE_MIN),
        sound = if (sound == SoundKind.PHONE_ALARM && alarmUri == null) SoundKind.BEEP else sound,
    )

    fun toRulesConfig() = RulesConfig(
        thresholdKmh = thresholdKmh,
        reminderIntervalMs = reminderIntervalS * 1_000L,
        simpleMode = simpleMode,
    )

    fun stepThreshold(delta: Int) = copy(thresholdKmh = (thresholdKmh + delta).coerceIn(THRESHOLD_KMH))

    /** Off (0) and the 3–60 s range, with nothing in between. */
    fun stepReminder(delta: Int): Settings {
        val next = when {
            reminderIntervalS == 0 -> if (delta > 0) REMINDER_S.first else 0
            reminderIntervalS + delta < REMINDER_S.first -> 0
            else -> (reminderIntervalS + delta).coerceAtMost(REMINDER_S.last)
        }
        return copy(reminderIntervalS = next)
    }
}

/** Settings ⇄ plain key/value pairs (Int, Boolean or String), the shape DataStore stores. */
object SettingsCodec {
    fun encode(s: Settings): Map<String, Any> = buildMap {
        put("thresholdKmh", s.thresholdKmh)
        put("mode", s.mode.name)
        put("mainBeepMs", s.mainBeepMs)
        put("reminderIntervalS", s.reminderIntervalS)
        put("sound", s.sound.name)
        s.alarmUri?.let { put("alarmUri", it) }
        s.alarmTitle?.let { put("alarmTitle", it) }
        put("volumePercent", s.volumePercent)
        put("output", s.output.name)
        put("pauseMin", s.pauseMin)
        put("keepScreenOn", s.keepScreenOn)
        put("recordDrives", s.recordDrives)
        put("carStart", s.carStart.name)
        s.carAddress?.let { put("carAddress", it) }
        s.carName?.let { put("carName", it) }
        s.obdAddress?.let { put("obdAddress", it) }
        s.obdName?.let { put("obdName", it) }
    }

    fun decode(m: Map<String, Any?>): Settings {
        val d = Settings()
        fun int(key: String, default: Int) = m[key] as? Int ?: default
        fun bool(key: String, default: Boolean) = m[key] as? Boolean ?: default
        fun str(key: String) = m[key] as? String
        fun <E : Enum<E>> enum(key: String, default: E, values: Array<E>) = values.firstOrNull { it.name == m[key] } ?: default
        return Settings(
            thresholdKmh = int("thresholdKmh", d.thresholdKmh),
            mode = enum("mode", d.mode, Mode.entries.toTypedArray()),
            mainBeepMs = int("mainBeepMs", d.mainBeepMs),
            reminderIntervalS = int("reminderIntervalS", d.reminderIntervalS),
            sound = enum("sound", d.sound, SoundKind.entries.toTypedArray()),
            alarmUri = str("alarmUri"),
            alarmTitle = str("alarmTitle"),
            volumePercent = int("volumePercent", d.volumePercent),
            output = enum("output", d.output, Output.entries.toTypedArray()),
            pauseMin = int("pauseMin", d.pauseMin),
            keepScreenOn = bool("keepScreenOn", d.keepScreenOn),
            recordDrives = bool("recordDrives", d.recordDrives),
            carStart = enum("carStart", d.carStart, CarStart.entries.toTypedArray()),
            carAddress = str("carAddress"),
            carName = str("carName"),
            obdAddress = str("obdAddress"),
            obdName = str("obdName"),
        ).clamped()
    }
}
