package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import nl.markmaaktmedia.guitartuner.domain.model.HeadstockLayout
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus
import nl.markmaaktmedia.guitartuner.domain.model.TuningString
import nl.markmaaktmedia.guitartuner.ui.components.HeadSpec
import nl.markmaaktmedia.guitartuner.ui.components.bouncyClickable
import nl.markmaaktmedia.guitartuner.ui.components.nutSlot
import nl.markmaaktmedia.guitartuner.ui.components.specFor
import nl.markmaaktmedia.guitartuner.ui.theme.LocalTunerSignals
import nl.markmaaktmedia.guitartuner.ui.theme.TunerIcons
import nl.markmaaktmedia.guitartuner.ui.theme.TunerMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The instrument's head with a bubble beside every tuner.
 *
 * The bubbles are the string picker and the progress of a tuning pass at once: tapping one
 * locks the tuner to that string, a tuned one turns green with a tick, and the one being
 * tuned has a ring round it. They sit on the left and right of the head, level with the tuner
 * they belong to, so "which peg do I turn" is answered by looking across rather than by
 * counting.
 *
 * The head is drawn from the same [HeadSpec] as the small silhouettes in the instrument
 * picker, so every instrument keeps its own peg layout. A Fender style head with six tuners
 * in a line would stack six bubbles in one column taller than the head, so inline heads are
 * drawn here with the tuners split over both sides, which is also how the player sees them
 * from behind the neck.
 */
@Composable
fun StringBoard(
    instrument: Instrument,
    tuning: Tuning,
    activeIndex: Int,
    tunedIndices: Set<Int>,
    soundingIndex: Int?,
    readings: StateFlow<TuningReading?>,
    inputLevelDb: StateFlow<Float>,
    onSelect: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val signals = LocalTunerSignals.current
    val density = LocalDensity.current

    val style = HeadStyleChoice.current
    val spec = remember(instrument.layout, tuning.stringCount) {
        specFor(boardLayout(instrument.layout), tuning.stringCount)
    }
    val status by readings.select(null as TuningStatus?) { it?.status }
    val inTuneNow by readings.select(false) {
        it != null && it.settled && it.status == TuningStatus.IN_TUNE && it.clarity >= 0.8f
    }
    val activeColor by animateColorAsState(
        targetValue = when (status) {
            // Quiet again: a string that is done stays green.
            null -> if (activeIndex in tunedIndices) signals.inTune else scheme.primary
            TuningStatus.IN_TUNE -> signals.inTune
            TuningStatus.FLAT -> signals.flat
            TuningStatus.SHARP -> signals.sharp
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "boardActive",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val dp = density.density
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val layout = remember(spec, style, tuning.stringCount, width, height) {
            BoardLayout.of(spec, style, tuning.stringCount, width, height, dp)
        }

        val colors = scheme.headColors(style)
        Canvas(Modifier.fillMaxSize()) {
            drawExpressiveHead(
                g = layout.geo,
                style = style,
                colors = colors,
                activeIndex = activeIndex,
                tunedIndices = tunedIndices,
                active = activeColor,
                tuned = signals.inTune,
                reach = layout.bubbles,
            )
        }

        // The vibrating string is its own layer so the wave redraws without touching the
        // head underneath it.
        VibratingString(
            layout = layout,
            activeIndex = activeIndex,
            color = activeColor,
            inputLevelDb = inputLevelDb,
            hasSignal = status != null,
        )

        tuning.strings.forEach { string ->
            val place = layout.bubbles[string.physicalIndex]
            StringBubble(
                string = string,
                isActive = string.physicalIndex == activeIndex,
                isTuned = string.physicalIndex in tunedIndices,
                isInTune = inTuneNow && string.physicalIndex == activeIndex,
                isSounding = string.physicalIndex == soundingIndex,
                style = style,
                activeColor = activeColor,
                onClick = { onSelect(string.physicalIndex) },
                onLongClick = { onPlay(string.physicalIndex) },
                size = with(density) { layout.bubbleDiameter.toDp() },
                modifier = Modifier
                    .graphicsLayer {
                        translationX = place.x - layout.bubbleDiameter / 2f
                        translationY = place.y - layout.bubbleDiameter / 2f
                    },
            )
        }
    }
}

/** Inline heads are split over both sides, see [StringBoard]. Everything else keeps its own. */
private fun boardLayout(layout: HeadstockLayout): HeadstockLayout =
    if (layout == HeadstockLayout.INLINE) HeadstockLayout.FOUR_THREE else layout

/** Where every part of the board sits, in pixels of the board. */
private class BoardLayout(
    val geo: HeadGeometry,
    val width: Float,
    val height: Float,
    val dp: Float,
    val bubbles: List<Offset>,
    val bubbleDiameter: Float,
) {
    val pegs: List<Offset> get() = geo.pegs
    val nutY: Float get() = geo.nutY

    /** Where each string crosses the nut, or for a banjo's drone, where it leaves its tuner. */
    val slots: List<Float> get() = geo.slots

    fun isDrone(index: Int) = geo.isDrone(index)

    companion object {
        fun of(
            spec: HeadSpec,
            style: HeadStyle,
            count: Int,
            width: Float,
            height: Float,
            dp: Float,
        ): BoardLayout {
            val gutter = 4f * dp
            // Large enough to tap with a thumb, small enough that three fit down one side.
            val wanted = 50f * dp
            val headWidth = min(width - 2f * (wanted + gutter), 300f * dp).coerceAtLeast(120f * dp)
            val headLeft = (width - headWidth) / 2f
            // One design dp is the density on a full size head and shrinks with it on a narrow
            // phone, so the buttons never reach over to the bubbles.
            val geo = HeadGeometry(spec, count, headLeft, headWidth, height, unit = headWidth / 300f)

            // Bubbles: one column down each side, level with their tuner, pushed apart where two
            // would touch and kept on the board.
            val peg = geo.pegs
            val centre = width / 2f
            val diameter = wanted
            val gap = diameter + 6f * dp
            val left = peg.indices.filter { peg[it].x < centre }.sortedBy { peg[it].y }
            val right = peg.indices.filter { peg[it].x >= centre }.sortedBy { peg[it].y }
            val bubbleY = FloatArray(count)
            for (column in listOf(left, right)) {
                val ys = spread(column.map { peg[it].y }, gap, diameter / 2f, height - diameter / 2f)
                column.forEachIndexed { i, index -> bubbleY[index] = ys[i] }
            }
            val frame = Frame(geo, style)
            val bubbles = peg.indices.map { i ->
                val side = if (peg[i].x < centre) -1f else 1f
                val near = diameter / 2f + 2f * dp
                val px = if (style == HeadStyle.BUTTONS) {
                    // The button is the tuner, so it stands just off the side of the head.
                    (frame.edge(peg[i].y, side) + side * (20f * geo.unit + diameter / 2f))
                        .coerceIn(near, width - near)
                } else {
                    if (side < 0f) near else width - near
                }
                Offset(px, bubbleY[i])
            }
            return BoardLayout(geo, width, height, dp, bubbles, diameter)
        }

        /** Pushes sorted positions apart to at least [gap], then back inside [min]..[max]. */
        fun spread(ys: List<Float>, gap: Float, min: Float, max: Float): List<Float> {
            if (ys.isEmpty()) return ys
            val out = ys.toMutableList()
            out[0] = out[0].coerceAtLeast(min)
            for (i in 1 until out.size) out[i] = out[i].coerceAtLeast(out[i - 1] + gap)
            out[out.lastIndex] = out.last().coerceAtMost(max)
            for (i in out.lastIndex - 1 downTo 0) out[i] = out[i].coerceAtMost(out[i + 1] - gap)
            return out
        }
    }
}

/**
 * The string being tuned, as a line that shakes in proportion to what the microphone hears.
 *
 * It is a standing wave pinned at the nut and at the bottom of the board, so the middle moves
 * most. Amplitude follows the input level and dies away with it, which is the one place the
 * screen shows that the app is hearing the instrument and not guessing.
 */
@Composable
private fun VibratingString(
    layout: BoardLayout,
    activeIndex: Int,
    color: Color,
    inputLevelDb: StateFlow<Float>,
    hasSignal: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "stringPhase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val level by inputLevelDb.collectAsLevel()
    val amplitude by animateFloatAsState(
        targetValue = if (hasSignal) ((level + 50f) / 32f).coerceIn(0f, 1f) else 0f,
        animationSpec = TunerMotion.spatial(),
        label = "amplitude",
    )
    Canvas(Modifier.fillMaxSize()) {
        val peg = layout.pegs.getOrNull(activeIndex) ?: return@Canvas
        val slot = layout.slots[activeIndex]
        val top = if (layout.isDrone(activeIndex)) peg.y else layout.nutY
        val bottom = layout.height - (NeckInsetDd + 8f) * layout.geo.unit
        val dp = layout.dp
        val path = Path()
        val steps = 28
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            val envelope = sin(PI * t).toFloat()
            val px = slot + amplitude * 3.2f * dp * envelope * sin(phase + t * 5f)
            val py = top + t * (bottom - top)
            if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 3.4f * dp, cap = StrokeCap.Round))
    }
}

@Composable
private fun StateFlow<Float>.collectAsLevel(): State<Float> = collectAsStateWithLifecycle()

/** One bubble per string: its note, whether it is the target, and whether it is done. */
@Composable
private fun StringBubble(
    string: TuningString,
    isActive: Boolean,
    isTuned: Boolean,
    isInTune: Boolean,
    isSounding: Boolean,
    style: HeadStyle,
    activeColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val signals = LocalTunerSignals.current

    val buttons = style == HeadStyle.BUTTONS
    val container by animateColorAsState(
        targetValue = when {
            isInTune -> signals.inTune
            isActive -> activeColor
            // On a tuner button a see through green would let its own stem shine through.
            isTuned -> if (buttons) signals.inTune else signals.inTune.copy(alpha = 0.22f)
            buttons -> scheme.tertiary
            style == HeadStyle.BLOCKS -> scheme.secondaryContainer
            else -> scheme.surfaceContainerHighest
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "bubbleContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            isInTune || isActive -> signals.onSignal
            isTuned -> if (buttons) signals.onSignal else signals.inTune
            buttons -> scheme.onTertiary
            style == HeadStyle.BLOCKS -> scheme.onSecondaryContainer
            else -> scheme.onSurface
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "bubbleContent",
    )
    // The shape morph of Material's expressive buttons: the chosen one squares up.
    val corner by animateDpAsState(
        targetValue = if (isActive) 16.dp else size / 2,
        animationSpec = TunerMotion.spatial(),
        label = "bubbleCorner",
    )
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    // The sounding bubble swells while the reference tone plays, which is the only
    // confirmation that the sound came from this app and not from the room.
    val swell by animateFloatAsState(
        targetValue = if (isSounding) 1.12f else 1f,
        animationSpec = TunerMotion.springy(),
        label = "bubbleSwell",
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(swell)
            .clip(shape)
            .background(container)
            .bouncyClickable(role = Role.RadioButton, onClickLabel = string.fullLabel, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = string.label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = content,
        )
        AnimatedVisibility(
            visible = isTuned && !isInTune && !buttons,
            enter = scaleIn(TunerMotion.springy()) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                painter = TunerIcons.CheckCircleFilled,
                contentDescription = null,
                tint = signals.inTune,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
