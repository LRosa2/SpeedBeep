package io.github.lrosa2.speedbeep.car

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.CarStart
import io.github.lrosa2.speedbeep.watch.WatchNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Spec §5.1 fallback (D16): Bluetooth ACL broadcasts for the linked car, used only when
 * "When the car connects" is "Ask with a notification". ACL broadcasts come only from the
 * system, and receiving them needs BLUETOOTH_CONNECT.
 */
class AclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val s = Graph.settings.settings.first()
                if (s.carStart != CarStart.NOTIFICATION || !device.address.equals(s.carAddress, ignoreCase = true)) return@launch
                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> CarController.carAppeared("bluetooth", autoStart = false)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        WatchNotifications.hideCarConnected(context)
                        CarController.carGone("bluetooth")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
