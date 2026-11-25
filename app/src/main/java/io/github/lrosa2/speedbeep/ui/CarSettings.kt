package io.github.lrosa2.speedbeep.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import io.github.lrosa2.speedbeep.car.CarLink
import io.github.lrosa2.speedbeep.engine.Settings

/** "Linked car: Link / Unlink" (spec §7.3). */
@Composable
fun CarSettings(s: Settings, change: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val link = remember { CarLink(context) }
    var message by remember { mutableStateOf<String?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    Section("Car")
    Text(s.carName?.let { "Linked car: $it" } ?: "No car linked")
    if (s.carAddress == null) {
        BigButton("Link my car") {
            message = null
            link.associate(
                context as Activity,
                onPending = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
                onLinked = { info ->
                    runCatching { link.observe(info) }.onFailure { e -> Graph.events.log("observe failed: ${e.message}") }
                    Graph.events.log("car linked: ${info.displayName} ${CarLink.address(info)}")
                    change { it.copy(carAddress = CarLink.address(info), carName = info.displayName?.toString() ?: "Car") }
                },
                onFailed = { error ->
                    message = "Linking failed: $error"
                    Graph.events.log("car link failed: $error")
                },
            )
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
    message?.let { Notice(it) }
}
