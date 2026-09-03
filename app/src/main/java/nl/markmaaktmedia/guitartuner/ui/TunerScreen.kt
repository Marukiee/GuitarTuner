package nl.markmaaktmedia.guitartuner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.TunerUiState
import nl.markmaaktmedia.guitartuner.ui.components.HeadstockView
import nl.markmaaktmedia.guitartuner.ui.components.InputLevelBar
import nl.markmaaktmedia.guitartuner.ui.components.InstrumentSheet
import nl.markmaaktmedia.guitartuner.ui.components.StringRail
import nl.markmaaktmedia.guitartuner.ui.components.TuneDial
import nl.markmaaktmedia.guitartuner.ui.components.TunerIconButton
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.connectedShape
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.PillShape
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion

/**
 * The whole tuner on one screen.
 *
 * The order down the page is the order of attention: what is being tuned at the top, the
 * meter in the middle where the eye rests, and every control that has to be reachable
 * with a thumb while an instrument is in the way at the bottom.
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inTuneNow by viewModel.inTuneNow.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        TopRow(
            state = state,
            onPickInstrument = { showPicker = true },
            onOpenSettings = onOpenSettings,
        )

        Spacer(Modifier.weight(0.5f))

        TuneDial(
            readings = viewModel.reading,
            holdProgress = viewModel.holdProgress,
            target = state.activeString,
            targetHz = state.activeString.targetHz(state.referenceHz),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(0.5f))

        AnimatedVisibility(
            visible = state.allTuned,
            enter = fadeIn() + expandVertically(TunerMotion.sizeSpring()),
            exit = fadeOut() + shrinkVertically(TunerMotion.sizeSpring()),
        ) {
            AllTunedBanner(onReset = viewModel::resetProgress)
        }

        Spacer(Modifier.height(16.dp))

        ControlRow(
            state = state,
            onToggleAuto = { viewModel.setAutoMode(!state.autoMode) },
            onPlayTone = { viewModel.playReferenceTone(state.activeStringIndex) },
            onReset = viewModel::resetProgress,
        )

        Spacer(Modifier.height(14.dp))

        StringRail(
            strings = state.strings,
            activeIndex = state.activeStringIndex,
            tunedIndices = state.tunedStringIndices,
            inTuneNow = inTuneNow,
            soundingIndex = state.soundingStringIndex,
            onSelect = viewModel::selectString,
        )

        Spacer(Modifier.height(18.dp))

        InputLevelBar(
            levelDb = viewModel.inputLevelDb,
            listening = state.isListening && !state.isMuted,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
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
private fun TopRow(
    state: TunerUiState,
    onPickInstrument: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // A connected button group, the same idiom as the segmented pickers in Settings: round at
    // the two ends, cut where the halves meet. The instrument bar and the settings button do
    // one job between them, "what am I tuning and how", and drawing the second as a separate
    // circle floating off to the right said they were unrelated.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ConnectedGap),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                // Fixed rather than content sized, so the settings button beside it can be
                // the same height. A round button that is visibly shorter than the bar it
                // sits next to reads as a mistake, not as a hierarchy.
                .height(TopBarHeight)
                .clip(connectedShape(0, 2, outer = TopBarHeight / 2, inner = ConnectedInner))
                .background(scheme.surfaceContainerHigh)
                .bouncyClickable(onClickLabel = "Instrument and tuning", onClick = onPickInstrument)
                .padding(start = 12.dp, end = 14.dp),
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
                    text = state.instrument.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "${state.tuning.displayName} · ${state.tuning.notation}",
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
            contentDescription = "Settings",
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

@Composable
private fun ControlRow(
    state: TunerUiState,
    onToggleAuto: () -> Unit,
    onPlayTone: () -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutoToggle(auto = state.autoMode, onClick = onToggleAuto, modifier = Modifier.weight(1f))

        TunerIconButton(
            icon = TunerIcons.ReferenceTone,
            contentDescription = "Play ${state.activeString.fullLabel}",
            onClick = onPlayTone,
            background = scheme.surfaceContainerHigh,
            tint = if (state.soundingStringIndex != null) scheme.primary else scheme.onSurfaceVariant,
        )
        TunerIconButton(
            icon = TunerIcons.Restart,
            contentDescription = "Start over",
            onClick = onReset,
            background = scheme.surfaceContainerHigh,
        )
    }
}

/**
 * Auto against manual.
 *
 * One control rather than two, because the two states are exclusive and a pair of
 * buttons where one is always redundant is a pair of buttons too many. It says which
 * mode is on rather than which one the tap would switch to, which is the way round that
 * people actually read a toggle.
 */
@Composable
private fun AutoToggle(auto: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (auto) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = TunerMotion.colourSpec(),
        label = "autoContainer",
    )
    val content by animateColorAsState(
        targetValue = if (auto) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = TunerMotion.colourSpec(),
        label = "autoContent",
    )

    Row(
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .bouncyClickable(onClickLabel = "Auto string detection", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = if (auto) TunerIcons.AutoFilled else TunerIcons.Auto,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(if (auto) R.string.auto_mode else R.string.manual_mode),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}

@Composable
private fun AllTunedBanner(onReset: () -> Unit) {
    val signals = LocalTunerSignals.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardSquircle)
            .background(signals.inTune.copy(alpha = 0.18f))
            .bouncyClickable(onClickLabel = "Start over", onClick = onReset)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = TunerIcons.CheckCircleFilled,
            contentDescription = null,
            tint = signals.inTune,
            modifier = Modifier.size(22.dp),
        )
        Box(Modifier.weight(1f)) {
            Text(
                text = "Every string in tune",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Start over",
            style = MaterialTheme.typography.labelMedium,
            color = signals.inTune,
        )
    }
}
