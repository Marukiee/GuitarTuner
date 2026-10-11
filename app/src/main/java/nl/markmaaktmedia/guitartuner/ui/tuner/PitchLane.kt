package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.theme.NoteBubbleStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus
import nl.markmaaktmedia.guitartuner.domain.model.TuningString
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import nl.markmaaktmedia.guitartuner.ui.theme.TunerSignalColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The meter, drawn as a line.
 *
 * One vertical line runs down the middle to the note being tuned. A bubble hangs from the top
 * of it and slides sideways with the pitch: left of the line is flat, right of it is sharp,
 * and the bubble sits on the line when the string is right. Behind the bubble a trail records
 * where the pitch has just been, so a peg turn reads as a curve with a direction and a
 * length, which a bare needle position never says.
 *
 * Everything that moves at the audio rate is read inside a draw or layer lambda, so twenty
 * one readings a second invalidate a draw phase and never reach composition. The springs
 * interpolate between those readings at display rate, which is what makes 21 Hz look like
 * continuous motion.
 *
 * The colour is worked out per frame from the bubble's position rather than switched on a
 * three way status, so the bubble warms and cools as the string is turned. A hard switch at
 * the edge of the in-tune window makes the last few cents feel like a cliff; a ramp makes
 * them feel like arriving.
 */
@Composable
fun PitchLane(
    readings: StateFlow<TuningReading?>,
    holdProgress: StateFlow<Float>,
    target: TuningString,
    targetHz: Float,
    allTuned: Boolean,
    onPlayTone: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signals = LocalTunerSignals.current
    val scheme = MaterialTheme.colorScheme

    val offset = remember { Animatable(0f) }
    val presence = remember { Animatable(0f) }
    val hold = remember { Animatable(0f) }

    LaunchedEffect(readings) {
        readings.collectLatest { reading ->
            offset.animateTo(
                targetValue = reading?.normalizedOffset ?: 0f,
                animationSpec = spring(
                    dampingRatio = TunerMotion.NeedleDamping,
                    stiffness = TunerMotion.NeedleStiffness,
                ),
            )
        }
    }
    LaunchedEffect(readings) {
        readings.map { it != null }.distinctUntilChanged().collectLatest { hasSignal ->
            presence.animateTo(
                targetValue = if (hasSignal) 1f else 0f,
                animationSpec = tween(TunerMotion.DurationMedium, easing = TunerMotion.Standard),
            )
        }
    }
    LaunchedEffect(holdProgress) {
        holdProgress.collectLatest { value -> hold.animateTo(value, tween(70, easing = LinearEasing)) }
    }

    val status by readings.select(TuningStatus.IN_TUNE) { it?.status ?: TuningStatus.IN_TUNE }
    val hasSignal by readings.select(false) { it != null }
    val inTuneNow by readings.select(false) {
        it != null && it.settled && it.status == TuningStatus.IN_TUNE && it.clarity >= 0.8f
    }
    val celebrate = allTuned && !hasSignal

    val trail = remember { Trail(TrailSamples) }
    val trailTick = remember { mutableIntStateOf(0) }
    val signalNow = rememberUpdatedState(hasSignal)
    LaunchedEffect(trail) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= TrailIntervalNanos) {
                    last = now
                    trail.push(if (signalNow.value && presence.value > 0.05f) offset.value else Float.NaN)
                    trailTick.intValue++
                }
            }
        }
    }

    val statusColor by animateColorAsState(
        targetValue = when {
            celebrate -> signals.inTune
            !hasSignal -> scheme.onSurfaceVariant
            status == TuningStatus.IN_TUNE -> signals.inTune
            status == TuningStatus.FLAT -> signals.flat
            else -> signals.sharp
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "statusColor",
    )

    // The check draws itself in when the string is in tune, and un-draws when it drifts out.
    val check by animateFloatAsState(
        targetValue = if (inTuneNow || celebrate) 1f else 0f,
        animationSpec = tween(260, easing = TunerMotion.Standard),
        label = "check",
    )
    val pop by animateFloatAsState(
        targetValue = if (inTuneNow || celebrate) 1.12f else 1f,
        animationSpec = TunerMotion.bouncy(),
        label = "pop",
    )
    // The idle bubble breathes, so a quiet tuner reads as listening and not as frozen.
    val breath = rememberBreath()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val geometry = remember(widthPx, heightPx, density) { LaneGeometry.of(widthPx, heightPx, density.density) }

        // Grid, window, line, markers, trail, hold ring and the bubble itself, in one pass.
        Canvas(
            Modifier
                .fillMaxSize()
                .gridBackdrop(scheme.onSurface),
        ) {
            drawLane(
                geometry = geometry,
                offset = offset.value,
                presence = presence.value,
                hold = hold.value,
                trail = trail,
                trailTick = trailTick.intValue,
                signals = signals,
                celebrate = celebrate,
                idleColor = scheme.onSurfaceVariant,
                markerColor = scheme.onSurfaceVariant,
                bubbleScale = pop * (1f + (1f - presence.value) * (breath.value - 1f)),
                bubbleFill = scheme.surfaceContainerHigh,
                celebrateFraction = if (celebrate) 1f else 0f,
            )
        }

        // The bubble's contents ride the same position as the disc: a number while the string
        // is out, a tick when it is in. Read in the layer block, so no recomposition per frame.
        Box(
            modifier = Modifier
                .size(with(density) { (geometry.bubbleRadius * 2f).toDp() })
                .graphicsLayer {
                    val x = geometry.centreX + (if (celebrate) 0f else offset.value) * geometry.halfRange
                    translationX = x - geometry.bubbleRadius
                    translationY = geometry.bubbleY - geometry.bubbleRadius
                    val s = pop * (1f + (1f - presence.value) * (breath.value - 1f))
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) {
            BubbleContent(readings = readings, check = check, hasSignal = hasSignal, celebrate = celebrate, signals = signals)
        }

        TargetNote(
            target = target,
            targetHz = targetHz,
            readings = readings,
            statusColor = statusColor,
            inTune = inTuneNow || celebrate,
            hasSignal = hasSignal,
            celebrate = celebrate,
            geometry = geometry,
            onClick = if (allTuned) onStartOver else onPlayTone,
        )
    }
}

/** Where everything sits in a lane of a given size, in pixels. */
private class LaneGeometry(
    val width: Float,
    val height: Float,
    val bubbleRadius: Float,
    val bubbleY: Float,
    val targetRadius: Float,
    val targetTop: Float,
    val captionTop: Float,
    val trailLength: Float,
    val dp: Float,
) {
    val centreX: Float get() = width / 2f
    /** How far the bubble can travel from the line, so it never leaves the lane. */
    val halfRange: Float get() = width / 2f - bubbleRadius - 10f * dp

    companion object {
        fun of(width: Float, height: Float, dp: Float): LaneGeometry {
            val bubbleRadius = 32f * dp
            // The same size as the bubble that says where you are, so the two read as a pair.
            val targetRadius = bubbleRadius
            val caption = 30f * dp
            val targetTop = (height - caption - targetRadius * 2f - 4f * dp).coerceAtLeast(bubbleRadius * 3f)
            val bubbleY = 14f * dp + bubbleRadius
            return LaneGeometry(
                width = width,
                height = height,
                bubbleRadius = bubbleRadius,
                bubbleY = bubbleY,
                targetRadius = targetRadius,
                targetTop = targetTop,
                captionTop = targetTop + targetRadius * 2f + 6f * dp,
                trailLength = minOf(targetTop - bubbleY - bubbleRadius - 18f * dp, 230f * dp).coerceAtLeast(40f * dp),
                dp = dp,
            )
        }
    }
}

/** A ring of recent bubble positions, newest first when read by age. NaN is "no signal". */
private class Trail(private val size: Int) {
    private val values = FloatArray(size) { Float.NaN }
    private var head = 0

    fun push(value: Float) {
        values[head] = value
        head = (head + 1) % size
    }

    /** Age 0 is the newest sample. */
    fun at(age: Int): Float = values[((head - 1 - age) % size + size) % size]

    val capacity: Int get() = size
}

private const val TrailSamples = 56
private const val TrailIntervalNanos = 26_000_000L

/** Where the in-tune window ends, as a fraction of the lane's half range. */
private const val InTuneFraction = TuningReading.IN_TUNE_CENTS / TuningReading.METER_RANGE_CENTS

/**
 * The colour of a position on the lane: green on the line, ramping out to the flat or sharp
 * colour so the last few cents read as arriving.
 */
private fun signalAt(offset: Float, s: TunerSignalColors): Color {
    val edge = if (offset < 0f) s.flat else s.sharp
    val blend = ((abs(offset) - InTuneFraction) / (0.4f - InTuneFraction)).coerceIn(0f, 1f)
    return lerp(s.inTune, edge, blend)
}

/**
 * A faint grid that fades out towards the edges, the one thing besides the line that tells
 * the eye this is a gauge. Built once per size and drawn as a single stroked path.
 */
private fun Modifier.gridBackdrop(ink: Color): Modifier = drawWithCache {
    val step = 26.dp.toPx()
    val path = Path()
    var x = size.width / 2f % step
    while (x < size.width) {
        path.moveTo(x, 0f)
        path.lineTo(x, size.height)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        path.moveTo(0f, y)
        path.lineTo(size.width, y)
        y += step
    }
    val brush = Brush.radialGradient(
        colors = listOf(ink.copy(alpha = 0.10f), ink.copy(alpha = 0.04f), Color.Transparent),
        center = Offset(size.width / 2f, size.height * 0.42f),
        radius = maxOf(size.width, size.height) * 0.62f,
    )
    onDrawBehind {
        drawPath(path, brush, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun DrawScope.drawLane(
    geometry: LaneGeometry,
    offset: Float,
    presence: Float,
    hold: Float,
    trail: Trail,
    trailTick: Int,
    signals: TunerSignalColors,
    celebrate: Boolean,
    idleColor: Color,
    markerColor: Color,
    bubbleScale: Float,
    bubbleFill: Color,
    celebrateFraction: Float,
) {
    // Reading the tick is what makes the trail redraw on its own clock.
    @Suppress("UNUSED_VARIABLE") val tick = trailTick
    val g = geometry
    val dp = g.dp
    val position = if (celebrate) 0f else offset
    val signalColor = signalAt(position, signals)
    val lineColor = signals.inTune
    val halfWindow = InTuneFraction * g.halfRange

    // The in-tune window: a band either side of the line, brighter the closer the bubble is.
    val closeness = (1f - abs(position) / 0.4f).coerceIn(0f, 1f) * maxOf(presence, celebrateFraction)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, lineColor.copy(alpha = 0.07f + 0.13f * closeness), lineColor.copy(alpha = 0.04f + 0.08f * closeness)),
            startY = 0f,
            endY = g.targetTop,
        ),
        topLeft = Offset(g.centreX - halfWindow, 0f),
        size = Size(halfWindow * 2f, g.targetTop),
        cornerRadius = CornerRadius(halfWindow),
    )

    // The line itself, from the top of the lane down to the note it is aimed at.
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0f), lineColor.copy(alpha = 0.9f), lineColor.copy(alpha = 0.9f)),
            startY = 0f,
            endY = g.targetTop,
        ),
        start = Offset(g.centreX, 0f),
        end = Offset(g.centreX, g.targetTop),
        strokeWidth = 3f * dp,
        cap = StrokeCap.Round,
    )

    // Flat and sharp, at the two ends of the bubble's travel. They light up on the side the
    // pitch is on, so the direction to turn the peg is readable without reading a number.
    val flatLit = if (presence > 0.01f) ((-position - InTuneFraction) / 0.3f).coerceIn(0f, 1f) * presence else 0f
    val sharpLit = if (presence > 0.01f) ((position - InTuneFraction) / 0.3f).coerceIn(0f, 1f) * presence else 0f
    val markerY = g.bubbleY
    val markerInset = g.bubbleRadius * 0.5f + 6f * dp
    drawFlat(Offset(markerInset + 6f * dp, markerY), dp, lerp(markerColor.copy(alpha = 0.42f), signals.flat, flatLit))
    drawSharp(Offset(g.width - markerInset - 6f * dp, markerY), dp, lerp(markerColor.copy(alpha = 0.42f), signals.sharp, sharpLit))

    // The trail: where the pitch has just been, fading with age. One stroked path with a
    // vertical gradient, because the trail only ever runs downwards, so a position down the
    // lane is an age and the gradient can carry the colour and the fade for the whole line.
    // Drawn as separate short segments it showed a dot at every joint where the round caps
    // overlapped.
    val originY = g.bubbleY + g.bubbleRadius * 0.7f
    val samples = trail.capacity
    val stepY = g.trailLength / samples
    val trailPath = Path()
    val stops = ArrayList<Pair<Float, Color>>(samples)
    var penDown = false
    for (age in 0 until samples) {
        val v = trail.at(age)
        val y = originY + age * stepY
        if (v.isNaN()) {
            penDown = false
            stops += (y - originY) / g.trailLength to Color.Transparent
            continue
        }
        val fade = 1f - age / samples.toFloat()
        stops += (y - originY) / g.trailLength to signalAt(v, signals).copy(alpha = 0.95f * fade * fade)
        val x = g.centreX + v * g.halfRange
        if (penDown) trailPath.lineTo(x, y) else trailPath.moveTo(x, y)
        penDown = true
    }
    if (stops.size > 1) {
        drawPath(
            path = trailPath,
            brush = Brush.verticalGradient(
                colorStops = stops.map { it.first to it.second }.toTypedArray(),
                startY = originY,
                endY = originY + g.trailLength,
            ),
            style = Stroke(width = 3.4f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // The bubble. Present while a note is heard; an empty ring breathing at the line when not.
    val x = g.centreX + position * g.halfRange
    val radius = g.bubbleRadius * bubbleScale
    val shown = maxOf(presence, celebrateFraction)
    if (shown < 0.99f) {
        drawCircle(
            color = idleColor.copy(alpha = 0.34f * (1f - shown)),
            radius = radius,
            center = Offset(g.centreX, g.bubbleY),
            style = Stroke(width = 2.2f * dp),
        )
    }
    if (shown > 0.01f) {
        drawCircle(signalColor.copy(alpha = 0.16f * shown), radius = radius + 8f * dp, center = Offset(x, g.bubbleY))
        drawCircle(signalColor.copy(alpha = shown), radius = radius, center = Offset(x, g.bubbleY))
        // The hold ring: fills round the bubble while the string is kept in tune, which is what
        // gives "keep it there" a visible end.
        if (hold > 0.001f) {
            drawArc(
                color = bubbleFill.copy(alpha = 0.9f),
                startAngle = -90f,
                sweepAngle = 360f * hold,
                useCenter = false,
                topLeft = Offset(x - radius + 5f * dp, g.bubbleY - radius + 5f * dp),
                size = Size((radius - 5f * dp) * 2f, (radius - 5f * dp) * 2f),
                style = Stroke(width = 3f * dp, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawFlat(centre: Offset, dp: Float, color: Color) {
    val stroke = Stroke(width = 2.3f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
    translate(centre.x, centre.y) {
        // One path for the stem and the bowl. Drawn as two strokes, the glyph is translucent
        // where they overlap and the overlap shows as a brighter patch at the joint; one stroke
        // is one shape, so the colour is the same everywhere.
        val glyph = Path().apply {
            moveTo(-3.5f * dp, -13f * dp)
            lineTo(-3.5f * dp, 11f * dp)
            moveTo(-3.5f * dp, 11f * dp)
            cubicTo(10f * dp, 6f * dp, 11f * dp, -7f * dp, -3.5f * dp, -2f * dp)
        }
        drawPath(glyph, color, style = stroke)
    }
}

private fun DrawScope.drawSharp(centre: Offset, dp: Float, color: Color) {
    val stroke = Stroke(width = 2.3f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
    translate(centre.x, centre.y) {
        val path = Path().apply {
            moveTo(-4f * dp, -12f * dp); lineTo(-4f * dp, 14f * dp)
            moveTo(4f * dp, -14f * dp); lineTo(4f * dp, 12f * dp)
            moveTo(-9f * dp, 3f * dp); lineTo(9f * dp, -3.5f * dp)
            moveTo(-9f * dp, 9.5f * dp); lineTo(9f * dp, 3f * dp)
        }
        drawPath(path, color, style = stroke)
    }
}

/** The number inside the bubble, or the tick that replaces it when the string is in tune. */
@Composable
private fun BubbleContent(
    readings: StateFlow<TuningReading?>,
    check: Float,
    hasSignal: Boolean,
    celebrate: Boolean,
    signals: TunerSignalColors,
) {
    val cents by readings.select(null as Int?) { it?.cents?.roundToInt() }
    val ink = signals.onSignal

    if (!celebrate && hasSignal) {
        Text(
            text = cents?.let { if (it > 0) "+$it" else it.toString() } ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ink.copy(alpha = 1f - check),
            textAlign = TextAlign.Center,
        )
    } else if (!celebrate) {
        Icon(
            painter = TunerIcons.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp),
        )
    }
    Canvas(Modifier.size(34.dp)) {
        if (check <= 0.001f) return@Canvas
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.18f, s * 0.54f)
            lineTo(s * 0.41f, s * 0.76f)
            lineTo(s * 0.84f, s * 0.26f)
        }
        val measured = Path()
        val measure = PathMeasure().apply { setPath(path, false) }
        measure.getSegment(0f, measure.length * check, measured, true)
        drawPath(measured, ink, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The note at the foot of the line, and one line under it saying what to do. */
@Composable
private fun BoxWithConstraintsScope.TargetNote(
    target: TuningString,
    targetHz: Float,
    readings: StateFlow<TuningReading?>,
    statusColor: Color,
    inTune: Boolean,
    hasSignal: Boolean,
    celebrate: Boolean,
    geometry: LaneGeometry,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val container by animateColorAsState(
        targetValue = if (inTune) statusColor.copy(alpha = 0.16f) else scheme.surfaceContainerHigh,
        animationSpec = TunerMotion.colourSpec(),
        label = "targetContainer",
    )

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer { translationY = geometry.targetTop },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(with(density) { (geometry.targetRadius * 2f).toDp() })
                .clip(CircleShape)
                .background(container)
                .border(2.5.dp, statusColor, CircleShape)
                // The note is the button for hearing it. Once every string is done the same
                // circle starts a new pass, and the caption under it says so.
                .bouncyClickable(
                    onClickLabel = stringResource(if (celebrate) R.string.start_over else R.string.play_note, target.fullLabel),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The note slides in the direction the target moved rather than being swapped in
            // place. Walking E A D G B E is a walk up the instrument, and a letter that is
            // simply replaced says nothing about which way you went. The direction comes from
            // the pitch and not from the peg order, so a re-entrant tuning still moves the way
            // it sounds.
            AnimatedContent(
                targetState = target,
                transitionSpec = {
                    val rising = targetState.note.midi > initialState.note.midi
                    val enter = slideInVertically(TunerMotion.spatial()) { h -> if (rising) h / 2 else -h / 2 } +
                        fadeIn(TunerMotion.fadeSpec())
                    val exit = slideOutVertically(TunerMotion.spatial()) { h -> if (rising) -h / 2 else h / 2 } +
                        fadeOut(TunerMotion.fadeSpec())
                    enter togetherWith exit using SizeTransform(clip = false)
                },
                label = "note",
            ) { note ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = note.label,
                        style = NoteBubbleStyle,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = note.note.octave.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 1.dp, bottom = 4.dp),
                    )
                }
            }
        }
        Caption(readings = readings, targetHz = targetHz, hasSignal = hasSignal, celebrate = celebrate, statusColor = statusColor)
    }
}

/** "Tune up · 82.4 Hz": the instruction first, the number it is measured against after it. */
@Composable
private fun Caption(
    readings: StateFlow<TuningReading?>,
    targetHz: Float,
    hasSignal: Boolean,
    celebrate: Boolean,
    statusColor: Color,
) {
    val status by readings.select(null as TuningStatus?) { it?.status }
    val measured by readings.select(null as Int?) { reading -> reading?.frequencyHz?.let { (it * 10f).roundToInt() } }

    val words = stringResource(
        when {
            celebrate -> R.string.all_tuned
            !hasSignal || status == null -> R.string.listening
            status == TuningStatus.IN_TUNE -> R.string.in_tune
            status == TuningStatus.FLAT -> R.string.tune_up
            else -> R.string.tune_down
        },
    )
    val hz = if (celebrate) stringResource(R.string.start_over)
    else "%.1f Hz".format(if (hasSignal && measured != null) measured!! / 10f else targetHz)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = words,
            style = MaterialTheme.typography.labelLarge,
            color = if (hasSignal || celebrate) statusColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hz,
            style = MaterialTheme.typography.labelLarge,
            color = if (celebrate) statusColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasSignal) 1f else 0.6f),
            maxLines = 1,
        )
    }
}

@Composable
private fun rememberBreath(): State<Float> {
    val transition = rememberInfiniteTransition(label = "breath")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = TunerMotion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathValue",
    )
}
