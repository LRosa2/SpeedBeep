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
        when (event.event) {
            DevicePresenceEvent.EVENT_BT_CONNECTED, DevicePresenceEvent.EVENT_BLE_APPEARED ->
                CarController.carAppeared("companion event ${event.event}")
            DevicePresenceEvent.EVENT_BT_DISCONNECTED, DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                CarController.carGone("companion event ${event.event}")
            else -> Graph.events.log("companion event ${event.event} ignored")
        }
    }
}
