package io.github.lrosa2.speedbeep.ui

import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lrosa2.speedbeep.Graph
import io.github.lrosa2.speedbeep.engine.DriveRecorder
import io.github.lrosa2.speedbeep.engine.Mode
import io.github.lrosa2.speedbeep.engine.Output
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SoundKind
import io.github.lrosa2.speedbeep.watch.Beeper
import java.util.Locale

/** Spec §7.3. [car] is the linked-car section (Task 9). */
@Composable
fun SettingsScreen(
    s: Settings,
    beeper: Beeper,
    change: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    car: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var testNote by remember { mutableStateOf<String?>(null) }
    val alarmPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        if (uri != null) {
            val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            change { it.copy(sound = SoundKind.PHONE_ALARM, alarmUri = uri.toString(), alarmTitle = title) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Section("Beep")
        Stepper("Threshold ${s.thresholdKmh} km/h", { change { it.stepThreshold(-1) } }, { change { it.stepThreshold(+1) } })
        Choice("Mode", listOf("Smart" to (s.mode == Mode.SMART), "Simple" to (s.mode == Mode.SIMPLE))) { i ->
            change { it.copy(mode = Mode.entries[i]) }
        }
        Stepper(
            "Main beep %.1f s".format(Locale.ROOT, s.mainBeepMs / 1_000.0),
            { change { it.copy(mainBeepMs = it.mainBeepMs - 500) } },
            { change { it.copy(mainBeepMs = it.mainBeepMs + 500) } },
        )
        Stepper(
            if (s.reminderIntervalS == 0) "Reminder off" else "Reminder every ${s.reminderIntervalS} s",
            { change { it.stepReminder(-1) } },
            { change { it.stepReminder(+1) } },
        )
        Stepper("Pause ${s.pauseMin} min", { change { it.copy(pauseMin = it.pauseMin - 1) } }, { change { it.copy(pauseMin = it.pauseMin + 1) } })

        Section("Sound")
        val sounds = listOf("Beep", "Double beep", "Chime", s.alarmTitle ?: "Phone alarm sound…")
        Choice("Sound", sounds.mapIndexed { i, label -> label to (s.sound.ordinal == i) }) { i ->
            if (i == SoundKind.PHONE_ALARM.ordinal) alarmPicker.launch(alarmPickerIntent(s)) else change { it.copy(sound = SoundKind.entries[i]) }
        }
        Stepper(
            "Volume ${s.volumePercent} %",
            { change { it.copy(volumePercent = it.volumePercent - 10) } },
            { change { it.copy(volumePercent = it.volumePercent + 10) } },
        )
        Choice("Output", listOf("Phone speaker" to (s.output == Output.PHONE_SPEAKER), "Car speakers" to (s.output == Output.CAR_SPEAKERS))) { i ->
            change { it.copy(output = Output.entries[i]) }
        }
        BigButton("Test beep") {
            beeper.playMain(s)
            Graph.events.log("test beep (${s.sound}, ${s.output}, ${s.volumePercent} %)")
            val alarmSilent = context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0
            testNote = if (s.output == Output.PHONE_SPEAKER && alarmSilent) "The alarm volume is off, so the phone speaker stays silent." else null
        }
        testNote?.let { Notice(it) }

        car()

        Section("Screen and records")
        Toggle("Keep screen on while the app is open", s.keepScreenOn) { on -> change { it.copy(keepScreenOn = on) } }
        Toggle("Record drives (for tuning)", s.recordDrives) { on -> change { it.copy(recordDrives = on) } }
        val latest = remember(s.recordDrives) { DriveRecorder.latest(Graph.drivesDir(context)) }
        if (latest != null) {
            OutlinedButton(onClick = { Sharing.file(context, "SpeedBeep ${latest.name}", latest) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share last drive (${latest.name})")
            }
        }
        OutlinedButton(
            onClick = { Sharing.text(context, "SpeedBeep event log", Graph.events.read().takeLast(200_000)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Share event log") }
    }
}

private fun alarmPickerIntent(s: Settings) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.alarmUri?.let { Uri.parse(it) })
