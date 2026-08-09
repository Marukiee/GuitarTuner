package nl.markmaaktmedia.guitartuner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledIconButton
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
import nl.markmaaktmedia.guitartuner.ui.components.PillHeight
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
 * All three controls are the same height and sit in one row with a single weight on the
 * instrument pill. The previous version gave the pill `weight(1f, fill = false)` *and* put a
 * `Spacer(weight(1f))` next to it, so the two split the free space and the pill was squeezed
 * until "Acoustic" wrapped onto a second line inside a rounded capsule.
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
            modifier = Modifier.weight(1f),
        )

        AutoPill(checked = autoMode, onCheckedChange = onAutoChange)

        FilledIconButton(
            onClick = onOpenSettings,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(PillHeight),
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Settings")
        }
    }
}

/**
 * Auto-detect on or off.
 *
 * A FilterChip was the wrong control: it is shorter than everything beside it, its selected state
 * is a faint tint, and "Auto" with no visible state tells you nothing about whether it is on. This
 * fills solid when active, carries a check that animates in, and states the mode in words when it
 * is off, which is the only time the word is ambiguous.
 */
@Composable
private fun AutoPill(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val container by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "autoContainer",
    )
    val content by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "autoContent",
    )

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.height(PillHeight).animateContentSize(
            spring(stiffness = Spring.StiffnessMediumLow),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(visible = checked) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(if (checked) R.string.auto_mode else R.string.manual_mode),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
