package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus
import nl.markmaaktmedia.guitartuner.domain.model.TuningString
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.NoteDisplayStyle
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The meter.
 *
 * A 200 degree arc reading plus or minus fifty cents, with the note it is measuring
 * against in the middle. Everything that moves at the audio rate is read *inside* the
 * `Canvas` draw lambda, so twenty-one readings a second invalidate the draw phase and
 * never reach composition or layout. The springs then interpolate between those readings
 * at display refresh rate, which is what makes a 21 Hz signal look like continuous
 * motion.
 *
 * The colour is computed per frame from the needle position rather than switched on the
 * three way status, so the meter warms and cools continuously as the string is turned.
 * A hard switch at the edge of the in-tune window makes the last few cents feel like a
 * cliff; a ramp makes them feel like arriving.
 */
@Composable
fun TuneDial(
    readings: StateFlow<TuningReading?>,
    holdProgress: StateFlow<Float>,
    target: TuningString,
    targetHz: Float,
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
        holdProgress.collectLatest { value ->
            hold.animateTo(value, tween(70, easing = LinearEasing))
        }
    }

    val status by readings.select(TuningStatus.IN_TUNE) { it?.status ?: TuningStatus.IN_TUNE }
    val hasSignal by readings.select(false) { it != null }

    val statusColor by animateColorAsState(
        targetValue = when {
            !hasSignal -> scheme.onSurfaceVariant
            status == TuningStatus.IN_TUNE -> signals.inTune
            status == TuningStatus.FLAT -> signals.flat
            else -> signals.sharp
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "statusColor",
    )

    Box(modifier = modifier.aspectRatio(1.12f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawDial(
                offset = offset.value,
                presence = presence.value,
                hold = hold.value,
                trackColor = signals.track,
                flatColor = signals.flat,
                sharpColor = signals.sharp,
                inTuneColor = signals.inTune,
                tickColor = scheme.onSurfaceVariant,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = target.label,
                    style = NoteDisplayStyle,
                    color = statusColor,
                )
                Text(
                    text = target.note.octave.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = statusColor.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 2.dp, bottom = 14.dp),
                )
            }
            FrequencyReadout(readings, targetHz)
        }

        CentsReadout(
            readings = readings,
            color = statusColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

/**
 * The measured frequency, and the target under it.
 *
 * Its own composable so that the twenty-one values a second it consumes invalidate one
 * line of text rather than the whole dial. Rounded to a tenth of a hertz, which is finer
 * than the ear and coarse enough that the digit settles instead of flickering.
 */
@Composable
private fun FrequencyReadout(readings: StateFlow<TuningReading?>, targetHz: Float) {
    val measured by readings.select(null as Int?) { reading ->
        reading?.frequencyHz?.let { (it * 10f).roundToInt() }
    }
    val text = measured?.let { "%.1f Hz".format(it / 10f) } ?: "%.1f Hz".format(targetHz)
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (measured == null) 0.45f else 1f,
        ),
    )
}

/**
 * The signed offset in whole cents, with the instruction under it.
 *
 * Whole cents on purpose: at a tenth of a cent the number never stops moving, and a
 * digit that never settles reads as an unreliable instrument even when the reading is
 * good.
 */
@Composable
private fun CentsReadout(
    readings: StateFlow<TuningReading?>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val cents by readings.select(null as Int?) { it?.cents?.roundToInt() }
    val status by readings.select(null as TuningStatus?) { it?.status }

    val label = stringResource(
        when {
            cents == null -> R.string.listening
            status == TuningStatus.IN_TUNE -> R.string.in_tune
            status == TuningStatus.FLAT -> R.string.tune_up
            else -> R.string.tune_down
        },
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = cents?.let { if (it > 0) "+$it" else it.toString() } ?: "–",
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Collects one derived value out of the reading flow.
 *
 * The `distinctUntilChanged` is the entire point: the flow itself fires twenty-one times
 * a second, and without it every text on the dial would recompose that often to show a
 * number that changed once.
 */
@Composable
private fun <T> StateFlow<TuningReading?>.select(
    initial: T,
    selector: (TuningReading?) -> T,
): State<T> {
    val flow = remember(this) { map(selector).distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initial)
}

private const val ARC_START = 170f
private const val ARC_SWEEP = 200f
private const val ARC_CENTRE = ARC_START + ARC_SWEEP / 2f

/** Where the in-tune window ends, as a fraction of the meter's half range. */
private const val IN_TUNE_FRACTION =
    TuningReading.IN_TUNE_CENTS / TuningReading.METER_RANGE_CENTS

private fun DrawScope.drawDial(
    offset: Float,
    presence: Float,
    hold: Float,
    trackColor: Color,
    flatColor: Color,
    sharpColor: Color,
    inTuneColor: Color,
    tickColor: Color,
) {
    val centre = Offset(size.width / 2f, size.height * 0.56f)
    val radius = minOf(size.width, size.height) * 0.40f
    val trackWidth = radius * 0.085f

    fun angleFor(value: Float) = ARC_CENTRE + value.coerceIn(-1f, 1f) * (ARC_SWEEP / 2f)

    fun pointAt(degrees: Float, distance: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        return Offset(
            centre.x + (cos(radians) * distance).toFloat(),
            centre.y + (sin(radians) * distance).toFloat(),
        )
    }

    // The colour ramps from the in-tune green out to the edge colour rather than
    // switching at the window boundary, so the last few cents read as arriving.
    val magnitude = abs(offset)
    val edgeColor = if (offset < 0f) flatColor else sharpColor
    val blend = ((magnitude - IN_TUNE_FRACTION) / (0.4f - IN_TUNE_FRACTION)).coerceIn(0f, 1f)
    val signalColor = lerp(inTuneColor, edgeColor, blend)

    val arcRect = Rect(
        offset = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2f, radius * 2f),
    )

    // In tune and holding: a soft wash behind the note, so the state is readable from
    // across a room without reading anything.
    val glow = (1f - blend) * presence
    if (glow > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(inTuneColor.copy(alpha = 0.22f * glow), Color.Transparent),
                center = centre,
                radius = radius * 0.95f,
            ),
            radius = radius * 0.95f,
            center = centre,
        )
    }

    drawArc(
        color = trackColor,
        startAngle = ARC_START,
        sweepAngle = ARC_SWEEP,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = trackWidth, cap = StrokeCap.Round),
    )

    // Ticks every five cents, with a taller one every twenty-five.
    val tickCount = 20
    for (i in 0..tickCount) {
        val value = -1f + 2f * i / tickCount
        val major = i % 5 == 0
        val angle = angleFor(value)
        val inner = radius - trackWidth * (if (major) 1.55f else 1.15f)
        val outer = radius - trackWidth * 0.75f
        drawLine(
            color = tickColor.copy(alpha = if (major) 0.55f else 0.26f),
            start = pointAt(angle, inner),
            end = pointAt(angle, outer),
            strokeWidth = trackWidth * (if (major) 0.34f else 0.2f),
            cap = StrokeCap.Round,
        )
    }

    if (presence > 0.01f) {
        // The lit segment runs from dead centre out to the needle, so the size of the
        // error is visible as a length and not only as a position.
        val needleAngle = angleFor(offset)
        val sweep = needleAngle - ARC_CENTRE
        if (abs(sweep) > 0.5f) {
            drawArc(
                color = signalColor.copy(alpha = 0.9f * presence),
                startAngle = if (sweep < 0f) needleAngle else ARC_CENTRE,
                sweepAngle = abs(sweep),
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = trackWidth, cap = StrokeCap.Round),
            )
        }

        drawLine(
            color = signalColor.copy(alpha = presence),
            start = pointAt(needleAngle, radius - trackWidth * 2.6f),
            end = pointAt(needleAngle, radius + trackWidth * 0.55f),
            strokeWidth = trackWidth * 0.9f,
            cap = StrokeCap.Round,
        )
    }

    // The hold ring grows outwards from the top as the half second runs, which is what
    // turns "keep it there" into something with a visible end.
    if (hold > 0.001f) {
        val holdRadius = radius + trackWidth * 1.9f
        val holdRect = Rect(
            offset = Offset(centre.x - holdRadius, centre.y - holdRadius),
            size = Size(holdRadius * 2f, holdRadius * 2f),
        )
        drawArc(
            color = inTuneColor,
            startAngle = ARC_CENTRE - hold * (ARC_SWEEP / 2f),
            sweepAngle = hold * ARC_SWEEP,
            useCenter = false,
            topLeft = holdRect.topLeft,
            size = holdRect.size,
            style = Stroke(width = trackWidth * 0.42f, cap = StrokeCap.Round),
        )
    }

    // The centre marker. Always drawn, always the in-tune colour: it is the thing the
    // needle is being aimed at.
    drawLine(
        color = inTuneColor.copy(alpha = 0.85f),
        start = pointAt(ARC_CENTRE, radius - trackWidth * 2.0f),
        end = pointAt(ARC_CENTRE, radius + trackWidth * 1.0f),
        strokeWidth = trackWidth * 0.4f,
        cap = StrokeCap.Round,
    )
}
