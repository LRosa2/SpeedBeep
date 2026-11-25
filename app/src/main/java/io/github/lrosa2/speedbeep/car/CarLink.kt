package io.github.lrosa2.speedbeep.car

import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.Build

/** Links the car through CompanionDeviceManager and observes its presence (spec §5.1, D12). */
class CarLink(context: Context) {
    companion object {
        fun address(info: AssociationInfo): String? = info.deviceMacAddress?.toString()?.uppercase()
    }

    private val cdm = context.getSystemService(CompanionDeviceManager::class.java)

    fun associations(): List<AssociationInfo> = cdm.myAssociations

    /** Android's own dialog lists the Bluetooth devices; the owner picks the car. */
    fun associate(
        activity: Activity,
        onPending: (IntentSender) -> Unit,
        onLinked: (AssociationInfo) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()
        cdm.associate(request, activity.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                onPending(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                onLinked(associationInfo)
            }

            override fun onFailure(error: CharSequence?) {
                onFailed(error?.toString() ?: "unknown error")
            }
        })
    }

    /** Presence events then reach [CarPresenceService]. */
    fun observe(info: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= 36) {
            cdm.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(info.id).build())
        } else {
            @Suppress("DEPRECATION")
            cdm.startObservingDevicePresence(address(info) ?: error("association ${info.id} has no address"))
        }
    }

    fun observeAll() = associations().forEach { observe(it) }

    fun stopObservingAll() = associations().forEach { stopObserving(it) }

    fun unlink() {
        for (info in associations()) {
            runCatching { stopObserving(info) }
            cdm.disassociate(info.id)
        }
    }

    private fun stopObserving(info: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= 36) {
            cdm.stopObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(info.id).build())
        } else {
            @Suppress("DEPRECATION")
            cdm.stopObservingDevicePresence(address(info) ?: return)
        }
    }
}
