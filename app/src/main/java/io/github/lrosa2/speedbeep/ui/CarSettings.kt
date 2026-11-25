package io.github.lrosa2.speedbeep.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.car.CarLink
import io.github.lrosa2.speedbeep.engine.CarStart
import io.github.lrosa2.speedbeep.engine.Settings

/** "Linked car" and "When the car connects" (spec §7.3, D16). */
@Composable
fun CarSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val link = remember { CarLink(context) }
    var message by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val nearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) null else "Without \"Nearby devices\" SpeedBeep cannot see the car connect."
    }
    val nearbyGranted = SetupProbe.granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    Section("Car")
    Text(s.carName?.let { "Linked car: $it" } ?: "No car linked")
    if (s.carAddress == null) {
        BigButton("Link my car") {
            message = null
            link.associate(
                context as Activity,
                onPending = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
                onLinked = { info ->
                    if (s.carStart == CarStart.AUTOMATIC) {
                        runCatching { link.observe(info) }.onFailure { e -> Graph.events.log("observe failed: ${e.message}") }
                    }
                    Graph.events.log("car linked: ${info.displayName} ${CarLink.address(info)}")
                    change { it.copy(carAddress = CarLink.address(info), carName = info.displayName?.toString() ?: "Car") }
                },
                onFailed = { error ->
                    message = "Linking failed: $error"
                    Graph.events.log("car link failed: $error")
                },
            )
        }
        if (s.carStart == CarStart.NOTIFICATION) {
            OutlinedButton(
                onClick = { if (nearbyGranted) picking = true else nearby.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pick from paired devices") }
        }
    } else {
        OutlinedButton(
            onClick = {
                link.unlink()
                Graph.events.log("car unlinked")
                change { it.copy(carAddress = null, carName = null) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlink car") }
    }
    Choice(
        "When the car connects",
        listOf("Start watching" to (s.carStart == CarStart.AUTOMATIC), "Ask with a notification" to (s.carStart == CarStart.NOTIFICATION)),
    ) { i ->
        val mode = CarStart.entries[i]
        if (mode == CarStart.NOTIFICATION) {
            runCatching { link.stopObservingAll() }
            if (!nearbyGranted) nearby.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            runCatching { link.observeAll() }.onFailure { e -> Graph.events.log("observe failed: ${e.message}") }
        }
        Graph.events.log("car start: $mode")
        change { it.copy(carStart = mode) }
    }
    message?.let { Notice(it) }
    if (picking) {
        PairedDevicesDialog(
            context,
            onPick = { name, address ->
                picking = false
                Graph.events.log("car picked: $name $address")
                change { it.copy(carAddress = address, carName = name) }
            },
            onDismiss = { picking = false },
        )
    }
}

/** Shown only once BLUETOOTH_CONNECT is granted. */
@SuppressLint("MissingPermission")
@Composable
private fun PairedDevicesDialog(context: Context, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    val devices = remember {
        context.getSystemService(BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
            .map { (it.name ?: it.address) to it.address.uppercase() }
            .sortedBy { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Paired devices") },
        text = {
            Column {
                if (devices.isEmpty()) Text("No paired devices")
                for ((name, address) in devices) {
                    TextButton(onClick = { onPick(name, address) }) { Text(name) }
                }
            }
        },
    )
}
