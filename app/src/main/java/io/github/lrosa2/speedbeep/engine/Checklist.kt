package io.github.lrosa2.speedbeep.engine

/** One line of the setup checklist on the main screen (spec §7.3), with its fix button's label. */
enum class SetupItem(val title: String, val fix: String) {
    NOTIFICATIONS("Allow notifications, so the controls show on the lock screen", "Allow"),
    LOCATION("Allow location", "Allow"),
    BACKGROUND_LOCATION("Allow location all the time, so the car can start SpeedBeep", "Allow"),
    CAR("Link your car", "Link"),
    NEARBY_DEVICES("Allow nearby devices, so SpeedBeep sees the car connect", "Allow"),
    BATTERY("Turn off battery optimisation (recommended)", "Turn off"),
    ALARM_VOLUME("The alarm volume is off: beeps on the phone speaker are silent", "Volume"),
}

data class SetupState(
    val notifications: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    val carLinked: Boolean,
    /** The §5.1 notification fallback is chosen, which needs BLUETOOTH_CONNECT. */
    val bluetoothNeeded: Boolean,
    val bluetoothAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val alarmVolumeOn: Boolean,
    val output: Output,
)

object Checklist {
    fun missing(s: SetupState): List<SetupItem> = buildList {
        if (!s.notifications) add(SetupItem.NOTIFICATIONS)
        if (!s.location) add(SetupItem.LOCATION) else if (!s.backgroundLocation) add(SetupItem.BACKGROUND_LOCATION)
        if (!s.carLinked) add(SetupItem.CAR)
        if (s.bluetoothNeeded && !s.bluetoothAllowed) add(SetupItem.NEARBY_DEVICES)
        if (!s.batteryUnrestricted) add(SetupItem.BATTERY)
        if (s.output == Output.PHONE_SPEAKER && !s.alarmVolumeOn) add(SetupItem.ALARM_VOLUME)
    }
}
