package io.github.lrosa2.speedbeep.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.obd.CarScanRunner
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
                CarScanRunner.running -> result = "Wait for the car scan to end, or stop it."
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
        Toggle("Remind me to unplug it after each drive", s.unplugReminder) { on -> change { it.copy(unplugReminder = on) } }
    }
    result?.let { Notice(it) }
    if (address != null) CarScanSection(address, busy = testing) { result = it }
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

/** "Scan car data" (spec §4.6): looks for the tyre pressures; the owner shares the file. */
@Composable
private fun CarScanSection(address: String, busy: Boolean, refuse: (String) -> Unit) {
    val context = LocalContext.current
    val scan by CarScanRunner.state.collectAsState()
    val latest = remember(scan) { CarScanRunner.latest(context) }
    when (val st = scan) {
        is CarScanRunner.State.Running -> {
            // The scan runs for minutes on a plain thread: the screen stays on, so the phone does not sleep.
            val view = LocalView.current
            DisposableEffect(view) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            Text("Scanning: ${st.progress}")
            OutlinedButton(onClick = { CarScanRunner.cancel("by you") }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop scan")
            }
        }
        else -> {
            OutlinedButton(
                onClick = {
                    when {
                        busy -> Unit
                        WatchBus.running -> refuse("Stop watching first: the adapter takes one connection at a time.")
                        else -> CarScanRunner.start(context, address, Graph.events)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Scan car data (tyre pressures)") }
            Text("Parked, power on, right after a drive. Stop watching first. It only reads and takes up to 20 minutes; keep SpeedBeep open.")
            if (st is CarScanRunner.State.Done) Notice("Car scan ${st.text}")
        }
    }
    if (latest != null && scan !is CarScanRunner.State.Running) {
        OutlinedButton(
            onClick = { Sharing.file(context, "SpeedBeep ${latest.name}", latest, "text/plain") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Share car scan (${latest.name})") }
    }
}
