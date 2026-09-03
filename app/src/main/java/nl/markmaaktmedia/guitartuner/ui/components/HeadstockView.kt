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
    HeadstockScale.SMALL -> 0.80f
    HeadstockScale.MEDIUM -> 0.92f
    HeadstockScale.LARGE -> 1f
}

private fun HeadstockScale.pegFactor(): Float = when (this) {
    HeadstockScale.SMALL -> 0.72f
    HeadstockScale.MEDIUM -> 1f
    HeadstockScale.LARGE -> 1.42f
}

/**
 * A head, as the few numbers it takes to draw one.
 *
 * Widths are fractions of the available width, heights fractions of the available height
 * measured from the top. The pegs carry their own coordinates because the interesting
 * layouts (a banjo's fifth peg, a Fender's six in a line) are exactly the ones that do
 * not follow from the outline.
 */
private data class HeadSpec(
    val topWidth: Float,
    val waistWidth: Float,
    val neckWidth: Float,
    val headBottom: Float,
    val topRadius: Float,
    /** Peg positions as (x, y) fractions. */
    val pegs: List<Offset>,
    val scroll: Boolean = false,
    /** Where each string leaves the nut, as an x fraction. Defaults to an even spread. */
    val nutSpread: Float = 0.5f,
)

private fun specFor(layout: HeadstockLayout, stringCount: Int): HeadSpec = when (layout) {
    HeadstockLayout.THREE_PER_SIDE -> {
        val perSide = stringCount / 2
        HeadSpec(
            topWidth = 0.80f,
            waistWidth = 0.62f,
            neckWidth = 0.44f,
            headBottom = 0.74f,
            topRadius = 0.16f,
            pegs = sidePegs(perSide, perSide, 0.11f, 0.89f, 0.18f, 0.64f),
            nutSpread = 0.30f,
        )
    }

    HeadstockLayout.FOUR_THREE -> HeadSpec(
        topWidth = 0.84f,
        waistWidth = 0.64f,
        neckWidth = 0.46f,
        headBottom = 0.78f,
        topRadius = 0.14f,
        pegs = sidePegs(stringCount - stringCount / 2, stringCount / 2, 0.10f, 0.90f, 0.15f, 0.68f),
        nutSpread = 0.32f,
    )

    HeadstockLayout.INLINE -> HeadSpec(
        // The Fender paddle: one straight edge with every tuner on it, the other curved.
        topWidth = 0.66f,
        waistWidth = 0.58f,
        neckWidth = 0.42f,
        headBottom = 0.80f,
        topRadius = 0.22f,
        pegs = List(stringCount) { index ->
            val t = if (stringCount == 1) 0.5f else index / (stringCount - 1f)
            Offset(0.22f, 0.13f + t * 0.55f)
        },
        nutSpread = 0.26f,
    )

    HeadstockLayout.TWO_PER_SIDE -> HeadSpec(
        topWidth = 0.74f,
        waistWidth = 0.60f,
        neckWidth = 0.46f,
        headBottom = 0.68f,
        topRadius = 0.20f,
        pegs = sidePegs(stringCount - stringCount / 2, stringCount / 2, 0.13f, 0.87f, 0.20f, 0.54f),
        nutSpread = 0.28f,
    )

    HeadstockLayout.PAIRED_FOUR -> HeadSpec(
        // A mandolin is eight strings in four courses, so the pegs sit in tight pairs.
        topWidth = 0.86f,
        waistWidth = 0.66f,
        neckWidth = 0.44f,
        headBottom = 0.76f,
        topRadius = 0.12f,
        pegs = buildList {
            repeat(stringCount) { index ->
                val t = index / (stringCount - 1f).coerceAtLeast(1f)
                add(Offset(0.12f, 0.18f + t * 0.52f))
                add(Offset(0.88f, 0.18f + t * 0.52f))
            }
        },
        nutSpread = 0.30f,
    )

    HeadstockLayout.BANJO -> HeadSpec(
        topWidth = 0.72f,
        waistWidth = 0.56f,
        neckWidth = 0.44f,
        headBottom = 0.62f,
        topRadius = 0.18f,
        // Four on the head, and the drone tuned from a peg partway down the neck.
        pegs = sidePegs(2, 2, 0.14f, 0.86f, 0.18f, 0.46f) + Offset(0.82f, 0.86f),
        nutSpread = 0.26f,
    )

    HeadstockLayout.SCROLL -> HeadSpec(
        topWidth = 0.52f,
        waistWidth = 0.44f,
        neckWidth = 0.34f,
        headBottom = 0.86f,
        topRadius = 0.10f,
        pegs = listOf(
            Offset(0.10f, 0.42f), Offset(0.90f, 0.52f),
            Offset(0.10f, 0.62f), Offset(0.90f, 0.72f),
        ).take(stringCount),
        scroll = true,
        nutSpread = 0.20f,
    )
}

private fun sidePegs(
    left: Int,
    right: Int,
    leftX: Float,
    rightX: Float,
    top: Float,
    bottom: Float,
): List<Offset> = buildList {
    fun column(count: Int, x: Float) {
        repeat(count) { index ->
            val t = if (count == 1) 0.5f else index / (count - 1f)
            add(Offset(x, top + t * (bottom - top)))
        }
    }
    column(left, leftX)
    column(right, rightX)
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
    fun x(fraction: Float) = insetX + fraction * w
    fun y(fraction: Float) = insetY + fraction * h

    val halfTop = spec.topWidth / 2f
    val halfWaist = spec.waistWidth / 2f
    val halfNeck = spec.neckWidth / 2f
    val top = spec.topRadius

    val body = Path().apply {
        moveTo(x(0.5f - halfTop + spec.topRadius * 0.5f), y(top * 0.35f))
        // Crown.
        cubicTo(
            x(0.5f - halfTop), y(0f),
            x(0.5f + halfTop), y(0f),
            x(0.5f + halfTop - spec.topRadius * 0.5f), y(top * 0.35f),
        )
        // Right side, in through the waist and out to the neck.
        cubicTo(
            x(0.5f + halfTop), y(spec.headBottom * 0.45f),
            x(0.5f + halfWaist), y(spec.headBottom * 0.78f),
            x(0.5f + halfNeck), y(spec.headBottom),
        )
        lineTo(x(0.5f + halfNeck), y(1f))
        lineTo(x(0.5f - halfNeck), y(1f))
        lineTo(x(0.5f - halfNeck), y(spec.headBottom))
        cubicTo(
            x(0.5f - halfWaist), y(spec.headBottom * 0.78f),
            x(0.5f - halfTop), y(spec.headBottom * 0.45f),
            x(0.5f - halfTop + spec.topRadius * 0.5f), y(top * 0.35f),
        )
        close()
    }

    drawPath(body, bodyColor)
    drawPath(body, outlineColor, style = Stroke(width = w * 0.035f))

    // The scroll. A bowed instrument's head is a carved spiral and nothing else in the
    // list looks remotely like it, so it is worth the extra arc.
    if (spec.scroll) {
        val centre = Offset(x(0.5f), y(0.16f))
        val radius = w * 0.19f
        drawCircle(bodyColor, radius = radius, center = centre)
        drawCircle(outlineColor, radius = radius, center = centre, style = Stroke(w * 0.035f))
        drawCircle(outlineColor, radius = radius * 0.42f, center = centre, style = Stroke(w * 0.03f))
    }

    // Strings running from the nut up to their peg, so the count is visible as strings
    // and not only as tuners.
    val nutY = 0.99f
    val pegs = spec.pegs
    pegs.forEachIndexed { index, peg ->
        val t = if (pegs.size == 1) 0.5f else index.toFloat() / (pegs.size - 1)
        val nutX = 0.5f + (t - 0.5f) * spec.nutSpread
        drawLine(
            color = stringColor,
            start = Offset(x(nutX), y(nutY)),
            end = Offset(x(peg.x), y(peg.y)),
            strokeWidth = w * 0.016f,
            cap = StrokeCap.Round,
        )
    }

    pegs.forEach { peg ->
        drawCircle(
            color = pegColor,
            radius = w * 0.055f * scale.pegFactor(),
            center = Offset(x(peg.x), y(peg.y)),
        )
    }
}

