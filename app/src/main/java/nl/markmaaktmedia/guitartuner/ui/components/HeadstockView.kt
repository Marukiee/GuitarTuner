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
 * [pegs] is ordered **by nut slot**, not by position on the head, and that ordering is
 * what makes the strings drawable. String one leaves the leftmost slot at the nut and
 * climbs to `pegs[0]`, string two to `pegs[1]`, and so on. On anything with tuners down
 * both sides that means one column is listed bottom to top, because the peg nearest the
 * nut on the treble side holds the innermost string, not the outermost. List both columns
 * top to bottom and every string on the right hand side crosses every other one.
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
     * A tuner partway down the neck whose string never reaches the nut. A banjo's drone
     * is the only one, and running its string to the nut like the others is precisely
     * what makes a banjo stop looking like a banjo.
     */
    val dronePeg: Offset? = null,
)

private fun specFor(layout: HeadstockLayout, stringCount: Int): HeadSpec = when (layout) {
    HeadstockLayout.THREE_PER_SIDE -> {
        // A slotted head: wide, gently flared, symmetric.
        val perSide = stringCount / 2
        HeadSpec(
            crownLeft = 0.14f, crownRight = 0.86f,
            waistLeft = 0.24f, waistRight = 0.76f,
            neckLeft = 0.33f, neckRight = 0.67f,
            headBottom = 0.70f, crownY = 0.10f,
            pegs = sidePegs(perSide, perSide, 0.21f, 0.79f, 0.18f, 0.56f),
            nutSpread = 0.40f,
        )
    }

    HeadstockLayout.FOUR_THREE -> HeadSpec(
        crownLeft = 0.12f, crownRight = 0.88f,
        waistLeft = 0.22f, waistRight = 0.78f,
        neckLeft = 0.32f, neckRight = 0.68f,
        headBottom = 0.74f, crownY = 0.08f,
        pegs = sidePegs(stringCount - stringCount / 2, stringCount / 2, 0.19f, 0.81f, 0.15f, 0.60f),
        nutSpread = 0.42f,
    )

    HeadstockLayout.INLINE -> HeadSpec(
        // The Fender paddle. Every tuner on one edge, and the outline asymmetric to match.
        crownLeft = 0.18f, crownRight = 0.66f,
        waistLeft = 0.24f, waistRight = 0.72f,
        neckLeft = 0.32f, neckRight = 0.66f,
        headBottom = 0.80f, crownY = 0.06f,
        pegs = List(stringCount) { index ->
            val t = if (stringCount == 1) 0.5f else index / (stringCount - 1f)
            Offset(0.30f, 0.12f + t * 0.56f)
        },
        nutSpread = 0.30f,
    )

    HeadstockLayout.TWO_PER_SIDE -> HeadSpec(
        // Short and square: nothing like the long flare of a six string head.
        crownLeft = 0.16f, crownRight = 0.84f,
        waistLeft = 0.24f, waistRight = 0.76f,
        neckLeft = 0.33f, neckRight = 0.67f,
        headBottom = 0.54f, crownY = 0.15f,
        pegs = sidePegs(stringCount - stringCount / 2, stringCount / 2, 0.22f, 0.78f, 0.16f, 0.38f),
        nutSpread = 0.36f,
    )

    HeadstockLayout.PAIRED_FOUR -> {
        // A mandolin is eight strings in four courses: four tuners a side in two tight
        // pairs each, and eight slots at the nut that pair up the same way.
        val perSide = stringCount / 2
        HeadSpec(
            crownLeft = 0.14f, crownRight = 0.86f,
            waistLeft = 0.22f, waistRight = 0.78f,
            neckLeft = 0.32f, neckRight = 0.68f,
            headBottom = 0.70f, crownY = 0.06f,
            pegs = pairedColumn(perSide, 0.21f, 0.16f, 0.56f, downwards = true) +
                pairedColumn(perSide, 0.79f, 0.16f, 0.56f, downwards = false),
            nutSpread = 0.40f,
            paired = true,
        )
    }

    HeadstockLayout.BANJO -> HeadSpec(
        crownLeft = 0.19f, crownRight = 0.81f,
        waistLeft = 0.26f, waistRight = 0.74f,
        neckLeft = 0.34f, neckRight = 0.66f,
        headBottom = 0.50f, crownY = 0.13f,
        pegs = sidePegs(2, 2, 0.24f, 0.76f, 0.15f, 0.35f),
        nutSpread = 0.34f,
        dronePeg = Offset(0.69f, 0.78f),
    )

    HeadstockLayout.SCROLL -> HeadSpec(
        // A pegbox: narrow, straight sided, the scroll curled over the top and the pegs
        // pushed through the sides so their buttons stand proud of it.
        crownLeft = 0.34f, crownRight = 0.66f,
        waistLeft = 0.34f, waistRight = 0.66f,
        neckLeft = 0.37f, neckRight = 0.63f,
        headBottom = 0.80f, crownY = 0.28f,
        pegs = listOf(
            Offset(0.27f, 0.44f), Offset(0.27f, 0.66f),
            Offset(0.73f, 0.66f), Offset(0.73f, 0.44f),
        ).take(stringCount),
        nutSpread = 0.18f,
        scroll = true,
    )
}

/**
 * Two columns of tuners, listed in nut slot order: the bass side top to bottom, then the
 * treble side bottom to top. See [HeadSpec.pegs] for why the second one is reversed.
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
    return column(left, leftX, downwards = true) + column(right, rightX, downwards = false)
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
        drawCircle(pegColor, radius = pegRadius, center = Offset(x(peg.x), y(peg.y)))
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
