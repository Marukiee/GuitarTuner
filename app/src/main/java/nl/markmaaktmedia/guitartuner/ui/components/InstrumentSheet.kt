package nl.markmaaktmedia.guitartuner.ui.components

import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.launch
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
 * It moves the way the menus of MarkMySteps do. The panel starts as exactly the bar, in the bar's
 * colour and with its corners, grows from there while the page dims, and folds back into it. So bar
 * and panel are one thing, not a block that appears next to a button. A panel that gets taller
 * because another instrument has more tunings grows instead of jumping. The back gesture shrinks it
 * a little and lightens the dim under the thumb, and folds it away when let go.
 *
 * It is drawn over the tuner and not in a window of its own, so that it can start on the very pixels
 * of the bar.
 */
@Composable
fun InstrumentSheet(
    instrument: Instrument,
    tuning: Tuning,
    onInstrument: (Instrument) -> Unit,
    onTuning: (Tuning) -> Unit,
    onDismiss: () -> Unit,
    /** The bar it comes out of, with the radius of its round end and of its cut end. */
    bar: LayoutCoordinates?,
    barOuter: Dp,
    barInner: Dp,
    /** 0 is the bar itself, 1 the whole panel. The caller owns it, to hide the bar meanwhile. */
    open: Animatable<Float, AnimationVector1D>,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    LaunchedEffect(instrument) {
        val index = Instrument.entries.indexOf(instrument)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    // Where this overlay is, and from that where the bar is within it.
    var overlay by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val anchor = remember(overlay, bar) {
        val here = overlay
        if (here != null && here.isAttached && bar != null && bar.isAttached) {
            here.localBoundingBoxOf(bar, clipBounds = false)
        } else {
            null
        }
    }
    val back = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(anchor != null) {
        if (anchor != null && !closing) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            open.animateTo(1f, tween(OpenMillis, easing = TunerMotion.Emphasised))
        }
    }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            scope.launch {
                open.animateTo(0f, tween(CloseMillis, easing = FastOutSlowInEasing))
                onDismiss()
            }
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

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { overlay = it }) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = Dim * open.value * (1f - back.value * 0.5f)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )

        if (anchor == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        // The panel keeps the same distance to both sides of the screen as the bar does to its own.
        val edge = with(density) { min(anchor.left, constraints.maxWidth - anchor.right).coerceAtLeast(0f) }
        val start = anchor.translate(-edge, -anchor.top)
        val shapeAt: (Float) -> Shape = { k -> unfolding(start, barOuter, barInner, rtl, k) }
        val label = stringResource(R.string.instrument_and_tuning)
        val sideways = with(density) { edge.toDp() }
        val top = with(density) { anchor.top.coerceAtLeast(0f).toDp() }

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = sideways, end = sideways, top = top, bottom = 16.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val k = open.value
                    // Tied to how open it is, so folding away ends on the bar at its own size.
                    val shrink = 1f - 0.08f * back.value * k
                    scaleX = shrink
                    scaleY = shrink
                    transformOrigin = TransformOrigin(if (rtl) 0.7f else 0.3f, 0f)
                    alpha = (k / HandOver).coerceIn(0f, 1f)
                    shape = shapeAt(k)
                    clip = true
                }
                .drawWithContent {
                    drawContent()
                    val k = open.value
                    // It starts in the bar's colour and passes into its own.
                    val tint = (1f - k / 0.55f).coerceIn(0f, 1f)
                    if (tint > 0f) drawRect(scheme.surfaceContainerHigh, alpha = tint)
                    // Twice the width: the outer half is clipped away.
                    drawOutline(
                        outline = shapeAt(k).createOutline(size, layoutDirection, this),
                        color = scheme.outlineVariant.copy(alpha = 0.55f * k),
                        style = Stroke(2.dp.toPx()),
                    )
                }
                .background(scheme.surfaceContainerLow)
                // A tap on the panel is not a tap on the dim behind it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .semantics {
                    paneTitle = label
                    dismiss {
                        close()
                        true
                    }
                }
                .animateContentSize(TunerMotion.sizeSpring()),
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = ((open.value - 0.1f) / 0.6f).coerceIn(0f, 1f) }
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
                // Another instrument has other tunings, which fade over while the panel resizes.
                AnimatedContent(
                    targetState = instrument to tuning,
                    contentKey = { it.first },
                    transitionSpec = { TunerMotion.swap() using SizeTransform(clip = false) { _, _ -> snap() } },
                    label = "tunings",
                ) { (of, chosen) ->
                    Column {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(GroupedSpacing),
                        ) {
                            of.tunings.forEachIndexed { index, entry ->
                                TuningRow(
                                    tuning = entry,
                                    selected = entry.id == chosen.id,
                                    shape = groupedShape(index, of.tunings.size),
                                    onClick = { onTuning(entry) },
                                )
                            }
                        }

                        AnimatedVisibility(chosen.isReentrant, enter = TunerMotion.unfold(), exit = TunerMotion.fold()) {
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

/**
 * The panel's outline [k] of the way open: at 0 exactly the bar, which sits at [bar] inside the
 * panel and has one round end and one cut end, and at 1 the whole panel with the same corner all
 * round.
 */
private fun unfolding(bar: Rect, outer: Dp, inner: Dp, rtl: Boolean, k: Float): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun corner(from: Dp) = CornerRadius(with(density) { lerp(from, PanelCorner, k).toPx() })
        val left = corner(if (rtl) inner else outer)
        val right = corner(if (rtl) outer else inner)
        return Outline.Rounded(
            RoundRect(
                rect = lerp(bar, Rect(0f, 0f, size.width, size.height), k),
                topLeft = left,
                topRight = right,
                bottomRight = right,
                bottomLeft = left,
            ),
        )
    }
}

/** The corners of the panel when it is all the way out. */
private val PanelCorner = 28.dp

/** How dark the page behind gets. */
private const val Dim = 0.32f

/** The share of the way in which the panel takes over from the bar. */
internal const val HandOver = 0.3f

private const val OpenMillis = 240
private const val CloseMillis = 220

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
