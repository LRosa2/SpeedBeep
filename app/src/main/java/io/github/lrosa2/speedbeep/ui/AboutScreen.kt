package io.github.lrosa2.speedbeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lrosa2.speedbeep.BuildConfig
import io.github.lrosa2.speedbeep.engine.LoadedRoads
import java.time.Instant
import java.time.ZoneOffset

/** Spec §7.5. */
@Composable
fun AboutScreen(roads: LoadedRoads?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }
        Text("SpeedBeep ${BuildConfig.VERSION_NAME}")
        Text("Road data © OpenStreetMap contributors, ODbL")
        val ts = roads?.dataTimestampSec
        Text(
            when {
                roads == null -> "Road file: loading"
                roads.problem != null -> "Road file: ${roads.problem}"
                ts != null -> "Road file: OSM data of " + Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).toLocalDate()
                else -> "Road file: no date"
            },
        )
    }
}
