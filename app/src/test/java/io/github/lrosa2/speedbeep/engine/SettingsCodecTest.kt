package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.RulesConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsCodecTest {
    @Test
    fun `no saved settings gives the spec defaults`() {
        val s = SettingsCodec.decode(emptyMap())
        assertEquals(53, s.thresholdKmh)
        assertEquals(Mode.SMART, s.mode)
        assertEquals(2_000, s.mainBeepMs)
        assertEquals(10, s.reminderIntervalS)
        assertEquals(SoundKind.BEEP, s.sound)
        assertEquals(80, s.volumePercent)
        assertEquals(Output.PHONE_SPEAKER, s.output)
        assertEquals(10, s.pauseMin)
        assertTrue(s.keepScreenOn)
        assertFalse(s.recordDrives)
        assertEquals(CarStart.AUTOMATIC, s.carStart)
        assertNull(s.carAddress)
    }

    @Test
    fun `settings survive a save and load`() {
        val s = Settings(
            thresholdKmh = 60, mode = Mode.SIMPLE, mainBeepMs = 3_500, reminderIntervalS = 0,
            sound = SoundKind.PHONE_ALARM, alarmUri = "content://media/1", alarmTitle = "Argon",
            volumePercent = 55, output = Output.CAR_SPEAKERS, pauseMin = 25, keepScreenOn = false,
            recordDrives = true, carStart = CarStart.NOTIFICATION, carAddress = "AA:BB:CC:DD:EE:FF", carName = "Yaris",
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `values outside the ranges are pulled in`() {
        val s = SettingsCodec.decode(
            mapOf("thresholdKmh" to 5, "mainBeepMs" to 60_000, "reminderIntervalS" to 1, "volumePercent" to 140, "pauseMin" to 0),
        )
        assertEquals(20, s.thresholdKmh)
        assertEquals(10_000, s.mainBeepMs)
        assertEquals(3, s.reminderIntervalS)
        assertEquals(100, s.volumePercent)
        assertEquals(1, s.pauseMin)
    }

    @Test
    fun `reminders can be turned off`() {
        assertEquals(0, SettingsCodec.decode(mapOf("reminderIntervalS" to 0)).reminderIntervalS)
    }

    @Test
    fun `an unknown saved name falls back to the default`() {
        assertEquals(Mode.SMART, SettingsCodec.decode(mapOf("mode" to "TURBO")).mode)
    }

    @Test
    fun `a phone alarm sound without its address falls back to the beep`() {
        assertEquals(SoundKind.BEEP, SettingsCodec.decode(mapOf("sound" to "PHONE_ALARM")).sound)
    }

    @Test
    fun `the rules get threshold, reminder interval and mode`() {
        assertEquals(
            RulesConfig(thresholdKmh = 60, reminderIntervalMs = 15_000, simpleMode = true),
            Settings(thresholdKmh = 60, reminderIntervalS = 15, mode = Mode.SIMPLE).toRulesConfig(),
        )
    }

    @Test
    fun `the threshold stepper stays in range`() {
        assertEquals(54, Settings().stepThreshold(+1).thresholdKmh)
        assertEquals(150, Settings(thresholdKmh = 150).stepThreshold(+1).thresholdKmh)
        assertEquals(20, Settings(thresholdKmh = 20).stepThreshold(-1).thresholdKmh)
    }

    @Test
    fun `the reminder stepper goes from off straight to 3 s and back`() {
        assertEquals(3, Settings(reminderIntervalS = 0).stepReminder(+1).reminderIntervalS)
        assertEquals(0, Settings(reminderIntervalS = 3).stepReminder(-1).reminderIntervalS)
        assertEquals(11, Settings(reminderIntervalS = 10).stepReminder(+1).reminderIntervalS)
        assertEquals(60, Settings(reminderIntervalS = 60).stepReminder(+1).reminderIntervalS)
    }

    @Test
    fun `the OBD adapter survives a save and load, and settings saved by 0_1_0 have none`() {
        val s = Settings(mode = Mode.SIMPLE, obdAddress = "00:1D:A5:68:98:8B", obdName = "OBDII")
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        val saved010 = SettingsCodec.decode(mapOf("thresholdKmh" to 55, "mode" to "SIMPLE"))
        assertNull(saved010.obdAddress)
        assertEquals(55, saved010.thresholdKmh)
    }
}
