package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import nl.markmaaktmedia.guitartuner.ui.components.pickPath
import kotlin.math.max
import kotlin.math.min

/** The colours of the head, all taken from the scheme so the guitar follows the wallpaper. */
internal data class HeadColors(
    val head: Color,
    val string: Color,
    val neck: Color,
    val fret: Color,
    val nut: Color,
    val knob: Color,
    val mark: Color,
    val post: Color,
    val postDot: Color,
)

internal fun ColorScheme.headColors() = HeadColors(
    head = primaryContainer,
    string = onPrimaryContainer.copy(alpha = 0.42f),
    neck = surfaceContainerHighest,
    fret = outlineVariant,
    nut = onPrimaryContainer,
    knob = primary,
    mark = tertiary,
    post = surface,
    postDot = primary,
)

/** How far above the bottom of the board the neck stops, in design dp. */
internal const val NeckInsetDd = 6f

/** The measurements the parts of the drawing share. */
private class Frame(g: HeadGeometry) {
    val u = g.unit
    val centre = g.centre
    val half = g.crownHalf
    val neckHalf = g.neckHalf
    val nutY = g.nutY

    /** Where the sides end and the sweep into the neck begins, and how wide the head is there. */
    private val sweepFrom = nutY * 0.79f
    private val low = half * 0.95f

    /** The x of the head's edge at height [y], on [side] (-1 left, 1 right). */
    fun edge(y: Float, side: Float): Float {
        if (y >= nutY) return centre + side * neckHalf
        if (y <= sweepFrom) return centre + side * (half + (low - half) * (y / sweepFrom).coerceIn(0f, 1f))
        val t = (y - sweepFrom) / (nutY - sweepFrom)
        return centre + side * (low + (neckHalf - low) * t * t * (3f - 2f * t))
    }

    /** Rounded shoulders with a dip in the middle of the crown, then one sweep into the neck. */
    fun outline(): Path {
        val top = 4f * u
        val r = 46f * u
        val sweep = nutY - sweepFrom
        return Path().apply {
            moveTo(centre - half, top + r)
            cubicTo(centre - half, top + r * 0.35f, centre - half + r * 0.35f, top, centre - half + r, top)
            cubicTo(centre - half * 0.35f, top, centre - half * 0.25f, top + 9f * u, centre, top + 9f * u)
            cubicTo(centre + half * 0.25f, top + 9f * u, centre + half * 0.35f, top, centre + half - r, top)
            cubicTo(centre + half - r * 0.35f, top, centre + half, top + r * 0.35f, centre + half, top + r)
            lineTo(centre + low, sweepFrom)
            cubicTo(
                centre + low, sweepFrom + sweep * 0.55f,
                centre + neckHalf, sweepFrom + sweep * 0.45f,
                centre + neckHalf, nutY,
            )
            lineTo(centre - neckHalf, nutY)
            cubicTo(
                centre - neckHalf, sweepFrom + sweep * 0.45f,
                centre - low, sweepFrom + sweep * 0.55f,
                centre - low, sweepFrom,
            )
            close()
        }
    }
}

/**
 * Draws a head as flat tonal shapes: the neck, a pill for every tuner, the head itself, the nut,
 * the strings and their posts. No texture and no shadow, only the scheme's colours.
 *
 * The string being tuned, and the ones already done, colour their own tuner, so "which peg do I
 * turn" is answered by colour and not by counting.
 */
internal fun DrawScope.drawHead(
    g: HeadGeometry,
    colors: HeadColors,
    activeIndex: Int,
    tunedIndices: Set<Int>,
    active: Color,
    tuned: Color,
) {
    val f = Frame(g)
    val u = f.u
    val bottom = size.height - NeckInsetDd * u

    fun stateColor(index: Int, idle: Color): Color = when {
        index == activeIndex -> active
        index in tunedIndices -> tuned
        else -> idle
    }

    drawNeck(f, colors, bottom)

    // The tuners go under the head, so each stem grows out of its side.
    g.pegs.forEachIndexed { i, peg ->
        val side = if (peg.x < f.centre) -1f else 1f
        val edge = f.edge(peg.y, side)
        val color = stateColor(i, colors.knob)
        val high = min(g.knobHeight, 36f * u)
        val wide = 24f * u
        val stemEnd = edge + side * 12f * u
        drawLine(color, Offset(edge - side * 4f * u, peg.y), Offset(stemEnd, peg.y), 9f * u)
        val left = if (side < 0f) stemEnd - wide else stemEnd
        drawRoundRect(color, Offset(left, peg.y - high / 2f), Size(wide, high), CornerRadius(wide / 2f))
    }

    drawBody(g, f, colors)

    val nutHigh = 9f * u
    drawRoundRect(
        colors.nut,
        Offset(f.centre - f.neckHalf - 3f * u, f.nutY - nutHigh / 2f),
        Size((f.neckHalf + 3f * u) * 2f, nutHigh),
        CornerRadius(nutHigh / 2f),
    )

    // Strings: each from its post straight to its place at the nut, then down the neck.
    g.pegs.forEachIndexed { i, peg ->
        val color = stateColor(i, colors.string)
        val weight = (4.2f - 2.4f * i / max(1, g.count - 1)) * u
        val slot = g.slots[i]
        val path = Path().apply {
            moveTo(peg.x, peg.y)
            if (!g.isDrone(i)) lineTo(slot, f.nutY)
            // Below the nut the string being tuned is drawn by the vibrating layer.
            if (i != activeIndex) lineTo(slot, bottom - 8f * u)
        }
        drawPath(path, color, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // Posts on top of the strings.
    g.pegs.forEachIndexed { i, peg ->
        drawCircle(colors.post, 12f * u, peg)
        drawCircle(stateColor(i, colors.postDot), 6f * u, peg)
    }
}

private fun DrawScope.drawNeck(f: Frame, colors: HeadColors, bottom: Float) {
    val u = f.u
    val left = f.centre - f.neckHalf
    val width = f.neckHalf * 2f
    val top = f.nutY
    drawPath(
        Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top - 4f * u, left + width, bottom),
                    bottomLeft = CornerRadius(28f * u),
                    bottomRight = CornerRadius(28f * u),
                ),
            )
        },
        colors.neck,
    )
    for (share in floatArrayOf(0.42f, 0.74f)) {
        val y = top + (bottom - top) * share
        drawLine(colors.fret, Offset(left + 10f * u, y), Offset(left + width - 10f * u, y), 4f * u, StrokeCap.Round)
    }
}

private fun DrawScope.drawBody(g: HeadGeometry, f: Frame, colors: HeadColors) {
    val u = f.u
    if (g.spec.scroll) {
        // A violin or cello: a rounded pegbox with the scroll as a ring on top.
        val radius = f.half * 0.86f
        val centre = Offset(f.centre, radius + 4f * u)
        drawPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(f.centre - f.neckHalf * 1.12f, centre.y, f.centre + f.neckHalf * 1.12f, f.nutY),
                        topLeft = CornerRadius(18f * u),
                        topRight = CornerRadius(18f * u),
                        bottomLeft = CornerRadius(10f * u),
                        bottomRight = CornerRadius(10f * u),
                    ),
                )
            },
            colors.head,
        )
        drawCircle(colors.head, radius, centre)
        drawCircle(colors.mark, radius * 0.50f, centre)
        drawCircle(colors.head, radius * 0.22f, centre)
        return
    }
    drawPath(f.outline(), colors.head)
    // The app's own pick, where a real head carries its maker's name.
    val markY = min(g.pegs.minOf { it.y } * 0.52f, 34f * u)
    translate(f.centre, markY + 8f * u) { drawPath(pickPath(32f * u), colors.mark) }
}
