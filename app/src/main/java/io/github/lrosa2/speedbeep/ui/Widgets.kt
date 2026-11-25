package io.github.lrosa2.speedbeep.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A full-width button big enough to hit at arm's length in a phone holder (§7.4). */
@Composable
fun BigButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
            Text("−", style = MaterialTheme.typography.headlineMedium)
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** A row of chips; [options] pairs each label with whether it is selected. */
@Composable
fun Choice(title: String, options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, (label, selected) ->
            FilterChip(selected = selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
    }
}
