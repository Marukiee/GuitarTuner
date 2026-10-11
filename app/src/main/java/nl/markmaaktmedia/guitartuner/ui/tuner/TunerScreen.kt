package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.TunerUiState
import nl.markmaaktmedia.guitartuner.ui.TunerViewModel
import nl.markmaaktmedia.guitartuner.ui.components.HeadstockView
import nl.markmaaktmedia.guitartuner.ui.components.InstrumentSheet
import nl.markmaaktmedia.guitartuner.ui.components.TunerIconButton
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.connectedShape

/**
 * The whole tuner on one screen.
 *
 * The order down the page is the order of attention: what is being tuned at the top, the
 * line in the middle where the eye rests, the instrument's head with a bubble per string
 * under it, and every control that has to be reachable with a thumb while an instrument is in
 * the way at the bottom.
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        TopBar(
            state = state,
            onPickInstrument = { showPicker = true },
            onOpenSettings = onOpenSettings,
        )

        PitchLane(
            readings = viewModel.reading,
            holdProgress = viewModel.holdProgress,
            target = state.activeString,
            targetHz = state.activeString.targetHz(state.referenceHz),
            allTuned = state.allTuned,
            onPlayTone = { viewModel.playReferenceTone(state.activeStringIndex) },
            onStartOver = viewModel::resetProgress,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        StringBoard(
            instrument = state.instrument,
            tuning = state.tuning,
            activeIndex = state.activeStringIndex,
            tunedIndices = state.tunedStringIndices,
            soundingIndex = state.soundingStringIndex,
            readings = viewModel.reading,
            inputLevelDb = viewModel.inputLevelDb,
            onSelect = viewModel::selectString,
            onPlay = viewModel::playReferenceTone,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showPicker) {
        InstrumentSheet(
            instrument = state.instrument,
            tuning = state.tuning,
            onInstrument = viewModel::selectInstrument,
            onTuning = viewModel::selectTuning,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun TopBar(
    state: TunerUiState,
    onPickInstrument: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // A connected button group: round at the two ends, cut where the halves meet. The
    // instrument bar and the settings button do one job between them, "what am I tuning and
    // how", and drawing the second as a separate circle floating off to the right said they
    // were unrelated.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ConnectedGap),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                // Fixed rather than content sized, so the settings button beside it can be the
                // same height. A round button visibly shorter than the bar it sits next to
                // reads as a mistake, not as a hierarchy.
                .height(TopBarHeight)
                .clip(connectedShape(0, 2, outer = TopBarHeight / 2, inner = ConnectedInner))
                .background(scheme.surfaceContainerHigh)
                .bouncyClickable(onClickLabel = stringResource(R.string.instrument_and_tuning), onClick = onPickInstrument)
                .padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeadstockView(
                layout = state.instrument.layout,
                stringCount = state.tuning.stringCount,
                scale = state.instrument.headstockScale,
                bodyColor = scheme.onSurfaceVariant.copy(alpha = 0.12f),
                outlineColor = scheme.onSurfaceVariant,
                pegColor = scheme.primary,
                stringColor = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(width = 27.dp, height = 40.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(state.instrument.nameRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "${stringResource(state.tuning.nameRes)} · ${state.tuning.notation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                painter = TunerIcons.ChevronDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        TunerIconButton(
            icon = TunerIcons.Settings,
            contentDescription = stringResource(R.string.settings_title),
            onClick = onOpenSettings,
            background = scheme.surfaceContainerHigh,
            size = TopBarHeight,
            iconSize = 22.dp,
            shape = connectedShape(1, 2, outer = TopBarHeight / 2, inner = ConnectedInner),
        )
    }
}

/** One height for the instrument bar and the settings button beside it. */
private val TopBarHeight = 58.dp

/** The slice between the two halves of the top bar, and the corners either side of it. */
private val ConnectedGap = 6.dp
private val ConnectedInner = 10.dp
