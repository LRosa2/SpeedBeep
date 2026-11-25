package io.github.lrosa2.speedbeep.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.engine.LoadedRoads
import io.github.lrosa2.speedbeep.engine.Mode
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SetupItem
import io.github.lrosa2.speedbeep.engine.StatusText
import io.github.lrosa2.speedbeep.engine.WatchStatus
import java.time.ZoneId

/** Spec §7.4: large speed, status line, big buttons, mode switch, threshold stepper. */
@Composable
fun MainScreen(
    status: WatchStatus?,
    settings: Settings,
    roads: LoadedRoads?,
    setup: List<SetupItem>,
    onStartStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onQuiet: () -> Unit,
    onMode: (Mode) -> Unit,
    onThreshold: (Int) -> Unit,
    onFix: (SetupItem) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSettings) { Text("Settings") }
            TextButton(onClick = onAbout) { Text("About") }
        }
        roads?.problem?.let { Notice("No road data: simple mode everywhere. ($it)") }
        for (item in setup) SetupRow(item) { onFix(item) }
        Text(
            text = status?.speedKmh?.toInt()?.toString() ?: "–",
            fontSize = 120.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = status?.let { StatusText.line(it, SystemClock.elapsedRealtime(), System.currentTimeMillis(), ZoneId.systemDefault()) }
                ?: "Not watching",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton(if (status == null) "Start" else "Stop", onStartStop)
        if (status != null) {
            if (status.phase == Phase.PAUSED) BigButton("Resume", onResume) else BigButton("Pause ${settings.pauseMin} min", onPause)
            if (status.phase == Phase.OVER) BigButton("Quiet this stretch", onQuiet)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            FilterChip(selected = settings.mode == Mode.SMART, onClick = { onMode(Mode.SMART) }, label = { Text("Smart") })
            FilterChip(selected = settings.mode == Mode.SIMPLE, onClick = { onMode(Mode.SIMPLE) }, label = { Text("Simple") })
        }
        Stepper("Threshold ${settings.thresholdKmh} km/h", onMinus = { onThreshold(-1) }, onPlus = { onThreshold(+1) })
    }
}

@Composable
private fun SetupRow(item: SetupItem, onFix: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.title, modifier = Modifier.weight(1f))
            Button(onClick = onFix) { Text(item.fix) }
        }
    }
}
