package nl.markmaaktmedia.guitartuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.ui.components.HeadstockView
import nl.markmaaktmedia.guitartuner.ui.components.InstrumentButton
import nl.markmaaktmedia.guitartuner.ui.components.InputLevelBar
import nl.markmaaktmedia.guitartuner.ui.components.TuningVisualizer

/**
 * Meter on top, headstock below, and the only chrome is the instrument selector.
 *
 * The meter now sits inside a large rounded container rather than floating on the background.
 * That one change does most of the work in making the screen read as current: Material 3
 * Expressive is built out of generously rounded, tonally distinct surfaces, and loose elements
 * scattered on a flat background is exactly what dates an app.
 *
 * The reading flow is handed to [TuningVisualizer] as a flow rather than collected here.
 * Collecting it at this level would recompose the entire screen, headstock included, about 21
 * times a second.
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inTuneNow by viewModel.inTuneNow.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        InstrumentBar(
            selected = state.instrument,
            autoMode = state.autoMode,
            onSelect = viewModel::selectInstrument,
            onAutoChange = viewModel::setAutoMode,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 12.dp)) {
                Text(
                    text = state.activeString.fullLabel,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "%.1f Hz".format(state.activeString.targetHz(state.referenceHz)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                TuningVisualizer(
                    reading = viewModel.reading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .padding(horizontal = 18.dp),
                )

                // The level bar is the one piece of diagnostics that earns permanent screen
                // space: it is the difference between "the app is broken" and "that microphone
                // is dead". Which capture path is in use belongs in Settings, not beside the
                // meter, where it was just noise next to the thing you are actually reading.
                InputLevelBar(
                    levelDb = viewModel.inputLevelDb,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                )
            }
        }

        HeadstockView(
            instrument = state.instrument,
            activeIndex = state.activeStringIndex,
            tunedIndices = state.tunedStringIndices,
            inTuneNow = inTuneNow,
            onPegSelected = viewModel::selectString,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * What you are tuning, whether it tracks automatically, and the way into Settings.
 *
 * The scrolling chip row this replaces could never fit six instruments, so one was always sliced
 * off at the fade edge, and a row of competing pills above the meter was noise around the thing
 * being read. One button now states the instrument and opens a sheet to change it.
 */
@Composable
private fun InstrumentBar(
    selected: Instrument,
    autoMode: Boolean,
    onSelect: (Instrument) -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InstrumentButton(
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(Modifier.weight(1f))

        FilterChip(
            selected = autoMode,
            onClick = { onAutoChange(!autoMode) },
            label = { Text(stringResource(R.string.auto_mode)) },
            shape = RoundedCornerShape(percent = 50),
            leadingIcon = if (autoMode) {
                { Icon(Icons.Rounded.Check, contentDescription = null) }
            } else {
                null
            },
        )

        FilledIconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Settings")
        }
    }
}
