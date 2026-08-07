package nl.markmaaktmedia.guitartuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.ui.components.HeadstockView
import nl.markmaaktmedia.guitartuner.ui.components.InputLevelBar
import nl.markmaaktmedia.guitartuner.ui.components.TuningVisualizer

/**
 * Meter on top, headstock below, and the only chrome is the instrument selector.
 *
 * The meter gets a fixed height rather than a weight. It has a fixed amount to say, and giving it
 * half the screen left it as a hairline stranded in whitespace while the headstock was squeezed
 * into a space too small for six pegs.
 *
 * Note that the reading flow is handed to [TuningVisualizer] as a flow rather than collected
 * here. Collecting it at this level would recompose the entire screen, headstock included, about
 * 21 times a second.
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        Text(
            text = state.activeString.fullLabel,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        TuningVisualizer(
            reading = viewModel.reading,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 20.dp),
        )

        MicRow(
            levelDb = viewModel.inputLevelDb,
            sourceLabel = state.micSource.label,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        HeadstockView(
            instrument = state.instrument,
            activeIndex = state.activeStringIndex,
            tunedIndices = state.tunedStringIndices,
            onPegSelected = viewModel::selectString,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * Instrument chips scroll; the Auto toggle does not.
 *
 * Auto used to be a Switch sitting to the right of the scrolling row, which meant the chip under
 * it was sliced in half at the fade point and looked like a rendering bug. As a pinned chip it is
 * always reachable and the row has a clean edge to fade against.
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
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fadingEdge()
                    .padding(start = 16.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Instrument.entries.forEach { instrument ->
                    FilterChip(
                        selected = instrument == selected,
                        onClick = { onSelect(instrument) },
                        label = { Text(instrument.displayName) },
                    )
                }
            }
        }

        FilterChip(
            selected = autoMode,
            onClick = { onAutoChange(!autoMode) },
            label = { Text(stringResource(R.string.auto_mode)) },
            leadingIcon = if (autoMode) {
                { Icon(Icons.Rounded.Check, contentDescription = null) }
            } else {
                null
            },
            modifier = Modifier.padding(end = 4.dp),
        )

        IconButton(onClick = onOpenSettings, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

/**
 * Input level plus the capture path in use.
 *
 * The level bar is not decoration. It is the difference between "the app is broken" and "that
 * microphone is dead", which on a handset with a damaged rear capsule is the whole story. The
 * label is a shortcut into Settings, where the capture path can be changed, because that is the
 * fix once the bar tells you nothing is arriving.
 */
@Composable
private fun MicRow(
    levelDb: StateFlow<Float>,
    sourceLabel: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InputLevelBar(levelDb, Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fades the trailing edge of a scrolling row so clipped content reads as "more", not as broken. */
private fun Modifier.fadingEdge(): Modifier = this
    // An off-screen layer is required for DstIn to have anything to punch through.
    .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0.88f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
