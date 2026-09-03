package nl.markmaaktmedia.guitartuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.guitartuner.domain.model.HeadstockLayout
import nl.markmaaktmedia.guitartuner.domain.model.HeadstockScale

/**
 * The silhouette of an instrument's head, drawn from its peg layout.
 *
 * This exists because a picker that lists "Acoustic guitar" and "Electric guitar" over
 * two identical drawings has told the player nothing. The tunings of those two are the
 * same; what differs is a slotted three a side head against six tuners in a line, and
 * that difference is instantly readable as a shape and not at all readable as a word.
 *
 * Everything is drawn from a normalised description rather than from a per instrument
 * vector, so a new instrument is a new entry in [HeadstockLayout] and nothing else.
 */
@Composable
fun HeadstockView(
    layout: HeadstockLayout,
    stringCount: Int,
    scale: HeadstockScale,
    modifier: Modifier = Modifier,
    bodyColor: Color,
    outlineColor: Color,
    pegColor: Color,
    stringColor: Color,
) {
    Canvas(modifier) {
        val spec = specFor(layout, stringCount)
        drawHead(spec, scale, bodyColor, outlineColor, pegColor, stringColor)
    }
}

/**
 * How big the head is drawn, and how big its tuners are relative to it.
 *
 * The second half matters more than the first. A four string bass and a ukulele both have
 * two pegs a side and four strings, and at the same peg size they draw the same picture.
 * They do not look the same in the hand at all: a bass has enormous tuners on a big head
 * and a ukulele has small ones on a short head, and reproducing that ratio is what makes
 * the two entries in the picker tell them apart.
 */
private fun HeadstockScale.sizeFactor(): Float = when (this) {
    HeadstockScale.SMALL -> 0.82f
    HeadstockScale.MEDIUM -> 0.92f
    HeadstockScale.LARGE -> 1f
}

private fun HeadstockScale.pegFactor(): Float = when (this) {
    HeadstockScale.SMALL -> 0.70f
    HeadstockScale.MEDIUM -> 1f
    HeadstockScale.LARGE -> 1.45f
}

/**
 * A head, as the few numbers it takes to draw one.
 *
 * The outline is described by an x fraction per side at three heights rather than by a
 * single width, because the two most recognisable heads on the list are the ones that are
 * not symmetric. A Fender has every tuner along one straight edge; drawn symmetrically it
 * is an acoustic with the pegs moved over, which is exactly what the first version drew.
 *
 * [pegs] is ordered **by nut slot**, not by position on the head. String one leaves the
 * leftmost slot at the nut and climbs to `pegs[0]`, string two to `pegs[1]`, and so on.
 *
 * Which peg that is follows one rule, and it is the rule the whole drawing rests on:
 *
 *   **the outermost string at the nut takes the peg nearest the nut.**
 *
 * Outer strings turn off almost immediately, which leaves the middle of the head clear for
 * the inner strings to run straight up to the far pegs between them. Do it the other way
 * round, sending the outer string to the far peg, and the inner strings have to cut back
 * out across it to reach the near pegs: every head then draws a cobweb. This is not a
 * stylistic choice, it is the only assignment with no crossings, and it holds for a column
 * of two, of three, or for six in a line down one edge.
 */
private data class HeadSpec(
    val crownLeft: Float,
    val crownRight: Float,
    val waistLeft: Float,
    val waistRight: Float,
    val neckLeft: Float,
    val neckRight: Float,
    /** Where the head ends and the neck begins, as a fraction of the height. */
    val headBottom: Float,
    /** How far down the crown's curve reaches before the sides take over. */
    val crownY: Float,
    /** Peg positions as (x, y) fractions, in nut slot order. */
    val pegs: List<Offset>,
    /** How wide the strings sit at the nut, as a fraction of the width. */
    val nutSpread: Float,
    /** Strings sit in tight pairs at the nut, the way a mandolin's courses do. */
    val paired: Boolean = false,
    val scroll: Boolean = false,
    /**
     * How far outside the string line the peg *buttons* are drawn, as a width fraction.
     *
     * A bowed instrument is the one head where where the string ends and where the tuner is
     * seen are not the same place: the pegs pass through the sides of the pegbox, so the
     * strings terminate inside it and the buttons stand proud of it. Drawing both at the
     * button leaves the strings crossing outside the box; drawing both at the string leaves a
     * pegbox with four dots painted on it and no violin in sight.
     */
    val pegProud: Float = 0f,
    /**
     * A tuner partway down the neck whose string never reaches the nut. A banjo's drone
     * is the only one, and running its string to the nut like the others is precisely
     * what makes a banjo stop looking like a banjo.
     */
    val dronePeg: Offset? = null,
)

/**
 * Where a peg column sits, given how wide the strings are at the nut.
 *
 * Lined up with the outermost nut slot, so the outer string leaves the nut straight up and
 * turns off to its peg over the shortest run on the head. It is also what a real head looks
 * like: the posts sit a couple of millimetres outside a nut nearly as wide as they are, not
 * out at the edges of the paddle.
 */
private fun pegEdge(nutSpread: Float): Float = 0.5f + nutSpread / 2f

/** The Fender post line, out at the bass edge rather than beside the nut. */
private const val INLINE_POST_X = 0.29f

private fun specFor(layout: HeadstockLayout, stringCount: Int): HeadSpec = when (layout) {
    HeadstockLayout.THREE_PER_SIDE -> {
        // A slotted head: wide, gently flared, symmetric.
        val perSide = stringCount / 2
        val nut = 0.34f
        HeadSpec(
            crownLeft = 0.24f, crownRight = 0.76f,
            waistLeft = 0.28f, waistRight = 0.72f,
            neckLeft = 0.33f, neckRight = 0.67f,
            headBottom = 0.70f, crownY = 0.10f,
            pegs = sidePegs(perSide, perSide, 1f - pegEdge(nut), pegEdge(nut), 0.18f, 0.56f),
            nutSpread = nut,
        )
    }

    HeadstockLayout.FOUR_THREE -> {
        val nut = 0.34f
        HeadSpec(
            crownLeft = 0.22f, crownRight = 0.78f,
            waistLeft = 0.27f, waistRight = 0.73f,
            neckLeft = 0.32f, neckRight = 0.68f,
            headBottom = 0.74f, crownY = 0.08f,
            pegs = sidePegs(
                stringCount - stringCount / 2, stringCount / 2,
                1f - pegEdge(nut), pegEdge(nut), 0.15f, 0.60f,
            ),
            nutSpread = nut,
        )
    }

    HeadstockLayout.INLINE -> {
        // The Fender paddle. Every tuner on one edge, and the outline asymmetric to match:
        // the paddle bulges past the posts on the bass side and curves away on the treble
        // side, which is the whole silhouette.
        val nut = 0.30f
        HeadSpec(
            crownLeft = 0.22f, crownRight = 0.68f,
            waistLeft = 0.28f, waistRight = 0.71f,
            neckLeft = 0.33f, neckRight = 0.67f,
            headBottom = 0.80f, crownY = 0.06f,
            // One straight row of posts hard against the bass edge, which is the whole
            // silhouette, so this is the one layout that does not take its x from [pegEdge].
            // The reversed index is the rule again: the outermost string at the nut is the
            // one nearest this edge, so it takes the post nearest the nut, and the rest run
            // up between the posts to the top of the paddle.
            pegs = List(stringCount) { index ->
                val step = if (stringCount == 1) 0.5f else (stringCount - 1 - index) / (stringCount - 1f)
                Offset(INLINE_POST_X, 0.12f + step * 0.56f)
            },
            nutSpread = nut,
        )
    }

    HeadstockLayout.TWO_PER_SIDE -> {
        // Short and square: nothing like the long flare of a six string head.
        val nut = 0.34f
        HeadSpec(
            crownLeft = 0.24f, crownRight = 0.76f,
            waistLeft = 0.28f, waistRight = 0.72f,
            neckLeft = 0.33f, neckRight = 0.67f,
            headBottom = 0.54f, crownY = 0.15f,
            pegs = sidePegs(
                stringCount - stringCount / 2, stringCount / 2,
                1f - pegEdge(nut), pegEdge(nut), 0.16f, 0.38f,
            ),
            nutSpread = nut,
        )
    }

    HeadstockLayout.PAIRED_FOUR -> {
        // A mandolin is eight strings in four courses: four tuners a side in two tight
        // pairs each, and eight slots at the nut that pair up the same way.
        val perSide = stringCount / 2
        val nut = 0.36f
        HeadSpec(
            crownLeft = 0.23f, crownRight = 0.77f,
            waistLeft = 0.27f, waistRight = 0.73f,
            neckLeft = 0.32f, neckRight = 0.68f,
            headBottom = 0.70f, crownY = 0.06f,
            pegs = pairedColumn(perSide, 1f - pegEdge(nut), 0.16f, 0.56f, downwards = false) +
                pairedColumn(perSide, pegEdge(nut), 0.16f, 0.56f, downwards = true),
            nutSpread = nut,
            paired = true,
        )
    }

    HeadstockLayout.BANJO -> {
        val nut = 0.32f
        HeadSpec(
            crownLeft = 0.22f, crownRight = 0.78f,
            waistLeft = 0.27f, waistRight = 0.73f,
            neckLeft = 0.34f, neckRight = 0.66f,
            headBottom = 0.50f, crownY = 0.13f,
            pegs = sidePegs(2, 2, 1f - pegEdge(nut), pegEdge(nut), 0.15f, 0.35f),
            nutSpread = nut,
            // The drone tuner sits partway down the neck, off to the treble side, and its
            // string never reaches the nut, so it is outside the no-crossing rule entirely.
            dronePeg = Offset(0.64f, 0.78f),
        )
    }

    HeadstockLayout.SCROLL -> {
        // A pegbox: narrow, straight sided, the scroll curled over the top and the pegs
        // pushed through the sides so their buttons stand proud of it.
        val nut = 0.24f
        HeadSpec(
            crownLeft = 0.34f, crownRight = 0.66f,
            waistLeft = 0.34f, waistRight = 0.66f,
            neckLeft = 0.37f, neckRight = 0.63f,
            headBottom = 0.80f, crownY = 0.28f,
            pegs = listOf(
                Offset(1f - pegEdge(nut), 0.66f), Offset(1f - pegEdge(nut), 0.44f),
                Offset(pegEdge(nut), 0.44f), Offset(pegEdge(nut), 0.66f),
            ).take(stringCount),
            nutSpread = nut,
            scroll = true,
            pegProud = 0.10f,
        )
    }
}

/**
 * Two columns of tuners, listed in nut slot order.
 *
 * The bass side runs bottom to top and the treble side top to bottom, which sounds backwards
 * until you follow one string: the leftmost slot is the outermost string on the bass side, and
 * by the rule in [HeadSpec.pegs] it takes the *lowest* peg, so the bass column has to be listed
 * starting at the bottom. The treble side is the mirror of that.
 */
private fun sidePegs(
    left: Int,
    right: Int,
    leftX: Float,
    rightX: Float,
    top: Float,
    bottom: Float,
): List<Offset> {
    fun column(count: Int, x: Float, downwards: Boolean) = List(count) { index ->
        val step = if (downwards) index else count - 1 - index
        val t = if (count == 1) 0.5f else step / (count - 1f)
        Offset(x, top + t * (bottom - top))
    }
    return column(left, leftX, downwards = false) + column(right, rightX, downwards = true)
}

/** One column of tuners grouped into pairs, for a course strung instrument. */
private fun pairedColumn(
    count: Int,
    x: Float,
    top: Float,
    bottom: Float,
    downwards: Boolean,
): List<Offset> {
    val pairs = (count / 2).coerceAtLeast(1)
    val gap = (bottom - top) * 0.18f
    val positions = List(count) { index ->
        val t = if (pairs == 1) 0.5f else (index / 2) / (pairs - 1f)
        top + t * (bottom - top - gap) + (index % 2) * gap
    }
    return List(count) { index ->
        Offset(x, positions[if (downwards) index else count - 1 - index])
    }
}

private fun DrawScope.drawHead(
    spec: HeadSpec,
    scale: HeadstockScale,
    bodyColor: Color,
    outlineColor: Color,
    pegColor: Color,
    stringColor: Color,
) {
    val factor = scale.sizeFactor()
    val w = size.width * factor
    val h = size.height * factor
    val insetX = (size.width - w) / 2f
    val insetY = size.height - h
    // Every fraction in a [HeadSpec] is written left to right off a photograph, which is the
    // front of the instrument: bass side on the left, the way a chord chart is drawn. The head
    // is then mirrored on the way to the canvas, because this drawing is not a chord chart. It
    // is shown to someone holding the instrument, and from behind the neck the bass side is on
    // the right, so an unmirrored head sends the low string off the wrong edge and hangs the
    // treble strings on the tuners the player can see are not theirs.
    fun x(fraction: Float) = insetX + (1f - fraction) * w
    fun y(fraction: Float) = insetY + fraction * h

    val bottom = spec.headBottom
    val body = Path().apply {
        moveTo(x(spec.crownLeft), y(spec.crownY))
        cubicTo(
            x(spec.crownLeft), y(0f),
            x(spec.crownRight), y(0f),
            x(spec.crownRight), y(spec.crownY),
        )
        cubicTo(
            x(spec.crownRight), y(bottom * 0.55f),
            x(spec.waistRight), y(bottom * 0.86f),
            x(spec.neckRight), y(bottom),
        )
        lineTo(x(spec.neckRight), y(1f))
        lineTo(x(spec.neckLeft), y(1f))
        lineTo(x(spec.neckLeft), y(bottom))
        cubicTo(
            x(spec.waistLeft), y(bottom * 0.86f),
            x(spec.crownLeft), y(bottom * 0.55f),
            x(spec.crownLeft), y(spec.crownY),
        )
        close()
    }

    val outline = w * 0.035f
    drawPath(body, bodyColor)
    drawPath(body, outlineColor, style = Stroke(width = outline))

    // The scroll. A bowed instrument's head is a carved spiral and nothing else on the
    // list looks remotely like it, so it is worth the extra circles.
    if (spec.scroll) {
        val centre = Offset(x(0.5f), y(0.12f))
        val radius = w * 0.20f
        drawCircle(bodyColor, radius = radius, center = centre)
        drawCircle(outlineColor, radius = radius, center = centre, style = Stroke(outline))
        drawCircle(outlineColor, radius = radius * 0.40f, center = centre, style = Stroke(outline * 0.8f))
    }

    // Strings run straight up the neck to the nut and only then fan out to the tuners.
    // Fanning the whole way, which is what the first version did, draws a bundle of
    // lines converging under the nut and reads as a cobweb rather than as an instrument.
    //
    // The fan itself is kept shallow. On a real head the nut is nearly as wide as the
    // spacing of the tuners, so the strings barely splay; drawing the nut narrow and the
    // tuners far apart turns six near parallel lines into a wine glass, which is what
    // this looked like before the nut was widened and the tuners brought in.
    val nutY = bottom + (1f - bottom) * 0.22f
    val stringWidth = (w * 0.028f).coerceAtLeast(1.0.dp.toPx())
    val pegs = spec.pegs
    pegs.forEachIndexed { index, peg ->
        val slot = x(nutSlot(index, pegs.size, spec))
        drawLine(stringColor, Offset(slot, y(1f)), Offset(slot, y(nutY)), stringWidth, StrokeCap.Round)
        drawLine(stringColor, Offset(slot, y(nutY)), Offset(x(peg.x), y(peg.y)), stringWidth, StrokeCap.Round)
    }

    drawLine(
        color = outlineColor,
        start = Offset(x(spec.neckLeft), y(nutY)),
        end = Offset(x(spec.neckRight), y(nutY)),
        strokeWidth = outline * 0.9f,
        cap = StrokeCap.Round,
    )

    // The drone runs down the neck from its own peg and never crosses the nut.
    spec.dronePeg?.let { drone ->
        drawLine(
            color = stringColor,
            start = Offset(x(drone.x), y(drone.y)),
            end = Offset(x(drone.x), y(1f)),
            strokeWidth = stringWidth,
            cap = StrokeCap.Round,
        )
    }

    val pegRadius = w * 0.052f * scale.pegFactor()
    (pegs + listOfNotNull(spec.dronePeg)).forEach { peg ->
        // Pushed away from the centre line, never toward it, so both sides stand out rather
        // than one side standing out and the other burying itself in the wood.
        val proud = if (peg.x < 0.5f) -spec.pegProud else spec.pegProud
        // The shaft. Without it the buttons read as four dots painted beside the pegbox
        // rather than as pegs driven through it, which is the one detail that says violin.
        if (spec.pegProud > 0f) {
            drawLine(
                color = pegColor,
                start = Offset(x(peg.x), y(peg.y)),
                end = Offset(x(peg.x + proud), y(peg.y)),
                strokeWidth = pegRadius * 0.55f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(pegColor, radius = pegRadius, center = Offset(x(peg.x + proud), y(peg.y)))
    }
}

/** Where string [index] of [count] crosses the nut, as an x fraction. */
private fun nutSlot(index: Int, count: Int, spec: HeadSpec): Float {
    if (count <= 1) return 0.5f
    val half = spec.nutSpread / 2f
    if (!spec.paired) return 0.5f - half + spec.nutSpread * (index / (count - 1f))

    // Courses: the gap inside a pair is about a third of the gap between them.
    val courses = count / 2
    val within = (index % 2) * 0.34f
    val t = (index / 2 + within) / (courses - 1 + 0.34f)
    return 0.5f - half + spec.nutSpread * t
}
