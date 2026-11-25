package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import android.view.ViewGroup
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import nl.markmaaktmedia.guitartuner.ui.theme.CardSquircle
import nl.markmaaktmedia.guitartuner.ui.theme.GroupedSpacing
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.groupedShape

/**
 * Instrument and tuning, in one panel that unfolds from the bar you tapped.
 *
 * They belong together because picking one nearly always means checking the other: the
 * player who switches to a bass is the same player who wants to know whether it is still
 * in drop D. Splitting them across two screens turns one decision into two navigations.
 *
 * It opens the way the panels of MarkMySteps do: the page dims, and the panel grows out of the
 * top, from the bar it belongs to, instead of rising from the bottom edge. A panel that gets
 * taller because another instrument has more tunings grows instead of jumping. The back
 * gesture shrinks it a little and lightens the dim under the thumb, and closes it when let go.
 */
@Composable
fun InstrumentSheet(
    instrument: Instrument,
    tuning: Tuning,
    onInstrument: (Instrument) -> Unit,
    onTuning: (Tuning) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(instrument) {
        val index = Instrument.entries.indexOf(instrument)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    val shown = remember { MutableTransitionState(false) }
    val back = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.targetState = true }
    // Once the panel has played its way out, it leaves.
    LaunchedEffect(shown.currentState, shown.isIdle) {
        if (shown.isIdle && !shown.currentState && !shown.targetState) onDismiss()
    }
    val close: () -> Unit = { shown.targetState = false }
    val origin = TransformOrigin(0.3f, 0f)

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window itself: no dim and no animation of its own, the panel does both.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.setWindowAnimations(0)
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
        PredictiveBackHandler { events ->
            try {
                events.collect { back.snapTo(it.progress) }
                close()
            } catch (cancelled: CancellationException) {
                scope.launch { back.animateTo(0f, tween(200)) }
            }
        }
        val dim by animateFloatAsState(
            targetValue = if (shown.targetState) 0.32f else 0f,
            animationSpec = TunerMotion.fadeSpec(),
            label = "panelDim",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim * (1f - back.value * 0.5f)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        ) {
            AnimatedVisibility(
                visibleState = shown,
                // Right under the instrument bar: its status bar, its own margin and its height.
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp + 58.dp + 8.dp, start = 12.dp, end = 12.dp),
                enter = scaleIn(TunerMotion.spatial(), initialScale = 0.85f, transformOrigin = origin) +
                    fadeIn(TunerMotion.fadeSpec()),
                exit = scaleOut(tween(220, easing = TunerMotion.Standard), targetScale = 0.9f, transformOrigin = origin) +
                    fadeOut(tween(160)),
            ) {
                val shape = RoundedCornerShape(32.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val k = 1f - 0.08f * back.value
                            scaleX = k
                            scaleY = k
                            transformOrigin = origin
                        }
                        .clip(shape)
                        .background(scheme.surfaceContainerLow)
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
                        // A tap on the panel is not a tap on the dim behind it.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .animateContentSize(TunerMotion.spatial()),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp - 190).dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 20.dp, bottom = 20.dp),
                    ) {
                        SheetHeading(stringResource(R.string.sheet_instrument))
                        LazyRow(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(Instrument.entries, key = { it.name }) { entry ->
                                InstrumentCard(
                                    instrument = entry,
                                    selected = entry == instrument,
                                    onClick = { onInstrument(entry) },
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        SheetHeading(stringResource(R.string.sheet_tuning))
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(GroupedSpacing),
                        ) {
                            instrument.tunings.forEachIndexed { index, entry ->
                                TuningRow(
                                    tuning = entry,
                                    selected = entry.id == tuning.id,
                                    shape = groupedShape(index, instrument.tunings.size),
                                    onClick = { onTuning(entry) },
                                )
                            }
                        }

                        if (tuning.isReentrant) {
                            Text(
                                text = stringResource(R.string.reentrant_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun InstrumentCard(
    instrument: Instrument,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = TunerMotion.colourSpec(),
        label = "instrumentCard",
    )
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface

    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(CardSquircle)
            .background(container)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
                shape = CardSquircle,
            )
            .bouncyClickable(onClickLabel = stringResource(instrument.nameRes), onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InstrumentIcon(
            instrument = instrument,
            modifier = Modifier.size(64.dp),
            selected = selected,
        )
        Text(
            text = stringResource(instrument.shortNameRes),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TuningRow(
    tuning: Tuning,
    selected: Boolean,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        animationSpec = TunerMotion.colourSpec(),
        label = "tuningRow",
    )
    val content = if (selected) scheme.onSecondaryContainer else scheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .bouncyClickable(onClickLabel = stringResource(tuning.nameRes), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(tuning.nameRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
            )
            Text(
                text = tuning.notation,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.72f),
            )
        }
        if (selected) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = TunerIcons.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
