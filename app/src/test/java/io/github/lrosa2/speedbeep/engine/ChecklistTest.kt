package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChecklistTest {
    private val ok = SetupState(
        notifications = true, location = true, backgroundLocation = true, carLinked = true,
        bluetoothNeeded = false, bluetoothAllowed = false, batteryUnrestricted = true,
        alarmVolumeOn = true, output = Output.PHONE_SPEAKER,
    )

    @Test
    fun `nothing to do when everything is set up`() {
        assertEquals(emptyList<SetupItem>(), Checklist.missing(ok))
    }

    @Test
    fun `location all the time is asked only after location`() {
        assertEquals(listOf(SetupItem.LOCATION), Checklist.missing(ok.copy(location = false, backgroundLocation = false)))
        assertEquals(listOf(SetupItem.BACKGROUND_LOCATION), Checklist.missing(ok.copy(backgroundLocation = false)))
    }

    @Test
    fun `a silent alarm volume matters only on the phone speaker`() {
        assertEquals(listOf(SetupItem.ALARM_VOLUME), Checklist.missing(ok.copy(alarmVolumeOn = false)))
        assertEquals(emptyList<SetupItem>(), Checklist.missing(ok.copy(alarmVolumeOn = false, output = Output.CAR_SPEAKERS)))
    }

    @Test
    fun `nearby devices matters only for the notification fallback`() {
        assertEquals(listOf(SetupItem.NEARBY_DEVICES), Checklist.missing(ok.copy(bluetoothNeeded = true)))
        assertEquals(emptyList<SetupItem>(), Checklist.missing(ok.copy(bluetoothNeeded = true, bluetoothAllowed = true)))
    }

    @Test
    fun `items come in setup order`() {
        val nothing = SetupState(false, false, false, false, true, false, false, false, Output.PHONE_SPEAKER)
        assertEquals(
            listOf(SetupItem.NOTIFICATIONS, SetupItem.LOCATION, SetupItem.CAR, SetupItem.NEARBY_DEVICES, SetupItem.BATTERY, SetupItem.ALARM_VOLUME),
            Checklist.missing(nothing),
        )
    }
}
