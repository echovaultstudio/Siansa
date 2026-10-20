package app.musix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.musix.situation.SituationOptions

/**
 * "Use the moment" in the Vibes panel (issue #247): what of the moment today's mixes may use, each on its own.
 * Weather and movement ask for their Android permission when switched on; if it's refused they stay off.
 */
@Composable
fun SituationPanel() {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val options = settings.situation
    var note by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<SituationOptions?>(null) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val wanted = pending
        pending = null
        if (wanted == null) return@rememberLauncherForActivityResult
        if (ok) graph.settings.setSituation(wanted)
        else note = "Android didn't allow it, so it stays off. You can allow it in the phone's settings for Musix."
    }

    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun change(next: SituationOptions) {
        note = null
        val permission = when {
            next.weather && !options.weather -> Manifest.permission.ACCESS_COARSE_LOCATION
            next.movement && !options.movement && Build.VERSION.SDK_INT >= 29 -> Manifest.permission.ACTIVITY_RECOGNITION
            else -> null
        }
        if (permission != null && !has(permission)) {
            pending = next
            ask.launch(permission)
        } else {
            graph.settings.setSituation(next)
        }
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = Space.Page, vertical = Space.Small)) {
        Column(Modifier.padding(Space.Page), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Text("Use the moment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Today's mixes can suit what's going on around you. Each is read once when a mix is made, never in " +
                    "between, so it costs next to no battery. The AI gets the weather and what you're doing, never where you are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SituationSwitches(options, ::change)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** The switches of [SituationPanel], without permissions, so their layout can be checked on its own (SpacingTest). */
@Composable
internal fun SituationSwitches(options: SituationOptions, onChange: (SituationOptions) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("situationSwitches")) {
        SituationRow("Weather", "Rain, sun, cold, sunset. Uses your approximate location, rounded to about 10 km, only to ask Open-Meteo.", options.weather) {
            onChange(options.copy(weather = it))
        }
        SituationRow("Movement", "Sitting, walking or running, from the step counter.", options.movement) {
            onChange(options.copy(movement = it))
        }
        SituationRow("Light", "A dark room or daylight, from the light sensor.", options.light) {
            onChange(options.copy(light = it))
        }
        SituationRow("Charging", "Whether the phone is charging, or its battery is low.", options.charging) {
            onChange(options.copy(charging = it))
        }
        SituationRow("Listening device", "Phone speaker, headphones or Bluetooth (a car suggests driving).", options.device) {
            onChange(options.copy(device = it))
        }
    }
}

@Composable
private fun SituationRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.Small).testTag("situation-$title"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Space.Medium)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
