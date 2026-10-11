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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
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

    val spec = remember(instrument.layout, tuning.stringCount) {
        specFor(boardLayout(instrument.layout), tuning.stringCount)
    }
    val status by readings.select(null as TuningStatus?) { it?.status }
    val inTuneNow by readings.select(false) {
        it != null && it.settled && it.status == TuningStatus.IN_TUNE && it.clarity >= 0.8f
    }
    val activeColor by animateColorAsState(
        targetValue = when (status) {
            null -> scheme.primary
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

        val layout = remember(spec, tuning.stringCount, width, height) {
            BoardLayout.of(spec, tuning.stringCount, width, height, dp)
        }

        val wood = remember(instrument) { instrument.wood() }
        Canvas(Modifier.fillMaxSize()) {
            drawBoard(
                layout = layout,
                spec = spec,
                wood = wood,
                activeIndex = activeIndex,
                tunedIndices = tunedIndices,
                active = activeColor,
                tuned = signals.inTune,
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
    val width: Float,
    val height: Float,
    val dp: Float,
    /** Left edge and width of the box the head is drawn in. */
    val headLeft: Float,
    val headWidth: Float,
    val pegs: List<Offset>,
    val knobs: List<Offset>,
    val bubbles: List<Offset>,
    val bubbleDiameter: Float,
    val nutY: Float,
    /** Where each string crosses the nut, or for a banjo's drone, where it leaves its tuner. */
    val slots: List<Float>,
    val hasDrone: Boolean,
) {
    fun isDrone(index: Int) = hasDrone && index == 0

    /** The crown and the tuners are spread wide, so the knobs have room beside the head... */
    fun headX(fraction: Float) = headLeft + ((fraction - 0.5f) * XSpread + 0.5f) * headWidth

    /** ...but the neck and the nut keep their true width, which is what makes the strings fan. */
    fun neckX(fraction: Float) = headLeft + fraction * headWidth
    fun headY(fraction: Float) = fraction * height

    companion object {
        /** The head is drawn wider than its small silhouette, so the tuners have room for knobs. */
        const val XSpread = 1.02f

        /** How much wider than the nut the neck is, as a fraction of the head box. */
        const val NeckPad = 0.03f

        fun of(spec: HeadSpec, count: Int, width: Float, height: Float, dp: Float): BoardLayout {
            val gutter = 4f * dp
            // Large enough to tap with a thumb, small enough that three fit down one side.
            val wanted = 50f * dp
            val headWidth = min(width - 2f * (wanted + gutter), 300f * dp).coerceAtLeast(120f * dp)
            val headLeft = (width - headWidth) / 2f

            val peg = ArrayList<Offset>(count)
            val pegSource = spec.pegs + listOfNotNull(spec.dronePeg)
            // A banjo's drone is string zero and sits off the neck; every other string takes the
            // peg at its own position in nut order.
            val order = if (spec.dronePeg != null) listOf(pegSource.last()) + spec.pegs else spec.pegs

            fun x(f: Float) = headLeft + ((f - 0.5f) * XSpread + 0.5f) * headWidth
            fun y(f: Float) = f * height

            order.take(count).forEach { peg += Offset(x(it.x), y(it.y)) }

            // Knobs sit just outside the edge of the wood, at the height of their own tuner, on
            // the side it is on: where the head sweeps in, the knob follows it in.
            val centre = width / 2f
            val crownHalf = (spec.crownRight - spec.crownLeft) / 2f * XSpread * headWidth
            val neckHalf = (spec.nutSpread / 2f + NeckPad) * headWidth
            val shape = headShapeFor(spec, neckHalf / crownHalf)
            val nutTop = nutYOf(spec, height)
            val knobs = peg.map { p ->
                val side = if (p.x < centre) -1f else 1f
                Offset(centre + side * (shape.edge(p.y / nutTop) * crownHalf + 17f * dp), p.y)
            }

            // Bubbles: one column down each side, level with their tuner, pushed apart where two
            // would touch and kept on the board.
            val diameter = wanted
            val gap = diameter + 6f * dp
            val left = peg.indices.filter { peg[it].x < centre }.sortedBy { peg[it].y }
            val right = peg.indices.filter { peg[it].x >= centre }.sortedBy { peg[it].y }
            val bubbleY = FloatArray(count)
            for (column in listOf(left, right)) {
                val ys = spread(column.map { peg[it].y }, gap, diameter / 2f, height - diameter / 2f)
                column.forEachIndexed { i, index -> bubbleY[index] = ys[i] }
            }
            val bubbles = peg.indices.map { i ->
                val px = if (peg[i].x < centre) diameter / 2f + 2f * dp else width - diameter / 2f - 2f * dp
                Offset(px, bubbleY[i])
            }
            val slots = (0 until peg.size).map { i ->
                if (spec.dronePeg != null && i == 0) peg[0].x
                else headLeft + nutSlot(if (spec.dronePeg != null) i - 1 else i, spec.pegs.size, spec) * headWidth
            }
            return BoardLayout(
                width = width,
                height = height,
                dp = dp,
                headLeft = headLeft,
                headWidth = headWidth,
                pegs = peg,
                knobs = knobs,
                bubbles = bubbles,
                bubbleDiameter = diameter,
                nutY = nutYOf(spec, height),
                slots = slots,
                hasDrone = spec.dronePeg != null,
            )
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

/** Where the nut sits down the board, which is also where the head ends. */
private fun nutYOf(spec: HeadSpec, height: Float) = height * (spec.headBottom + (1f - spec.headBottom) * 0.22f)

private fun headShapeFor(spec: HeadSpec, neckUnits: Float): HeadShape =
    if (spec.scroll) HeadShape.pegbox(neckUnits) else HeadShape.arch(neckUnits)

private fun DrawScope.drawBoard(
    layout: BoardLayout,
    spec: HeadSpec,
    wood: Wood,
    activeIndex: Int,
    tunedIndices: Set<Int>,
    active: Color,
    tuned: Color,
) {
    val dp = layout.dp
    val w = layout.headWidth
    fun x(f: Float) = layout.headX(f)
    fun y(f: Float) = layout.headY(f)
    fun n(f: Float) = layout.neckX(f)
    val bottom = spec.headBottom
    // A little wider than the nut, so the outer strings stand inside the neck and not on its edge.
    val neckLeft = 0.5f - (spec.nutSpread / 2f + BoardLayout.NeckPad)
    val neckRight = 0.5f + (spec.nutSpread / 2f + BoardLayout.NeckPad)
    val nutY = layout.nutY

    val centreX = layout.width / 2f
    val crownHalf = (spec.crownRight - spec.crownLeft) / 2f * BoardLayout.XSpread * w
    val neckHalf = n(neckRight) - centreX
    val shape = headShapeFor(spec, neckHalf / crownHalf)
    val head = shape.path(centreX, crownHalf, 0f, nutY)
    val bounds = head.getBounds()

    // The neck under the nut: dark, with frets and one inlay, because a plain dark bar reads as
    // a gap in the picture and not as part of the instrument.
    val fretboard = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = n(neckLeft), top = nutY, right = n(neckRight), bottom = layout.height + 12f * dp,
                topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius(6f * dp), bottomRightCornerRadius = CornerRadius(6f * dp),
            ),
        )
    }
    drawPath(fretboard, Brush.verticalGradient(listOf(wood.fretboardTop, wood.fretboardBottom), startY = nutY, endY = layout.height))
    val neckSpan = layout.height - nutY
    for (fret in listOf(0.27f, 0.52f, 0.74f, 0.92f)) {
        val fy = nutY + neckSpan * fret
        drawLine(Color(0xFFC9CDD2).copy(alpha = 0.42f), Offset(n(neckLeft), fy), Offset(n(neckRight), fy), 1.6f * dp)
    }
    drawCircle(wood.binding.copy(alpha = 0.55f), 3.2f * dp, Offset((n(neckLeft) + n(neckRight)) / 2f, nutY + neckSpan * 0.395f))

    // The head: the wood, a faint grain, a soft sheen from the top left, and the binding.
    drawPath(head, Brush.verticalGradient(listOf(wood.top, wood.bottom), startY = bounds.top, endY = nutY))
    clipPath(head) {
        val random = kotlin.random.Random(11)
        val lines = 15
        for (i in 0 until lines) {
            val gx = bounds.left + bounds.width * (i + random.nextFloat() * 0.8f) / lines
            val drift = (random.nextFloat() - 0.5f) * 14f * dp
            val grain = Path().apply {
                moveTo(gx, bounds.top)
                cubicTo(gx + drift, bounds.top + bounds.height * 0.33f, gx - drift, bounds.top + bounds.height * 0.66f, gx + drift * 0.4f, nutY)
            }
            val dark = i % 3 != 0
            drawPath(
                grain,
                if (dark) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.07f),
                style = Stroke(width = (0.8f + random.nextFloat() * 1.6f) * dp),
            )
        }
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                start = Offset(bounds.left, bounds.top),
                end = Offset(bounds.center.x, bounds.top + bounds.height * 0.55f),
            ),
            topLeft = bounds.topLeft,
            size = bounds.size,
        )
    }
    drawPath(head, Color.Black.copy(alpha = 0.55f), style = Stroke(width = 4.6f * dp, join = StrokeJoin.Round))
    drawPath(head, wood.binding, style = Stroke(width = 2.4f * dp, join = StrokeJoin.Round))

    // A bowed instrument's scroll: carved wood curled over the top of the pegbox.
    if (spec.scroll) {
        val centre = Offset(x(0.5f), y(0.1f))
        val radius = w * 0.17f
        drawCircle(Brush.verticalGradient(listOf(wood.top, wood.bottom), startY = centre.y - radius, endY = centre.y + radius), radius, centre)
        drawCircle(Color.Black.copy(alpha = 0.55f), radius, centre, style = Stroke(4.6f * dp))
        drawCircle(wood.binding, radius, centre, style = Stroke(2.4f * dp))
        drawCircle(Color.Black.copy(alpha = 0.45f), radius * 0.45f, centre, style = Stroke(1.8f * dp))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius * 0.16f, centre, style = Stroke(1.6f * dp))
    } else {
        // The truss rod cover: a dark plate with a screw, the detail that says guitar.
        val plateW = w * 0.15f
        val plateTop = nutY * 0.50f
        val plateBottom = nutY * 0.90f
        val cx = (n(neckLeft) + n(neckRight)) / 2f
        val plate = Path().apply {
            moveTo(cx - plateW / 2f, plateTop + plateW / 2f)
            arcTo(
                androidx.compose.ui.geometry.Rect(cx - plateW / 2f, plateTop, cx + plateW / 2f, plateTop + plateW),
                180f, 180f, false,
            )
            lineTo(cx + plateW * 0.30f, plateBottom)
            lineTo(cx - plateW * 0.30f, plateBottom)
            close()
        }
        drawPath(plate, Color(0xFF241711))
        drawPath(plate, Color.White.copy(alpha = 0.10f), style = Stroke(1.2f * dp))
        drawCircle(Color(0xFFC9CDD2), 2.6f * dp, Offset(cx, plateTop + plateW * 0.5f))
        drawCircle(Color(0xFF6A6F76), 1.1f * dp, Offset(cx, plateTop + plateW * 0.5f))
    }

    // The nut: bone, the one bright line across the neck.
    drawLine(
        color = Color(0xFFF2E8D2),
        start = Offset(n(neckLeft), nutY),
        end = Offset(n(neckRight), nutY),
        strokeWidth = 4f * dp,
        cap = StrokeCap.Butt,
    )

    // Strings: straight down the neck from the nut, and from the nut out to their tuner. The
    // wound bass strings are bronze and thick, the plain ones nickel and thin.
    val count = layout.pegs.size
    for (i in 0 until count) {
        val peg = layout.pegs[i]
        val isActive = i == activeIndex
        val isTuned = i in tunedIndices
        val wound = i < (count + 1) / 2
        val base = if (wound) Color(0xFFD9B98A) else Color(0xFFDCE0E5)
        val colour = when {
            isActive -> active
            isTuned -> androidx.compose.ui.graphics.lerp(base, tuned, 0.55f)
            else -> base.copy(alpha = 0.82f)
        }
        val thickness = (2.5f - 1.4f * i / (count - 1).coerceAtLeast(1)) * dp
        val slot = layout.slots[i]
        if (layout.isDrone(i)) {
            drawLine(colour, peg, Offset(peg.x, layout.height), thickness, StrokeCap.Round)
        } else {
            // The part below the nut is drawn by the vibrating layer for the active string.
            if (!isActive) drawLine(colour, Offset(slot, layout.height), Offset(slot, nutY), thickness, StrokeCap.Round)
            drawLine(colour, Offset(slot, nutY), peg, thickness, StrokeCap.Round)
        }
    }

    // Tuners: a post on the head, a shaft out through the edge and a chrome knob beyond it. The
    // string being tuned lights its own, so "which peg" is answered by colour, not by counting.
    for (i in 0 until count) {
        val peg = layout.pegs[i]
        val knobCentre = layout.knobs[i]
        val isActive = i == activeIndex
        val isTuned = i in tunedIndices
        val halo = when {
            isActive -> active
            isTuned -> tuned
            else -> null
        }
        drawLine(Color(0xFFBFC4CA), peg, Offset(knobCentre.x, peg.y), 2.6f * dp, StrokeCap.Round)
        // The bushing the string winds round: a chrome ring with the post in the middle.
        drawCircle(Color.Black.copy(alpha = 0.5f), 7.6f * dp, peg)
        drawCircle(Color(0xFFD7DBE0), 6.4f * dp, peg)
        drawCircle(Color(0xFF9AA1A9), 3.4f * dp, peg)
        drawCircle(Color(0xFFE9ECEF), 2f * dp, peg)

        val knobTop = peg.y - 15f * dp
        val knobRect = androidx.compose.ui.geometry.Rect(knobCentre.x - 9f * dp, knobTop, knobCentre.x + 9f * dp, knobTop + 30f * dp)
        if (halo != null) {
            drawRoundRect(
                color = halo.copy(alpha = 0.95f),
                topLeft = Offset(knobRect.left - 2.6f * dp, knobRect.top - 2.6f * dp),
                size = Size(knobRect.width + 5.2f * dp, knobRect.height + 5.2f * dp),
                cornerRadius = CornerRadius(11.6f * dp),
            )
        }
        val chrome = if (wood.ebonyPegs) {
            listOf(Color(0xFF5B463B), Color(0xFF241812), Color(0xFF120B08))
        } else {
            listOf(Color(0xFFFFFFFF), Color(0xFFC7CCD2), Color(0xFF7F868F))
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(chrome, startX = knobRect.left, endX = knobRect.right),
            topLeft = knobRect.topLeft,
            size = knobRect.size,
            cornerRadius = CornerRadius(6f * dp),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = knobRect.topLeft,
            size = knobRect.size,
            cornerRadius = CornerRadius(6f * dp),
            style = Stroke(1f * dp),
        )
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
        val bottom = layout.height
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
        drawPath(path, color, style = Stroke(width = 1.9f * dp, cap = StrokeCap.Round))
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val signals = LocalTunerSignals.current

    val container by animateColorAsState(
        targetValue = when {
            isInTune -> signals.inTune
            isTuned -> signals.inTune.copy(alpha = 0.22f)
            isActive -> scheme.primaryContainer
            else -> scheme.surfaceContainerHigh
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "bubbleContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            isInTune -> signals.onSignal
            isTuned -> signals.inTune
            isActive -> scheme.onPrimaryContainer
            else -> scheme.onSurfaceVariant
        },
        animationSpec = TunerMotion.colourSpec(),
        label = "bubbleContent",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (isActive) 2.5.dp else 0.dp,
        animationSpec = TunerMotion.spatial(),
        label = "bubbleRing",
    )
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
            .clip(CircleShape)
            .background(container)
            .border(ringWidth, scheme.primary, CircleShape)
            .bouncyClickable(role = Role.RadioButton, onClickLabel = string.fullLabel, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = string.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
        AnimatedVisibility(
            visible = isTuned && !isInTune,
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
