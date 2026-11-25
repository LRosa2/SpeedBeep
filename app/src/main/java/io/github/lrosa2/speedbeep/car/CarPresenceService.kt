package io.github.lrosa2.speedbeep.car

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import io.github.lrosa2.speedbeep.Graph

/** Bound by Android while the linked car is present (spec §5.1). */
class CarPresenceService : CompanionDeviceService() {

    // API 34–35 call these; API 36 calls onDevicePresenceEvent instead.
    @Deprecated("Replaced by onDevicePresenceEvent on API 36")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        CarController.carAppeared("companion")
    }

    @Deprecated("Replaced by onDevicePresenceEvent on API 36")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        CarController.carGone("companion")
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        // The car is linked as a classic Bluetooth device, so only its connection counts. A head unit
        // may stop advertising BLE once connected, and that must not end the drive.
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED -> CarController.carAppeared("companion, connected")
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> CarController.carGone("companion, disconnected")
            else -> Graph.events.log("companion event ${event.event} ignored")
        }
    }
}
