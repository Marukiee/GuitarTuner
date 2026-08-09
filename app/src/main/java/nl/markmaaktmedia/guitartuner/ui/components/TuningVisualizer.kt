package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.R
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus
import nl.markmaaktmedia.guitartuner.ui.theme.flatAccent
import nl.markmaaktmedia.guitartuner.ui.theme.inTuneAccent
import nl.markmaaktmedia.guitartuner.ui.theme.sharpAccent

/**
 * The tuning meter: a fixed target line with a morphing bubble that swims towards it.
 *
 * ## Why this is built the way it is
 *
 * The reading arrives about 21 times a second. If the composable read it as ordinary state, the
 * whole subtree would recompose 21 times a second and the animation would be quantised to the
 * analysis rate rather than the display rate.
 *
 * Instead the flow drives three [Animatable]s inside a `LaunchedEffect`, and every one of them is
 * read *inside* the `Canvas` draw lambda or a `graphicsLayer` block. A state read in either place
 * invalidates only the draw phase: no recomposition, no relayout, and the springs interpolate at
 * whatever rate the display runs at. `collectLatest` cancels the previous `animateTo` when a new
 * reading lands, and a cancelled `Animatable` keeps its velocity, so retargeting 21 times a
 * second reads as one continuous motion rather than a series of restarts.
 *
 * The only thing that genuinely recomposes is the numeric readout, which is isolated in its own
 * composable and rounded to whole cents so it settles rather than flickering.
 */
@Composable
fun TuningVisualizer(
    reading: StateFlow<TuningReading?>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // Horizontal position of the bubble, -1 (50 cents flat) to +1 (50 cents sharp).
    val offset = remember { Animatable(0f) }
    // 0 = far from the target, 1 = dead on. Drives colour, shape and rotation together.
    val closeness = remember { Animatable(0f) }
    // IDLE_PRESENCE = nothing is being heard, 1 = a note is present. It never reaches 0: an
    // empty meter with a lone hairline in it looks broken rather than idle.
    val presence = remember { Animatable(IDLE_PRESENCE) }

    LaunchedEffectReading(reading, offset, closeness, presence)

    val spin = rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing)),
        label = "spinAngle",
    )

    // Morph endpoints are allocated once. Rebuilding a RoundedPolygon per frame would undo the
    // whole point of keeping this in the draw phase.
    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.82f,
                rounding = CornerRounding(0.45f),
                innerRounding = CornerRounding(0.55f),
            ),
            end = RoundedPolygon.circle(numVertices = 16),
        )
    }

    Column(modifier, verticalArrangement = Arrangement.Center) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Geometry is computed once, here, and shared by the canvas and the readout.
            //
            // It used to be worked out twice, and the two copies disagreed. The readout derived
            // its travel inside a graphicsLayer block, where `size` is the size of the node the
            // modifier sits on, which was the small box wrapping the text rather than the canvas.
            // `size.width / 2 - 70.dp` came out negative on that box, so the number slid the
            // opposite way to the bubble it was supposed to be printed on. The two centres did
            // not agree vertically either.
            val bubbleRadius = 52.dp
            val tickGap = 28.dp
            val centreYDp = (maxHeight - tickGap) / 2f
            val travelDp = maxWidth / 2f - bubbleRadius - 10.dp

            val travel = with(density) { travelDp.toPx() }
            val centreY = with(density) { centreYDp.toPx() }
            val radius = with(density) { bubbleRadius.toPx() }

            Canvas(Modifier.fillMaxSize()) {
                val centreX = size.width / 2f

                drawScale(
                    centreX = centreX,
                    tickY = centreY + radius + with(density) { tickGap.toPx() },
                    lineTop = centreY - radius - with(density) { 20.dp.toPx() },
                    travel = travel,
                    tickColor = colors.onSurfaceVariant.copy(alpha = 0.38f),
                    trackColor = colors.onSurfaceVariant.copy(alpha = 0.12f),
                    targetColor = colors.primary,
                    density = density,
                )

                val here = offset.value
                val near = closeness.value
                val alpha = presence.value

                val live = lerp(
                    if (here < 0f) colors.flatAccent else colors.sharpAccent,
                    colors.inTuneAccent,
                    near,
                )

                // At rest the bubble is a neutral surface shape. It only takes on the warm or
                // cool accent once there is actually a note to be warm or cool about.
                val bubbleColor = lerp(
                    colors.surfaceContainerHighest,
                    live,
                    ((alpha - IDLE_PRESENCE) / (1f - IDLE_PRESENCE)).coerceIn(0f, 1f),
                )

                // Off target the bubble is a slowly turning cookie; on target it settles into a
                // still circle and swells slightly, which is the "locked on" cue.
                val path = morph.toPath(progress = near).asComposePath()
                val scale = radius * (0.92f + 0.08f * near)
                path.transform(
                    Matrix().apply {
                        translate(centreX + here * travel, centreY)
                        rotateZ(spin.value * (1f - near))
                        scale(scale, scale)
                    },
                )

                drawPath(path, bubbleColor)
            }

            // The readout rides along with the bubble, off the same numbers. Both translations
            // are draw-phase properties, so moving it neither recomposes nor relayouts.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset.value * travel
                        translationY = centreY - size.height / 2f
                        // Fade the number out entirely at rest; a "0" with nothing playing is a lie.
                        alpha = ((presence.value - IDLE_PRESENCE) / (1f - IDLE_PRESENCE))
                            .coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                CentsReadout(reading)
            }
        }

        StatusLabel(reading, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/**
 * Split out so the springs are set up once and the flow subscription is not re-created on every
 * recomposition of the visualizer.
 */
@Composable
private fun LaunchedEffectReading(
    reading: StateFlow<TuningReading?>,
    offset: Animatable<Float, *>,
    closeness: Animatable<Float, *>,
    presence: Animatable<Float, *>,
) {
    LaunchedEffect(reading) {
        reading.collectLatest { current ->
            if (current == null) {
                coroutineScope {
                    launch { presence.animateTo(IDLE_PRESENCE, tween(350)) }
                    launch { closeness.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                }
                return@collectLatest
            }
            coroutineScope {
                launch {
                    presence.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                }
                launch {
                    closeness.animateTo(
                        targetValue = closenessOf(current.cents),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
                launch {
                    offset.animateTo(
                        targetValue = current.normalizedOffset,
                        // Slightly under-damped on purpose: the bubble overshoots the target a
                        // hair and settles back, which reads as physical rather than mechanical.
                        animationSpec = spring(
                            dampingRatio = 0.62f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        }
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScale(
    centreX: Float,
    tickY: Float,
    lineTop: Float,
    travel: Float,
    tickColor: Color,
    trackColor: Color,
    targetColor: Color,
    density: androidx.compose.ui.unit.Density,
) {
    val minorHeight = with(density) { 9.dp.toPx() }
    val majorHeight = with(density) { 17.dp.toPx() }

    // A baseline under the ticks, so the scale reads as one object instead of loose marks.
    drawLine(
        color = trackColor,
        start = Offset(centreX - travel, tickY),
        end = Offset(centreX + travel, tickY),
        strokeWidth = with(density) { 2.dp.toPx() },
        cap = StrokeCap.Round,
    )

    for (cents in -50..50 step 5) {
        if (cents == 0) continue
        val major = cents % 25 == 0
        val x = centreX + (cents / 50f) * travel
        val half = (if (major) majorHeight else minorHeight) / 2f
        drawLine(
            color = tickColor,
            start = Offset(x, tickY - half),
            end = Offset(x, tickY + half),
            strokeWidth = with(density) { (if (major) 2.5f else 1.5f).dp.toPx() },
            cap = StrokeCap.Round,
        )
    }

    // The target line spans the bubble and the scale so the bubble visibly crosses it.
    drawLine(
        color = targetColor,
        start = Offset(centreX, lineTop),
        end = Offset(centreX, tickY + majorHeight),
        strokeWidth = with(density) { 4.dp.toPx() },
        cap = StrokeCap.Round,
    )
}

/** How present the bubble is when nothing is playing. */
private const val IDLE_PRESENCE = 0.34f

/**
 * The one part that really recomposes. Rounding to whole cents means it changes a few times a
 * second at most instead of on every reading.
 *
 * The colour is computed here rather than inherited, and it is picked from the *luminance of the
 * bubble underneath*. A fixed `onPrimary` was white, which is unreadable the moment the bubble is
 * a light tone, and on a dynamic-colour scheme there is no way to know in advance whether it will
 * be. This mirrors the same closeness curve the bubble uses, so the two can never disagree.
 */
@Composable
private fun CentsReadout(reading: StateFlow<TuningReading?>) {
    val colors = MaterialTheme.colorScheme
    val current by reading.collectAsStateWithLifecycle()
    val cents = current?.cents?.let { Math.round(it) } ?: 0

    val fill = current?.let { bubbleFill(it.cents, colors) } ?: colors.surfaceContainerHighest

    Text(
        text = if (current == null) "" else if (cents > 0) "+$cents" else "$cents",
        color = contentColorOn(fill),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusLabel(reading: StateFlow<TuningReading?>, modifier: Modifier = Modifier) {
    val current by reading.collectAsStateWithLifecycle()
    val label = when (current?.status) {
        null -> R.string.listening
        TuningStatus.FLAT -> R.string.tune_up
        TuningStatus.SHARP -> R.string.tune_down
        TuningStatus.IN_TUNE -> R.string.in_tune
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
