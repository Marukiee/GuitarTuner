package nl.markmaaktmedia.guitartuner.ui.tuner

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import nl.markmaaktmedia.guitartuner.ui.components.pickPath
import kotlin.math.max
import kotlin.math.min

/** A scalloped shape of radius one around the origin. */
private fun scallop(points: Int, inner: Float, round: Float): Path = RoundedPolygon.star(
    numVerticesPerRadius = points,
    radius = 1f,
    innerRadius = inner,
    rounding = CornerRounding(round),
    innerRounding = CornerRounding(round),
).toPath().asComposePath()

private val Cookie: Path by lazy { scallop(points = 8, inner = 0.80f, round = 0.24f) }
private val Tile: Path by lazy { scallop(points = 9, inner = 0.88f, round = 0.22f) }

/** The scalloped tile behind the small glyph of the stacked style. */
internal fun DrawScope.drawScallop(centre: Offset, radius: Float, color: Color) =
    shape(Tile, centre, radius, color)

private fun DrawScope.shape(path: Path, centre: Offset, radius: Float, color: Color, turn: Float = 0f) {
    withTransform({
        translate(centre.x, centre.y)
        rotate(turn, pivot = Offset.Zero)
        scale(radius, radius, pivot = Offset.Zero)
    }) { drawPath(path, color) }
}

/** The measurements every style shares. */
internal class Frame(val g: HeadGeometry, val style: HeadStyle) {
    val u = g.unit
    val centre = g.centre
    val half = g.crownHalf
    val neckHalf = g.neckHalf
    val nutY = g.nutY

    /** Where the straight sides end and the sweep into the neck begins, as a share of the head. */
    private val sweepFrom = if (style == HeadStyle.BLOCKS) 1f else 0.79f

    /** How much narrower the head is where the sweep starts than at its crown. */
    private val taper = when (style) {
        HeadStyle.SOFT -> 0.05f
        HeadStyle.BUTTONS -> 0.02f
        HeadStyle.BLOCKS -> 0f
    }

    /** The x of the head's edge at height [y], on [side] (-1 left, 1 right). */
    fun edge(y: Float, side: Float): Float {
        if (y >= nutY) return centre + side * neckHalf
        val v = (y / nutY).coerceIn(0f, 1f)
        val wide = half * (1f - taper * min(v / sweepFrom, 1f))
        if (v <= sweepFrom) return centre + side * wide
        val t = (v - sweepFrom) / (1f - sweepFrom)
        val eased = t * t * (3f - 2f * t)
        return centre + side * (wide + (neckHalf - wide) * eased)
    }

    /** The head's outline for the two styles that sweep into the neck. */
    fun outline(): Path {
        val top = 4f * u
        val low = half * (1f - taper)
        val from = nutY * sweepFrom
        val sweep = nutY - from
        return Path().apply {
            if (style == HeadStyle.BUTTONS) {
                // A full arch: half a circle's worth of curve, flattened a little.
                val rise = half * 0.78f
                moveTo(centre - half, top + rise)
                cubicTo(centre - half, top + rise * 0.42f, centre - half * 0.58f, top, centre, top)
                cubicTo(centre + half * 0.58f, top, centre + half, top + rise * 0.42f, centre + half, top + rise)
            } else {
                val r = 46f * u
                moveTo(centre - half, top + r)
                cubicTo(centre - half, top + r * 0.35f, centre - half + r * 0.35f, top, centre - half + r, top)
                // A dip in the middle of the crown, the one flourish this head has.
                cubicTo(centre - half * 0.35f, top, centre - half * 0.25f, top + 9f * u, centre, top + 9f * u)
                cubicTo(centre + half * 0.25f, top + 9f * u, centre + half * 0.35f, top, centre + half - r, top)
                cubicTo(centre + half - r * 0.35f, top, centre + half, top + r * 0.35f, centre + half, top + r)
            }
            lineTo(centre + low, from)
            cubicTo(centre + low, from + sweep * 0.55f, centre + neckHalf, from + sweep * 0.45f, centre + neckHalf, nutY)
            lineTo(centre - neckHalf, nutY)
            cubicTo(centre - neckHalf, from + sweep * 0.45f, centre - low, from + sweep * 0.55f, centre - low, from)
            close()
        }
    }
}

private fun roundedRect(rect: Rect, top: Float, bottom: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect = rect,
            topLeft = CornerRadius(top),
            topRight = CornerRadius(top),
            bottomRight = CornerRadius(bottom),
            bottomLeft = CornerRadius(bottom),
        ),
    )
}

/** How far above the bottom of the board the neck and the strings stop. */
internal const val NeckInsetDd = 6f

/**
 * Draws a head as flat tonal shapes: no wood, no chrome, no shadow, only the scheme's colours.
 * [reach] gives, for a style whose tuners are the note buttons, where each button sits so a stem
 * can be drawn out to it.
 */
internal fun DrawScope.drawExpressiveHead(
    g: HeadGeometry,
    style: HeadStyle,
    colors: HeadColors,
    activeIndex: Int,
    tunedIndices: Set<Int>,
    active: Color,
    tuned: Color,
    reach: List<Offset> = emptyList(),
) {
    val f = Frame(g, style)
    val u = f.u
    val bottom = size.height - NeckInsetDd * u

    fun stateColor(index: Int, idle: Color): Color = when {
        index == activeIndex -> active
        index in tunedIndices -> tuned
        else -> idle
    }

    drawNeck(f, colors, bottom)

    // Stems go under the head, so each one grows out of its side.
    if (style == HeadStyle.BUTTONS) {
        g.pegs.forEachIndexed { i, peg ->
            val to = reach.getOrNull(i) ?: return@forEachIndexed
            val side = if (peg.x < f.centre) -1f else 1f
            drawLine(
                color = colors.knob,
                start = Offset(f.edge(peg.y, side) - side * 14f * u, peg.y),
                end = to,
                strokeWidth = 20f * u,
                cap = StrokeCap.Round,
            )
        }
    }

    // The tuners of the other two styles, tucked a little under the head.
    if (style != HeadStyle.BUTTONS) {
        g.pegs.forEachIndexed { i, peg ->
            val side = if (peg.x < f.centre) -1f else 1f
            val edge = f.edge(peg.y, side)
            val color = stateColor(i, colors.knob)
            if (style == HeadStyle.SOFT) {
                val high = min(g.knobHeight, 36f * u)
                val stemEnd = edge + side * 12f * u
                drawLine(color, Offset(edge - side * 4f * u, peg.y), Offset(stemEnd, peg.y), 9f * u)
                val wide = 24f * u
                val left = if (side < 0f) stemEnd - wide else stemEnd
                drawRoundRect(color, Offset(left, peg.y - high / 2f), Size(wide, high), CornerRadius(wide / 2f))
            } else {
                val radius = min(g.knobHeight * 0.58f, 21f * u)
                val centre = Offset(edge + side * (radius + 5f * u), peg.y)
                drawLine(color, Offset(edge - side * 4f * u, peg.y), centre, 8f * u)
                shape(Cookie, centre, radius, color, turn = if (i == activeIndex) 22.5f else 0f)
            }
        }
    }

    drawBody(f, colors)
    drawNut(f, colors)

    // Strings: each from its post straight to its place at the nut, then down the neck.
    g.pegs.forEachIndexed { i, peg ->
        val color = stateColor(i, colors.onHead.copy(alpha = 0.42f))
        val weight = (4.2f - 2.4f * i / max(1, g.count - 1)) * u
        val slot = g.slots[i]
        if (g.isDrone(i)) {
            drawLine(color, peg, Offset(slot, bottom - 8f * u), weight, StrokeCap.Round)
        } else {
            val path = Path().apply {
                moveTo(peg.x, peg.y)
                lineTo(slot, f.nutY)
                // The string being tuned is drawn below the nut by the vibrating layer.
                if (i != activeIndex) lineTo(slot, bottom - 8f * u)
            }
            drawPath(path, color, style = Stroke(weight, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    // Posts on top of the strings.
    g.pegs.forEachIndexed { i, peg ->
        val color = stateColor(i, colors.post)
        val dot = stateColor(i, colors.postDot)
        when (style) {
            HeadStyle.SOFT -> {
                drawCircle(colors.post, 12f * u, peg)
                drawCircle(dot, 6f * u, peg)
            }

            HeadStyle.BUTTONS -> {
                drawCircle(color, 9f * u, peg)
                drawCircle(colors.postDot, 3.5f * u, peg)
            }

            HeadStyle.BLOCKS -> {
                shape(Cookie, peg, 13f * u, color)
                drawCircle(colors.postDot, 4f * u, peg)
            }
        }
    }
}

private fun DrawScope.drawNeck(f: Frame, colors: HeadColors, bottom: Float) {
    val u = f.u
    val left = f.centre - f.neckHalf
    val width = f.neckHalf * 2f
    val top = f.nutY
    if (f.style == HeadStyle.BLOCKS) {
        // The frets are the gaps between tiles.
        val gap = 6f * u
        val first = top + 14f * u
        val shares = floatArrayOf(0.40f, 0.33f, 0.27f)
        val room = bottom - first - gap * (shares.size - 1)
        if (room <= 0f) return
        var y = first
        shares.forEachIndexed { i, share ->
            val high = room * share
            val last = i == shares.lastIndex
            drawPath(
                roundedRect(Rect(left, y, left + width, y + high), 9f * u, if (last) 28f * u else 9f * u),
                colors.neck,
            )
            y += high + gap
        }
        return
    }
    drawPath(
        roundedRect(Rect(left, top - 4f * u, left + width, bottom), 0f, 28f * u),
        colors.neck,
    )
    for (share in floatArrayOf(0.42f, 0.74f)) {
        val y = top + (bottom - top) * share
        drawLine(
            colors.fret,
            Offset(left + 10f * u, y),
            Offset(left + width - 10f * u, y),
            4f * u,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBody(f: Frame, colors: HeadColors) {
    val u = f.u
    val g = f.g
    if (g.spec.scroll) {
        // A violin or cello: a rounded pegbox with the scroll as a ring on top.
        val radius = f.half * 0.86f
        val centre = Offset(f.centre, radius + 4f * u)
        drawPath(
            roundedRect(
                Rect(f.centre - f.neckHalf * 1.12f, centre.y, f.centre + f.neckHalf * 1.12f, f.nutY),
                18f * u,
                10f * u,
            ),
            colors.head,
        )
        drawCircle(colors.head, radius, centre)
        drawCircle(colors.accent, radius * 0.50f, centre)
        drawCircle(colors.head, radius * 0.22f, centre)
        return
    }
    val head = if (f.style == HeadStyle.BLOCKS) {
        roundedRect(
            Rect(f.centre - f.half, 4f * u, f.centre + f.half, f.nutY - 8f * u),
            52f * u,
            18f * u,
        )
    } else {
        f.outline()
    }
    drawPath(head, colors.head)

    val firstPost = g.pegs.minOf { it.y }
    val markY = min(firstPost * 0.52f, 34f * u)
    if (f.style == HeadStyle.BLOCKS) {
        shape(Cookie, Offset(f.centre, markY + 2f * u), 15f * u, colors.accent)
    } else {
        // The app's own pick, where a real head carries its maker's name.
        translate(f.centre, markY + 8f * u) { drawPath(pickPath(32f * u), colors.accent) }
    }
}

private fun DrawScope.drawNut(f: Frame, colors: HeadColors) {
    val u = f.u
    val over = if (f.style == HeadStyle.BLOCKS) 5f * u else 3f * u
    val high = if (f.style == HeadStyle.BLOCKS) 12f * u else 9f * u
    val y = if (f.style == HeadStyle.BLOCKS) f.nutY - 2f * u else f.nutY - high / 2f
    drawRoundRect(
        colors.nut,
        Offset(f.centre - f.neckHalf - over, y),
        Size((f.neckHalf + over) * 2f, high),
        CornerRadius(high / 2f),
    )
}
