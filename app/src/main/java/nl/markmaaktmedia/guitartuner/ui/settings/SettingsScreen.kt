package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.audio.MicSource
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import kotlin.math.roundToInt

/**
 * Everything that used to be either buried in a chip row or not adjustable at all.
 *
 * The microphone section is the one that earns its place. Which capture path works is a property
 * of the specific handset, not something the app can reason its way to, so it has to be a
 * setting: this app was inert on a phone with a damaged rear microphone precisely because the
 * choice was hardcoded to the theoretically-best option.
 */
@Composable
fun SettingsScreen(
    instrument: Instrument,
    micSource: MicSource,
    micOptions: List<MicSource>,
    themeMode: ThemeMode,
    referenceHz: Float,
    onInstrument: (Instrument) -> Unit,
    onMicSource: (MicSource) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onReferenceHz: (Float) -> Unit,
    onBack: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                title = "Instrument",
                subtitle = "Decides the tuning, the number of pegs and the headstock shape.",
            ) {
                Column(Modifier.selectableGroup()) {
                    Instrument.entries.forEach { option ->
                        ChoiceRow(
                            label = option.displayName,
                            detail = option.strings.joinToString(" ") { it.fullLabel },
                            selected = option == instrument,
                            onSelect = { onInstrument(option) },
                        )
                    }
                }
            }

            SettingsCard(
                title = "Microphone",
                subtitle = "Which capture path the recorder opens. This decides which physical " +
                    "microphone the phone actually uses, so if one of yours is damaged, pick " +
                    "another here.",
            ) {
                Column(Modifier.selectableGroup()) {
                    micOptions.forEach { option ->
                        ChoiceRow(
                            label = option.label,
                            detail = option.description,
                            selected = option == micSource,
                            onSelect = { onMicSource(option) },
                        )
                    }
                }
            }

            SettingsCard(title = "Appearance", subtitle = null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == themeMode,
                            onClick = { onThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) {
                            Text(mode.label)
                        }
                    }
                }
            }

            SettingsCard(
                title = "Reference pitch",
                subtitle = "Everything is derived from this, so a whole ensemble can be moved off " +
                    "concert pitch at once.",
            ) {
                Text(
                    text = "A = ${referenceHz.roundToInt()} Hz",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = referenceHz,
                    onValueChange = { onReferenceHz(it.roundToInt().toFloat()) },
                    valueRange = 415f..466f,
                    steps = 50,
                )
            }

            Text(
                text = "Guitar Tuner $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // onClick = null: the row above owns the click, so the button must not also handle it.
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Plain-language explanation of what each capture path actually does. */
private val MicSource.description: String
    get() = when (this) {
        MicSource.VoiceRecognition ->
            "Gain control off, and a microphone that works on nearly every phone. The default."
        MicSource.Main -> "The primary microphone, at the bottom next to the USB-C port."
        MicSource.Unprocessed ->
            "Bypasses all processing, best accuracy, but often a secondary microphone."
        MicSource.Camcorder -> "The rear-facing microphone, next to the camera."
    }
