package io.github.lrosa2.speedbeep.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.obd.ObdLinks
import io.github.lrosa2.speedbeep.obd.ObdTest
import io.github.lrosa2.speedbeep.watch.WatchBus

/** "OBD adapter" (spec §4.5, §7.3): choose, test, remove. */
@Composable
fun ObdSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val nearby = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) picking = true else result = "Without \"Nearby devices\" SpeedBeep cannot reach the adapter."
    }

    Section("OBD adapter")
    Text(
        s.obdName?.let { "Adapter: $it. Simple mode uses the car's speed from it." }
            ?: "No adapter. Pair an ELM327 in Android's Bluetooth settings first (PIN 1234 or 0000), then choose it here.",
    )
    val address = s.obdAddress
    if (address == null) {
        OutlinedButton(
            onClick = {
                result = null
                if (SetupProbe.granted(context, Manifest.permission.BLUETOOTH_CONNECT)) {
                    picking = true
                } else {
                    nearby.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Choose adapter") }
    } else {
        BigButton(if (testing) "Testing the adapter…" else "Test adapter") {
            when {
                testing -> Unit
                WatchBus.running -> result = "Stop watching first: the adapter takes one connection at a time."
                else -> {
                    testing = true
                    result = null
                    ObdTest.run(context, address, Graph.events) {
                        testing = false
                        result = it
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                Graph.events.log("OBD adapter removed")
                result = null
                change { it.copy(obdAddress = null, obdName = null) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Remove adapter") }
    }
    result?.let { Notice(it) }
    if (picking) {
        PairedDevicesDialog(
            context,
            onPick = { name, picked ->
                picking = false
                Graph.events.log("OBD adapter chosen: $name $picked")
                change { it.copy(obdAddress = picked, obdName = name) }
            },
            onDismiss = { picking = false },
            title = "Choose the OBD adapter",
            extra = ObdLinks.extraChoices,
        )
    }
}
